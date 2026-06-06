package com.local.offlinemediaplayer.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.screens.MeScreen
import com.local.offlinemediaplayer.viewmodel.AnalyticsViewModel
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.ThemeViewModel

@Composable
fun AdaptiveMeScreen(
    viewModel: PlaybackViewModel,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    analyticsViewModel: AnalyticsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onPlayMedia: (MediaFile) -> Unit,
    onNavigateToAccessibilityGuide: () -> Unit,
    isSearchVisible: Boolean
) {
    val widthClass = LocalWindowSizeClass.current
    val posture = LocalDevicePosture.current

    val modifier = if (widthClass == AppWidthClass.Expanded) {
        Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = AdaptiveLayoutConstants.MAX_CONTENT_WIDTH)
    } else {
        Modifier.fillMaxSize()
    }

    Box(modifier = modifier) {
        // We wrap MeScreen instead of passing modifier, as it currently uses fillMaxSize internally
        // (If MeScreen takes a modifier, it should be passed here, but assuming it doesn't)
        MeScreen(
            viewModel = viewModel,
            themeViewModel = themeViewModel,
            analyticsViewModel = analyticsViewModel,
            libraryViewModel = libraryViewModel,
            onPlayMedia = onPlayMedia,
            onNavigateToAccessibilityGuide = onNavigateToAccessibilityGuide,
            isSearchVisible = isSearchVisible
        )
    }
}
