package com.ste4n1.blechat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.ste4n1.blechat.data.SettingsRepository

@Composable
fun BleChatTheme(
    themeMode: String = SettingsRepository.THEME_SYSTEM,
    useMaterialYou: Boolean = true,
    customColor: Color = Color(0xFF6750A4),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        SettingsRepository.THEME_LIGHT -> {
            if (useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(context)
            } else if (useMaterialYou) {
                lightColorScheme()
            } else {
                customLightScheme(customColor)
            }
        }

        SettingsRepository.THEME_DARK, SettingsRepository.THEME_DARK_GRAY -> {
            val base = when {
                useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    dynamicDarkColorScheme(context)
                useMaterialYou -> darkColorScheme()
                else -> customDarkScheme(customColor)
            }
            base.copy(
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                surfaceVariant = Color(0xFF2C2C2C),
                onBackground = Color(0xFFE6E6E6),
                onSurface = Color(0xFFE6E6E6),
                onSurfaceVariant = Color(0xFFCACACA)
            )
        }

        SettingsRepository.THEME_AMOLED -> {
            val base = when {
                useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    dynamicDarkColorScheme(context)
                useMaterialYou -> darkColorScheme()
                else -> customDarkScheme(customColor)
            }
            base.copy(
                background = Color(0xFF000000),
                surface = Color(0xFF000000),
                surfaceContainer = Color(0xFF0A0A0A),
                surfaceContainerLow = Color(0xFF050505),
                surfaceContainerHigh = Color(0xFF121212),
                surfaceVariant = Color(0xFF1A1A1A),
                onBackground = Color(0xFFEAEAEA),
                onSurface = Color(0xFFEAEAEA),
                onSurfaceVariant = Color(0xFFBDBDBD),
                outline = Color(0xFF444444),
                outlineVariant = Color(0xFF333333)
            )
        }

        else -> {
            if (systemDark) {
                if (useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicDarkColorScheme(context)
                } else if (useMaterialYou) {
                    darkColorScheme()
                } else {
                    customDarkScheme(customColor)
                }
            } else {
                if (useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicLightColorScheme(context)
                } else if (useMaterialYou) {
                    lightColorScheme()
                } else {
                    customLightScheme(customColor)
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

private fun customLightScheme(seed: Color): ColorScheme {
    val onPrimary = if (seed.luminance() > 0.5f) Color.Black else Color.White
    return lightColorScheme(
        primary = seed,
        onPrimary = onPrimary,
        primaryContainer = lerp(seed, Color.White, 0.72f),
        onPrimaryContainer = lerp(seed, Color.Black, 0.72f),
        inversePrimary = lerp(seed, Color.White, 0.4f),
        secondary = lerp(seed, Color.Gray, 0.25f),
        onSecondary = onPrimary,
        secondaryContainer = lerp(seed, Color.White, 0.78f),
        onSecondaryContainer = lerp(seed, Color.Black, 0.7f),
        tertiary = lerp(seed, Color(0xFF00BFA5), 0.45f),
        onTertiary = Color.White,
        tertiaryContainer = lerp(seed, Color.White, 0.82f),
        onTertiaryContainer = lerp(seed, Color.Black, 0.7f),
        background = lerp(seed, Color.White, 0.95f),
        onBackground = lerp(seed, Color.Black, 0.2f),
        surface = lerp(seed, Color.White, 0.97f),
        onSurface = lerp(seed, Color.Black, 0.2f),
        surfaceVariant = lerp(seed, Color.White, 0.86f),
        onSurfaceVariant = lerp(seed, Color.Black, 0.45f),
        outline = lerp(seed, Color.Gray, 0.5f),
        outlineVariant = lerp(seed, Color.Gray, 0.75f)
    )
}

private fun customDarkScheme(seed: Color): ColorScheme {
    val primary = lerp(seed, Color.White, 0.35f)
    return darkColorScheme(
        primary = primary,
        onPrimary = Color.Black,
        primaryContainer = lerp(seed, Color.Black, 0.35f),
        onPrimaryContainer = lerp(seed, Color.White, 0.15f),
        inversePrimary = lerp(seed, Color.Black, 0.25f),
        secondary = lerp(seed, Color.White, 0.45f),
        onSecondary = Color.Black,
        secondaryContainer = lerp(seed, Color.Black, 0.45f),
        onSecondaryContainer = lerp(seed, Color.White, 0.2f),
        tertiary = lerp(Color(0xFF00BFA5), Color.White, 0.4f),
        onTertiary = Color.Black,
        tertiaryContainer = lerp(seed, Color.Black, 0.6f),
        onTertiaryContainer = lerp(seed, Color.White, 0.2f),
        background = lerp(seed, Color.Black, 0.9f),
        onBackground = lerp(seed, Color.White, 0.15f),
        surface = lerp(seed, Color.Black, 0.88f),
        onSurface = lerp(seed, Color.White, 0.15f),
        surfaceVariant = lerp(seed, Color.Black, 0.7f),
        onSurfaceVariant = lerp(seed, Color.White, 0.35f),
        outline = lerp(seed, Color.Gray, 0.4f),
        outlineVariant = lerp(seed, Color.Gray, 0.6f)
    )
}
