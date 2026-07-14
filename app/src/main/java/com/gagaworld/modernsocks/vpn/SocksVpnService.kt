package com.gagaworld.modernsocks.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.gagaworld.modernsocks.MainActivity
import com.gagaworld.modernsocks.ModernSocksApplication
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.data.repository.ConnectionProfile
import com.gagaworld.modernsocks.data.model.AppRoutingMode
import com.gagaworld.modernsocks.data.model.AppRoutingPolicy
import com.gagaworld.modernsocks.tunnel.SocketProtector
import com.gagaworld.modernsocks.tunnel.TunnelExitResult
import com.gagaworld.modernsocks.tunnel.TunnelStartResult
import com.gagaworld.modernsocks.tunnel.TunnelStats
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SocksVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tunOwner = VpnResourceOwner()
    private val cleanupStarted = AtomicBoolean(true)
    private val nativeStarted = AtomicBoolean(false)
    private val reconnectStarted = AtomicBoolean(false)
    private val userStopping = AtomicBoolean(false)
    private val networkLock = Any()
    private val underlyingNetworks = mutableSetOf<Network>()
    private val reconnectPolicy = ReconnectPolicy()
    private val socks5Probe = Socks5Probe()
    private val container by lazy {
        (application as ModernSocksApplication).container
    }
    private var sessionJob: Job? = null
    private var nativeMonitorJob: Job? = null
    private var statsJob: Job? = null
    private var healthJob: Job? = null
    private var reconnectJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var connectedAtElapsedRealtime = 0L
    private var accumulatedUploadBytes = 0L
    private var accumulatedDownloadBytes = 0L

    @Volatile
    private var pendingTerminalState: ConnectionState? = null

    @Volatile
    private var activeGeneration: Long = NO_GENERATION

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val generation = intent?.getLongExtra(EXTRA_GENERATION, NO_GENERATION)
            ?: NO_GENERATION
        when (intent?.action) {
            ACTION_CONNECT -> handleConnect(generation)
            ACTION_STOP -> handleStop(generation)
            else -> stopSelf(startId)
        }
        return Service.START_NOT_STICKY
    }

    override fun onRevoke() {
        val generation = activeGeneration
        if (generation != NO_GENERATION) {
            userStopping.set(true)
            container.vpnStateStore.transition(generation, ConnectionState.Stopping)
            requestCleanup(
                generation = generation,
                finalState = ConnectionState.Error(ConnectionFailure.PERMISSION_REVOKED),
            )
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        userStopping.set(true)
        unregisterNetworkCallback()
        reconnectJob?.cancel()
        statsJob?.cancel()
        container.tunnelEngine.requestStop()
        container.tunnelEngine.forceStop()
        tunOwner.close()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        val generation = activeGeneration
        if (generation != NO_GENERATION) {
            val terminalState = pendingTerminalState
            if (terminalState != null) {
                container.vpnStateStore.transition(generation, terminalState)
            } else if (!cleanupStarted.get()) {
                container.vpnStateStore.transition(generation, ConnectionState.Stopping)
                container.vpnStateStore.transition(generation, ConnectionState.Disconnected)
            }
        }
        pendingTerminalState = null
        cleanupStarted.set(true)
        activeGeneration = NO_GENERATION
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleConnect(generation: Long) {
        if (generation == NO_GENERATION) {
            stopSelf()
            return
        }
        val snapshot = container.vpnStateStore.snapshot()
        if (snapshot.generation != generation || snapshot.state !is ConnectionState.Starting) {
            stopSelf()
            return
        }
        activeGeneration = generation
        cleanupStarted.set(false)
        userStopping.set(false)
        reconnectStarted.set(false)
        pendingTerminalState = null
        connectedAtElapsedRealtime = 0L
        accumulatedUploadBytes = 0L
        accumulatedDownloadBytes = 0L
        container.vpnStateStore.updateMetrics(generation, ConnectionMetrics())
        try {
            startInForeground(active = false, generation = generation)
            registerNetworkCallback(generation)
        } catch (_: Exception) {
            fail(generation, ConnectionFailure.SERVICE_START_FAILED)
            return
        }
        sessionJob?.cancel()
        sessionJob = serviceScope.launch {
            when (val result = startTunnelAttempt(generation)) {
                AttemptResult.Success -> Unit
                is AttemptResult.DeterministicFailure -> fail(generation, result.failure)
                is AttemptResult.TransientFailure -> requestReconnect(generation, result.failure)
            }
        }
    }

    private fun handleStop(requestedGeneration: Long) {
        val generation = activeGeneration.takeIf { it != NO_GENERATION }
            ?: requestedGeneration
        if (generation == NO_GENERATION) {
            stopSelf()
            return
        }
        userStopping.set(true)
        reconnectJob?.cancel()
        reconnectStarted.set(false)
        container.vpnStateStore.transition(generation, ConnectionState.Stopping)
        requestCleanup(generation, ConnectionState.Disconnected)
    }

    private suspend fun startTunnelAttempt(generation: Long): AttemptResult {
        if (!isCurrentGeneration(generation) || userStopping.get()) {
            return AttemptResult.DeterministicFailure(ConnectionFailure.UNEXPECTED)
        }
        val profile = runCatching { container.profileRepository.getSelectedConnectionProfile() }
            .getOrElse {
                return AttemptResult.DeterministicFailure(ConnectionFailure.NATIVE_START_FAILED)
            }
            ?: return AttemptResult.DeterministicFailure(ConnectionFailure.NO_PROFILE)
        val appRoutingPolicy = resolveAppRoutingPolicy(profile)
            ?: return AttemptResult.DeterministicFailure(ConnectionFailure.APP_ROUTING_INVALID)

        // No VPN interface exists yet, so this socket cannot loop through the tunnel.
        // Calling VpnService.protect() here is both unnecessary and device-dependent.
        when (socks5Probe.probe(profile, protector = null)) {
            Socks5ProbeResult.Success -> Unit
            Socks5ProbeResult.AuthenticationFailed ->
                return AttemptResult.DeterministicFailure(ConnectionFailure.AUTHENTICATION_FAILED)
            Socks5ProbeResult.ProtocolError ->
                return AttemptResult.DeterministicFailure(ConnectionFailure.SOCKS_PROTOCOL_ERROR)
            Socks5ProbeResult.Unreachable ->
                return AttemptResult.TransientFailure(ConnectionFailure.PROXY_UNREACHABLE)
        }
        if (!isStartableState(generation) || userStopping.get()) {
            return AttemptResult.DeterministicFailure(ConnectionFailure.UNEXPECTED)
        }
        val descriptor = establishInterface(profile, appRoutingPolicy)
            ?: return AttemptResult.TransientFailure(ConnectionFailure.TUN_ESTABLISH_FAILED)
        if (!tunOwner.attach(descriptor)) {
            return AttemptResult.DeterministicFailure(ConnectionFailure.UNEXPECTED)
        }
        val ownedNativeFd = runCatching {
            ParcelFileDescriptor.dup(descriptor.fileDescriptor).detachFd()
        }.getOrElse {
            tunOwner.close()
            return AttemptResult.TransientFailure(ConnectionFailure.NATIVE_START_FAILED)
        }
        val startResult = container.tunnelEngine.start(
            ownedTunFd = ownedNativeFd,
            profile = profile,
            protector = SocketProtector(::protect),
        )
        if (startResult != TunnelStartResult.STARTED) {
            tunOwner.close()
            return AttemptResult.TransientFailure(ConnectionFailure.NATIVE_START_FAILED)
        }
        nativeStarted.set(true)
        delay(NATIVE_STARTUP_GRACE_MILLIS)
        if (!container.tunnelEngine.isRunning()) {
            stopTunnelResources()
            return AttemptResult.TransientFailure(ConnectionFailure.NATIVE_EXITED)
        }
        if (!container.vpnStateStore.transition(generation, ConnectionState.Connected)) {
            stopTunnelResources()
            return AttemptResult.DeterministicFailure(ConnectionFailure.UNEXPECTED)
        }
        if (connectedAtElapsedRealtime == 0L) {
            connectedAtElapsedRealtime = SystemClock.elapsedRealtime()
        }
        reconnectStarted.set(false)
        container.vpnStateStore.updateMetrics(
            generation,
            currentMetrics(reconnectAttempt = 0),
        )
        startStats(generation)
        startHealthMonitor(generation, profile)
        nativeMonitorJob?.cancel()
        nativeMonitorJob = serviceScope.launch { monitorNativeExit(generation) }
        return try {
            startInForeground(active = true, generation = generation)
            AttemptResult.Success
        } catch (_: Exception) {
            AttemptResult.DeterministicFailure(ConnectionFailure.SERVICE_START_FAILED)
        }
    }

    private fun requestReconnect(generation: Long, failure: ConnectionFailure) {
        if (!isCurrentGeneration(generation) || userStopping.get() || cleanupStarted.get()) return
        if (!reconnectStarted.compareAndSet(false, true)) return
        reconnectJob = serviceScope.launch {
            val profile = runCatching { container.profileRepository.getSelectedConnectionProfile() }
                .getOrNull()
            val appAutoReconnect = runCatching {
                container.settingsRepository.settings.first().autoConnect
            }.getOrDefault(false)
            if (profile == null || !profile.autoReconnect || !appAutoReconnect) {
                reconnectStarted.set(false)
                fail(generation, failure)
                return@launch
            }
            if (!container.vpnStateStore.transition(generation, ConnectionState.Reconnecting)) {
                reconnectStarted.set(false)
                return@launch
            }
            if (!stopTunnelResources()) {
                reconnectStarted.set(false)
                fail(generation, ConnectionFailure.NATIVE_STOP_TIMEOUT)
                return@launch
            }
            for (attempt in 1..reconnectPolicy.maxAttempts) {
                if (!isCurrentGeneration(generation) || userStopping.get()) return@launch
                container.vpnStateStore.updateMetrics(
                    generation,
                    currentMetrics(reconnectAttempt = attempt),
                )
                delay(checkNotNull(reconnectPolicy.delayForAttempt(attempt)))
                if (!hasUnderlyingNetwork()) continue
                when (val result = startTunnelAttempt(generation)) {
                    AttemptResult.Success -> return@launch
                    is AttemptResult.DeterministicFailure -> {
                        reconnectStarted.set(false)
                        fail(generation, result.failure)
                        return@launch
                    }
                    is AttemptResult.TransientFailure -> Unit
                }
            }
            reconnectStarted.set(false)
            fail(generation, ConnectionFailure.RECONNECT_EXHAUSTED)
        }
    }

    private fun establishInterface(
        profile: ConnectionProfile,
        appRoutingPolicy: AppRoutingPolicy,
    ): ParcelFileDescriptor? = try {
        val builder = Builder()
            .setSession(getString(R.string.vpn_session_name))
            .setMtu(profile.mtu)
            .addAddress(TUN_ADDRESS, TUN_PREFIX)
            .setBlocking(false)
        VpnIpv4RoutePolicy.routes(profile.bypassLan).forEach { route ->
            builder.addRoute(route.address, route.prefixLength)
        }
        when (appRoutingPolicy.mode) {
            AppRoutingMode.ALL_APPS -> Unit
            AppRoutingMode.ONLY_SELECTED -> appRoutingPolicy.packages.forEach {
                builder.addAllowedApplication(it)
            }
            AppRoutingMode.BYPASS_SELECTED -> appRoutingPolicy.packages.forEach {
                builder.addDisallowedApplication(it)
            }
        }
        builder.establish()
    } catch (_: Exception) {
        null
    }

    private fun resolveAppRoutingPolicy(profile: ConnectionProfile): AppRoutingPolicy? {
        val policy = profile.appRoutingPolicy
        if (policy.mode == AppRoutingMode.ALL_APPS) return policy.copy(packages = emptySet())
        val installed = policy.packages.filterTo(linkedSetOf()) { packageName ->
            runCatching { packageManager.getApplicationInfo(packageName, 0) }.isSuccess
        }
        return policy.copy(packages = installed).takeIf(AppRoutingPolicy::isSafe)
    }

    private fun monitorNativeExit(generation: Long) {
        while (serviceScope.isActive && isCurrentGeneration(generation)) {
            val result = container.tunnelEngine.awaitExit(NATIVE_MONITOR_INTERVAL_MILLIS)
            if (result == TunnelExitResult.TIMED_OUT) continue
            nativeStarted.set(false)
            if (!cleanupStarted.get() && !userStopping.get()) {
                requestReconnect(generation, ConnectionFailure.NATIVE_EXITED)
            }
            return
        }
    }

    private fun startStats(generation: Long) {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            while (isActive && isCurrentGeneration(generation) && nativeStarted.get()) {
                container.vpnStateStore.updateMetrics(
                    generation,
                    currentMetrics(reconnectAttempt = 0),
                )
                delay(STATS_INTERVAL_MILLIS)
            }
        }
    }

    private fun startHealthMonitor(generation: Long, profile: ConnectionProfile) {
        healthJob?.cancel()
        healthJob = serviceScope.launch {
            var consecutiveFailures = 0
            while (isActive && isCurrentGeneration(generation) && nativeStarted.get()) {
                delay(HEALTH_CHECK_INTERVAL_MILLIS)
                if (!isActive || userStopping.get() || !nativeStarted.get()) return@launch
                when (socks5Probe.probe(profile, JavaSocketProtector(::protect))) {
                    Socks5ProbeResult.Success -> consecutiveFailures = 0
                    Socks5ProbeResult.Unreachable -> {
                        consecutiveFailures += 1
                        if (consecutiveFailures >= HEALTH_CHECK_FAILURE_THRESHOLD) {
                            requestReconnect(generation, ConnectionFailure.PROXY_UNREACHABLE)
                            return@launch
                        }
                    }
                    Socks5ProbeResult.AuthenticationFailed -> {
                        fail(generation, ConnectionFailure.AUTHENTICATION_FAILED)
                        return@launch
                    }
                    Socks5ProbeResult.ProtocolError -> {
                        fail(generation, ConnectionFailure.SOCKS_PROTOCOL_ERROR)
                        return@launch
                    }
                }
            }
        }
    }

    private fun currentMetrics(reconnectAttempt: Int): ConnectionMetrics {
        val stats = if (nativeStarted.get()) {
            container.tunnelEngine.stats()
        } else {
            TunnelStats()
        }
        val duration = connectedAtElapsedRealtime.takeIf { it > 0 }?.let {
            SystemClock.elapsedRealtime() - it
        } ?: 0L
        return ConnectionMetrics(
            connectedDurationMillis = duration.coerceAtLeast(0),
            uploadedBytes = accumulatedUploadBytes + stats.uploadedBytes,
            downloadedBytes = accumulatedDownloadBytes + stats.downloadedBytes,
            reconnectAttempt = reconnectAttempt,
        )
    }

    private fun captureNativeStats() {
        if (!nativeStarted.get()) return
        val stats = container.tunnelEngine.stats()
        accumulatedUploadBytes += stats.uploadedBytes
        accumulatedDownloadBytes += stats.downloadedBytes
    }

    private fun stopTunnelResources(): Boolean {
        statsJob?.cancel()
        statsJob = null
        healthJob?.cancel()
        healthJob = null
        nativeMonitorJob?.cancel()
        nativeMonitorJob = null
        captureNativeStats()
        container.tunnelEngine.requestStop()
        tunOwner.close()
        var result = if (nativeStarted.get()) {
            container.tunnelEngine.awaitExit(NATIVE_STOP_TIMEOUT_MILLIS)
        } else {
            TunnelExitResult.EXITED
        }
        if (result == TunnelExitResult.TIMED_OUT) {
            container.tunnelEngine.forceStop()
            result = container.tunnelEngine.awaitExit(NATIVE_FORCE_CLOSE_WAIT_MILLIS)
        }
        nativeStarted.set(false)
        return result != TunnelExitResult.TIMED_OUT
    }

    private fun fail(generation: Long, failure: ConnectionFailure) {
        if (!isCurrentGeneration(generation)) return
        container.vpnStateStore.transition(generation, ConnectionState.Error(failure))
        requestCleanup(generation, finalState = null)
    }

    private fun requestCleanup(generation: Long, finalState: ConnectionState?) {
        if (!isCurrentGeneration(generation) && activeGeneration != NO_GENERATION) return
        if (!cleanupStarted.compareAndSet(false, true)) return
        serviceScope.launch {
            reconnectJob?.takeUnless { it === coroutineContext[Job] }?.cancel()
            reconnectJob = null
            reconnectStarted.set(false)
            sessionJob?.takeUnless { it === coroutineContext[Job] }?.cancel()
            unregisterNetworkCallback()
            val stopped = stopTunnelResources()
            sessionJob = null
            ServiceCompat.stopForeground(
                this@SocksVpnService,
                ServiceCompat.STOP_FOREGROUND_REMOVE,
            )
            val terminalState = if (!stopped) {
                ConnectionState.Error(ConnectionFailure.NATIVE_STOP_TIMEOUT)
            } else {
                finalState
            }
            pendingTerminalState = terminalState
            stopSelf()
        }
    }

    private fun registerNetworkCallback(generation: Long) {
        if (networkCallback != null) return
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(networkLock) { underlyingNetworks += network }
            }

            override fun onLost(network: Network) {
                val empty = synchronized(networkLock) {
                    underlyingNetworks -= network
                    underlyingNetworks.isEmpty()
                }
                if (empty && container.vpnStateStore.snapshot().state is ConnectionState.Connected) {
                    requestReconnect(generation, ConnectionFailure.NETWORK_UNAVAILABLE)
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        networkCallback = callback
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        synchronized(networkLock) { underlyingNetworks.clear() }
        runCatching {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback)
        }
    }

    private fun hasUnderlyingNetwork(): Boolean =
        synchronized(networkLock) { underlyingNetworks.isNotEmpty() }

    private fun isCurrentGeneration(generation: Long): Boolean =
        generation == activeGeneration && generation != NO_GENERATION

    private fun isStartableState(generation: Long): Boolean {
        val snapshot = container.vpnStateStore.snapshot()
        return snapshot.generation == generation &&
            (snapshot.state is ConnectionState.Starting ||
                snapshot.state is ConnectionState.Reconnecting)
    }

    private fun startInForeground(active: Boolean, generation: Long) {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(active, generation),
            foregroundType,
        )
    }

    private fun buildNotification(active: Boolean, generation: Long) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_status)
            .setContentTitle(
                getString(
                    if (active) R.string.vpn_notification_active_title
                    else R.string.vpn_notification_starting_title,
                ),
            )
            .setContentText(
                getString(
                    if (active) R.string.vpn_notification_active_text
                    else R.string.vpn_notification_starting_text,
                ),
            )
            .setContentIntent(contentPendingIntent())
            .addAction(0, getString(R.string.vpn_notification_stop), stopPendingIntent(generation))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun contentPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopPendingIntent(generation: Long): PendingIntent = PendingIntent.getService(
        this,
        REQUEST_STOP,
        stopIntent(this, generation),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.vpn_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.vpn_notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private sealed interface AttemptResult {
        data object Success : AttemptResult
        data class TransientFailure(val failure: ConnectionFailure) : AttemptResult
        data class DeterministicFailure(val failure: ConnectionFailure) : AttemptResult
    }

    companion object {
        private const val ACTION_CONNECT = "com.gagaworld.modernsocks.action.CONNECT_VPN"
        private const val ACTION_STOP = "com.gagaworld.modernsocks.action.STOP_VPN"
        private const val EXTRA_GENERATION = "vpn_generation"
        private const val NOTIFICATION_CHANNEL_ID = "vpn_connection"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN_APP = 100
        private const val REQUEST_STOP = 101
        private const val NO_GENERATION = -1L
        private const val TUN_ADDRESS = "198.18.0.1"
        private const val TUN_PREFIX = 32
        private const val NATIVE_STARTUP_GRACE_MILLIS = 250L
        private const val NATIVE_MONITOR_INTERVAL_MILLIS = 1_000L
        private const val NATIVE_STOP_TIMEOUT_MILLIS = 3_000L
        private const val NATIVE_FORCE_CLOSE_WAIT_MILLIS = 2_000L
        private const val STATS_INTERVAL_MILLIS = 1_000L
        private const val HEALTH_CHECK_INTERVAL_MILLIS = 15_000L
        private const val HEALTH_CHECK_FAILURE_THRESHOLD = 2

        fun connectIntent(context: Context, generation: Long): Intent =
            Intent(context, SocksVpnService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_GENERATION, generation)

        fun stopIntent(context: Context, generation: Long): Intent =
            Intent(context, SocksVpnService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_GENERATION, generation)
    }
}
