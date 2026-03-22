package com.local.offlinemediaplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM playback_history WHERE mediaType = 'AUDIO' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPlayedAudio(): PlaybackHistory?

    @Query("SELECT * FROM playback_history WHERE mediaType = 'AUDIO' ORDER BY timestamp DESC LIMIT 1")
    fun getLastPlayedAudioFlow(): Flow<PlaybackHistory?>

    // Fetch unfinished videos (progress > 0 and < 95% complete), ordered by most recently watched
    @Query("SELECT * FROM playback_history WHERE mediaType = 'VIDEO' AND position > 0 AND (duration = 0 OR position < (duration * 0.95)) ORDER BY timestamp DESC LIMIT 10")
    fun getContinueWatching(): Flow<List<PlaybackHistory>>

    // --- Analytics ---
    @Query("INSERT OR IGNORE INTO media_analytics (mediaId, playCount, skipCount, lastPlayed) VALUES (:mediaId, 0, 0, :timestamp)")
    suspend fun initAnalytics(mediaId: Long, timestamp: Long)

    @Query("UPDATE media_analytics SET playCount = playCount + 1, lastPlayed = :timestamp WHERE mediaId = :mediaId")
    suspend fun incrementPlayCount(mediaId: Long, timestamp: Long)

    @Query("SELECT * FROM media_analytics WHERE mediaId = :mediaId")
    suspend fun getAnalytics(mediaId: Long): MediaAnalytics?

    @Query("SELECT * FROM media_analytics WHERE mediaId IN (:mediaIds)")
    suspend fun getAnalyticsForIds(mediaIds: List<Long>): List<MediaAnalytics>

    @Query("SELECT mediaId FROM media_analytics ORDER BY playCount DESC LIMIT 1")
    suspend fun getOverallFavoriteMediaId(): Long?

    // --- Advanced Analytics (New) ---

    // Playtime
    @Query("INSERT OR IGNORE INTO daily_playtime (date, totalPlaytimeMs) VALUES (:date, 0)")
    suspend fun initDailyPlaytime(date: Long)

    @Query("UPDATE daily_playtime SET totalPlaytimeMs = totalPlaytimeMs + :durationMs WHERE date = :date")
    suspend fun addToDailyPlaytime(date: Long, durationMs: Long)

    @Query("SELECT totalPlaytimeMs FROM daily_playtime WHERE date = :date")
    fun getPlaytimeForDay(date: Long): Flow<Long?>

    @Query("SELECT SUM(totalPlaytimeMs) FROM daily_playtime WHERE date >= :startDate AND date <= :endDate")
    fun getPlaytimeRange(startDate: Long, endDate: Long): Flow<Long?>

    // Get all dates with activity to calculate streak in code
    @Query("SELECT date FROM daily_playtime WHERE totalPlaytimeMs > 60000 ORDER BY date DESC")
    fun getActiveDays(): Flow<List<Long>>

    // Play Events
    @Insert
    suspend fun logPlayEvent(event: PlayEvent)

    // Most played in range (Current Favorite)
    @Query("SELECT mediaId FROM play_events WHERE timestamp >= :sinceTimestamp GROUP BY mediaId ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun getMostPlayedMediaIdSince(sinceTimestamp: Long): Long?

    // --- Playlists ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsWithRefs(): Flow<List<PlaylistWithRefs>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMediaToPlaylist(crossRef: PlaylistMediaCrossRef)

    @Query("DELETE FROM playlist_media_cross_ref WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeMediaFromPlaylist(playlistId: String, mediaId: Long)

    @Query("DELETE FROM playlist_media_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylistMedia(playlistId: String)

    @Query("SELECT mediaId FROM playlist_media_cross_ref WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getMediaIdsForPlaylist(playlistId: String): List<Long>

    // New methods for Playlist management
    @Query("SELECT COUNT(*) FROM playlists WHERE name = :name AND isVideo = :isVideo")
    suspend fun getPlaylistCount(name: String, isVideo: Boolean): Int

    @Query("UPDATE playlists SET name = :newName WHERE id = :id")
    suspend fun updatePlaylistName(id: String, newName: String)

    // --- Bookmarks ---
    @Query("SELECT * FROM bookmarks WHERE mediaId = :mediaId ORDER BY timestamp ASC")
    fun getBookmarks(mediaId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    // --- Persistent Queue ---
    @Query("SELECT * FROM current_queue ORDER BY sortOrder ASC")
    suspend fun getSavedQueue(): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItemEntity>)

    @Query("DELETE FROM current_queue")
    suspend fun clearQueue()

    @Transaction
    suspend fun replaceQueue(items: List<QueueItemEntity>) {
        clearQueue()
        insertQueueItems(items)
    }
}
