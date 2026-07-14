package com.gagaworld.modernsocks.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStateMachineTest {
    @Test
    fun completeConnectionAndStopFollowLegalTransitions() {
        val store = VpnConnectionStateStore()
        val generation = requireNotNull(store.beginConnection())

        assertTrue(store.transition(generation, ConnectionState.Starting))
        assertTrue(store.transition(generation, ConnectionState.Connected))
        assertTrue(store.transition(generation, ConnectionState.Stopping))
        assertTrue(store.transition(generation, ConnectionState.Disconnected))
        assertEquals(ConnectionState.Disconnected, store.state.value)
    }

    @Test
    fun staleGenerationCannotOverwriteCurrentConnection() {
        val store = VpnConnectionStateStore()
        val current = requireNotNull(store.beginConnection())

        assertFalse(store.transition(current + 1, ConnectionState.Starting))
        assertEquals(ConnectionState.PreparingPermission, store.state.value)
        assertFalse(
            store.updateMetrics(
                current + 1,
                ConnectionMetrics(uploadedBytes = 42),
            ),
        )
        assertEquals(ConnectionMetrics(), store.metrics.value)
    }

    @Test
    fun startingMayEnterReconnectAndUserStopPath() {
        val store = VpnConnectionStateStore()
        val generation = requireNotNull(store.beginConnection())

        assertTrue(store.transition(generation, ConnectionState.Starting))
        assertTrue(store.transition(generation, ConnectionState.Reconnecting))
        assertTrue(store.updateMetrics(generation, ConnectionMetrics(reconnectAttempt = 2)))
        assertEquals(2, store.metrics.value.reconnectAttempt)
        assertTrue(store.transition(generation, ConnectionState.Stopping))
        assertTrue(store.transition(generation, ConnectionState.Disconnected))
    }

    @Test
    fun repeatedConnectIsRejectedUntilTerminalState() {
        val store = VpnConnectionStateStore()
        val generation = requireNotNull(store.beginConnection())

        assertNull(store.beginConnection())
        assertTrue(
            store.transition(
                generation,
                ConnectionState.Error(ConnectionFailure.TUN_ESTABLISH_FAILED),
            ),
        )
        requireNotNull(store.beginConnection())
    }

    @Test
    fun illegalTransitionIsRejected() {
        val store = VpnConnectionStateStore()
        val generation = requireNotNull(store.beginConnection())

        assertFalse(store.transition(generation, ConnectionState.Connected))
        assertEquals(ConnectionState.PreparingPermission, store.state.value)
    }
}
