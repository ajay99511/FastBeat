package com.local.offlinemediaplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PlaybackHistory::class, MediaAnalytics::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}