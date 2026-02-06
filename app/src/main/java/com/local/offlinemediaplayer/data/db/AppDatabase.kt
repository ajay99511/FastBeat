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
        QueueItemEntity::class
    ],
    version = 3, // Bumped version to 3
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}
