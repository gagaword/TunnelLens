package com.gagaworld.modernsocks.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeVpnController : VpnController {
    private val mutableConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val mutablePermissionRequest = MutableStateFlow<VpnPermissionRequest?>(null)
    private val mutableMetrics = MutableStateFlow(ConnectionMetrics())
    private val operationMutex = Mutex()

    override val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()
    override val connectionMetrics: StateFlow<ConnectionMetrics> = mutableMetrics.asStateFlow()
    override val permissionRequest: StateFlow<VpnPermissionRequest?> =
        mutablePermissionRequest.asStateFlow()

    override suspend fun connect() = operationMutex.withLock {
        if (mutableConnectionState.value !is ConnectionState.Disconnected &&
            mutableConnectionState.value !is ConnectionState.Error
        ) {
            return@withLock
        }

        mutableConnectionState.value = ConnectionState.Starting
        try {
            delay(400)
            mutableConnectionState.value = ConnectionState.Connected
        } catch (cancellation: CancellationException) {
            mutableConnectionState.value = ConnectionState.Disconnected
            throw cancellation
        }
    }

    override suspend fun disconnect() = operationMutex.withLock {
        if (mutableConnectionState.value !is ConnectionState.Connected &&
            mutableConnectionState.value !is ConnectionState.Reconnecting
        ) {
            return@withLock
        }

        mutableConnectionState.value = ConnectionState.Stopping
        try {
            delay(250)
            mutableConnectionState.value = ConnectionState.Disconnected
        } catch (cancellation: CancellationException) {
            mutableConnectionState.value = ConnectionState.Disconnected
            throw cancellation
        }
    }

    override suspend fun onPermissionResult(granted: Boolean) = Unit
}
