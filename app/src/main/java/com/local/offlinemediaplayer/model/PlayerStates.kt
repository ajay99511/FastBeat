package com.local.offlinemediaplayer.model

import androidx.media3.common.MediaItem

/**
 * Snapshot of the Audio Player state before interruption (e.g., by a video).
 */
data class AudioPlayerState(
    val queue: List<MediaFile>,
    val currentIndex: Int,
    val position: Long,
    val isPlaying: Boolean,
    val isShuffleEnabled: Boolean,
    val repeatMode: Int
)

/**
 * Snapshot of Video Player state (mostly for symmetry/future expansion,
 * as video state is primarily persisted to DB).
 */
data class VideoPlayerState(
    val mediaItem: MediaItem?,
    val position: Long
)
