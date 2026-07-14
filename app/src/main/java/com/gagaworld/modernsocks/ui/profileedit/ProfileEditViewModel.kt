package com.gagaworld.modernsocks.ui.profileedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gagaworld.modernsocks.data.model.ProfileDraft
import com.gagaworld.modernsocks.data.model.ProfileField
import com.gagaworld.modernsocks.data.model.ProfileValidator
import com.gagaworld.modernsocks.data.model.ValidationError
import com.gagaworld.modernsocks.data.preferences.SettingsRepository
import com.gagaworld.modernsocks.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileEditUiState(
    val draft: ProfileDraft = ProfileDraft(),
    val errors: Map<ProfileField, ValidationError> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val showAdvanced: Boolean = false,
    val saveCompleted: Boolean = false,
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
)

sealed interface ProfileEditAction {
    data class NameChanged(val value: String) : ProfileEditAction
    data class HostChanged(val value: String) : ProfileEditAction
    data class PortChanged(val value: String) : ProfileEditAction
    data class AuthenticationChanged(val enabled: Boolean) : ProfileEditAction
    data class UsernameChanged(val value: String) : ProfileEditAction
    data class PasswordChanged(val value: String) : ProfileEditAction
    data class DnsChanged(val value: String) : ProfileEditAction
    data class MtuChanged(val value: String) : ProfileEditAction
    data class BypassLanChanged(val enabled: Boolean) : ProfileEditAction
    data class AutoReconnectChanged(val enabled: Boolean) : ProfileEditAction
    data object ToggleAdvanced : ProfileEditAction
    data object Save : ProfileEditAction
    data object ErrorConsumed : ProfileEditAction
}

class ProfileEditViewModel(
    private val profileId: Long?,
    private val repository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = mutableUiState.asStateFlow()
    private var baselineDraft = ProfileDraft()

    init {
        viewModelScope.launch {
            val expandAdvanced = runCatching {
                settingsRepository.settings.first().expandAdvancedByDefault
            }.getOrDefault(false)
            if (profileId == null) {
                mutableUiState.value = ProfileEditUiState(
                    isLoading = false,
                    showAdvanced = expandAdvanced,
                )
            } else {
                runCatching { repository.getDraft(profileId) }
                    .onSuccess { draft ->
                        mutableUiState.value = if (draft == null) {
                            ProfileEditUiState(isLoading = false, loadFailed = true)
                        } else {
                            baselineDraft = draft
                            ProfileEditUiState(
                                draft = draft,
                                isLoading = false,
                                showAdvanced = expandAdvanced,
                            )
                        }
                    }
                    .onFailure {
                        mutableUiState.value = ProfileEditUiState(
                            isLoading = false,
                            loadFailed = true,
                        )
                    }
            }
        }
    }

    fun onAction(action: ProfileEditAction) {
        when (action) {
            is ProfileEditAction.NameChanged -> updateDraft { copy(name = action.value) }
            is ProfileEditAction.HostChanged -> updateDraft { copy(host = action.value) }
            is ProfileEditAction.PortChanged -> updateDraft { copy(port = action.value.filter(Char::isDigit)) }
            is ProfileEditAction.AuthenticationChanged -> updateDraft {
                copy(authenticationEnabled = action.enabled)
            }
            is ProfileEditAction.UsernameChanged -> updateDraft { copy(username = action.value) }
            is ProfileEditAction.PasswordChanged -> updateDraft { copy(password = action.value) }
            is ProfileEditAction.DnsChanged -> updateDraft { copy(dnsServer = action.value) }
            is ProfileEditAction.MtuChanged -> updateDraft { copy(mtu = action.value.filter(Char::isDigit)) }
            is ProfileEditAction.BypassLanChanged -> updateDraft { copy(bypassLan = action.enabled) }
            is ProfileEditAction.AutoReconnectChanged -> updateDraft {
                copy(autoReconnect = action.enabled)
            }
            ProfileEditAction.ToggleAdvanced -> mutableUiState.update {
                it.copy(showAdvanced = !it.showAdvanced)
            }
            ProfileEditAction.Save -> save()
            ProfileEditAction.ErrorConsumed -> mutableUiState.update {
                it.copy(loadFailed = false, saveFailed = false)
            }
        }
    }

    private fun updateDraft(transform: ProfileDraft.() -> ProfileDraft) {
        mutableUiState.update { state ->
            val updatedDraft = state.draft.transform()
            state.copy(
                draft = updatedDraft,
                errors = emptyMap(),
                saveFailed = false,
                hasUnsavedChanges = updatedDraft != baselineDraft,
            )
        }
    }

    private fun save() {
        val validation = ProfileValidator.validate(mutableUiState.value.draft)
        if (!validation.isValid) {
            mutableUiState.update { it.copy(errors = validation.errors) }
            return
        }
        val profile = checkNotNull(validation.profile)
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSaving = true, saveFailed = false) }
            runCatching { repository.save(profile) }
                .onSuccess {
                    mutableUiState.update {
                        baselineDraft = it.draft
                        it.copy(
                            isSaving = false,
                            saveCompleted = true,
                            hasUnsavedChanges = false,
                        )
                    }
                }
                .onFailure {
                    mutableUiState.update { it.copy(isSaving = false, saveFailed = true) }
                }
        }
    }
}
