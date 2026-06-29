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
import com.local.offlinemediaplayer.data.db.MediaDao
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

/** A partially-watched video with its saved resume position. */
data class ContinueWatchingItem(
    val media: MediaFile,
    val position: Long,
    val duration: Long
) {
    val progress: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val app: Application,
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaDao: MediaDao
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

    private val _artistSearchQuery = MutableStateFlow("")
    val artistSearchQuery = _artistSearchQuery.asStateFlow()

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

    // --- Play Count Maps for MOST_PLAYED sorting ---
    private val _playCountMap = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val playCountMap = _playCountMap.asStateFlow()

    private val _videoPlayCountMap = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val videoPlayCountMap = _videoPlayCountMap.asStateFlow()

    val sortedMovies = combine(moviesList, _movieSortOption, _videoPlayCountMap) { list, sort, playCounts ->
        when (sort) {
            SortOption.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOption.DURATION_ASC -> list.sortedBy { it.duration }
            SortOption.DURATION_DESC -> list.sortedByDescending { it.duration }
            SortOption.DATE_ADDED_DESC -> list.sortedByDescending { it.dateAdded }
            SortOption.MOST_PLAYED -> list.sortedByDescending { playCounts[it.id] ?: 0 }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Refresh play counts whenever the audio list changes
        viewModelScope.launch(Dispatchers.IO) {
            audioList.collect { list ->
                if (list.isNotEmpty()) {
                    val analytics = mediaDao.getAnalyticsForIds(list.map { it.id })
                    _playCountMap.value = analytics.associate { it.mediaId to it.playCount }
                } else {
                    _playCountMap.value = emptyMap()
                }
            }
        }
        // Refresh video play counts whenever the video list changes
        viewModelScope.launch(Dispatchers.IO) {
            videoList.collect { list ->
                if (list.isNotEmpty()) {
                    val analytics = mediaDao.getAnalyticsForIds(list.map { it.id })
                    _videoPlayCountMap.value = analytics.associate { it.mediaId to it.playCount }
                } else {
                    _videoPlayCountMap.value = emptyMap()
                }
            }
        }
    }

    val videoFolders = videoList.map { videos ->
        videos.groupBy { it.bucketId }.map { (bucketId, bucketVideos) ->
            // Prefer a representative video that already has a cached thumbnail to avoid
            // decoding raw video URIs in the folder grid.
            val representative = bucketVideos.firstOrNull { it.thumbnailPath != null }
                ?: bucketVideos.firstOrNull()
            VideoFolder(
                id = bucketId,
                name = bucketVideos.firstOrNull()?.bucketName ?: "Unknown",
                videoCount = bucketVideos.size,
                thumbnailUri = representative?.uri ?: android.net.Uri.EMPTY,
                thumbnailPath = representative?.thumbnailPath
            )
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Continue Watching (resume) ---
    val continueWatching = combine(videoList, mediaDao.getContinueWatching()) { videos, history ->
        val byId = videos.associateBy { it.id }
        history.mapNotNull { h ->
            byId[h.mediaId]?.let { media ->
                val total = if (h.duration > 0) h.duration else media.duration
                ContinueWatchingItem(media, h.position, total)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map of video mediaId -> resume progress fraction (0f..1f) for thumbnail progress bars.
    val watchProgressMap = combine(videoList, mediaDao.getAllVideoHistory()) { videos, history ->
        val ids = videos.mapTo(HashSet()) { it.id }
        history.filter { it.mediaId in ids && it.duration > 0 }
            .associate { it.mediaId to (it.position.toFloat() / it.duration).coerceIn(0f, 1f) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val filteredAudioList = combine(audioList, _searchQuery, _sortOption, _playCountMap) { list, query, sort, playCounts ->
        var result = list
        if (query.isNotEmpty()) {
            result = result.filter { it.title.contains(query, true) || (it.artist?.contains(query, true) == true) }
        }
        when (sort) {
            SortOption.TITLE_ASC -> result.sortedBy { it.title }
            SortOption.TITLE_DESC -> result.sortedByDescending { it.title }
            SortOption.DURATION_ASC -> result.sortedBy { it.duration }
            SortOption.DURATION_DESC -> result.sortedByDescending { it.duration }
            SortOption.DATE_ADDED_DESC -> result.sortedByDescending { it.dateAdded }
            SortOption.MOST_PLAYED -> result.sortedByDescending { playCounts[it.id] ?: 0 }
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

    // --- Decades (derived from albums, grouped into 10-year buckets by first year) ---
    val decades = albums.map { list ->
        list.groupBy { (it.firstYear ?: 0) / 10 * 10 }
            .map { (decadeStart, albumsInDecade) ->
                com.local.offlinemediaplayer.model.Decade(
                    startYear = decadeStart,
                    albumCount = albumsInDecade.size,
                    albumArtUri = albumsInDecade.firstOrNull { it.albumArtUri != null }?.albumArtUri
                )
            }
            // Newest decade first; the "Unknown" bucket (startYear 0) naturally sorts last.
            .sortedByDescending { it.startYear }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Artists (derived from the audio library) ---
    // A single tag can name several performers ("Dhanush, Anirudh"), so every
    // song is fanned out into one (name, song) pair per artist it mentions.
    // Those names are then collapsed by a normalized key (see ArtistGrouping)
    // to merge inconsistent spellings, and a tidy label is chosen per group.
    val artists = audioList.map { list ->
        list.flatMap { song ->
            com.local.offlinemediaplayer.model.ArtistGrouping.splitTokens(song.artist)
                .map { name -> name to song }
        }
            .groupBy { (name, _) -> com.local.offlinemediaplayer.model.ArtistGrouping.key(name) }
            .map { (_, pairs) ->
                val songs = pairs.map { it.second }.distinctBy { it.id }
                com.local.offlinemediaplayer.model.Artist(
                    name = com.local.offlinemediaplayer.model.ArtistGrouping
                        .displayName(pairs.map { it.first }),
                    songCount = songs.size,
                    albumCount = songs.mapNotNull { it.albumId.takeIf { id -> id > 0 } }.distinct().size,
                    albumArtUri = songs.firstOrNull { it.albumArtUri != null }?.albumArtUri
                )
            }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredArtists = combine(artists, _artistSearchQuery) { list, query ->
        if (query.isEmpty()) list
        else list.filter { it.name.contains(query, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateAlbumSearchQuery(query: String) { _albumSearchQuery.value = query }
    fun updateArtistSearchQuery(query: String) { _artistSearchQuery.value = query }
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
            playlistRepository.cleanupDeletedMedia(ids)
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
            playlistRepository.cleanupDeletedMedia(songIds)
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
        viewModelScope.launch {
            mediaRepository.removeMediaIds(listOf(id))
            playlistRepository.cleanupDeletedMedia(listOf(id))
            _pendingImageDeleteId.value = null
        }
    }
}
