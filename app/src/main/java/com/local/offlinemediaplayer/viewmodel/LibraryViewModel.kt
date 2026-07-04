package com.local.offlinemediaplayer.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
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

    private fun <T> saveSortState(prefix: String, state: SortState<T>)
            where T : Enum<T>, T : SortableField {
        sharedPrefs.edit {
            putInt("${prefix}_field", state.field.ordinal)
            putBoolean("${prefix}_asc", state.ascending)
        }
    }

    /**
     * Loads a media sort state, migrating once from the legacy combined-enum
     * preference (stored under [legacyKey] as a [SortOption] ordinal) if the
     * new field/direction keys have not been written yet.
     */
    private fun loadMediaSortState(prefix: String, legacyKey: String): SortState<SortField> {
        val fieldKey = "${prefix}_field"
        if (!sharedPrefs.contains(fieldKey)) {
            val legacy = SortOption.entries.getOrNull(sharedPrefs.getInt(legacyKey, -1))
            val migrated = when (legacy) {
                SortOption.TITLE_ASC -> SortState(SortField.TITLE, ascending = true)
                SortOption.TITLE_DESC -> SortState(SortField.TITLE, ascending = false)
                SortOption.DURATION_ASC -> SortState(SortField.DURATION, ascending = true)
                SortOption.DURATION_DESC -> SortState(SortField.DURATION, ascending = false)
                SortOption.MOST_PLAYED -> SortState(SortField.MOST_PLAYED)
                SortOption.DATE_ADDED_DESC, null -> SortState(SortField.DATE_ADDED)
            }
            saveSortState(prefix, migrated)
            return migrated
        }
        val field = SortField.entries.getOrElse(sharedPrefs.getInt(fieldKey, 0)) { SortField.DATE_ADDED }
        return SortState(field, sharedPrefs.getBoolean("${prefix}_asc", field.defaultAscending))
    }

    /** Album counterpart of [loadMediaSortState], migrating from [AlbumSortOption]. */
    private fun loadAlbumSortState(prefix: String, legacyKey: String): SortState<AlbumSortField> {
        val fieldKey = "${prefix}_field"
        if (!sharedPrefs.contains(fieldKey)) {
            val legacy = AlbumSortOption.entries.getOrNull(sharedPrefs.getInt(legacyKey, -1))
            val migrated = when (legacy) {
                AlbumSortOption.ARTIST_ASC -> SortState(AlbumSortField.ARTIST)
                AlbumSortOption.YEAR_DESC -> SortState(AlbumSortField.YEAR)
                AlbumSortOption.SONG_COUNT_DESC -> SortState(AlbumSortField.SONG_COUNT)
                AlbumSortOption.NAME_ASC, null -> SortState(AlbumSortField.NAME)
            }
            saveSortState(prefix, migrated)
            return migrated
        }
        val field = AlbumSortField.entries.getOrElse(sharedPrefs.getInt(fieldKey, 0)) { AlbumSortField.NAME }
        return SortState(field, sharedPrefs.getBoolean("${prefix}_asc", field.defaultAscending))
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

    private val _albumSortState = MutableStateFlow(loadAlbumSortState("sort_albums", "sort_albums"))
    val albumSortState = _albumSortState.asStateFlow()

    private val _audioSortState = MutableStateFlow(loadMediaSortState("sort_audio", "sort_audio"))
    val audioSortState = _audioSortState.asStateFlow()

    private val _videoSortState = MutableStateFlow(loadMediaSortState("sort_video", "sort_video"))
    val videoSortState = _videoSortState.asStateFlow()

    private val _movieSortState = MutableStateFlow(loadMediaSortState("sort_movies", "sort_movies"))
    val movieSortState = _movieSortState.asStateFlow()

    val moviesList = videoList.map { list -> list.filter { it.duration >= 3600000 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Play Count Maps for MOST_PLAYED sorting ---
    private val _playCountMap = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val playCountMap = _playCountMap.asStateFlow()

    private val _videoPlayCountMap = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val videoPlayCountMap = _videoPlayCountMap.asStateFlow()

    val sortedMovies = combine(moviesList, _movieSortState, _videoPlayCountMap) { list, sort, playCounts ->
        list.applySort(sort, playCounts)
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

    val filteredAudioList = combine(audioList, _searchQuery, _audioSortState, _playCountMap) { list, query, sort, playCounts ->
        var result = list
        if (query.isNotEmpty()) {
            result = result.filter { it.title.contains(query, true) || (it.artist?.contains(query, true) == true) }
        }
        result.applySort(sort, playCounts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAlbums = combine(albums, _albumSearchQuery, _albumSortState) { list, query, sort ->
        var result = list
        if (query.isNotEmpty()) {
            result = result.filter { it.name.contains(query, true) || it.artist.contains(query, true) }
        }
        val comparator: Comparator<com.local.offlinemediaplayer.model.Album> = when (sort.field) {
            AlbumSortField.NAME -> compareBy { it.name.lowercase() }
            AlbumSortField.ARTIST -> compareBy { it.artist.lowercase() }
            AlbumSortField.YEAR -> compareBy { it.firstYear ?: 0 }
            AlbumSortField.SONG_COUNT -> compareBy { it.songCount }
        }
        result.sortedWith(if (sort.ascending) comparator else comparator.reversed())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Decades (derived from songs, grouped into 10-year buckets by release year) ---
    val decades = audioList.map { list ->
        list.groupBy { (it.year ?: 0) / 10 * 10 }
            .map { (decadeStart, songsInDecade) ->
                com.local.offlinemediaplayer.model.Decade(
                    startYear = decadeStart,
                    songCount = songsInDecade.size,
                    albumArtUri = songsInDecade.firstOrNull { it.albumArtUri != null }?.albumArtUri
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
    fun updateAudioSort(state: SortState<SortField>) {
        _audioSortState.value = state
        saveSortState("sort_audio", state)
    }
    fun updateVideoSort(state: SortState<SortField>) {
        _videoSortState.value = state
        saveSortState("sort_video", state)
    }
    fun updateAlbumSort(state: SortState<AlbumSortField>) {
        _albumSortState.value = state
        saveSortState("sort_albums", state)
    }
    fun updateMovieSort(state: SortState<SortField>) {
        _movieSortState.value = state
        saveSortState("sort_movies", state)
    }

    // --- View layout (grid vs list) persistence ---
    private val _videoGridView = MutableStateFlow(sharedPrefs.getBoolean("view_video_grid", true))
    val videoGridView = _videoGridView.asStateFlow()

    private val _folderGridView = MutableStateFlow(sharedPrefs.getBoolean("view_folder_grid", true))
    val folderGridView = _folderGridView.asStateFlow()

    private val _movieGridView = MutableStateFlow(sharedPrefs.getBoolean("view_movie_grid", true))
    val movieGridView = _movieGridView.asStateFlow()

    private val _albumListView = MutableStateFlow(sharedPrefs.getBoolean("view_album_list", false))
    val albumListView = _albumListView.asStateFlow()

    fun toggleVideoGridView() = toggleViewPref(_videoGridView, "view_video_grid")
    fun toggleFolderGridView() = toggleViewPref(_folderGridView, "view_folder_grid")
    fun toggleMovieGridView() = toggleViewPref(_movieGridView, "view_movie_grid")
    fun toggleAlbumListView() = toggleViewPref(_albumListView, "view_album_list")

    private fun toggleViewPref(state: MutableStateFlow<Boolean>, key: String) {
        val newValue = !state.value
        state.value = newValue
        sharedPrefs.edit { putBoolean(key, newValue) }
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

    // --- File Rename ---
    // Same consent pattern as deletion: Android 11+ asks up front via
    // createWriteRequest; Android 10 retries after a RecoverableSecurityException
    // grant; Android 8-9 renames the file directly and updates MediaStore.
    private var pendingRename: Pair<MediaFile, String>? = null

    private val _renameIntentEvent = MutableSharedFlow<IntentSender>()
    val renameIntentEvent = _renameIntentEvent.asSharedFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    /** Characters MediaStore/FAT/exFAT reject in file names. */
    private val invalidFileNameChars = Regex("[/\\\\:*?\"<>|\\x00]")

    /**
     * Renames the file behind [file] to [newBaseName], preserving the original
     * extension. Validation failures surface through [userMessage].
     */
    fun renameMedia(file: MediaFile, newBaseName: String) {
        val sanitized = newBaseName.trim().trimEnd('.')
        if (sanitized.isEmpty()) {
            viewModelScope.launch { _userMessage.emit("Name cannot be empty") }
            return
        }
        if (invalidFileNameChars.containsMatchIn(sanitized)) {
            viewModelScope.launch { _userMessage.emit("Name contains invalid characters (/ \\ : * ? \" < > |)") }
            return
        }
        val extension = file.displayName.substringAfterLast('.', "")
        val newDisplayName = if (extension.isEmpty()) sanitized else "$sanitized.$extension"
        if (newDisplayName == file.displayName) return

        pendingRename = file to newDisplayName
        viewModelScope.launch(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent: PendingIntent =
                        MediaStore.createWriteRequest(app.contentResolver, listOf(file.uri))
                    _renameIntentEvent.emit(pendingIntent.intentSender)
                } catch (e: Exception) {
                    Log.e("LibraryViewModel", "createWriteRequest failed", e)
                    pendingRename = null
                    _userMessage.emit("Rename failed")
                }
            } else {
                performPendingRename()
            }
        }
    }

    /** Call when the user grants the system write-permission dialog. */
    fun onRenamePermissionGranted() {
        viewModelScope.launch(Dispatchers.IO) { performPendingRename() }
    }

    /** Call when the user denies the system write-permission dialog. */
    fun onRenameDenied() {
        pendingRename = null
    }

    private suspend fun performPendingRename() {
        val (file, newDisplayName) = pendingRename ?: return
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // Pre-scoped-storage: MediaStore does not move the file for us,
                // so rename on disk first, then point the row at the new path.
                val oldFile = File(file.path)
                val newFile = File(oldFile.parentFile, newDisplayName)
                if (newFile.exists()) {
                    _userMessage.emit("A file with that name already exists")
                    pendingRename = null
                    return
                }
                if (!oldFile.renameTo(newFile)) {
                    _userMessage.emit("Rename failed")
                    pendingRename = null
                    return
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                    put(MediaStore.MediaColumns.DATA, newFile.absolutePath)
                }
                app.contentResolver.update(file.uri, values, null, null)
                onRenameSuccess(file.id, newDisplayName)
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                }
                val updated = app.contentResolver.update(file.uri, values, null, null)
                if (updated > 0) {
                    onRenameSuccess(file.id, newDisplayName)
                } else {
                    _userMessage.emit("Rename failed")
                }
            }
            pendingRename = null
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                // Keep pendingRename so the retry after user consent can finish.
                _renameIntentEvent.emit(e.userAction.actionIntent.intentSender)
            } else {
                Log.e("LibraryViewModel", "Rename rejected", e)
                pendingRename = null
                _userMessage.emit("Rename not permitted for this file")
            }
        } catch (e: Exception) {
            // MediaStore throws IllegalStateException when the target name is taken.
            Log.e("LibraryViewModel", "Rename failed", e)
            pendingRename = null
            _userMessage.emit(
                if (e is IllegalStateException) "A file with that name already exists"
                else "Rename failed"
            )
        }
    }

    private suspend fun onRenameSuccess(id: Long, newDisplayName: String) {
        mediaRepository.applyRename(id, newDisplayName)
        _userMessage.emit("Renamed to \"$newDisplayName\"")
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
