package com.gagaworld.modernsocks.ui.approuting

import com.gagaworld.modernsocks.FakeProfileRepository
import com.gagaworld.modernsocks.MainDispatcherRule
import com.gagaworld.modernsocks.data.apps.InstalledApp
import com.gagaworld.modernsocks.data.apps.InstalledAppRepository
import com.gagaworld.modernsocks.data.model.AppRoutingMode
import com.gagaworld.modernsocks.testProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppRoutingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onlySelectedRequiresAnAppAndPersistsSelection() = runTest {
        val repository = FakeProfileRepository(listOf(testProfile()))
        val apps = InstalledAppRepository {
            listOf(InstalledApp("com.example.browser", "Browser", false))
        }
        val viewModel = AppRoutingViewModel(1, repository, apps)
        advanceUntilIdle()

        viewModel.onAction(AppRoutingAction.ModeChanged(AppRoutingMode.ONLY_SELECTED))
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.onAction(AppRoutingAction.AppToggled("com.example.browser"))
        viewModel.onAction(AppRoutingAction.Save)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveCompleted)
        assertEquals(AppRoutingMode.ONLY_SELECTED, repository.appRoutingPolicy.mode)
        assertEquals(setOf("com.example.browser"), repository.appRoutingPolicy.packages)
    }
}
