package com.local.offlinemediaplayer.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.offlinemediaplayer.R
import com.local.offlinemediaplayer.data.AppPreferencesManager
import com.local.offlinemediaplayer.data.LibraryLayout
import com.local.offlinemediaplayer.data.LibrarySort
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.model.UserMessage
import com.local.offlinemediaplayer.model.VideoFolder
import com.local.offlinemediaplayer.repository.MediaRepository
import com.local.offlinemediaplayer.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import java.io.File
import javax.inject.Inject

/** A partially-watched video with its saved resume position. */
data class ContinueWatchingItem(
    val media: MediaFile,
    val position: Long,
    val duration: Long,
) {
    val progress: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val app: Application,
        private val mediaRepository: MediaRepository,
        private val playlistRepository: PlaylistRepository,
        private val mediaDao: MediaDao,
        private val appPrefs: AppPreferencesManager,
    ) : AndroidViewModel(app) {
        private fun <T> saveSortState(
            sort: LibrarySort,
            state: SortState<T>,
        )
            where T : Enum<T>, T : SortableField {
            viewModelScope.launch { appPrefs.setSort(sort, state.field.ordinal, state.ascending) }
        }

        /**
         * Loads a media sort state, migrating once from the legacy combined-enum preference (stored
         * under the list's own key as a [SortOption] ordinal) if the new field/direction keys have
         * not been written yet.
         *
         * Suspend since P5-C.3 — DataStore has no synchronous read. Called from `init`, so the
         * flows below hold their defaults for the few frames before the read lands.
         */
        private suspend fun loadMediaSortState(sort: LibrarySort): SortState<SortField> {
            val stored = appPrefs.getSort(sort)
            if (stored == null) {
                val legacy = SortOption.entries.getOrNull(appPrefs.getLegacySortOrdinal(sort) ?: -1)
                val migrated =
                    when (legacy) {
                        SortOption.TITLE_ASC -> SortState(SortField.TITLE, ascending = true)
                        SortOption.TITLE_DESC -> SortState(SortField.TITLE, ascending = false)
                        SortOption.DURATION_ASC -> SortState(SortField.DURATION, ascending = true)
                        SortOption.DURATION_DESC -> SortState(SortField.DURATION, ascending = false)
                        SortOption.MOST_PLAYED -> SortState(SortField.MOST_PLAYED)
                        SortOption.DATE_ADDED_DESC, null -> SortState(SortField.DATE_ADDED)
                    }
                appPrefs.setSort(sort, migrated.field.ordinal, migrated.ascending)
                return migrated
            }
            val field = SortField.entries.getOrElse(stored.fieldOrdinal) { SortField.DATE_ADDED }
            return SortState(field, stored.ascending ?: field.defaultAscending)
        }

        /** Album counterpart of [loadMediaSortState], migrating from [AlbumSortOption]. */
        private suspend fun loadAlbumSortState(sort: LibrarySort): SortState<AlbumSortField> {
            val stored = appPrefs.getSort(sort)
            if (stored == null) {
                val legacy = AlbumSortOption.entries.getOrNull(appPrefs.getLegacySortOrdinal(sort) ?: -1)
                val migrated =
                    when (legacy) {
                        AlbumSortOption.ARTIST_ASC -> SortState(AlbumSortField.ARTIST)
                        AlbumSortOption.YEAR_DESC -> SortState(AlbumSortField.YEAR)
                        AlbumSortOption.SONG_COUNT_DESC -> SortState(AlbumSortField.SONG_COUNT)
                        AlbumSortOption.NAME_ASC, null -> SortState(AlbumSortField.NAME)
                    }
                appPrefs.setSort(sort, migrated.field.ordinal, migrated.ascending)
                return migrated
            }
            val field = AlbumSortField.entries.getOrElse(stored.fieldOrdinal) { AlbumSortField.NAME }
            return SortState(field, stored.ascending ?: field.defaultAscending)
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

        // These four start on the same defaults the loaders fall back to, and are replaced from
        // DataStore in `init` (P5-C.3).
        private val _albumSortState = MutableStateFlow(SortState(AlbumSortField.NAME))
        val albumSortState = _albumSortState.asStateFlow()

        private val _audioSortState = MutableStateFlow(SortState(SortField.DATE_ADDED))
        val audioSortState = _audioSortState.asStateFlow()

        private val _videoSortState = MutableStateFlow(SortState(SortField.DATE_ADDED))
        val videoSortState = _videoSortState.asStateFlow()

        private val _movieSortState = MutableStateFlow(SortState(SortField.DATE_ADDED))
        val movieSortState = _movieSortState.asStateFlow()

        val moviesList =
            videoList
                .map { list -> list.filter { it.duration >= 3600000 } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // --- Play Count Maps for MOST_PLAYED sorting ---
        private val _playCountMap = MutableStateFlow<Map<Long, Int>>(emptyMap())
        val playCountMap = _playCountMap.asStateFlow()

        private val _videoPlayCountMap = MutableStateFlow<Map<Long, Int>>(emptyMap())
        val videoPlayCountMap = _videoPlayCountMap.asStateFlow()

        val sortedMovies =
            combine(moviesList, _movieSortState, _videoPlayCountMap) { list, sort, playCounts ->
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

        val videoFolders =
            videoList
                .map { videos ->
                    videos
                        .groupBy { it.bucketId }
                        .map { (bucketId, bucketVideos) ->
                            // Prefer a representative video that already has a cached thumbnail to avoid
                            // decoding raw video URIs in the folder grid.
                            val representative =
                                bucketVideos.firstOrNull { it.thumbnailPath != null }
                                    ?: bucketVideos.firstOrNull()
                            VideoFolder(
                                id = bucketId,
                                name = bucketVideos.firstOrNull()?.bucketName ?: "Unknown",
                                videoCount = bucketVideos.size,
                                thumbnailUri = representative?.uri ?: android.net.Uri.EMPTY,
                                thumbnailPath = representative?.thumbnailPath,
                            )
                        }.sortedBy { it.name }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // --- Continue Watching (resume) ---
        val continueWatching =
            combine(videoList, mediaDao.getContinueWatching()) { videos, history ->
                val byId = videos.associateBy { it.id }
                history.mapNotNull { h ->
                    byId[h.mediaId]?.let { media ->
                        val total = if (h.duration > 0) h.duration else media.duration
                        ContinueWatchingItem(media, h.position, total)
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Map of video mediaId -> resume progress fraction (0f..1f) for thumbnail progress bars.
        val watchProgressMap =
            combine(videoList, mediaDao.getAllVideoHistory()) { videos, history ->
                val ids = videos.mapTo(HashSet()) { it.id }
                history
                    .filter { it.mediaId in ids && it.duration > 0 }
                    .associate { it.mediaId to (it.position.toFloat() / it.duration).coerceIn(0f, 1f) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        val filteredAudioList =
            combine(audioList, _searchQuery, _audioSortState, _playCountMap) { list, query, sort, playCounts ->
                var result = list
                if (query.isNotEmpty()) {
                    result =
                        result.filter { it.title.contains(query, true) || (it.artist?.contains(query, true) == true) }
                }
                result.applySort(sort, playCounts)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val filteredAlbums =
            combine(albums, _albumSearchQuery, _albumSortState) { list, query, sort ->
                var result = list
                if (query.isNotEmpty()) {
                    result = result.filter { it.name.contains(query, true) || it.artist.contains(query, true) }
                }
                val comparator: Comparator<com.local.offlinemediaplayer.model.Album> =
                    when (sort.field) {
                        AlbumSortField.NAME -> compareBy { it.name.lowercase() }
                        AlbumSortField.ARTIST -> compareBy { it.artist.lowercase() }
                        AlbumSortField.YEAR -> compareBy { it.firstYear ?: 0 }
                        AlbumSortField.SONG_COUNT -> compareBy { it.songCount }
                    }
                result.sortedWith(if (sort.ascending) comparator else comparator.reversed())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // --- Decades (derived from songs, grouped into 10-year buckets by release year) ---
        val decades =
            audioList
                .map { list ->
                    list
                        .groupBy { (it.year ?: 0) / 10 * 10 }
                        .map { (decadeStart, songsInDecade) ->
                            com.local.offlinemediaplayer.model.Decade(
                                startYear = decadeStart,
                                songCount = songsInDecade.size,
                                albumArtUri = songsInDecade.firstOrNull { it.albumArtUri != null }?.albumArtUri,
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
        val artists =
            audioList
                .map { list ->
                    list
                        .flatMap { song ->
                            com.local.offlinemediaplayer.model.ArtistGrouping
                                .splitTokens(song.artist)
                                .map { name -> name to song }
                        }.groupBy { (name, _) ->
                            com.local.offlinemediaplayer.model.ArtistGrouping
                                .key(name)
                        }.map { (_, pairs) ->
                            val songs = pairs.map { it.second }.distinctBy { it.id }
                            com.local.offlinemediaplayer.model.Artist(
                                name =
                                    com.local.offlinemediaplayer.model.ArtistGrouping
                                        .displayName(pairs.map { it.first }),
                                songCount = songs.size,
                                albumCount = songs.mapNotNull { it.albumId.takeIf { id -> id > 0 } }.distinct().size,
                                albumArtUri = songs.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                            )
                        }.sortedBy { it.name.lowercase() }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val filteredArtists =
            combine(artists, _artistSearchQuery) { list, query ->
                if (query.isEmpty()) {
                    list
                } else {
                    list.filter { it.name.contains(query, true) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        fun updateSearchQuery(query: String) {
            _searchQuery.value = query
        }

        fun updateAlbumSearchQuery(query: String) {
            _albumSearchQuery.value = query
        }

        fun updateArtistSearchQuery(query: String) {
            _artistSearchQuery.value = query
        }

        fun updateFolderSearchQuery(query: String) {
            _folderSearchQuery.value = query
        }

        fun updateAudioSort(state: SortState<SortField>) {
            _audioSortState.value = state
            saveSortState(LibrarySort.AUDIO, state)
        }

        fun updateVideoSort(state: SortState<SortField>) {
            _videoSortState.value = state
            saveSortState(LibrarySort.VIDEO, state)
        }

        fun updateAlbumSort(state: SortState<AlbumSortField>) {
            _albumSortState.value = state
            saveSortState(LibrarySort.ALBUMS, state)
        }

        fun updateMovieSort(state: SortState<SortField>) {
            _movieSortState.value = state
            saveSortState(LibrarySort.MOVIES, state)
        }

        // --- View layout (grid vs list) persistence ---
        private val _videoGridView = MutableStateFlow(LibraryLayout.VIDEO_GRID.default)
        val videoGridView = _videoGridView.asStateFlow()

        private val _folderGridView = MutableStateFlow(LibraryLayout.FOLDER_GRID.default)
        val folderGridView = _folderGridView.asStateFlow()

        private val _movieGridView = MutableStateFlow(LibraryLayout.MOVIE_GRID.default)
        val movieGridView = _movieGridView.asStateFlow()

        private val _albumListView = MutableStateFlow(LibraryLayout.ALBUM_LIST.default)
        val albumListView = _albumListView.asStateFlow()

        /**
         * Hydrates every persisted list preference from DataStore (P5-C.3). One coroutine, so the
         * four sorts and four layouts settle together rather than the screen re-sorting twice.
         *
         * Deliberately placed **after** the eight properties it writes, not in the init block
         * above. `viewModelScope` dispatches on `Main.immediate`, so a coroutine launched from a
         * constructor on the main thread begins running before the constructor returns — and would
         * touch these flows before they exist.
         */
        init {
            viewModelScope.launch {
                _audioSortState.value = loadMediaSortState(LibrarySort.AUDIO)
                _videoSortState.value = loadMediaSortState(LibrarySort.VIDEO)
                _movieSortState.value = loadMediaSortState(LibrarySort.MOVIES)
                _albumSortState.value = loadAlbumSortState(LibrarySort.ALBUMS)
                _videoGridView.value = appPrefs.getLayout(LibraryLayout.VIDEO_GRID)
                _folderGridView.value = appPrefs.getLayout(LibraryLayout.FOLDER_GRID)
                _movieGridView.value = appPrefs.getLayout(LibraryLayout.MOVIE_GRID)
                _albumListView.value = appPrefs.getLayout(LibraryLayout.ALBUM_LIST)
            }
        }

        fun toggleVideoGridView() = toggleViewPref(_videoGridView, LibraryLayout.VIDEO_GRID)

        fun toggleFolderGridView() = toggleViewPref(_folderGridView, LibraryLayout.FOLDER_GRID)

        fun toggleMovieGridView() = toggleViewPref(_movieGridView, LibraryLayout.MOVIE_GRID)

        fun toggleAlbumListView() = toggleViewPref(_albumListView, LibraryLayout.ALBUM_LIST)

        private fun toggleViewPref(
            state: MutableStateFlow<Boolean>,
            layout: LibraryLayout,
        ) {
            val newValue = !state.value
            state.value = newValue
            viewModelScope.launch { appPrefs.setLayout(layout, newValue) }
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
        // Initializing the variables and setting state flow auto update state

        private var pendingAlbumDeleteIds: List<Long>? = null

        private val _deleteIntentEvent = MutableSharedFlow<IntentSender>()
        val deleteIntentEvent = _deleteIntentEvent.asSharedFlow()

        // --- Legacy (pre-Android 11) deletion ---
        // contentResolver.delete() only works directly on files the app owns.
        // Android 10 throws RecoverableSecurityException with a per-file consent
        // intent; the queue below pauses on it and resumes from the UI result
        // callback, so multi-file deletes survive one consent dialog per file.
        private class LegacyDeleteState(
            val remaining: ArrayDeque<MediaFile>,
            val total: Int,
            val deletedIds: MutableList<Long> = mutableListOf(),
            val onComplete: suspend (List<Long>) -> Unit,
        )

        private var legacyDeleteState: LegacyDeleteState? = null

        private suspend fun startLegacyDelete(
            files: List<MediaFile>,
            onComplete: suspend (List<Long>) -> Unit,
        ) {
            legacyDeleteState =
                LegacyDeleteState(
                    remaining = ArrayDeque(files),
                    total = files.size,
                    onComplete = onComplete,
                )
            processLegacyDelete()
        }

        private suspend fun processLegacyDelete() {
            val state = legacyDeleteState ?: return
            while (state.remaining.isNotEmpty()) {
                val file = state.remaining.first()
                try {
                    app.contentResolver.delete(file.uri, null, null)
                    state.deletedIds.add(file.id)
                    state.remaining.removeFirst()
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        // Paused: onDeleteConsentResult() resumes after the dialog.
                        _deleteIntentEvent.emit(e.userAction.actionIntent.intentSender)
                        return
                    }
                    Log.e("LibraryViewModel", "Delete rejected for ${file.displayName}", e)
                    state.remaining.removeFirst()
                } catch (e: Exception) {
                    Log.e("LibraryViewModel", "Delete failed for ${file.displayName}", e)
                    state.remaining.removeFirst()
                }
            }
            finishLegacyDelete(state)
        }

        private suspend fun finishLegacyDelete(state: LegacyDeleteState) {
            legacyDeleteState = null
            if (state.deletedIds.size < state.total) {
                _userMessage.emit(
                    if (state.deletedIds.isEmpty()) {
                        UserMessage.of(R.string.msg_delete_none_succeeded)
                    } else {
                        UserMessage.of(R.string.msg_delete_partially_failed)
                    },
                )
            }
            if (state.deletedIds.isNotEmpty()) {
                state.onComplete(state.deletedIds.toList())
            }
        }

        /**
         * Routes a RESULT_OK from the system delete dialog. Returns true when a
         * paused legacy queue consumed it (Android 10 consent grants access to the
         * file — the delete itself is still ours to retry).
         */
        private fun resumeLegacyDeleteIfPending(): Boolean {
            if (legacyDeleteState == null) return false
            viewModelScope.launch(Dispatchers.IO) { processLegacyDelete() }
            return true
        }

        /** Call when the user cancels the system delete dialog. */
        fun onDeleteCancelled() {
            val state = legacyDeleteState ?: return
            legacyDeleteState = null
            // Commit whatever was already deleted before the user backed out.
            viewModelScope.launch(Dispatchers.IO) {
                if (state.deletedIds.isNotEmpty()) state.onComplete(state.deletedIds.toList())
            }
        }

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
                    startLegacyDelete(filesToDelete) { deletedIds -> onDeleteSuccess(deletedIds) }
                }
            }
        }

        fun onDeleteSuccess() {
            if (resumeLegacyDeleteIfPending()) return
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
                    startLegacyDelete(allSongsInAlbums) { deletedIds ->
                        // Clean up only what was actually removed from disk.
                        mediaRepository.removeMediaIds(deletedIds)
                        playlistRepository.cleanupDeletedMedia(deletedIds)
                        _selectedAlbumIds.value = emptySet()
                        _isAlbumSelectionMode.value = false
                        pendingAlbumDeleteIds = null
                    }
                }
            }
        }

        fun onAlbumDeleteSuccess() {
            if (resumeLegacyDeleteIfPending()) return
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

        // Messages carry a string resource, not finished English (F-33).
        private val _userMessage = MutableSharedFlow<UserMessage>()
        val userMessage = _userMessage.asSharedFlow()

        /** Characters MediaStore/FAT/exFAT reject in file names. */
        private val invalidFileNameChars = Regex("[/\\\\:*?\"<>|\\x00]")

        /**
         * Renames the file behind [file] to [newBaseName], preserving the original
         * extension. Validation failures surface through [userMessage].
         */
        fun renameMedia(
            file: MediaFile,
            newBaseName: String,
        ) {
            val sanitized = newBaseName.trim().trimEnd('.')
            if (sanitized.isEmpty()) {
                viewModelScope.launch { _userMessage.emit(UserMessage.of(R.string.msg_rename_empty_name)) }
                return
            }
            if (invalidFileNameChars.containsMatchIn(sanitized)) {
                viewModelScope.launch { _userMessage.emit(UserMessage.of(R.string.msg_rename_invalid_characters)) }
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
                        _userMessage.emit(UserMessage.of(R.string.msg_rename_failed))
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
                        _userMessage.emit(UserMessage.of(R.string.msg_rename_name_taken))
                        pendingRename = null
                        return
                    }
                    if (!oldFile.renameTo(newFile)) {
                        _userMessage.emit(UserMessage.of(R.string.msg_rename_failed))
                        pendingRename = null
                        return
                    }
                    val values =
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                            put(MediaStore.MediaColumns.DATA, newFile.absolutePath)
                        }
                    app.contentResolver.update(file.uri, values, null, null)
                    onRenameSuccess(file.id, newDisplayName)
                } else {
                    val values =
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                        }
                    val updated = app.contentResolver.update(file.uri, values, null, null)
                    if (updated > 0) {
                        onRenameSuccess(file.id, newDisplayName)
                    } else {
                        _userMessage.emit(UserMessage.of(R.string.msg_rename_failed))
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
                    _userMessage.emit(UserMessage.of(R.string.msg_rename_not_permitted))
                }
            } catch (e: Exception) {
                // MediaStore throws IllegalStateException when the target name is taken.
                Log.e("LibraryViewModel", "Rename failed", e)
                pendingRename = null
                _userMessage.emit(
                    if (e is IllegalStateException) {
                        UserMessage.of(R.string.msg_rename_name_taken)
                    } else {
                        UserMessage.of(R.string.msg_rename_failed)
                    },
                )
            }
        }

        private suspend fun onRenameSuccess(
            id: Long,
            newDisplayName: String,
        ) {
            mediaRepository.applyRename(id, newDisplayName)
            _userMessage.emit(UserMessage.of(R.string.msg_renamed_to, newDisplayName))
        }

        // --- Image Deletion ---
        private val pendingImageDeleteId = MutableStateFlow<Long?>(null)

        fun deleteImage(image: MediaFile) {
            viewModelScope.launch(Dispatchers.IO) {
                pendingImageDeleteId.value = image.id
                val uris = listOf(image.uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(app.contentResolver, uris)
                    _deleteIntentEvent.emit(pendingIntent.intentSender)
                } else {
                    startLegacyDelete(listOf(image)) { deletedIds ->
                        mediaRepository.removeMediaIds(deletedIds)
                        playlistRepository.cleanupDeletedMedia(deletedIds)
                        pendingImageDeleteId.value = null
                    }
                }
            }
        }

        fun onImageDeleteSuccess() {
            if (resumeLegacyDeleteIfPending()) return
            val id = pendingImageDeleteId.value ?: return
            viewModelScope.launch {
                mediaRepository.removeMediaIds(listOf(id))
                playlistRepository.cleanupDeletedMedia(listOf(id))
                pendingImageDeleteId.value = null
            }
        }
    }
