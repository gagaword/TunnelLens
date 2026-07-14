package com.gagaworld.modernsocks.data.transfer

import com.gagaworld.modernsocks.testProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileTransferCodecTest {
    @Test
    fun exportContainsConfigurationButNoCredentialFields() {
        val encoded = ProfileTransferCodec.encode(
            listOf(testProfile().copy(authenticationEnabled = true, hasStoredCredentials = true)),
        )

        assertFalse(encoded.contains("password", ignoreCase = true))
        assertFalse(encoded.contains("username", ignoreCase = true))
        assertFalse(encoded.contains("ciphertext", ignoreCase = true))
        assertEquals("example.invalid", ProfileTransferCodec.decode(encoded).single().host)
        assertFalse(ProfileTransferCodec.decode(encoded).single().authenticationEnabled)
    }

    @Test
    fun malformedOrInvalidImport_isRejectedBeforePersistence() {
        assertThrows(ProfileTransferException::class.java) {
            ProfileTransferCodec.decode("{\"formatVersion\":1,\"profiles\":[{\"name\":\"Bad\",\"host\":\"bad host\",\"port\":1080}]}")
        }
    }
}
