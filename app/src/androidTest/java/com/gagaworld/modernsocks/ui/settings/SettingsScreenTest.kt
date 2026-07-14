package com.gagaworld.modernsocks.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.data.preferences.AppSettings
import com.gagaworld.modernsocks.data.preferences.ThemeMode
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme
import com.gagaworld.modernsocks.data.locale.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingDarkThemeSendsAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var selected: ThemeMode? = null
        composeRule.setContent {
            ModernSocksTheme(dynamicColor = false) {
                SettingsScreen(
                    settings = AppSettings(),
                    language = AppLanguage.SYSTEM,
                    onLanguageChanged = {},
                    onThemeModeChanged = { selected = it },
                    onDynamicColorChanged = {},
                    onAutoConnectChanged = {},
                    onExpandAdvancedChanged = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_theme_dark)).performClick()
        composeRule.runOnIdle { assertEquals(ThemeMode.DARK, selected) }
    }

    @Test
    fun selectingSimplifiedChineseSendsAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var selected: AppLanguage? = null
        composeRule.setContent {
            ModernSocksTheme(dynamicColor = false) {
                SettingsScreen(
                    settings = AppSettings(),
                    language = AppLanguage.SYSTEM,
                    onLanguageChanged = { selected = it },
                    onThemeModeChanged = {},
                    onDynamicColorChanged = {},
                    onAutoConnectChanged = {},
                    onExpandAdvancedChanged = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.settings_language_simplified_chinese),
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(AppLanguage.SIMPLIFIED_CHINESE, selected)
        }
    }

    @Test
    fun openSourceNoticeOpensBundledLicense() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            ModernSocksTheme(dynamicColor = false) {
                SettingsScreen(
                    settings = AppSettings(),
                    language = AppLanguage.SYSTEM,
                    onLanguageChanged = {},
                    onThemeModeChanged = {},
                    onDynamicColorChanged = {},
                    onAutoConnectChanged = {},
                    onExpandAdvancedChanged = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.settings_open_source_view_license),
        ).performScrollTo().performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_open_source_license_title),
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.action_close),
        ).performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_open_source_license_title),
        ).assertDoesNotExist()
    }
}
