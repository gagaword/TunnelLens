package com.gagaworld.modernsocks.tunnel

import com.gagaworld.modernsocks.data.repository.ConnectionProfile
import java.nio.charset.StandardCharsets

object HevConfigEncoder {
    private const val MAX_CONFIG_BYTES = 64 * 1024

    fun encode(profile: ConnectionProfile): ByteArray {
        require(profile.port in 1..65535)
        require(profile.mtu in 1280..9000)
        require(profile.host.isNotBlank())
        val credentials = profile.credentials
        val text = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: ${profile.mtu}")
            appendLine("  ipv4: \"198.18.0.1\"")
            appendLine("socks5:")
            appendLine("  address: ${yamlString(profile.host)}")
            appendLine("  port: ${profile.port}")
            if (credentials != null) {
                appendLine("  username: ${yamlString(credentials.username)}")
                appendLine("  password: ${yamlString(credentials.password)}")
            }
            appendLine("misc:")
            appendLine("  connect-timeout: 10000")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  log-file: \"/dev/null\"")
            appendLine("  log-level: \"error\"")
        }
        return text.toByteArray(StandardCharsets.UTF_8).also { bytes ->
            require(bytes.size <= MAX_CONFIG_BYTES)
        }
    }

    internal fun yamlString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\u000C' -> append("\\f")
                '\r' -> append("\\r")
                else -> if (character.code < 0x20 || character.code == 0x7F) {
                    append("\\x")
                    append(character.code.toString(16).uppercase().padStart(2, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}
