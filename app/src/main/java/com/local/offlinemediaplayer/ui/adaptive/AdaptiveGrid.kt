package com.local.offlinemediaplayer.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun adaptiveGridColumns(widthClass: AppWidthClass): Int {
    return when (widthClass) {
        AppWidthClass.Compact -> 2
        AppWidthClass.Medium -> 3
        AppWidthClass.Expanded -> 4
    }
}

fun adaptiveImageCellSize(widthClass: AppWidthClass): Dp {
    return when (widthClass) {
        AppWidthClass.Compact -> 100.dp
        AppWidthClass.Medium -> 130.dp
        AppWidthClass.Expanded -> 160.dp
    }
}
