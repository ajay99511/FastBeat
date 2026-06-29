package com.local.offlinemediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The set of auto-generated ("smart") playlists. These are derived entirely from data the app
 * already collects — [MediaRepository.audioList] (MediaStore) and the media_analytics table —
 * so they require NO new data collection and are never persisted as real playlists.
 *
 * [id] is a stable, URL-safe identifier used for navigation routes and the player's playlist
 * context ("SMART_<id>"). [title] is the user-facing name.
 */
enum class SmartPlaylistType(val id: String, val title: String) {
    MOST_PLAYED("most_played", "Most Played"),
    RECENTLY_ADDED("recently_added", "Recently Added"),
    FORGOTTEN("forgotten", "Forgotten"),
    NEVER_PLAYED("never_played", "Never Played"),
    MOST_SKIPPED("most_skipped", "Most Skipped");

    companion object {
        fun fromId(id: String): SmartPlaylistType? = entries.find { it.id == id }
    }
}

@HiltViewModel
class SmartPlaylistViewModel @Inject constructor(
    mediaRepository: MediaRepository,
    mediaDao: MediaDao
) : ViewModel() {

    companion object {
        private const val MOST_PLAYED_LIMIT = 25
        private const val MOST_SKIPPED_LIMIT = 25
        private const val RECENTLY_ADDED_DAYS = 30L
        private const val FORGOTTEN_DAYS = 30L
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val DAY_SEC = 24L * 60 * 60
    }

    /**
     * Reactive map of every smart playlist to its resolved songs. Recomputes automatically when
     * either the audio library or playback analytics change. All ordering is intrinsic to the
     * category (e.g. Most Played is play-count descending), so detail screens preserve it as-is.
     *
     * Unit notes (verified against existing code):
     *  - MediaFile.dateAdded is epoch SECONDS (MediaStore.DATE_ADDED, see FormatUtils.formatDate).
     *  - MediaAnalytics.lastPlayed is epoch MILLIS (System.currentTimeMillis, see recordPlay).
     */
    val smartPlaylists: StateFlow<Map<SmartPlaylistType, List<MediaFile>>> =
        combine(
            mediaRepository.audioList,
            mediaDao.getAllAnalyticsFlow()
        ) { audio, analytics ->
            val byId = audio.associateBy { it.id }
            val analyticsById = analytics.associateBy { it.mediaId }
            val nowMs = System.currentTimeMillis()
            val recentlyAddedThresholdSec = (nowMs / 1000) - RECENTLY_ADDED_DAYS * DAY_SEC
            val forgottenThresholdMs = FORGOTTEN_DAYS * DAY_MS

            val mostPlayed = analytics
                .filter { it.playCount > 0 }
                .sortedByDescending { it.playCount }
                .take(MOST_PLAYED_LIMIT)
                .mapNotNull { byId[it.mediaId] }

            val recentlyAdded = audio
                .filter { it.dateAdded > recentlyAddedThresholdSec }
                .sortedByDescending { it.dateAdded }

            val forgotten = analytics
                .filter { it.playCount > 0 && it.lastPlayed > 0 && (nowMs - it.lastPlayed) > forgottenThresholdMs }
                .sortedBy { it.lastPlayed }
                .mapNotNull { byId[it.mediaId] }

            // A track is "never played" if it has no analytics row at all, or a zero play count.
            val neverPlayed = audio
                .filter { (analyticsById[it.id]?.playCount ?: 0) == 0 }

            val mostSkipped = analytics
                .filter { it.skipCount > 0 }
                .sortedByDescending { it.skipCount }
                .take(MOST_SKIPPED_LIMIT)
                .mapNotNull { byId[it.mediaId] }

            mapOf(
                SmartPlaylistType.MOST_PLAYED to mostPlayed,
                SmartPlaylistType.RECENTLY_ADDED to recentlyAdded,
                SmartPlaylistType.FORGOTTEN to forgotten,
                SmartPlaylistType.NEVER_PLAYED to neverPlayed,
                SmartPlaylistType.MOST_SKIPPED to mostSkipped
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}
