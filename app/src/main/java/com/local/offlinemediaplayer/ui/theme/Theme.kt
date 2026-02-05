
package com.local.offlinemediaplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- Theme Configuration Data Class ---
data class AppThemeConfig(
    val id: String,
    val primaryColor: Color,
    val subtitle: String,
    val curatedTitle: String
)

// --- Default Theme (Fallback) ---
val DefaultTheme = AppThemeConfig(
    id = "orange",
    primaryColor = Color(0xFFFF5500),
    subtitle = "HIDDEN LEAF MEDIA SCROLL",
    curatedTitle = "Hokage Selections"
)

// --- CompositionLocal Provider ---
val LocalAppTheme = staticCompositionLocalOf { DefaultTheme }

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun OfflineMediaPlayerTheme(
    currentThemeConfig: AppThemeConfig? = null, // Optional dynamic config
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic color to force our custom theme
    content: @Composable () -> Unit
) {
    // 1. Determine active config (default to fallback if null)
    val activeTheme = currentThemeConfig ?: DefaultTheme

    // 2. Bridge Custom Color to Material3 ColorScheme
    // We override 'primary' so standard components (Sliders, TextFields) pick it up automatically.
    val colorScheme = darkColorScheme(
        primary = activeTheme.primaryColor,
        secondary = activeTheme.primaryColor,
        tertiary = Pink80,
        background = Color(0xFF0B0B0F),
        surface = Color(0xFF1E1E24)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color(0xFF0B0B0F).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    // 3. Wrap in CompositionLocalProvider
    CompositionLocalProvider(LocalAppTheme provides activeTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
