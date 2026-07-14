package com.gagaworld.modernsocks.data.preferences

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val autoConnect: Boolean = false,
    val expandAdvancedByDefault: Boolean = false,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
