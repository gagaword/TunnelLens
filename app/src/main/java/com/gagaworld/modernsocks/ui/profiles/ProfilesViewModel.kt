package com.gagaworld.modernsocks.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gagaworld.modernsocks.data.model.ProxyProfile
import com.gagaworld.modernsocks.data.repository.DeletedProfile
import com.gagaworld.modernsocks.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import com.gagaworld.modernsocks.data.transfer.ProfileTransferManager

data class UndoDeleteUiModel(
    val token: Long,
    val profileName: String,
)

data class ProfilesUiState(
    val profiles: List<ProxyProfile> = emptyList(),
    val isLoading: Boolean = true,
    val undoDelete: UndoDeleteUiModel? = null,
    val operationFailed: Boolean = false,
    val transferResult: ProfileTransferResult? = null,
)

data class ProfileTransferResult(
    val token: Long,
    val count: Int,
    val type: ProfileTransferType,
)

enum class ProfileTransferType { IMPORTED, EXPORTED }

class ProfilesViewModel(
    private val repository: ProfileRepository,
    private val transferManager: ProfileTransferManager? = null,
) : ViewModel() {
    private val transientState = MutableStateFlow(TransientState())
    private var pendingDeletedProfile: DeletedProfile? = null

    val uiState: StateFlow<ProfilesUiState> = combine(
        repository.profiles,
        transientState,
    ) { profiles, transient ->
        ProfilesUiState(
            profiles = profiles,
            isLoading = false,
            undoDelete = transient.undoDelete,
            operationFailed = transient.operationFailed,
            transferResult = transient.transferResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfilesUiState(),
    )

    fun select(profileId: Long) = launchOperation {
        repository.select(profileId)
    }

    fun duplicate(profileId: Long, copyName: String) = launchOperation {
        repository.duplicate(profileId, copyName)
    }

    fun delete(profileId: Long) = launchOperation {
        val deleted = repository.delete(profileId) ?: return@launchOperation
        pendingDeletedProfile = deleted
        transientState.value = TransientState(
            undoDelete = UndoDeleteUiModel(
                token = System.nanoTime(),
                profileName = deleted.name,
            ),
        )
    }

    fun undoDelete() = launchOperation {
        pendingDeletedProfile?.let { repository.restore(it) }
        pendingDeletedProfile = null
        transientState.value = TransientState()
    }

    fun consumeDeleteMessage() {
        transientState.value = transientState.value.copy(undoDelete = null)
        pendingDeletedProfile = null
    }

    fun consumeError() {
        transientState.value = transientState.value.copy(operationFailed = false)
    }

    fun importFrom(uri: Uri) = launchOperation {
        val count = checkNotNull(transferManager).importFrom(uri)
        transientState.value = TransientState(
            transferResult = ProfileTransferResult(
                token = System.nanoTime(),
                count = count,
                type = ProfileTransferType.IMPORTED,
            ),
        )
    }

    fun exportTo(uri: Uri) = launchOperation {
        val count = checkNotNull(transferManager).exportTo(uri)
        transientState.value = TransientState(
            transferResult = ProfileTransferResult(
                token = System.nanoTime(),
                count = count,
                type = ProfileTransferType.EXPORTED,
            ),
        )
    }

    fun consumeTransferResult() {
        transientState.value = transientState.value.copy(transferResult = null)
    }

    private fun launchOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure {
                    transientState.value = transientState.value.copy(operationFailed = true)
                }
        }
    }

    private data class TransientState(
        val undoDelete: UndoDeleteUiModel? = null,
        val operationFailed: Boolean = false,
        val transferResult: ProfileTransferResult? = null,
    )
}
