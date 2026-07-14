package com.gagaworld.modernsocks.ui.home

import com.gagaworld.modernsocks.vpn.ConnectionState
import com.gagaworld.modernsocks.vpn.ConnectionMetrics

data class HomeUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isLoadingProfile: Boolean = false,
    val currentProfileName: String? = null,
    val currentProfileAddress: String? = null,
    val canConnect: Boolean = false,
    val metrics: ConnectionMetrics = ConnectionMetrics(),
)
