package com.gagaworld.modernsocks.ui.approuting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gagaworld.modernsocks.data.apps.InstalledApp
import com.gagaworld.modernsocks.data.apps.InstalledAppRepository
import com.gagaworld.modernsocks.data.model.AppRoutingMode
import com.gagaworld.modernsocks.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppRoutingUiState(
    val apps: List<InstalledApp> = emptyList(),
    val mode: AppRoutingMode = AppRoutingMode.ALL_APPS,
    val selectedPackages: Set<String> = emptySet(),
    val query: String = "",
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false,
    val error: Boolean = false,
) {
    val visibleApps: List<InstalledApp>
        get() = apps.filter { app ->
            (showSystemApps || !app.isSystem) &&
                (query.isBlank() || app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true))
        }

    val canSave: Boolean
        get() = mode != AppRoutingMode.ONLY_SELECTED || selectedPackages.isNotEmpty()
}

sealed interface AppRoutingAction {
    data class ModeChanged(val mode: AppRoutingMode) : AppRoutingAction
    data class AppToggled(val packageName: String) : AppRoutingAction
    data class QueryChanged(val value: String) : AppRoutingAction
    data class ShowSystemChanged(val value: Boolean) : AppRoutingAction
    data object SelectVisible : AppRoutingAction
    data object Clear : AppRoutingAction
    data object Save : AppRoutingAction
}

class AppRoutingViewModel(
    private val profileId: Long,
    private val profileRepository: ProfileRepository,
    private val installedAppRepository: InstalledAppRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppRoutingUiState())
    val uiState: StateFlow<AppRoutingUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val policy = requireNotNull(profileRepository.getAppRouting(profileId))
                installedAppRepository.loadLaunchableApps() to policy
            }.onSuccess { (apps, policy) ->
                mutableUiState.value = AppRoutingUiState(
                    apps = apps,
                    mode = policy.mode,
                    selectedPackages = policy.packages,
                    isLoading = false,
                )
            }.onFailure {
                mutableUiState.update { it.copy(isLoading = false, error = true) }
            }
        }
    }

    fun onAction(action: AppRoutingAction) {
        when (action) {
            is AppRoutingAction.ModeChanged -> mutableUiState.update {
                it.copy(mode = action.mode, error = false)
            }
            is AppRoutingAction.AppToggled -> mutableUiState.update {
                val selected = it.selectedPackages.toMutableSet().apply {
                    if (!add(action.packageName)) remove(action.packageName)
                }
                it.copy(selectedPackages = selected, error = false)
            }
            is AppRoutingAction.QueryChanged -> mutableUiState.update { it.copy(query = action.value) }
            is AppRoutingAction.ShowSystemChanged -> mutableUiState.update {
                it.copy(showSystemApps = action.value)
            }
            AppRoutingAction.SelectVisible -> mutableUiState.update {
                it.copy(selectedPackages = it.selectedPackages + it.visibleApps.map(InstalledApp::packageName))
            }
            AppRoutingAction.Clear -> mutableUiState.update { it.copy(selectedPackages = emptySet()) }
            AppRoutingAction.Save -> save()
        }
    }

    private fun save() {
        val state = mutableUiState.value
        if (!state.canSave) {
            mutableUiState.update { it.copy(error = true) }
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSaving = true, error = false) }
            runCatching {
                profileRepository.updateAppRouting(profileId, state.mode, state.selectedPackages)
            }.onSuccess {
                mutableUiState.update { it.copy(isSaving = false, saveCompleted = true) }
            }.onFailure {
                mutableUiState.update { it.copy(isSaving = false, error = true) }
            }
        }
    }
}
