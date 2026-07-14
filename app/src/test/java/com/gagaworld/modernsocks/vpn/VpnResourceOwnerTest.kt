package com.gagaworld.modernsocks.vpn

import java.io.Closeable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnResourceOwnerTest {
    @Test
    fun closeIsIdempotent() {
        val owner = VpnResourceOwner()
        val resource = CountingCloseable()

        assertTrue(owner.attach(resource))
        assertTrue(owner.close())
        assertFalse(owner.close())
        assertEquals(1, resource.closeCount)
    }

    @Test
    fun rejectedSecondResourceIsClosedImmediately() {
        val owner = VpnResourceOwner()
        val first = CountingCloseable()
        val second = CountingCloseable()

        assertTrue(owner.attach(first))
        assertFalse(owner.attach(second))
        assertEquals(0, first.closeCount)
        assertEquals(1, second.closeCount)
        owner.close()
        assertEquals(1, first.closeCount)
    }

    private class CountingCloseable : Closeable {
        var closeCount = 0

        override fun close() {
            closeCount += 1
        }
    }
}
