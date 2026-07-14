package com.gagaworld.modernsocks.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gagaworld.modernsocks.MainActivity
import com.gagaworld.modernsocks.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TunnelLensAppShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun drawerAndOverflowNavigateToTopLevelDestinations() {
        val context = composeRule.activity

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.navigation_open_drawer))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.navigation_profiles))
            .performClick()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_more))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(OVERFLOW_SETTINGS_TEST_TAG).performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_language_title))
            .assertIsDisplayed()
    }

    @Test
    fun profileEditorBackReturnsToProfiles() {
        val context = composeRule.activity

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.navigation_open_drawer))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.navigation_profiles))
            .performClick()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_add_profile))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.profile_edit_new_title))
            .assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_back))
            .performClick()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_add_profile))
            .assertIsDisplayed()
    }
}
