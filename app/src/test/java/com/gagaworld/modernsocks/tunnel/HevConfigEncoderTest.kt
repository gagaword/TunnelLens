package com.gagaworld.modernsocks.tunnel

import com.gagaworld.modernsocks.data.repository.ConnectionProfile
import com.gagaworld.modernsocks.security.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HevConfigEncoderTest {
    @Test
    fun credentialsAreQuotedWithoutAllowingYamlInjection() {
        val encoded = HevConfigEncoder.encode(
            ConnectionProfile(
                id = 1,
                host = "proxy.example",
                port = 1080,
                mtu = 1500,
                autoReconnect = true,
                credentials = Credentials(
                    username = "user\nadmin: true",
                    password = "quote\" slash\\ tab\t",
                ),
            ),
        ).toString(Charsets.UTF_8)

        assertTrue(encoded.contains("username: \"user\\nadmin: true\""))
        assertTrue(encoded.contains("password: \"quote\\\" slash\\\\ tab\\t\""))
        assertEquals(1, Regex("(?m)^misc:$").findAll(encoded).count())
        assertFalse(encoded.contains("\nadmin: true\n"))
    }

    @Test
    fun unauthenticatedConfigOmitsCredentialKeysAndUdpMode() {
        val encoded = HevConfigEncoder.encode(
            ConnectionProfile(1, "192.0.2.1", 1080, 1500, true, credentials = null),
        ).toString(Charsets.UTF_8)

        assertFalse(encoded.contains("username:"))
        assertFalse(encoded.contains("password:"))
        assertFalse(encoded.contains("udp:"))
        assertTrue(encoded.contains("log-file: \"/dev/null\""))
    }
}
