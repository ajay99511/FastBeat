package com.local.offlinemediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.offlinemediaplayer.data.SortPreferencesManager
import com.local.offlinemediaplayer.data.db.MediaAnalytics
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val mediaDao: MediaDao,
    private val sortPrefsManager: SortPreferencesManager
) : ViewModel() {

    val playlists = playlistRepository.playlistsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val audioPlaylists = playlists.map { list -> list.filter { !it.isVideo } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoPlaylists = playlists.map { list -> list.filter { it.isVideo } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String, isVideo: Boolean = false) {
        val currentPlaylists = playlists.value
        if (currentPlaylists.any { it.name.equals(name, ignoreCase = true) && it.isVideo == isVideo }) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) { playlistRepository.createPlaylist(name, isVideo) }
    }

    fun renamePlaylist(id: String, newName: String) {
        val currentPlaylists = playlists.value
        val playlist = currentPlaylists.find { it.id == id } ?: return
        if (currentPlaylists.any { it.name.equals(newName, ignoreCase = true) && it.isVideo == playlist.isVideo && it.id != id }) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) { playlistRepository.renamePlaylist(id, newName) }
    }

    fun deletePlaylist(playlistId: String) =
        viewModelScope.launch(Dispatchers.IO) { playlistRepository.deletePlaylist(playlistId) }

    fun addSongToPlaylist(playlistId: String, mediaId: Long) =
        viewModelScope.launch(Dispatchers.IO) { playlistRepository.addSongToPlaylist(playlistId, mediaId) }

    fun addSongsToPlaylist(playlistId: String, mediaIds: List<Long>) {
        val playlist = playlists.value.find { it.id == playlistId } ?: return
        val currentIds = playlist.mediaIds
        val newIdsToAdd = mediaIds.filter { !currentIds.contains(it) }
        
        if (newIdsToAdd.isEmpty()) return
        
        val updatedIds = currentIds + newIdsToAdd
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.updatePlaylistTracks(playlistId, updatedIds)
        }
    }

    fun removeSongFromPlaylist(playlistId: String, mediaId: Long) =
        viewModelScope.launch(Dispatchers.IO) { playlistRepository.removeSongFromPlaylist(playlistId, mediaId) }

    suspend fun getAnalyticsForIds(ids: List<Long>): List<MediaAnalytics> =
        mediaDao.getAnalyticsForIds(ids)

    fun toggleAlbumInFavorites(albumSongs: List<MediaFile>) {
        val favPlaylist = playlists.value.find { it.name == "Favorites" && !it.isVideo } ?: return
        val allInFav = albumSongs.all { favPlaylist.mediaIds.contains(it.id) }
        val newMediaIds = favPlaylist.mediaIds.toMutableList()
        if (allInFav) {
            albumSongs.forEach { newMediaIds.remove(it.id) }
        } else {
            albumSongs.forEach { if (!newMediaIds.contains(it.id)) newMediaIds.add(it.id) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.updatePlaylistTracks(favPlaylist.id, newMediaIds)
        }
    }

    // --- Audio Playlist Sort Persistence ---
    fun getAudioPlaylistSort(playlistId: String) = sortPrefsManager.getAudioPlaylistSort(playlistId)
    fun saveAudioPlaylistSort(playlistId: String, option: com.local.offlinemediaplayer.ui.screens.AudioSortOption, ascending: Boolean) {
        sortPrefsManager.saveAudioPlaylistSort(playlistId, option, ascending)
    }

    // --- Video Playlist Sort Persistence ---
    fun getVideoPlaylistSort(playlistId: String) = sortPrefsManager.getVideoPlaylistSort(playlistId)
    fun saveVideoPlaylistSort(playlistId: String, option: com.local.offlinemediaplayer.ui.screens.VideoSortOption, ascending: Boolean) {
        sortPrefsManager.saveVideoPlaylistSort(playlistId, option, ascending)
    }
}
