package com.gagaworld.modernsocks.ui.home

import com.gagaworld.modernsocks.MainDispatcherRule
import com.gagaworld.modernsocks.FakeProfileRepository
import com.gagaworld.modernsocks.testProfile
import com.gagaworld.modernsocks.vpn.ConnectionState
import com.gagaworld.modernsocks.vpn.FakeVpnController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialState_isDisconnectedWithoutAProfile() = runTest {
        val viewModel = HomeViewModel(FakeVpnController(), FakeProfileRepository())

        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Disconnected)
        assertTrue(viewModel.uiState.value.currentProfileName == null)
        assertTrue(viewModel.uiState.value.currentProfileAddress == null)
    }

    @Test
    fun primaryAction_drivesFakeConnectAndDisconnectStates() = runTest {
        val viewModel = HomeViewModel(
            FakeVpnController(),
            FakeProfileRepository(listOf(testProfile())),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        viewModel.onPrimaryAction()
        runCurrent()
        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Starting)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Connected)

        viewModel.onPrimaryAction()
        runCurrent()
        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Stopping)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connectionState is ConnectionState.Disconnected)
    }
}
