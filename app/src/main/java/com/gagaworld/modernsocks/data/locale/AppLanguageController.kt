package com.gagaworld.modernsocks.data.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

interface AppLanguageController {
    fun currentLanguage(): AppLanguage
    fun setLanguage(language: AppLanguage)
}

class AndroidAppLanguageController : AppLanguageController {
    override fun currentLanguage(): AppLanguage = AppLanguage.fromLanguageTag(
        AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag(),
    )

    override fun setLanguage(language: AppLanguage) {
        val locales = language.languageTag
            ?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
