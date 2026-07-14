package com.gagaworld.modernsocks.tunnel

import com.gagaworld.modernsocks.data.repository.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HevSocks5TunnelEngineTest {
    @Test
    fun startClearsEncodedConfigurationAfterNativeCall() {
        val native = FakeHevNativeBindings()
        val engine = HevSocks5TunnelEngine(native)

        val result = engine.start(
            ownedTunFd = 42,
            profile = connectionProfile(),
            protector = SocketProtector { true },
        )

        assertEquals(TunnelStartResult.STARTED, result)
        assertTrue(native.receivedConfig.all { it == 0.toByte() })
    }

    @Test
    fun nativeResultsAreMappedToEngineContract() {
        val native = FakeHevNativeBindings().apply {
            waitResult = NativeTunnel.RESULT_WAIT_TIMEOUT
            statsValues = longArrayOf(1, 2, 3, 4)
        }
        val engine = HevSocks5TunnelEngine(native)

        assertEquals(TunnelExitResult.TIMED_OUT, engine.awaitExit(100))
        assertEquals(TunnelStats(uploadedBytes = 2, downloadedBytes = 4), engine.stats())
        assertEquals("test-hev", engine.version())
    }

    private fun connectionProfile() = ConnectionProfile(
        id = 1,
        host = "127.0.0.1",
        port = 1080,
        mtu = 1500,
        autoReconnect = false,
    )
}

private class FakeHevNativeBindings : HevNativeBindings {
    lateinit var receivedConfig: ByteArray
    var waitResult: Int = NativeTunnel.RESULT_OK
    var statsValues: LongArray = LongArray(4)

    override fun start(
        ownedTunFd: Int,
        config: ByteArray,
        protector: SocketProtector,
    ): Int {
        receivedConfig = config
        return NativeTunnel.RESULT_OK
    }

    override fun stop() = Unit
    override fun forceCloseTun() = Unit
    override fun forceShutdownSockets() = Unit
    override fun waitForExit(timeoutMillis: Long): Int = waitResult
    override fun isRunning(): Boolean = true
    override fun getStats(): LongArray = statsValues
    override fun version(): String = "test-hev"
}
