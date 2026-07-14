package com.gagaworld.modernsocks.vpn

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVpnControllerTest {
    @Test
    fun grantedPreparationStartsAndStopsMatchingGeneration() = runTest {
        val store = VpnConnectionStateStore()
        val commands = RecordingCommands()
        val controller = AndroidVpnController(store, VpnPreparation { null }, commands)

        controller.connect()

        val generation = store.snapshot().generation
        assertEquals(ConnectionState.Starting, controller.connectionState.value)
        assertEquals(listOf(generation), commands.connectGenerations)
        assertTrue(store.transition(generation, ConnectionState.Connected))

        controller.disconnect()

        assertEquals(ConnectionState.Stopping, controller.connectionState.value)
        assertEquals(listOf(generation), commands.stopGenerations)
    }

    @Test
    fun serviceStartFailureBecomesStructuredError() = runTest {
        val controller = AndroidVpnController(
            stateStore = VpnConnectionStateStore(),
            preparation = VpnPreparation { null },
            serviceCommands = object : VpnServiceCommands {
                override fun connect(generation: Long) = error("start failed")
                override fun stop(generation: Long) = Unit
            },
        )

        controller.connect()

        assertEquals(
            ConnectionState.Error(ConnectionFailure.SERVICE_START_FAILED),
            controller.connectionState.value,
        )
    }

    private class RecordingCommands : VpnServiceCommands {
        val connectGenerations = mutableListOf<Long>()
        val stopGenerations = mutableListOf<Long>()

        override fun connect(generation: Long) {
            connectGenerations += generation
        }

        override fun stop(generation: Long) {
            stopGenerations += generation
        }
    }
}
