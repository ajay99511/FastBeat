package com.local.offlinemediaplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    // --- Playback History ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHistory(history: PlaybackHistory)

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId")
    suspend fun getHistory(mediaId: Long): PlaybackHistory?

    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPlayed(): PlaybackHistory?

    // --- Analytics ---
    @Query("INSERT OR IGNORE INTO media_analytics (mediaId, playCount, skipCount, lastPlayed) VALUES (:mediaId, 0, 0, :timestamp)")
    suspend fun initAnalytics(mediaId: Long, timestamp: Long)

    @Query("UPDATE media_analytics SET playCount = playCount + 1, lastPlayed = :timestamp WHERE mediaId = :mediaId")
    suspend fun incrementPlayCount(mediaId: Long, timestamp: Long)

    @Query("SELECT * FROM media_analytics WHERE mediaId = :mediaId")
    suspend fun getAnalytics(mediaId: Long): MediaAnalytics?
}
