package com.gagaworld.modernsocks.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = OnDeepBlue,
    primaryContainer = PaleBlue,
    onPrimaryContainer = OnPaleBlue,
    secondary = SeaGreen,
    onSecondary = OnSeaGreen,
    secondaryContainer = PaleGreen,
    onSecondaryContainer = OnPaleGreen,
    background = WarmBackground,
)

private val DarkColors = darkColorScheme(
    primary = ColorTokens.DarkPrimary,
    onPrimary = ColorTokens.DarkOnPrimary,
    primaryContainer = ColorTokens.DarkPrimaryContainer,
    onPrimaryContainer = ColorTokens.DarkOnPrimaryContainer,
    secondary = PaleGreen,
    onSecondary = OnPaleGreen,
    background = DarkBackground,
)

private object ColorTokens {
    val DarkPrimary = androidx.compose.ui.graphics.Color(0xFFAFC6FF)
    val DarkOnPrimary = androidx.compose.ui.graphics.Color(0xFF002E69)
    val DarkPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF164584)
    val DarkOnPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD9E2FF)
}

@Composable
fun ModernSocksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
