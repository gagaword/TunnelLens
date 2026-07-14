package com.gagaworld.modernsocks.ui.profileedit

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.data.model.ProfileValidator
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ProfileEditScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun invalidDraftDisplaysFieldErrors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = mutableStateOf(ProfileEditUiState(isLoading = false))
        composeRule.setContent {
            ModernSocksTheme(dynamicColor = false) {
                ProfileEditScreen(
                    uiState = state.value,
                    isNewProfile = true,
                    onAction = { action ->
                        if (action == ProfileEditAction.Save) {
                            state.value = state.value.copy(
                                errors = ProfileValidator.validate(state.value.draft).errors,
                            )
                        }
                    },
                    onBack = {},
                    onAppRouting = {},
                )
            }
        }

        composeRule.onAllNodesWithText(context.getString(R.string.action_save))[0].performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.validation_required))
            .assertCountEquals(2)
    }

    @Test
    fun backWithUnsavedChanges_requiresDiscardConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backCalled = mutableStateOf(false)
        composeRule.setContent {
            ModernSocksTheme(dynamicColor = false) {
                ProfileEditScreen(
                    uiState = ProfileEditUiState(
                        isLoading = false,
                        hasUnsavedChanges = true,
                    ),
                    isNewProfile = true,
                    onAction = {},
                    onBack = { backCalled.value = true },
                    onAppRouting = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_back))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.profile_discard_changes_title))
            .assertIsDisplayed()
        assertFalse(backCalled.value)

        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        assertFalse(backCalled.value)

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_back))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_discard)).performClick()
        assertTrue(backCalled.value)
    }
}
