package com.gagaworld.modernsocks.vpn

import com.gagaworld.modernsocks.data.repository.ConnectionProfile
import com.gagaworld.modernsocks.security.Credentials
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Test

class Socks5ProbeTest {
    @Test
    fun noAuthenticationHandshakeSucceeds() = withServer { server ->
        val worker = thread {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                assertEquals(5, input.read())
                assertEquals(1, input.read())
                assertEquals(0, input.read())
                output.write(byteArrayOf(5, 0))
            }
        }
        assertEquals(
            Socks5ProbeResult.Success,
            probe(server, credentials = null, protector = null),
        )
        worker.join()
    }

    @Test
    fun authenticationRejectionIsDeterministic() = withServer { server ->
        val worker = thread {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                input.readNBytes(3)
                output.write(byteArrayOf(5, 2))
                val version = input.read()
                val usernameLength = input.read()
                input.readNBytes(usernameLength)
                val passwordLength = input.read()
                input.readNBytes(passwordLength)
                assertEquals(1, version)
                output.write(byteArrayOf(1, 1))
            }
        }
        assertEquals(
            Socks5ProbeResult.AuthenticationFailed,
            probe(
                server,
                Credentials("test-user", "wrong-password"),
                JavaSocketProtector { true },
            ),
        )
        worker.join()
    }

    @Test
    fun loopbackProbeDoesNotRequireVpnProtection() = withServer { server ->
        val worker = thread {
            server.accept().use { socket ->
                socket.getInputStream().readNBytes(3)
                socket.getOutputStream().write(byteArrayOf(5, 0))
            }
        }

        val result = Socks5Probe(timeoutMillis = 1_000).probe(
            profile = ConnectionProfile(
                id = 1,
                host = "127.0.0.1",
                port = server.localPort,
                mtu = 1500,
                autoReconnect = true,
                credentials = null,
            ),
            protector = JavaSocketProtector {
                throw AssertionError("loopback must not be protected")
            },
        )

        assertEquals(Socks5ProbeResult.Success, result)
        worker.join()
    }

    private fun probe(
        server: ServerSocket,
        credentials: Credentials?,
        protector: JavaSocketProtector?,
    ): Socks5ProbeResult =
        Socks5Probe(timeoutMillis = 1_000).probe(
            profile = ConnectionProfile(
                id = 1,
                host = "127.0.0.1",
                port = server.localPort,
                mtu = 1500,
                autoReconnect = true,
                credentials = credentials,
            ),
            protector = protector,
        )

    private fun withServer(block: (ServerSocket) -> Unit) {
        ServerSocket(0).use(block)
    }
}
