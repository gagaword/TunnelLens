package com.gagaworld.modernsocks.tunnel

import com.gagaworld.modernsocks.data.repository.ConnectionProfile

data class TunnelStats(
    val uploadedBytes: Long = 0,
    val downloadedBytes: Long = 0,
)

enum class TunnelStartResult {
    STARTED,
    FAILED,
}

enum class TunnelExitResult {
    EXITED,
    TIMED_OUT,
}

/**
 * Process-local owner of one native TUN session.
 *
 * The engine takes ownership of [ownedTunFd] for every start attempt, including
 * rejected attempts. Implementations must make stop operations idempotent.
 */
interface TunnelEngine {
    fun start(
        ownedTunFd: Int,
        profile: ConnectionProfile,
        protector: SocketProtector,
    ): TunnelStartResult

    fun requestStop()

    fun forceStop()

    fun awaitExit(timeoutMillis: Long): TunnelExitResult

    fun isRunning(): Boolean

    fun stats(): TunnelStats

    fun version(): String
}
