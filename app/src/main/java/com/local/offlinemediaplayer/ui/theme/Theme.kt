
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
    currentThemeConfig: AppThemeConfig? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 1. Determine active config (default to fallback if null)
    val activeTheme = currentThemeConfig ?: DefaultTheme

    // 2. Build ColorScheme based on Dark/Light mode
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = activeTheme.primaryColor,
            secondary = activeTheme.primaryColor,
            tertiary = Pink80,
            background = Color(0xFF0B0B0F), // Deep Dark
            surface = Color(0xFF1E1E24),
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = activeTheme.primaryColor,
            secondary = activeTheme.primaryColor,
            tertiary = Pink40,
            background = Color(0xFFF2F2F7), // Light Gray (iOS style)
            surface = Color(0xFFFFFFFF),    // Pure White
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            // If light theme, status bar icons should be dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
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
