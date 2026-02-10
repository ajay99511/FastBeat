package com.local.offlinemediaplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaybackHistory::class,
        MediaAnalytics::class,
        PlaylistEntity::class,
        PlaylistMediaCrossRef::class,
        BookmarkEntity::class,
        QueueItemEntity::class,
        DailyPlaytime::class,
        PlayEvent::class
    ],
    version = 5, // Bumped version to 5 for Track Persistence
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}