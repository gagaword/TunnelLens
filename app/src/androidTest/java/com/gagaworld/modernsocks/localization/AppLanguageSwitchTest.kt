package com.gagaworld.modernsocks.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import com.gagaworld.modernsocks.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test

class AppLanguageSwitchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun resetLanguage() {
        composeRule.runOnUiThread {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    @Test
    fun settingsPickerSwitchesBetweenEnglishAndSimplifiedChinese() {
        composeRule.runOnUiThread {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
        composeRule.waitForText("Settings")
        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithText("简体中文").performClick()
        composeRule.waitForText("设置")

        composeRule.onNodeWithText("English").performClick()
        composeRule.waitForText("Settings")
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForText(
    text: String,
) {
    waitUntil(timeoutMillis = 10_000) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
