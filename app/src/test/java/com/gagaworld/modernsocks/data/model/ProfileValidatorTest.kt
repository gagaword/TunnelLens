package com.gagaworld.modernsocks.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {
    @Test
    fun validDomainProfile_isNormalized() {
        val result = ProfileValidator.validate(
            ProfileDraft(
                name = "  Office  ",
                host = " proxy.example ",
                port = "1080",
                dnsServer = "dns.example",
                mtu = "1500",
            ),
        )

        assertTrue(result.isValid)
        assertEquals("Office", result.profile?.name)
        assertEquals("proxy.example", result.profile?.host)
    }

    @Test
    fun ipv4AndIpv6_areAcceptedWithoutDnsLookup() {
        assertTrue(ProfileValidator.isValidHost("192.0.2.1"))
        assertTrue(ProfileValidator.isValidHost("[2001:db8::1]"))
        assertFalse(ProfileValidator.isValidHost("999.1.1.1"))
    }

    @Test
    fun invalidPortMtuAndDns_returnFieldErrors() {
        val result = ProfileValidator.validate(
            ProfileDraft(
                name = "Test",
                host = "example.invalid",
                port = "70000",
                dnsServer = "bad host",
                mtu = "1000",
            ),
        )

        assertNull(result.profile)
        assertEquals(ValidationError.OUT_OF_RANGE, result.errors[ProfileField.PORT])
        assertEquals(ValidationError.OUT_OF_RANGE, result.errors[ProfileField.MTU])
        assertEquals(ValidationError.INVALID_HOST, result.errors[ProfileField.DNS])
    }

    @Test
    fun newAuthenticatedProfile_requiresUsernameAndPassword() {
        val result = ProfileValidator.validate(
            ProfileDraft(
                name = "Test",
                host = "example.invalid",
                authenticationEnabled = true,
            ),
        )

        assertEquals(ValidationError.REQUIRED, result.errors[ProfileField.USERNAME])
        assertEquals(ValidationError.REQUIRED, result.errors[ProfileField.PASSWORD])
    }

    @Test
    fun existingProtectedPassword_canRemainBlank() {
        val result = ProfileValidator.validate(
            ProfileDraft(
                id = 1,
                name = "Test",
                host = "example.invalid",
                authenticationEnabled = true,
                username = "user",
                password = "",
                hasStoredPassword = true,
            ),
        )

        assertTrue(result.isValid)
    }

    @Test
    fun socks5CredentialsRespectProtocolByteLimit() {
        val result = ProfileValidator.validate(
            ProfileDraft(
                name = "Test",
                host = "example.invalid",
                authenticationEnabled = true,
                username = "用".repeat(86),
                password = "密".repeat(86),
            ),
        )

        assertEquals(ValidationError.TOO_LONG, result.errors[ProfileField.USERNAME])
        assertEquals(ValidationError.TOO_LONG, result.errors[ProfileField.PASSWORD])
    }
}
