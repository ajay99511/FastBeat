package com.local.offlinemediaplayer.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.local.offlinemediaplayer.ui.adaptive.AdaptiveLayoutConstants.MAX_CONTENT_WIDTH
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme

@Composable
fun AdaptiveMainLayout(
    widthClass: AppWidthClass,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isVideoPlayingFullscreen: Boolean,
    content: @Composable () -> Unit
) {
    val theme = LocalAppTheme.current
    val themeColor = MaterialTheme.colorScheme.primary

    Row(modifier = Modifier.fillMaxSize()) {
        if (!isVideoPlayingFullscreen) {
            when (widthClass) {
                AppWidthClass.Medium -> {
                    FastBeatNavigationRail(
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        themeColor = themeColor
                    )
                }
                AppWidthClass.Expanded -> {
                    FastBeatNavigationDrawer(
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        themeColor = themeColor
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentWidth(Alignment.CenterHorizontally)
                                .widthIn(max = MAX_CONTENT_WIDTH)
                        ) {
                            content()
                        }
                    }
                    return@Row // FastBeatNavigationDrawer handles content internally
                }
                else -> {}
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}
