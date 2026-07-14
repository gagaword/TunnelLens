package com.gagaworld.modernsocks.vpn

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

class VpnPermissionRequest(
    val generation: Long,
    val intent: Intent,
) {
    override fun toString(): String =
        "VpnPermissionRequest(generation=$generation, intent=[REDACTED])"
}

interface VpnController {
    val connectionState: StateFlow<ConnectionState>
    val connectionMetrics: StateFlow<ConnectionMetrics>
    val permissionRequest: StateFlow<VpnPermissionRequest?>

    suspend fun connect()

    suspend fun disconnect()

    suspend fun onPermissionResult(granted: Boolean)
}
