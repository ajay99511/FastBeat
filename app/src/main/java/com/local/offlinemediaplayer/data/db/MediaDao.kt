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

    // --- Playlists ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMediaToPlaylist(crossRef: PlaylistMediaCrossRef)

    @Query("DELETE FROM playlist_media_cross_ref WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeMediaFromPlaylist(playlistId: String, mediaId: Long)

    @Query("DELETE FROM playlist_media_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylistMedia(playlistId: String)

    @Query("SELECT mediaId FROM playlist_media_cross_ref WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getMediaIdsForPlaylist(playlistId: String): List<Long>
}
