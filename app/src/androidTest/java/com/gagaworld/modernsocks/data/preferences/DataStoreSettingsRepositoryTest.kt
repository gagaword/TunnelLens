package com.gagaworld.modernsocks.data.preferences

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {
    @Test
    fun preferencesAreObservableAndPersisted() = runTest {
        val repository = DataStoreSettingsRepository(ApplicationProvider.getApplicationContext())

        repository.setThemeMode(ThemeMode.DARK)
        repository.setDynamicColor(false)
        repository.setAutoConnect(true)
        repository.setExpandAdvancedByDefault(true)

        val settings = repository.settings.first()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertFalse(settings.dynamicColor)
        assertTrue(settings.autoConnect)
        assertTrue(settings.expandAdvancedByDefault)
    }
}
