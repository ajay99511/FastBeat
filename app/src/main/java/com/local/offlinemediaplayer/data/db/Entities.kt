package com.local.offlinemediaplayer.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey val mediaId: Long,
    val position: Long,
    val duration: Long = 0, // Added duration to calculate progress percentage
    val timestamp: Long,
    val mediaType: String, // "AUDIO" or "VIDEO"
    val audioTrackIndex: Int = -1, // New field for track persistence
    val subtitleTrackIndex: Int = -1 // New field for track persistence (-1 = default/none or unset)
)

@Entity(tableName = "media_analytics")
data class MediaAnalytics(
    @PrimaryKey val mediaId: Long,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0
)

// NEW: Tracks total listening time per day (date is normalized to midnight)
@Entity(tableName = "daily_playtime")
data class DailyPlaytime(
    @PrimaryKey val date: Long,
    val totalPlaytimeMs: Long
)

// NEW: Log individual plays to calculate "Recent Favorites"
@Entity(
    tableName = "play_events",
    indices = [Index(value = ["mediaId"]), Index(value = ["timestamp"])]
)
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val timestamp: Long = System.currentTimeMillis()
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

// Helper class for Room Relations to observe changes in both tables
data class PlaylistWithRefs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val refs: List<PlaylistMediaCrossRef>
)

// NEW: For timestamp bookmarks
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val timestamp: Long,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

// NEW: For persistent "Now Playing" queue
@Entity(tableName = "current_queue")
data class QueueItemEntity(
    @PrimaryKey val mediaId: Long,
    val sortOrder: Int
)