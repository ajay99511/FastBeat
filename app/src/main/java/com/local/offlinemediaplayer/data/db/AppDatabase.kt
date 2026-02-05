package com.local.offlinemediaplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaybackHistory::class,
        MediaAnalytics::class,
        PlaylistEntity::class,
        PlaylistMediaCrossRef::class
    ],
    version = 2, // Bumped version
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}
