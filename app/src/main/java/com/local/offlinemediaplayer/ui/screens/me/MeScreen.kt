package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.AnalyticsViewModel
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.ThemeViewModel

/**
 * The "Me" tab: listening stats, suggestions, and the app's own settings.
 *
 * This function owns the screen's state and the vertical order of its sections, and nothing else.
 * Each section lives in its own file in this package and takes data and callbacks, so it can be
 * rendered on its own. The sections are, top to bottom: theme switcher, search box, continue
 * watching, shuffle-all, library stats, activity trends, listening activity, suggestions, and the
 * two settings cards.
 */
@Composable
fun MeScreen(
    viewModel: PlaybackViewModel,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    analyticsViewModel: AnalyticsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onPlayMedia: (MediaFile) -> Unit,
    onNavigateToAccessibilityGuide: () -> Unit,
    isSearchVisible: Boolean,
) {
    val theme = LocalAppTheme.current
    val currentTheme by themeViewModel.currentTheme.collectAsStateWithLifecycle()
    val isDarkTheme by themeViewModel.isDarkTheme.collectAsStateWithLifecycle()
    val audioList by libraryViewModel.audioList.collectAsStateWithLifecycle()

    // Realtime Analytics
    val analytics by analyticsViewModel.realtimeAnalytics.collectAsStateWithLifecycle()

    // Continue Watching Data
    val continueWatchingList by analyticsViewModel.continueWatchingList.collectAsStateWithLifecycle()

    // Library Stats & Activity Trends
    val libraryStats by analyticsViewModel.libraryStats.collectAsStateWithLifecycle()
    val activityBuckets by analyticsViewModel.activityBuckets.collectAsStateWithLifecycle()
    val activityRange by analyticsViewModel.activityRange.collectAsStateWithLifecycle()
    val topLists by analyticsViewModel.topLists.collectAsStateWithLifecycle()

    // Lifetime totals and week-over-week momentum
    val listeningTotals by analyticsViewModel.listeningTotals.collectAsStateWithLifecycle()

    // Simple local search state for MeScreen
    var searchQuery by remember { mutableStateOf("") }

    var isExpanded by remember { mutableStateOf(false) }
    val suggestions = rememberSuggestions(audioList, searchQuery, isExpanded)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp), // Space for MiniPlayer
    ) {
        // Theme switcher
        ThemeSwitcherRow(
            activeThemeId = currentTheme.id,
            onThemeSelected = { themeViewModel.updateTheme(it) },
        )

        // Search box
        CollapsibleSearchBox(
            isVisible = isSearchVisible,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholderText = "Search in suggested...",
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Continue watching
        ContinueWatchingSection(
            entries = continueWatchingList,
            primaryColor = theme.primaryColor,
            onPlayMedia = onPlayMedia,
        )

        // Shuffle all audio
        ShuffleAllButton(
            audioList = audioList,
            primaryColor = theme.primaryColor,
            onShuffle = { list ->
                if (list.isNotEmpty()) {
                    viewModel.setQueue(list, 0, true)
                }
            },
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Library stats
        LibraryStatsSection(
            stats = libraryStats,
            primaryColor = theme.primaryColor,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Activity trends
        ActivityTrendsSection(
            buckets = activityBuckets,
            selectedRange = activityRange,
            onRangeSelected = analyticsViewModel::selectActivityRange,
            primaryColor = theme.primaryColor,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Top tracks / artists / albums, over the range the chart above is showing
        TopListsSection(
            lists = topLists,
            range = activityRange,
            primaryColor = theme.primaryColor,
            onPlayMedia = onPlayMedia,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Listening activity
        ListeningActivitySection(
            analytics = analytics,
            totals = listeningTotals,
            currentTrack = viewModel.currentTrack,
            lastPlayedAudio = viewModel.lastPlayedAudio,
            primaryColor = theme.primaryColor,
            onPlayMedia = onPlayMedia,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Suggestions
        if (suggestions.pool.isNotEmpty()) {
            SuggestedTracksSection(
                tracks = suggestions.visible,
                featured = if (searchQuery.isEmpty()) suggestions.pool.firstOrNull() else null,
                showMoreButton = !isExpanded && searchQuery.isEmpty() && suggestions.pool.size > 5,
                primaryColor = theme.primaryColor,
                curatedTitle = theme.curatedTitle,
                onTrackClick = { song ->
                    val startIndex = audioList.indexOfFirst { it.id == song.id }
                    if (startIndex >= 0) {
                        viewModel.setQueue(audioList, startIndex, false)
                    } else {
                        onPlayMedia(song)
                    }
                },
                onPlayAll = {
                    if (audioList.isNotEmpty()) {
                        viewModel.setQueue(audioList, 0, false)
                    }
                },
                onShuffleAll = {
                    if (audioList.isNotEmpty()) {
                        viewModel.setQueue(audioList, audioList.indices.random(), true)
                    }
                },
                onExpand = { isExpanded = true },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dark mode
        DarkModeToggleCard(
            isDarkTheme = isDarkTheme,
            primaryColor = theme.primaryColor,
            onToggle = { themeViewModel.toggleThemeMode() },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Accessibility guide
        AccessibilityGuideCard(
            primaryColor = theme.primaryColor,
            onClick = onNavigateToAccessibilityGuide,
        )
    }
}

/**
 * The suggestion pool and the slice of it currently on screen.
 *
 * [pool] is the shuffled sample taken once per library; [visible] is what the list actually shows
 * after the search filter and the collapsed/expanded choice have been applied. [MeScreen] needs
 * both: the pool decides whether the section appears at all and whether a "view all" affordance is
 * warranted, while the slice is what gets rendered.
 */
private data class SuggestionState(
    val pool: List<MediaFile>,
    val visible: List<MediaFile>,
)

/**
 * Derives [SuggestionState] from the library and the current search/expansion state.
 *
 * The sample is keyed on [audioList] so it is drawn once and survives typing in the search box —
 * re-shuffling on every keystroke would reorder the list under the user's fingers.
 */
@Composable
private fun rememberSuggestions(
    audioList: List<MediaFile>,
    searchQuery: String,
    isExpanded: Boolean,
): SuggestionState {
    val pool =
        remember(audioList) {
            if (audioList.isNotEmpty()) {
                audioList
                    .asSequence()
                    .shuffled()
                    .take(50)
                    .toList()
            } else {
                emptyList()
            }
        }

    val filtered =
        if (searchQuery.isNotEmpty()) {
            pool.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist?.contains(searchQuery, ignoreCase = true) == true
            }
        } else {
            pool
        }

    val visible = if (isExpanded || searchQuery.isNotEmpty()) filtered else filtered.take(5)

    return SuggestionState(pool = pool, visible = visible)
}
