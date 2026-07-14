package com.gagaworld.modernsocks.data.locale

enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    SIMPLIFIED_CHINESE("zh-CN"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromLanguageTag(languageTag: String?): AppLanguage = when {
            languageTag.isNullOrBlank() -> SYSTEM
            languageTag.startsWith("zh", ignoreCase = true) -> SIMPLIFIED_CHINESE
            languageTag.startsWith("en", ignoreCase = true) -> ENGLISH
            else -> SYSTEM
        }
    }
}
