package com.gagaworld.modernsocks.tunnel

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Tun2ProxyNativeTest {
    @Test
    fun sourceBuiltLibraryLoadsAndInvalidFdDoesNotStartSession() {
        val available = runCatching { Tun2ProxyNative.abiVersion() }.getOrNull()
        assumeTrue("Run with -Ptun2proxyPoc=true to package the reviewed candidate", available != null)
        assertEquals(Tun2ProxyNative.ABI_VERSION, Tun2ProxyNative.abiVersion())
        assertEquals("tun2proxy/0.8.2+tunnellens.1", Tun2ProxyNative.version())
        assertFalse(Tun2ProxyNative.isRunning())

        val result = Tun2ProxyNative.start(
            ownedTunFd = -1,
            proxyType = Tun2ProxyNative.PROXY_HTTP,
            proxyAddress = "192.0.2.10".encodeToByteArray(),
            proxyPort = 8888,
            mtu = 1500,
            username = null,
            password = null,
            dnsStrategy = Tun2ProxyNative.DNS_OVER_TCP,
            dnsAddress = "8.8.8.8".encodeToByteArray(),
            ipv6Enabled = false,
            tcpTimeoutSeconds = 600,
            udpTimeoutSeconds = 10,
            maxSessions = 200,
        )

        assertEquals(Tun2ProxyNative.BRIDGE_ERROR_INVALID_INPUT, result)
        assertFalse(Tun2ProxyNative.isRunning())
        assertEquals(Tun2ProxyNative.RESULT_OK, Tun2ProxyNative.requestStop())
        assertEquals(Tun2ProxyNative.RESULT_OK, Tun2ProxyNative.waitForExit(100))
    }
}
