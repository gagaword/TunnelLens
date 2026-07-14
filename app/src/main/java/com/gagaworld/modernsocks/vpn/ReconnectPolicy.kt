package com.gagaworld.modernsocks.vpn

class ReconnectPolicy(
    private val delaysMillis: List<Long> = DEFAULT_DELAYS_MILLIS,
) {
    init {
        require(delaysMillis.isNotEmpty())
        require(delaysMillis.all { it >= 0 })
    }

    val maxAttempts: Int get() = delaysMillis.size

    fun delayForAttempt(attempt: Int): Long? = delaysMillis.getOrNull(attempt - 1)

    companion object {
        val DEFAULT_DELAYS_MILLIS = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
    }
}
