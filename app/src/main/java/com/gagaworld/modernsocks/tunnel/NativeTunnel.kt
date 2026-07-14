package com.gagaworld.modernsocks.tunnel

import androidx.annotation.Keep

@Keep
fun interface SocketProtector {
    @Keep
    fun protect(fd: Int): Boolean
}

@Keep
object NativeTunnel {
    init {
        System.loadLibrary("modern_socks_tunnel")
    }

    @Keep
    external fun start(ownedTunFd: Int, config: ByteArray, protector: SocketProtector): Int

    @Keep
    external fun stop()

    @Keep
    external fun forceCloseTun()

    @Keep
    external fun forceShutdownSockets()

    @Keep
    external fun waitForExit(timeoutMillis: Long): Int

    @Keep
    external fun isRunning(): Boolean

    @Keep
    external fun getStats(): LongArray

    @Keep
    external fun version(): String

    const val RESULT_OK = 0
    const val RESULT_WAIT_TIMEOUT = -105
}
