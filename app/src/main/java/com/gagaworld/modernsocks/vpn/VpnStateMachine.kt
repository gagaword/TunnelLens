package com.gagaworld.modernsocks.vpn

object VpnStateMachine {
    fun canTransition(from: ConnectionState, to: ConnectionState): Boolean {
        if (from == to) return true
        return when (from) {
            ConnectionState.Disconnected -> to is ConnectionState.PreparingPermission
            ConnectionState.PreparingPermission ->
                to is ConnectionState.Starting ||
                    to is ConnectionState.Disconnected ||
                    to is ConnectionState.Error
            ConnectionState.Starting ->
                to is ConnectionState.Connected ||
                    to is ConnectionState.Reconnecting ||
                    to is ConnectionState.Stopping ||
                    to is ConnectionState.Error
            ConnectionState.Connected ->
                to is ConnectionState.Reconnecting ||
                    to is ConnectionState.Stopping ||
                    to is ConnectionState.Error
            ConnectionState.Reconnecting ->
                to is ConnectionState.Connected ||
                    to is ConnectionState.Stopping ||
                    to is ConnectionState.Error
            ConnectionState.Stopping ->
                to is ConnectionState.Disconnected || to is ConnectionState.Error
            is ConnectionState.Error ->
                to is ConnectionState.PreparingPermission || to is ConnectionState.Disconnected
        }
    }
}

data class VpnStateSnapshot(
    val generation: Long,
    val state: ConnectionState,
)

class VpnConnectionStateStore {
    private val lock = Any()
    private var generation = 0L
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow<ConnectionState>(
        ConnectionState.Disconnected,
    )
    private val mutableMetrics = kotlinx.coroutines.flow.MutableStateFlow(ConnectionMetrics())

    val state: kotlinx.coroutines.flow.StateFlow<ConnectionState> = mutableState
    val metrics: kotlinx.coroutines.flow.StateFlow<ConnectionMetrics> = mutableMetrics

    fun updateMetrics(expectedGeneration: Long, metrics: ConnectionMetrics): Boolean =
        synchronized(lock) {
            if (expectedGeneration != generation) return@synchronized false
            mutableMetrics.value = metrics
            true
        }

    fun snapshot(): VpnStateSnapshot = synchronized(lock) {
        VpnStateSnapshot(generation = generation, state = mutableState.value)
    }

    fun beginConnection(): Long? = synchronized(lock) {
        if (mutableState.value !is ConnectionState.Disconnected &&
            mutableState.value !is ConnectionState.Error
        ) {
            return@synchronized null
        }
        generation += 1
        mutableMetrics.value = ConnectionMetrics()
        mutableState.value = ConnectionState.PreparingPermission
        generation
    }

    fun transition(expectedGeneration: Long, next: ConnectionState): Boolean = synchronized(lock) {
        if (expectedGeneration != generation) return@synchronized false
        val current = mutableState.value
        if (!VpnStateMachine.canTransition(current, next)) return@synchronized false
        mutableState.value = next
        true
    }
}
