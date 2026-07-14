package com.gagaworld.modernsocks.tunnel

import androidx.annotation.Keep

/**
 * Typed JNI boundary for the source-built tun2proxy candidate.
 *
 * The current VPN service intentionally remains on [HevSocks5TunnelEngine]
 * until proxy-route exclusion and end-to-end HTTP/SOCKS5 lifecycle tests pass.
 * Callers own and must clear credential byte arrays after [start] returns.
 */
@Keep
internal object Tun2ProxyNative {
    init {
        System.loadLibrary("tunnellens_tun2proxy")
        System.loadLibrary("modern_socks_tunnel")
    }

    @Keep
    external fun abiVersion(): Int

    @Keep
    external fun version(): String

    @Keep
    external fun start(
        ownedTunFd: Int,
        proxyType: Int,
        proxyAddress: ByteArray,
        proxyPort: Int,
        mtu: Int,
        username: ByteArray?,
        password: ByteArray?,
        dnsStrategy: Int,
        dnsAddress: ByteArray,
        ipv6Enabled: Boolean,
        tcpTimeoutSeconds: Long,
        udpTimeoutSeconds: Long,
        maxSessions: Int,
    ): Int

    @Keep
    external fun requestStop(): Int

    @Keep
    external fun waitForExit(timeoutMillis: Long): Int

    @Keep
    external fun isRunning(): Boolean

    @Keep
    external fun lastExitCode(): Int

    @Keep
    external fun getStats(): LongArray

    const val ABI_VERSION = 1
    const val PROXY_SOCKS5 = 1
    const val PROXY_HTTP = 2
    const val DNS_DIRECT = 0
    const val DNS_OVER_TCP = 1
    const val DNS_VIRTUAL = 2
    const val RESULT_OK = 0
    const val RESULT_TIMEOUT = 1
    const val ERROR_INVALID_CONFIG = -10
    const val BRIDGE_ERROR_INVALID_INPUT = -1001
}
