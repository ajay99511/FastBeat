
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
            background = DarkSurfaceBase,
            surface = DarkSurfaceContainer,
            surfaceVariant = DarkSurfaceContainerHigh,
            surfaceContainer = DarkSurfaceContainerHigh,
            surfaceContainerLow = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceContainerHighest,
            surfaceDim = DarkSurfaceDim,
            surfaceBright = DarkSurfaceBright,
            onBackground = DarkTextPrimary,
            onSurface = DarkTextPrimary,
            onSurfaceVariant = DarkTextSecondary,
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant,
            inverseSurface = LightSurfaceBase,
            inverseOnSurface = LightTextPrimary,
            primaryContainer = activeTheme.primaryColor.copy(alpha = 0.12f),
            onPrimaryContainer = activeTheme.primaryColor,
            error = Color(0xFFFF6B6B),
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = activeTheme.primaryColor,
            secondary = activeTheme.primaryColor,
            tertiary = Pink40,
            background = LightSurfaceBase,
            surface = LightSurfaceBright,
            surfaceVariant = LightSurfaceContainer,
            surfaceContainer = LightSurfaceContainerHigh,
            surfaceContainerLow = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHighest,
            surfaceDim = LightSurfaceDim,
            surfaceBright = LightSurfaceBright,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary,
            onSurfaceVariant = LightTextSecondary,
            outline = LightOutline,
            outlineVariant = LightOutlineVariant,
            inverseSurface = DarkSurfaceBase,
            inverseOnSurface = DarkTextPrimary,
            primaryContainer = activeTheme.primaryColor.copy(alpha = 0.10f),
            onPrimaryContainer = activeTheme.primaryColor,
            error = Color(0xFFDC3545),
            onError = Color.White
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            // If light theme, status bar icons should be dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
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
