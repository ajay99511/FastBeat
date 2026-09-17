package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.viewmodel.AnalyticsViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * The whole statistics surface: library summary, activity chart, top lists, listening activity.
 *
 * Grouped into one composable because they are one feature, not four that happen to sit near each
 * other — the chart's range selector governs the top lists too, so splitting them across call sites
 * would put the control and the thing it controls in different files.
 *
 * It is also the seam DS-6.1 promised. Moving the statistics onto a dedicated screen later means
 * calling this from there instead of from [MeScreen]; nothing below needs to change, and no section
 * needs to be re-plumbed.
 *
 * **This is the one composable here that takes a ViewModel.** The sections it calls all take plain
 * data and callbacks and stay renderable on their own — that property is what makes them testable,
 * and it is deliberately not spent here. This layer exists precisely so that collecting seven flows
 * happens in one place instead of being pushed up into [MeScreen]'s parameter list.
 */
@Composable
internal fun StatsSections(
    analyticsViewModel: AnalyticsViewModel,
    currentTrack: StateFlow<MediaFile?>,
    lastPlayedAudio: StateFlow<MediaFile?>,
    primaryColor: Color,
    onPlayMedia: (MediaFile) -> Unit,
) {
    val libraryStats by analyticsViewModel.libraryStats.collectAsStateWithLifecycle()
    val activityBuckets by analyticsViewModel.activityBuckets.collectAsStateWithLifecycle()
    val activityRange by analyticsViewModel.activityRange.collectAsStateWithLifecycle()
    val topLists by analyticsViewModel.topLists.collectAsStateWithLifecycle()
    val analytics by analyticsViewModel.realtimeAnalytics.collectAsStateWithLifecycle()
    val listeningTotals by analyticsViewModel.listeningTotals.collectAsStateWithLifecycle()
    val records by analyticsViewModel.records.collectAsStateWithLifecycle()

    Column {
        LibraryStatsSection(
            stats = libraryStats,
            primaryColor = primaryColor,
        )

        Spacer(modifier = Modifier.height(24.dp))

        ActivityTrendsSection(
            buckets = activityBuckets,
            selectedRange = activityRange,
            onRangeSelected = analyticsViewModel::selectActivityRange,
            primaryColor = primaryColor,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Directly under the chart, because it is governed by the chart's range selector.
        TopListsSection(
            lists = topLists,
            range = activityRange,
            primaryColor = primaryColor,
            onPlayMedia = onPlayMedia,
        )

        Spacer(modifier = Modifier.height(24.dp))

        ListeningActivitySection(
            analytics = analytics,
            totals = listeningTotals,
            records = records,
            currentTrack = currentTrack,
            lastPlayedAudio = lastPlayedAudio,
            primaryColor = primaryColor,
            onPlayMedia = onPlayMedia,
        )
    }
}
