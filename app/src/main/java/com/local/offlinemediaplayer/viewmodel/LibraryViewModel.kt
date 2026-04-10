package com.local.offlinemediaplayer.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.model.VideoFolder
import com.local.offlinemediaplayer.repository.MediaRepository
import com.local.offlinemediaplayer.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val app: Application,
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository
) : AndroidViewModel(app) {

    private val sharedPrefs = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private fun saveSortPreference(key: String, ordinal: Int) {
        sharedPrefs.edit { putInt(key, ordinal) }
    }

    private fun loadSortPreference(key: String, default: Int): Int {
        return sharedPrefs.getInt(key, default)
    }

    val isRefreshing = mediaRepository.isRefreshing
    val videoList = mediaRepository.videoList
    val audioList = mediaRepository.audioList
    val imageList = mediaRepository.imageList
    val albums = mediaRepository.albums

    fun scanMedia() {
        viewModelScope.launch {
            mediaRepository.scanMedia()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _albumSearchQuery = MutableStateFlow("")
    val albumSearchQuery = _albumSearchQuery.asStateFlow()

    private val _folderSearchQuery = MutableStateFlow("")
    val folderSearchQuery = _folderSearchQuery.asStateFlow()

    private val _albumSortOption = MutableStateFlow(
        AlbumSortOption.entries[loadSortPreference("sort_albums", AlbumSortOption.NAME_ASC.ordinal)]
    )
    val albumSortOption = _albumSortOption.asStateFlow()

    private val _sortOption = MutableStateFlow(
        SortOption.entries[loadSortPreference("sort_audio", SortOption.DATE_ADDED_DESC.ordinal)]
    )
    val sortOption = _sortOption.asStateFlow()

    private val _videoSortOption = MutableStateFlow(
        SortOption.entries[loadSortPreference("sort_video", SortOption.DATE_ADDED_DESC.ordinal)]
    )
    val videoSortOption = _videoSortOption.asStateFlow()

    private val _movieSortOption = MutableStateFlow(
        SortOption.entries[loadSortPreference("sort_movies", SortOption.DATE_ADDED_DESC.ordinal)]
    )
    val movieSortOption = _movieSortOption.asStateFlow()

    val moviesList = videoList.map { list -> list.filter { it.duration >= 3600000 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedMovies = combine(moviesList, _movieSortOption) { list, sort ->
        when (sort) {
            SortOption.TITLE_ASC -> list.sortedBy { it.title }
            SortOption.TITLE_DESC -> list.sortedByDescending { it.title }
            SortOption.DURATION_ASC -> list.sortedBy { it.duration }
            SortOption.DURATION_DESC -> list.sortedByDescending { it.duration }
            SortOption.DATE_ADDED_DESC -> list.sortedByDescending { it.id }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoFolders = videoList.map { videos ->
        videos.groupBy { it.bucketId }.map { (bucketId, bucketVideos) ->
            VideoFolder(
                id = bucketId,
                name = bucketVideos.firstOrNull()?.bucketName ?: "Unknown",
                videoCount = bucketVideos.size,
                thumbnailUri = bucketVideos.firstOrNull()?.uri ?: android.net.Uri.EMPTY
            )
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAudioList = combine(audioList, _searchQuery, _sortOption) { list, query, sort ->
        var result = list
        if (query.isNotEmpty()) {
            result = result.filter { it.title.contains(query, true) || (it.artist?.contains(query, true) == true) }
        }
        when (sort) {
            SortOption.TITLE_ASC -> result.sortedBy { it.title }
            SortOption.TITLE_DESC -> result.sortedByDescending { it.title }
            SortOption.DURATION_ASC -> result.sortedBy { it.duration }
            SortOption.DURATION_DESC -> result.sortedByDescending { it.duration }
            SortOption.DATE_ADDED_DESC -> result.sortedByDescending { it.id }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAlbums = combine(albums, _albumSearchQuery, _albumSortOption) { list, query, sort ->
        var result = list
        if (query.isNotEmpty()) {
            result = result.filter { it.name.contains(query, true) || it.artist.contains(query, true) }
        }
        when (sort) {
            AlbumSortOption.NAME_ASC -> result.sortedBy { it.name.lowercase() }
            AlbumSortOption.ARTIST_ASC -> result.sortedBy { it.artist.lowercase() }
            AlbumSortOption.YEAR_DESC -> result.sortedByDescending { it.firstYear ?: 0 }
            AlbumSortOption.SONG_COUNT_DESC -> result.sortedByDescending { it.songCount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateAlbumSearchQuery(query: String) { _albumSearchQuery.value = query }
    fun updateFolderSearchQuery(query: String) { _folderSearchQuery.value = query }
    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
        saveSortPreference("sort_audio", option.ordinal)
    }
    fun updateVideoSortOption(option: SortOption) {
        _videoSortOption.value = option
        saveSortPreference("sort_video", option.ordinal)
    }
    fun updateAlbumSortOption(option: AlbumSortOption) {
        _albumSortOption.value = option
        saveSortPreference("sort_albums", option.ordinal)
    }
    fun updateMovieSortOption(option: SortOption) {
        _movieSortOption.value = option
        saveSortPreference("sort_movies", option.ordinal)
    }

    private val _selectedMediaIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMediaIds = _selectedMediaIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    // --- ALBUM SELECTIONS ---
    private val _selectedAlbumIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedAlbumIds = _selectedAlbumIds.asStateFlow()

    private val _isAlbumSelectionMode = MutableStateFlow(false)
    val isAlbumSelectionMode = _isAlbumSelectionMode.asStateFlow()
    //Initializing the variables and setting state flow auto update state

    private var pendingAlbumDeleteIds: List<Long>? = null

    private val _deleteIntentEvent = MutableSharedFlow<IntentSender>()
    val deleteIntentEvent = _deleteIntentEvent.asSharedFlow()

    fun toggleSelectionMode(enable: Boolean) {
        _isSelectionMode.value = enable
        if (!enable) _selectedMediaIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        val current = _selectedMediaIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedMediaIds.value = current
        if (current.isEmpty()) _isSelectionMode.value = false
    }

    fun selectAll(ids: List<Long>) {
        _selectedMediaIds.value = ids.toSet()
    }

    // --- ALBUM SELECTION METHODS ---
    fun toggleAlbumSelectionMode(enable: Boolean) {
        _isAlbumSelectionMode.value = enable
        if (!enable) _selectedAlbumIds.value = emptySet()
    }

    fun toggleAlbumSelection(id: Long) {
        val current = _selectedAlbumIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedAlbumIds.value = current
        if (current.isEmpty()) _isAlbumSelectionMode.value = false
    }

    fun selectAllAlbums(ids: List<Long>) {
        _selectedAlbumIds.value = ids.toSet()
    }

    fun deleteSelectedMedia() {
        val idsToDelete = _selectedMediaIds.value.toList()
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val allMedia = videoList.value + audioList.value
            val filesToDelete = allMedia.filter { idsToDelete.contains(it.id) }
            val uris = filesToDelete.map { it.uri }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(app.contentResolver, uris)
                _deleteIntentEvent.emit(pendingIntent.intentSender)
            } else {
                try {
                    for (file in filesToDelete) app.contentResolver.delete(file.uri, null, null)
                    onDeleteSuccess(idsToDelete)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun onDeleteSuccess() {
        onDeleteSuccess(_selectedMediaIds.value.toList())
    }

    private fun onDeleteSuccess(ids: List<Long>) {
        viewModelScope.launch {
            mediaRepository.removeMediaIds(ids)
            // Note: need to read playlist flows with firstOrNull outside but simple approach:
            // Since repo handles media lists, just sync playlists
            // We can emit a broadcast or call into repo?
            // Easy way: let PlaylistRepo update.
            _selectedMediaIds.value = emptySet()
            _isSelectionMode.value = false
        }
    }

    // --- ALBUM DELETION METHODS ---
    fun deleteSelectedAlbums() {
        val idsToDelete = _selectedAlbumIds.value.toList()
        if (idsToDelete.isEmpty()) return

        pendingAlbumDeleteIds = idsToDelete
        viewModelScope.launch(Dispatchers.IO) {
            val allSongsInAlbums = audioList.value.filter { idsToDelete.contains(it.albumId) }
            if (allSongsInAlbums.isEmpty()) {
                onAlbumDeleteSuccess()
                return@launch
            }
            val uris = allSongsInAlbums.map { it.uri }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(app.contentResolver, uris)
                _deleteIntentEvent.emit(pendingIntent.intentSender)
            } else {
                try {
                    for (file in allSongsInAlbums) app.contentResolver.delete(file.uri, null, null)
                    onAlbumDeleteSuccess()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun onAlbumDeleteSuccess() {
        val albumIds = pendingAlbumDeleteIds ?: return
        val songIds = audioList.value.filter { albumIds.contains(it.albumId) }.map { it.id }
        viewModelScope.launch {
            mediaRepository.removeMediaIds(songIds)
            _selectedAlbumIds.value = emptySet()
            _isAlbumSelectionMode.value = false
        }
        pendingAlbumDeleteIds = null
    }

    // --- Image Deletion ---
    private val _pendingImageDeleteId = MutableStateFlow<Long?>(null)

    fun deleteImage(image: MediaFile) {
        viewModelScope.launch(Dispatchers.IO) {
            _pendingImageDeleteId.value = image.id
            val uris = listOf(image.uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(app.contentResolver, uris)
                _deleteIntentEvent.emit(pendingIntent.intentSender)
            } else {
                try {
                    app.contentResolver.delete(image.uri, null, null)
                    onImageDeleteSuccess()
                } catch (e: Exception) {
                    e.printStackTrace()
                    _pendingImageDeleteId.value = null
                }
            }
        }
    }

    fun onImageDeleteSuccess() {
        val id = _pendingImageDeleteId.value ?: return
        mediaRepository.removeMediaIds(listOf(id))
        _pendingImageDeleteId.value = null
    }
}
