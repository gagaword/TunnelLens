package com.gagaworld.modernsocks.ui.profiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.data.model.ProxyProfile
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfilesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileCardShowsSelectionAndMenuActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var copied = false
        val profile = ProxyProfile(
            id = 1,
            name = "UI profile",
            host = "example.invalid",
            port = 1080,
            authenticationEnabled = true,
            hasStoredCredentials = true,
            dnsServer = null,
            mtu = 1500,
            bypassLan = true,
            autoReconnect = true,
            isSelected = true,
            createdAt = 0,
            updatedAt = 0,
        )
        composeRule.setContent {
            ModernSocksTheme(dynamicColor = false) {
                ProfilesScreen(
                    uiState = ProfilesUiState(listOf(profile), isLoading = false),
                    onAddProfile = {},
                    onEditProfile = {},
                    onImportProfiles = {},
                    onExportProfiles = {},
                    onSelectProfile = {},
                    onDuplicateProfile = { _, _ -> copied = true },
                    onDeleteProfile = {},
                    onUndoDelete = {},
                    onDeleteMessageConsumed = {},
                    onErrorConsumed = {},
                    onTransferResultConsumed = {},
                )
            }
        }

        composeRule.onNodeWithText("UI profile").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.profile_selected)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.action_more_for_profile, "UI profile"),
        ).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_copy)).performClick()
        composeRule.runOnIdle { assertTrue(copied) }
    }
}
