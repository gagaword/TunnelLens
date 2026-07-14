package com.gagaworld.modernsocks.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface VpnPreparation {
    fun prepare(): Intent?
}

interface VpnServiceCommands {
    fun connect(generation: Long)
    fun stop(generation: Long)
}

class AndroidVpnPreparation(
    private val context: Context,
) : VpnPreparation {
    override fun prepare(): Intent? = VpnService.prepare(context)
}

class AndroidVpnServiceCommands(
    private val context: Context,
) : VpnServiceCommands {
    override fun connect(generation: Long) {
        ContextCompat.startForegroundService(
            context,
            SocksVpnService.connectIntent(context, generation),
        )
    }

    override fun stop(generation: Long) {
        context.startService(SocksVpnService.stopIntent(context, generation))
    }
}

class AndroidVpnController(
    private val stateStore: VpnConnectionStateStore,
    private val preparation: VpnPreparation,
    private val serviceCommands: VpnServiceCommands,
) : VpnController {
    private val operationMutex = Mutex()
    private val mutablePermissionRequest = MutableStateFlow<VpnPermissionRequest?>(null)

    override val connectionState: StateFlow<ConnectionState> = stateStore.state
    override val connectionMetrics: StateFlow<ConnectionMetrics> = stateStore.metrics
    override val permissionRequest: StateFlow<VpnPermissionRequest?> =
        mutablePermissionRequest.asStateFlow()

    override suspend fun connect() = operationMutex.withLock {
        val generation = stateStore.beginConnection() ?: return@withLock
        try {
            val permissionIntent = preparation.prepare()
            if (permissionIntent == null) {
                startService(generation)
            } else {
                mutablePermissionRequest.value = VpnPermissionRequest(
                    generation = generation,
                    intent = permissionIntent,
                )
            }
        } catch (_: Exception) {
            mutablePermissionRequest.value = null
            stateStore.transition(
                generation,
                ConnectionState.Error(ConnectionFailure.SERVICE_START_FAILED),
            )
        }
    }

    override suspend fun onPermissionResult(granted: Boolean) = operationMutex.withLock {
        val request = mutablePermissionRequest.value ?: return@withLock
        mutablePermissionRequest.value = null
        val snapshot = stateStore.snapshot()
        if (snapshot.generation != request.generation ||
            snapshot.state !is ConnectionState.PreparingPermission
        ) {
            return@withLock
        }
        if (granted) {
            startService(request.generation)
        } else {
            stateStore.transition(
                request.generation,
                ConnectionState.Error(ConnectionFailure.PERMISSION_DENIED),
            )
        }
    }

    override suspend fun disconnect() = operationMutex.withLock {
        val snapshot = stateStore.snapshot()
        when (snapshot.state) {
            ConnectionState.Disconnected -> Unit
            ConnectionState.PreparingPermission -> {
                mutablePermissionRequest.value = null
                stateStore.transition(snapshot.generation, ConnectionState.Disconnected)
            }
            is ConnectionState.Error -> {
                mutablePermissionRequest.value = null
                stateStore.transition(snapshot.generation, ConnectionState.Disconnected)
            }
            ConnectionState.Starting,
            ConnectionState.Connected,
            ConnectionState.Reconnecting,
            -> {
                if (!stateStore.transition(snapshot.generation, ConnectionState.Stopping)) {
                    return@withLock
                }
                try {
                    serviceCommands.stop(snapshot.generation)
                } catch (_: Exception) {
                    stateStore.transition(
                        snapshot.generation,
                        ConnectionState.Error(ConnectionFailure.SERVICE_STOP_FAILED),
                    )
                }
            }
            ConnectionState.Stopping -> Unit
        }
    }

    private fun startService(generation: Long) {
        if (!stateStore.transition(generation, ConnectionState.Starting)) return
        try {
            serviceCommands.connect(generation)
        } catch (_: Exception) {
            stateStore.transition(
                generation,
                ConnectionState.Error(ConnectionFailure.SERVICE_START_FAILED),
            )
        }
    }
}
