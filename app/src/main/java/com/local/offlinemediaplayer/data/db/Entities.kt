package com.local.offlinemediaplayer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey val mediaId: Long,
    val position: Long,
    val duration: Long = 0, // Added duration to calculate progress percentage
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

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val isVideo: Boolean
)

@Entity(
    tableName = "playlist_media_cross_ref",
    primaryKeys = ["playlistId", "mediaId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["mediaId"])],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistMediaCrossRef(
    val playlistId: String,
    val mediaId: Long,
    val addedAt: Long = System.currentTimeMillis() // Used for ordering
)
