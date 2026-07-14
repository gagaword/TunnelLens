package com.gagaworld.modernsocks.tunnel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gagaworld.modernsocks.data.repository.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeTunnelTest {
    @Test
    fun pinnedLibraryLoadsAndRejectsInvalidDescriptor() {
        assertEquals("2.14.4-4d6c334-ms1", NativeTunnel.version())
        assertFalse(NativeTunnel.isRunning())

        val result = NativeTunnel.start(
            ownedTunFd = -1,
            config = byteArrayOf(1),
            protector = SocketProtector { true },
        )

        assertTrue(result < 0)
        assertFalse(NativeTunnel.isRunning())
    }

    @Test
    fun hevEngineAdapterLoadsPinnedRuntimeAndMapsRejectedStart() {
        val engine = HevSocks5TunnelEngine()

        assertEquals("2.14.4-4d6c334-ms1", engine.version())
        assertEquals(
            TunnelStartResult.FAILED,
            engine.start(
                ownedTunFd = -1,
                profile = ConnectionProfile(
                    id = 1,
                    host = "127.0.0.1",
                    port = 1080,
                    mtu = 1500,
                    autoReconnect = false,
                ),
                protector = SocketProtector { true },
            ),
        )
        assertFalse(engine.isRunning())
    }
}
