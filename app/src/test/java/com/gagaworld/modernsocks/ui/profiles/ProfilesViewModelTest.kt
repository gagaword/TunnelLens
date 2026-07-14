package com.gagaworld.modernsocks.ui.profiles

import com.gagaworld.modernsocks.FakeProfileRepository
import com.gagaworld.modernsocks.MainDispatcherRule
import com.gagaworld.modernsocks.testProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun deleteAndUndo_restoresProfile() = runTest {
        val repository = FakeProfileRepository(listOf(testProfile()))
        val viewModel = ProfilesViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.delete(1)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.profiles.size)
        assertNotNull(viewModel.uiState.value.undoDelete)

        viewModel.undoDelete()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.profiles.size)
    }
}
