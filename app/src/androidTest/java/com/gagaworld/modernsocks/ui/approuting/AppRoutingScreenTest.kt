package com.gagaworld.modernsocks.ui.approuting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.data.apps.InstalledApp
import com.gagaworld.modernsocks.data.model.AppRoutingMode
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppRoutingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlySelectedRequiresAndTogglesApplication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var toggled = false
        composeRule.setContent {
            ModernSocksTheme(dynamicColor = false) {
                AppRoutingScreen(
                    state = AppRoutingUiState(
                        apps = listOf(InstalledApp("com.example.browser", "Browser", false)),
                        mode = AppRoutingMode.ONLY_SELECTED,
                        isLoading = false,
                    ),
                    onAction = { toggled = it is AppRoutingAction.AppToggled },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.action_save)).assertIsNotEnabled()
        composeRule.onNodeWithText("Browser").assertIsDisplayed().performClick()
        assertTrue(toggled)
    }
}
