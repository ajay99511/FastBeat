package com.local.offlinemediaplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey val mediaId: Long,
    val position: Long,
    val timestamp: Long,
    val mediaType: String // "AUDIO" or "VIDEO"
)

@Entity(tableName = "media_analytics")
data class MediaAnalytics(
    @PrimaryKey val mediaId: Long,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0
)
