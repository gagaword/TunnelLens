package com.gagaworld.modernsocks.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gagaworld.modernsocks.data.preferences.AppSettings
import com.gagaworld.modernsocks.data.preferences.SettingsRepository
import com.gagaworld.modernsocks.data.preferences.ThemeMode
import com.gagaworld.modernsocks.data.locale.AppLanguage
import com.gagaworld.modernsocks.data.locale.AppLanguageController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val languageController: AppLanguageController,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )
    private val mutableLanguage = MutableStateFlow(languageController.currentLanguage())
    val language: StateFlow<AppLanguage> = mutableLanguage.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = update { repository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = update { repository.setDynamicColor(enabled) }
    fun setAutoConnect(enabled: Boolean) = update { repository.setAutoConnect(enabled) }
    fun setExpandAdvanced(enabled: Boolean) = update {
        repository.setExpandAdvancedByDefault(enabled)
    }
    fun setLanguage(language: AppLanguage) {
        if (mutableLanguage.value == language) return
        mutableLanguage.value = language
        languageController.setLanguage(language)
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
