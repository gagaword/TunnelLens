package com.gagaworld.modernsocks.data.locale

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun languageTagsMapToSupportedOptions() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.fromLanguageTag("zh-Hans-CN"),
        )
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("fr-FR"))
    }
}
