package com.gagaworld.modernsocks.data.model

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.charset.StandardCharsets

enum class ProfileField {
    NAME,
    HOST,
    PORT,
    USERNAME,
    PASSWORD,
    DNS,
    MTU,
}

enum class ValidationError {
    REQUIRED,
    TOO_LONG,
    INVALID_HOST,
    INVALID_NUMBER,
    OUT_OF_RANGE,
}

data class ValidatedProfile(
    val id: Long?,
    val name: String,
    val host: String,
    val port: Int,
    val authenticationEnabled: Boolean,
    val username: String,
    val password: String,
    val hasStoredPassword: Boolean,
    val dnsServer: String?,
    val mtu: Int,
    val bypassLan: Boolean,
    val autoReconnect: Boolean,
)

data class ProfileValidationResult(
    val profile: ValidatedProfile?,
    val errors: Map<ProfileField, ValidationError>,
) {
    val isValid: Boolean
        get() = profile != null && errors.isEmpty()
}

object ProfileValidator {
    const val MIN_MTU = 1280
    const val MAX_MTU = 9000

    fun validate(draft: ProfileDraft): ProfileValidationResult {
        val errors = linkedMapOf<ProfileField, ValidationError>()
        val name = draft.name.trim()
        val host = normalizeHost(draft.host)
        val port = parseNumber(draft.port, ProfileField.PORT, 1..65535, errors)
        val mtu = parseNumber(draft.mtu, ProfileField.MTU, MIN_MTU..MAX_MTU, errors)
        val dns = draft.dnsServer.trim().ifEmpty { null }

        when {
            name.isEmpty() -> errors[ProfileField.NAME] = ValidationError.REQUIRED
            name.length > 64 -> errors[ProfileField.NAME] = ValidationError.TOO_LONG
        }
        when {
            host.isEmpty() -> errors[ProfileField.HOST] = ValidationError.REQUIRED
            !isValidHost(host) -> errors[ProfileField.HOST] = ValidationError.INVALID_HOST
        }
        if (dns != null && !isValidHost(normalizeHost(dns))) {
            errors[ProfileField.DNS] = ValidationError.INVALID_HOST
        }
        if (draft.authenticationEnabled) {
            when {
                draft.username.isBlank() -> errors[ProfileField.USERNAME] = ValidationError.REQUIRED
                draft.username.toByteArray(StandardCharsets.UTF_8).size > 255 ->
                    errors[ProfileField.USERNAME] = ValidationError.TOO_LONG
            }
            when {
                draft.password.isEmpty() && !draft.hasStoredPassword -> {
                    errors[ProfileField.PASSWORD] = ValidationError.REQUIRED
                }
                draft.password.toByteArray(StandardCharsets.UTF_8).size > 255 ->
                    errors[ProfileField.PASSWORD] = ValidationError.TOO_LONG
            }
        }

        val profile = if (errors.isEmpty() && port != null && mtu != null) {
            ValidatedProfile(
                id = draft.id,
                name = name,
                host = host,
                port = port,
                authenticationEnabled = draft.authenticationEnabled,
                username = draft.username,
                password = draft.password,
                hasStoredPassword = draft.hasStoredPassword,
                dnsServer = dns?.let(::normalizeHost),
                mtu = mtu,
                bypassLan = draft.bypassLan,
                autoReconnect = draft.autoReconnect,
            )
        } else {
            null
        }
        return ProfileValidationResult(profile = profile, errors = errors)
    }

    fun isValidHost(value: String): Boolean {
        val host = normalizeHost(value)
        if (host.isEmpty() || host.length > 253 || host.any(Char::isWhitespace)) return false
        if (host.contains(':')) return isValidIpv6(host)
        if (host.all { it.isDigit() || it == '.' }) return isValidIpv4(host)
        return isValidDomain(host)
    }

    fun normalizeHost(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith('[') && trimmed.endsWith(']') && trimmed.length > 2) {
            trimmed.substring(1, trimmed.lastIndex)
        } else {
            trimmed
        }
    }

    private fun isValidIpv4(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                (part.length == 1 || part.first() != '0') && part.toInt() in 0..255
        }
    }

    private fun isValidIpv6(host: String): Boolean = runCatching {
        InetAddress.getByName(host) is Inet6Address
    }.getOrDefault(false)

    private fun isValidDomain(host: String): Boolean = runCatching {
        val ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
        ascii.isNotEmpty() && ascii.length <= 253 && ascii.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.first() != '-' && label.last() != '-'
        }
    }.getOrDefault(false)

    private fun parseNumber(
        rawValue: String,
        field: ProfileField,
        range: IntRange,
        errors: MutableMap<ProfileField, ValidationError>,
    ): Int? {
        val value = rawValue.trim()
        if (value.isEmpty()) {
            errors[field] = ValidationError.REQUIRED
            return null
        }
        val number = value.toIntOrNull()
        if (number == null) {
            errors[field] = ValidationError.INVALID_NUMBER
            return null
        }
        if (number !in range) {
            errors[field] = ValidationError.OUT_OF_RANGE
            return null
        }
        return number
    }
}
