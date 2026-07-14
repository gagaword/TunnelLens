package com.gagaworld.modernsocks.vpn

import com.gagaworld.modernsocks.data.repository.ConnectionProfile
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun interface JavaSocketProtector {
    fun protect(socket: Socket): Boolean
}

sealed interface Socks5ProbeResult {
    data object Success : Socks5ProbeResult
    data object Unreachable : Socks5ProbeResult
    data object AuthenticationFailed : Socks5ProbeResult
    data object ProtocolError : Socks5ProbeResult
}

class Socks5Probe(
    private val timeoutMillis: Int = 10_000,
) {
    init {
        require(timeoutMillis > 0)
    }

    fun probe(
        profile: ConnectionProfile,
        protector: JavaSocketProtector?,
    ): Socks5ProbeResult {
        val credentials = profile.credentials
        val username = credentials?.username?.toByteArray(Charsets.UTF_8)
        val password = credentials?.password?.toByteArray(Charsets.UTF_8)
        return try {
            val address = InetSocketAddress(profile.host, profile.port)
            if (address.isUnresolved) return Socks5ProbeResult.Unreachable
            Socket().use { socket ->
                // Loopback SOCKS servers never traverse the VPN and protecting their
                // socket breaks adb-reverse and some local proxy-app integrations.
                if (
                    protector != null &&
                    address.address?.isLoopbackAddress != true &&
                    !protector.protect(socket)
                ) {
                    return Socks5ProbeResult.Unreachable
                }
                socket.soTimeout = timeoutMillis
                socket.connect(address, timeoutMillis)
                val output = socket.getOutputStream()
                val input = socket.getInputStream()
                val method = if (credentials == null) METHOD_NO_AUTH else METHOD_USER_PASSWORD
                output.write(byteArrayOf(SOCKS_VERSION, 1, method))
                output.flush()
                val responseVersion = input.readRequired()
                val selectedMethod = input.readRequired()
                if (responseVersion != SOCKS_VERSION.toInt()) {
                    return Socks5ProbeResult.ProtocolError
                }
                if (selectedMethod == METHOD_REJECTED.toInt()) {
                    return if (credentials == null) {
                        Socks5ProbeResult.ProtocolError
                    } else {
                        Socks5ProbeResult.AuthenticationFailed
                    }
                }
                if (selectedMethod != method.toInt()) return Socks5ProbeResult.ProtocolError
                if (credentials != null) {
                    checkNotNull(username)
                    checkNotNull(password)
                    if (username.isEmpty() || username.size > 255 || password.isEmpty() || password.size > 255) {
                        return Socks5ProbeResult.ProtocolError
                    }
                    output.write(AUTH_VERSION.toInt())
                    output.write(username.size)
                    output.write(username)
                    output.write(password.size)
                    output.write(password)
                    output.flush()
                    val authVersion = input.readRequired()
                    val authStatus = input.readRequired()
                    if (authVersion != AUTH_VERSION.toInt()) return Socks5ProbeResult.ProtocolError
                    if (authStatus != 0) return Socks5ProbeResult.AuthenticationFailed
                }
                Socks5ProbeResult.Success
            }
        } catch (_: UnknownHostException) {
            Socks5ProbeResult.Unreachable
        } catch (_: SocketTimeoutException) {
            Socks5ProbeResult.Unreachable
        } catch (_: EOFException) {
            Socks5ProbeResult.ProtocolError
        } catch (_: Exception) {
            Socks5ProbeResult.Unreachable
        } finally {
            username?.fill(0)
            password?.fill(0)
        }
    }

    private fun java.io.InputStream.readRequired(): Int =
        read().takeIf { it >= 0 } ?: throw EOFException()

    private companion object {
        const val SOCKS_VERSION: Byte = 5
        const val METHOD_NO_AUTH: Byte = 0
        const val METHOD_USER_PASSWORD: Byte = 2
        const val METHOD_REJECTED: Byte = -1
        const val AUTH_VERSION: Byte = 1
    }
}
