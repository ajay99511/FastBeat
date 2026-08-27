package com.local.offlinemediaplayer.domain

import com.local.offlinemediaplayer.data.db.PlaybackHistory
import com.local.offlinemediaplayer.model.MediaFile
import javax.inject.Inject

/** A partially-watched video with its saved resume position. */
data class ContinueWatchingItem(
    val media: MediaFile,
    val position: Long,
    val duration: Long,
) {
    val progress: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/**
 * Pairs saved playback positions with the videos they belong to, dropping any whose file is gone.
 *
 * Extracted in P5-A.1 from **two** places that were doing the same join by hand —
 * `LibraryViewModel.continueWatching` and `AnalyticsViewModel.continueWatchingList` — with
 * different results. Only the Library copy applied the duration fallback below, so the same video
 * could show a progress bar on one screen and none on the other.
 *
 * Takes lists rather than a DAO for the same reason as [CalculateStreakUseCase]: both call sites
 * `combine` this with a live video list, and a one-shot read would freeze the screen.
 *
 * The rules:
 *  - History for media that no longer exists is dropped. Videos get deleted between sessions, and
 *    a resume entry pointing at a missing file must not surface as a broken row.
 *  - Duration falls back to the media's own when history recorded none. A zero duration would make
 *    every progress bar read 0 %, which looks like "not started" for a half-watched film.
 *  - Input order is preserved — the query already sorts most-recently-watched first, and that is
 *    the order the row is meant to show.
 */
class GetContinueWatchingUseCase
    @Inject
    constructor() {
        operator fun invoke(
            videos: List<MediaFile>,
            history: List<PlaybackHistory>,
        ): List<ContinueWatchingItem> {
            val videosById = videos.associateBy { it.id }
            return history.mapNotNull { entry ->
                val media = videosById[entry.mediaId] ?: return@mapNotNull null
                ContinueWatchingItem(
                    media = media,
                    position = entry.position,
                    duration = if (entry.duration > 0) entry.duration else media.duration,
                )
            }
        }
    }
