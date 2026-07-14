package com.gagaworld.modernsocks.tunnel

import android.os.ParcelFileDescriptor
import com.gagaworld.modernsocks.data.repository.ConnectionProfile

class HevSocks5TunnelEngine internal constructor(
    private val native: HevNativeBindings = JniHevNativeBindings,
) : TunnelEngine {
    override fun start(
        ownedTunFd: Int,
        profile: ConnectionProfile,
        protector: SocketProtector,
    ): TunnelStartResult {
        val config = try {
            HevConfigEncoder.encode(profile)
        } catch (_: Exception) {
            closeOwnedFd(ownedTunFd)
            return TunnelStartResult.FAILED
        }
        return try {
            runCatching { native.start(ownedTunFd, config, protector) }
                .fold(
                    onSuccess = { result ->
                        if (result == NativeTunnel.RESULT_OK) {
                            TunnelStartResult.STARTED
                        } else {
                            TunnelStartResult.FAILED
                        }
                    },
                    onFailure = {
                        closeOwnedFd(ownedTunFd)
                        TunnelStartResult.FAILED
                    },
                )
        } finally {
            config.fill(0)
        }
    }

    override fun requestStop() = native.stop()

    override fun forceStop() {
        native.forceShutdownSockets()
        native.forceCloseTun()
    }

    override fun awaitExit(timeoutMillis: Long): TunnelExitResult =
        if (native.waitForExit(timeoutMillis) == NativeTunnel.RESULT_WAIT_TIMEOUT) {
            TunnelExitResult.TIMED_OUT
        } else {
            TunnelExitResult.EXITED
        }

    override fun isRunning(): Boolean = native.isRunning()

    override fun stats(): TunnelStats {
        val values = native.getStats()
        return TunnelStats(
            uploadedBytes = values.getOrElse(1) { 0L }.coerceAtLeast(0),
            downloadedBytes = values.getOrElse(3) { 0L }.coerceAtLeast(0),
        )
    }

    override fun version(): String = native.version()

    private fun closeOwnedFd(fd: Int) {
        if (fd < 0) return
        runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
    }
}

internal interface HevNativeBindings {
    fun start(ownedTunFd: Int, config: ByteArray, protector: SocketProtector): Int
    fun stop()
    fun forceCloseTun()
    fun forceShutdownSockets()
    fun waitForExit(timeoutMillis: Long): Int
    fun isRunning(): Boolean
    fun getStats(): LongArray
    fun version(): String
}

private object JniHevNativeBindings : HevNativeBindings {
    override fun start(
        ownedTunFd: Int,
        config: ByteArray,
        protector: SocketProtector,
    ): Int = NativeTunnel.start(ownedTunFd, config, protector)

    override fun stop() = NativeTunnel.stop()

    override fun forceCloseTun() = NativeTunnel.forceCloseTun()

    override fun forceShutdownSockets() = NativeTunnel.forceShutdownSockets()

    override fun waitForExit(timeoutMillis: Long): Int =
        NativeTunnel.waitForExit(timeoutMillis)

    override fun isRunning(): Boolean = NativeTunnel.isRunning()

    override fun getStats(): LongArray = NativeTunnel.getStats()

    override fun version(): String = NativeTunnel.version()
}
