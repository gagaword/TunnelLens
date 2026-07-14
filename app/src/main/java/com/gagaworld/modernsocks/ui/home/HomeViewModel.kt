package com.gagaworld.modernsocks.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gagaworld.modernsocks.vpn.ConnectionState
import com.gagaworld.modernsocks.vpn.VpnController
import com.gagaworld.modernsocks.vpn.VpnPermissionRequest
import com.gagaworld.modernsocks.data.model.endpoint
import com.gagaworld.modernsocks.data.repository.ProfileRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val vpnController: VpnController,
    profileRepository: ProfileRepository,
) : ViewModel() {
    val permissionRequest: StateFlow<VpnPermissionRequest?> = vpnController.permissionRequest

    val uiState: StateFlow<HomeUiState> = combine(
        vpnController.connectionState,
        vpnController.connectionMetrics,
        profileRepository.selectedProfile,
    ) { connectionState, metrics, profile ->
        HomeUiState(
            connectionState = connectionState,
            isLoadingProfile = false,
            currentProfileName = profile?.name,
            currentProfileAddress = profile?.endpoint,
            canConnect = profile != null,
            metrics = metrics,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = HomeUiState(
                connectionState = vpnController.connectionState.value,
                isLoadingProfile = true,
            ),
        )

    fun onPrimaryAction() {
        if (!uiState.value.canConnect) return
        when (vpnController.connectionState.value) {
            ConnectionState.Disconnected,
            is ConnectionState.Error,
            -> viewModelScope.launch { vpnController.connect() }

            ConnectionState.Connected,
            ConnectionState.Reconnecting,
            -> viewModelScope.launch { vpnController.disconnect() }
            ConnectionState.PreparingPermission,
            ConnectionState.Starting,
            ConnectionState.Stopping,
            -> Unit
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        viewModelScope.launch { vpnController.onPermissionResult(granted) }
    }
}
