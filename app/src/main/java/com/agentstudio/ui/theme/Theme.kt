package com.agentstudio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = PrimaryLight,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = SecondaryLight,
    tertiary = Tertiary,
    onTertiary = OnPrimary,
    tertiaryContainer = TertiaryDark,
    onTertiaryContainer = TertiaryLight,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = OnSurface,
    error = Error,
    onError = OnPrimary,
    errorContainer = ErrorDark,
    onErrorContainer = OnPrimary,
    outline = SurfaceLight,
    outlineVariant = BackgroundLight
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = Primary,
    secondary = SecondaryDark,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = Secondary,
    tertiary = TertiaryDark,
    onTertiary = OnPrimary,
    tertiaryContainer = TertiaryLight,
    onTertiaryContainer = Tertiary,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF1E293B),
    error = Error,
    onError = OnPrimary,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = ErrorDark,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun AgentStudioTheme(
    darkTheme: Boolean = true, // Always use dark theme for this app
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
