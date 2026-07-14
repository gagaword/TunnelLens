package com.gagaworld.modernsocks.ui.settings

import com.gagaworld.modernsocks.FakeSettingsRepository
import com.gagaworld.modernsocks.FakeAppLanguageController
import com.gagaworld.modernsocks.data.locale.AppLanguage
import com.gagaworld.modernsocks.MainDispatcherRule
import com.gagaworld.modernsocks.data.preferences.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updatesArePersistedThroughRepository() = runTest {
        val repository = FakeSettingsRepository()
        val languageController = FakeAppLanguageController()
        val viewModel = SettingsViewModel(repository, languageController)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.settings.collect()
        }

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setAutoConnect(true)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.settings.value.themeMode)
        assertTrue(viewModel.settings.value.autoConnect)

        viewModel.setLanguage(AppLanguage.SIMPLIFIED_CHINESE)
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, viewModel.language.value)
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, languageController.language)
    }
}
