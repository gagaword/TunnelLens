package com.gagaworld.modernsocks.ui.profileedit

import com.gagaworld.modernsocks.FakeProfileRepository
import com.gagaworld.modernsocks.FakeSettingsRepository
import com.gagaworld.modernsocks.MainDispatcherRule
import com.gagaworld.modernsocks.data.model.ProfileField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveWithEmptyFields_exposesValidationErrors() = runTest {
        val viewModel = ProfileEditViewModel(
            profileId = null,
            repository = FakeProfileRepository(),
            settingsRepository = FakeSettingsRepository(),
        )
        advanceUntilIdle()

        viewModel.onAction(ProfileEditAction.NameChanged(""))
        viewModel.onAction(ProfileEditAction.HostChanged(""))
        viewModel.onAction(ProfileEditAction.Save)

        assertTrue(ProfileField.NAME in viewModel.uiState.value.errors)
        assertTrue(ProfileField.HOST in viewModel.uiState.value.errors)
        assertFalse(viewModel.uiState.value.saveCompleted)
    }

    @Test
    fun validDraft_isNormalizedAndSaved() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = ProfileEditViewModel(null, repository, FakeSettingsRepository())
        advanceUntilIdle()

        viewModel.onAction(ProfileEditAction.NameChanged("  Test  "))
        viewModel.onAction(ProfileEditAction.HostChanged("example.invalid"))
        viewModel.onAction(ProfileEditAction.Save)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveCompleted)
        assertNotNull(repository.savedProfile)
        assertEquals("Test", repository.savedProfile?.name)
    }

    @Test
    fun editingAndRevertingDraft_tracksUnsavedChanges() = runTest {
        val viewModel = ProfileEditViewModel(
            profileId = null,
            repository = FakeProfileRepository(),
            settingsRepository = FakeSettingsRepository(),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onAction(ProfileEditAction.NameChanged("Temporary"))
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onAction(ProfileEditAction.NameChanged(""))
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }
}
