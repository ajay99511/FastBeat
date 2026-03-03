package com.local.offlinemediaplayer.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.data.db.PlaylistEntity
import com.local.offlinemediaplayer.data.db.PlaylistMediaCrossRef
import com.local.offlinemediaplayer.model.Playlist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao
) {
    private val gson = Gson()
    private val legacyFile = File(context.filesDir, "playlists.json")

    // Internal model matching the OLD JSON structure exactly
    private data class LegacyPlaylist(
        val id: String,
        val name: String,
        val items: List<String>? = null, // Old React version used 'items' (String IDs)
        val mediaIds: List<Long>? = null, // Safety for mixed versions
        val createdAt: Long,
        val isVideo: Boolean = false
    )

    // Observe playlists from DB and map to Domain Model
    // We use getAllPlaylistsWithRefs() so Room triggers an update whenever the playlist_media_cross_ref table changes
    val playlistsFlow: Flow<List<Playlist>> = mediaDao.getAllPlaylistsWithRefs().map { playlistWithRefs ->
        playlistWithRefs.map { item ->
            val sortedRefs = item.refs.sortedBy { it.addedAt }
            Playlist(
                id = item.playlist.id,
                name = item.playlist.name,
                mediaIds = sortedRefs.map { it.mediaId },
                createdAt = item.playlist.createdAt,
                isVideo = item.playlist.isVideo
            )
        }
    }

    suspend fun migrateLegacyData() {
        withContext(Dispatchers.IO) {
            if (legacyFile.exists()) {
                try {
                    val json = legacyFile.readText()
                    // Use LegacyPlaylist type to safely read 'items' string array
                    val type = object : TypeToken<List<LegacyPlaylist>>() {}.type
                    val legacyPlaylists: List<LegacyPlaylist>? = gson.fromJson(json, type)

                    legacyPlaylists?.forEach { playlist ->
                        // 1. Insert Playlist
                        mediaDao.insertPlaylist(
                            PlaylistEntity(
                                id = playlist.id,
                                name = playlist.name,
                                createdAt = playlist.createdAt,
                                isVideo = playlist.isVideo
                            )
                        )

                        // 2. Resolve IDs (Handle both 'items' string array and potential 'mediaIds')
                        val idsToMigrate = mutableListOf<Long>()

                        // Map String IDs (from "items") to Long
                        playlist.items?.forEach { strId ->
                            strId.toLongOrNull()?.let { idsToMigrate.add(it) }
                        }

                        // Add existing Long IDs if any
                        playlist.mediaIds?.let { idsToMigrate.addAll(it) }

                        // 3. Insert Songs
                        idsToMigrate.distinct().forEachIndexed { index, mediaId ->
                            mediaDao.addMediaToPlaylist(
                                PlaylistMediaCrossRef(
                                    playlistId = playlist.id,
                                    mediaId = mediaId,
                                    addedAt = System.currentTimeMillis() + index
                                )
                            )
                        }
                    }
                    // 4. Delete Legacy File on success
                    legacyFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun ensureDefaultPlaylists() {
        // Double check is performed inside createPlaylist to avoid duplicates
        createPlaylist("Favorites", false)
        createPlaylist("Love", true)
    }

    suspend fun createPlaylist(name: String, isVideo: Boolean) {
        // Prevent duplicates: Check if a playlist with same name/type exists
        if (mediaDao.getPlaylistCount(name, isVideo) > 0) {
            return
        }

        val newId = java.util.UUID.randomUUID().toString()
        mediaDao.insertPlaylist(
            PlaylistEntity(
                id = newId,
                name = name,
                createdAt = System.currentTimeMillis(),
                isVideo = isVideo
            )
        )
    }

    suspend fun deletePlaylist(id: String) {
        mediaDao.deletePlaylist(id)
    }

    suspend fun renamePlaylist(id: String, newName: String) {
        mediaDao.updatePlaylistName(id, newName)
    }

    suspend fun addSongToPlaylist(playlistId: String, mediaId: Long) {
        mediaDao.addMediaToPlaylist(
            PlaylistMediaCrossRef(
                playlistId = playlistId,
                mediaId = mediaId
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: String, mediaId: Long) {
        mediaDao.removeMediaFromPlaylist(playlistId, mediaId)
    }

    suspend fun updatePlaylistTracks(playlistId: String, mediaIds: List<Long>) {
        mediaDao.clearPlaylistMedia(playlistId)
        mediaIds.forEachIndexed { index, id ->
            mediaDao.addMediaToPlaylist(
                PlaylistMediaCrossRef(
                    playlistId = playlistId,
                    mediaId = id,
                    addedAt = System.currentTimeMillis() + index
                )
            )
        }
    }
}
