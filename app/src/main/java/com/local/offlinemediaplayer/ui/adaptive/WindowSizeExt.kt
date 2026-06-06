package com.local.offlinemediaplayer.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass

enum class AppWidthClass {
    Compact, Medium, Expanded
}

fun WindowWidthSizeClass.toAppWidthClass(): AppWidthClass {
    // WindowWidthSizeClass in androidx.window.core.layout behaves such that
    // < 600dp is COMPACT, 600-839dp is MEDIUM, >= 840dp is EXPANDED
    return when (this) {
        WindowWidthSizeClass.COMPACT -> AppWidthClass.Compact
        WindowWidthSizeClass.MEDIUM -> AppWidthClass.Medium
        WindowWidthSizeClass.EXPANDED -> AppWidthClass.Expanded
        else -> AppWidthClass.Compact // Fallback
    }
}

fun appWidthClassFromDp(widthDp: Float): AppWidthClass {
    return when {
        widthDp < 600f -> AppWidthClass.Compact
        widthDp < 840f -> AppWidthClass.Medium
        else -> AppWidthClass.Expanded
    }
}

object AdaptiveLayoutConstants {
    val MAX_CONTENT_WIDTH: Dp = 840.dp
    const val MEDIUM_WIDTH_DP: Int = 600
    const val EXPANDED_WIDTH_DP: Int = 840
}
