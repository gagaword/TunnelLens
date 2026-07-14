package com.gagaworld.modernsocks.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun usesBoundedExponentialSchedule() {
        val policy = ReconnectPolicy()
        assertEquals(5, policy.maxAttempts)
        assertEquals(1_000L, policy.delayForAttempt(1))
        assertEquals(2_000L, policy.delayForAttempt(2))
        assertEquals(5_000L, policy.delayForAttempt(3))
        assertEquals(10_000L, policy.delayForAttempt(4))
        assertEquals(30_000L, policy.delayForAttempt(5))
        assertNull(policy.delayForAttempt(6))
    }
}
