package com.gagaworld.modernsocks.legal

import androidx.test.platform.app.InstrumentationRegistry
import com.gagaworld.modernsocks.R
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceResourcesTest {
    @Test
    fun bundledGplMatchesReviewedCanonicalText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = context.resources.openRawResource(R.raw.gpl_3_0).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02X".format(byte) }

        assertEquals(
            "3972DC9744F6499F0F9B2DBF76696F2AE7AD8AF9B23DDE66D6AF86C9DFB36986",
            digest,
        )
    }

    @Test
    fun bundledNoticesCoverCurrentAndCandidateNativeComponents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notices = context.resources.openRawResource(R.raw.third_party_notices)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        assertTrue(notices.contains("hev-socks5-tunnel"))
        assertTrue(notices.contains("lwIP"))
        assertTrue(notices.contains("tun2proxy 0.8.2"))
    }
}
