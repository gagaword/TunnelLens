package com.gagaworld.modernsocks.ui.home

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme
import com.gagaworld.modernsocks.vpn.ConnectionState
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disconnectedState_showsPlaceholderAndSendsPrimaryAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            ModernSocksTheme(dynamicColor = false) {
                HomeScreen(
                    uiState = HomeUiState(),
                    onPrimaryAction = {},
                )
            }
        }

        composeRule.onNodeWithTag(CONNECTION_STATUS_TEST_TAG)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.connection_disconnected))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.no_profile_selected))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_connect_fake))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.action_add_profile_first))
            .assertIsNotEnabled()
    }

    @Test
    fun connectedState_showsDisconnectAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            ModernSocksTheme(darkTheme = true, dynamicColor = false) {
                HomeScreen(
                    uiState = HomeUiState(
                        connectionState = ConnectionState.Connected,
                        canConnect = true,
                    ),
                    onPrimaryAction = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.connection_connected))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_disconnect_fake))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun reconnectingState_keepsUserStopAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            ModernSocksTheme(dynamicColor = false) {
                HomeScreen(
                    uiState = HomeUiState(
                        connectionState = ConnectionState.Reconnecting,
                        canConnect = true,
                    ),
                    onPrimaryAction = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.connection_reconnecting))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_disconnect_fake))
            .assertIsEnabled()
            .assertHasClickAction()
    }
}
