package com.local.offlinemediaplayer.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.local.offlinemediaplayer.data.ThumbnailManager
import com.local.offlinemediaplayer.data.db.BookmarkEntity
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.data.db.PlayEvent
import com.local.offlinemediaplayer.data.db.PlaybackHistory
import com.local.offlinemediaplayer.data.db.QueueItemEntity
import com.local.offlinemediaplayer.model.Album
import com.local.offlinemediaplayer.model.AudioPlayerState
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.model.Playlist
import com.local.offlinemediaplayer.model.VideoFolder
import com.local.offlinemediaplayer.repository.PlaylistRepository
import com.local.offlinemediaplayer.service.PlaybackService
import com.local.offlinemediaplayer.ui.theme.AppThemeConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOption {
    TITLE_ASC,
    TITLE_DESC,
    DURATION_ASC,
    DURATION_DESC,
    DATE_ADDED_DESC
}

enum class AlbumSortOption {
    NAME_ASC,
    ARTIST_ASC,
    YEAR_DESC,
    SONG_COUNT_DESC
}

enum class ResizeMode {
    FIT,
    FILL,
    ZOOM
}

// Data class for UI consumption
data class RealtimeAnalytics(
        val todayPlaytimeMinutes: Int = 0,
        val weekPlaytimeMinutes: Int = 0,
        val avgDailyMinutes: Int = 0,
        val streakDays: Int = 0,
        val currentFavorite: MediaFile? = null,
        val allTimeFavorite: MediaFile? = null
)

// Data class for audio/subtitle track info
data class TrackInfo(
        val groupIndex: Int,
        val trackIndex: Int,
        val name: String,
        val language: String?,
        val isSelected: Boolean
)

@OptIn(UnstableApi::class)
@HiltViewModel
class MainViewModel
@Inject
constructor(
        private val app: Application,
        private val playlistRepository: PlaylistRepository,
        private val mediaDao: MediaDao,
        private val thumbnailManager: ThumbnailManager
) : AndroidViewModel(app) {

    // --- STATE PRESERVATION ---
    private var savedAudioState: AudioPlayerState? = null

    // --- ANALYTICS INTERNAL STATE ---
    // Tracks accumulated listening time for the CURRENT track to determine if it counts as a "play"
    private var currentTrackPlaytimeAccumulator = 0L
    private var hasLoggedCurrentTrack = false

    // --- PENDING TRACK RESTORATION ---
    private var pendingAudioTrackIndex: Int = -1
    private var pendingSubtitleTrackIndex: Int = -1

    // --- THEMING STATE ---
    private val themes =
            mapOf(
                    "blue" to
                            AppThemeConfig("blue", Color(0xFF00E5FF), "DIGITAL WAVES", "Quick Mix"),
                    "green" to
                            AppThemeConfig(
                                    "green",
                                    Color(0xFF22C55E),
                                    "ECO FREQUENCY",
                                    "Fresh Finds"
                            ),
                    "orange" to
                            AppThemeConfig(
                                    "orange",
                                    Color(0xFFFF5500),
                                    "AMBER HORIZON",
                                    "Jump Back In"
                            )
            )

    // Persistence
    private val sharedPrefs = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val _isDarkTheme = MutableStateFlow(sharedPrefs.getBoolean("is_dark_mode", true))
    val isDarkTheme = _isDarkTheme.asStateFlow()

    // Initialize theme from SharedPreferences
    private val savedThemeId = sharedPrefs.getString("current_theme_id", "orange") ?: "orange"
    private val _currentTheme = MutableStateFlow(themes[savedThemeId] ?: themes["orange"]!!)
    val currentTheme = _currentTheme.asStateFlow()

    fun updateTheme(themeId: String) {
        _currentTheme.value = themes[themeId] ?: themes["orange"]!!
        sharedPrefs.edit { putString("current_theme_id", themeId) }
    }

    fun toggleThemeMode() {
        val newMode = !_isDarkTheme.value
        _isDarkTheme.value = newMode
        sharedPrefs.edit { putBoolean("is_dark_mode", newMode) }
        //        sharedPrefs.edit().putBoolean("is_dark_mode", newMode).apply()
    }

    // Media Lists
    private val _videoList = MutableStateFlow<List<MediaFile>>(emptyList())
    val videoList = _videoList.asStateFlow()

    private val _audioList = MutableStateFlow<List<MediaFile>>(emptyList())
    val audioList = _audioList.asStateFlow()

    private val _imageList = MutableStateFlow<List<MediaFile>>(emptyList())
    val imageList = _imageList.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums = _albums.asStateFlow()

    // --- REFRESH STATE ---
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // --- REALTIME ANALYTICS STATE ---
    private val _analyticsUpdateTrigger = MutableStateFlow(0L) // Used to force refresh logic

    val realtimeAnalytics =
            combine(_analyticsUpdateTrigger, _audioList, _videoList) { _, audio, videos ->
                        calculateAnalytics(audio + videos)
                    }
                    .stateIn(
                            viewModelScope,
                            SharingStarted.WhileSubscribed(5000),
                            RealtimeAnalytics()
                    )

    // --- MOVIES TAB STATE (Videos > 1 Hour) ---
    private val _movieSortOption = MutableStateFlow(SortOption.DATE_ADDED_DESC)
    val movieSortOption = _movieSortOption.asStateFlow()

    val moviesList =
            _videoList
                    .map { list ->
                        list.filter { it.duration >= 3600000 } // 1 Hour = 3,600,000 ms
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedMovies =
            combine(moviesList, _movieSortOption) { list, sort ->
                        when (sort) {
                            SortOption.TITLE_ASC -> list.sortedBy { it.title }
                            SortOption.TITLE_DESC -> list.sortedByDescending { it.title }
                            SortOption.DURATION_ASC -> list.sortedBy { it.duration }
                            SortOption.DURATION_DESC -> list.sortedByDescending { it.duration }
                            SortOption.DATE_ADDED_DESC ->
                                    list.sortedByDescending { it.id } // Proxy for latest
                        }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateMovieSortOption(option: SortOption) {
        _movieSortOption.value = option
    }

    // --- QUEUE STATE ---
    private val _currentQueue = MutableStateFlow<List<MediaFile>>(emptyList())
    val currentQueue = _currentQueue.asStateFlow()

    private val _currentIndex = MutableStateFlow<Int?>(null)
    val currentIndex = _currentIndex.asStateFlow()

    // Display queue for UI - reflects shuffled order when shuffle is enabled
    private val _displayQueue = MutableStateFlow<List<MediaFile>>(emptyList())
    val displayQueue = _displayQueue.asStateFlow()

    // --- CONTINUE WATCHING FLOW ---
    val continueWatchingList =
            combine(_videoList, mediaDao.getContinueWatching()) { videos, historyItems ->
                        historyItems.mapNotNull { history ->
                            val video = videos.find { it.id == history.mediaId }
                            if (video != null) {
                                video to history
                            } else null
                        }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived State: Video Folders
    val videoFolders =
            _videoList
                    .map { videos ->
                        videos
                                .groupBy { it.bucketId }
                                .map { (bucketId, bucketVideos) ->
                                    VideoFolder(
                                            id = bucketId,
                                            name = bucketVideos.firstOrNull()?.bucketName
                                                            ?: "Unknown",
                                            videoCount = bucketVideos.size,
                                            thumbnailUri = bucketVideos.firstOrNull()?.uri
                                                            ?: Uri.EMPTY
                                    )
                                }
                                .sortedBy { it.name }
                    }
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = emptyList()
                    )

    // Playlist State
    val playlists =
            playlistRepository.playlistsFlow.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList()
            )

    val audioPlaylists =
            playlists
                    .map { list -> list.filter { !it.isVideo } }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoPlaylists =
            playlists
                    .map { list -> list.filter { it.isVideo } }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search and Sort State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _albumSearchQuery = MutableStateFlow("")
    val albumSearchQuery = _albumSearchQuery.asStateFlow()

    private val _folderSearchQuery = MutableStateFlow("")
    val folderSearchQuery = _folderSearchQuery.asStateFlow()

    private val _albumSortOption = MutableStateFlow(AlbumSortOption.NAME_ASC)
    val albumSortOption = _albumSortOption.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_ADDED_DESC)
    val sortOption = _sortOption.asStateFlow()

    val filteredAudioList =
            combine(_audioList, _searchQuery, _sortOption) { list, query, sort ->
                        var result = list
                        if (query.isNotEmpty()) {
                            result =
                                    result.filter {
                                        it.title.contains(query, ignoreCase = true) ||
                                                (it.artist?.contains(query, ignoreCase = true) ==
                                                        true)
                                    }
                        }
                        when (sort) {
                            SortOption.TITLE_ASC -> result.sortedBy { it.title }
                            SortOption.TITLE_DESC -> result.sortedByDescending { it.title }
                            SortOption.DURATION_ASC -> result.sortedBy { it.duration }
                            SortOption.DURATION_DESC -> result.sortedByDescending { it.duration }
                            SortOption.DATE_ADDED_DESC -> result.sortedByDescending { it.id }
                        }
                    }
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = emptyList()
                    )

    val filteredAlbums =
            combine(_albums, _albumSearchQuery, _albumSortOption) { list, query, sort ->
                        var result = list
                        if (query.isNotEmpty()) {
                            result =
                                    result.filter {
                                        it.name.contains(query, ignoreCase = true) ||
                                                it.artist.contains(query, ignoreCase = true)
                                    }
                        }
                        when (sort) {
                            AlbumSortOption.NAME_ASC -> result.sortedBy { it.name.lowercase() }
                            AlbumSortOption.ARTIST_ASC -> result.sortedBy { it.artist.lowercase() }
                            AlbumSortOption.YEAR_DESC ->
                                    result.sortedByDescending { it.firstYear ?: 0 }
                            AlbumSortOption.SONG_COUNT_DESC ->
                                    result.sortedByDescending { it.songCount }
                        }
                    }
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = emptyList()
                    )

    // Player State
    private val _player = MutableStateFlow<MediaController?>(null)
    val player = _player.asStateFlow()

    private val _currentTrack = MutableStateFlow<MediaFile?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _isPlayerLocked = MutableStateFlow(false)
    val isPlayerLocked = _isPlayerLocked.asStateFlow()

    private val _resizeMode = MutableStateFlow(ResizeMode.FIT)
    val resizeMode = _resizeMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode = _isInPipMode.asStateFlow()

    // --- VIDEO PLAYER VISIBILITY STATE ---
    // Explicitly tracks if the fullscreen player should be shown.
    // This decouples "Current Track is Video" from "Show Player".
    private val _isVideoPlayerVisible = MutableStateFlow(false)
    val isVideoPlayerVisible = _isVideoPlayerVisible.asStateFlow()

    // --- NAVIGATION STATE ---
    private val _navigateToPlayer = MutableStateFlow(false)
    val navigateToPlayer = _navigateToPlayer.asStateFlow()

    fun handleIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("open_player", false) == true) {
            _navigateToPlayer.value = true
        }
    }

    fun onPlayerNavigationConsumed() {
        _navigateToPlayer.value = false
    }

    /**
     * Returns true if a video is currently playing and PIP should be triggered. Used by
     * MainActivity to enter PIP when home button is pressed.
     */
    fun shouldEnterPipMode(): Boolean {
        return _currentTrack.value?.isVideo == true && _isPlaying.value
    }

    // Removed duplicate setPipMode

    // Removed duplicate setPipMode
    // Removed duplicate closeVideo due to ambiguity

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionUpdateJob: Job? = null

    // --- BOOKMARKS FLOW ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentBookmarks =
            _currentTrack
                    .flatMapLatest { track ->
                        if (track != null) {
                            mediaDao.getBookmarks(track.id)
                        } else {
                            flowOf(emptyList())
                        }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- FAVORITES FLOW (Is Current Track Liked?) ---
    val isCurrentTrackFavorite =
            combine(_currentTrack, playlists) { track, allPlaylists ->
                        if (track == null) return@combine false
                        val favPlaylist =
                                allPlaylists.find { it.name == "Favorites" && !it.isVideo }
                        favPlaylist != null && favPlaylist.mediaIds.contains(track.id)
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- LAST PLAYED AUDIO FLOW ---
    val lastPlayedAudio =
            combine(_audioList, mediaDao.getLastPlayedAudioFlow()) { audioFiles, history ->
                        if (history != null) {
                            audioFiles.find { it.id == history.mediaId }
                        } else null
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Track current position in display queue (for highlighting current track in shuffled view)
    val displayQueueIndex =
            combine(_currentTrack, _displayQueue) { track, queue ->
                        if (track == null) null
                        else queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- SELECTION & DELETION STATE ---
    private val _selectedMediaIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMediaIds = _selectedMediaIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _deleteIntentEvent = MutableSharedFlow<IntentSender>()
    val deleteIntentEvent = _deleteIntentEvent.asSharedFlow()

    init {
        initializeMediaController()
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.migrateLegacyData()
            // Fix: Call ensureDefaultPlaylists directly via repo to check actual DB state
            playlistRepository.ensureDefaultPlaylists()

            // Trigger analytics refresh on start
            _analyticsUpdateTrigger.value = System.currentTimeMillis()
        }
    }

    // --- Actions for UI ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    fun updateAlbumSearchQuery(query: String) {
        _albumSearchQuery.value = query
    }
    fun updateFolderSearchQuery(query: String) {
        _folderSearchQuery.value = query
    }
    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun updateAlbumSortOption(option: AlbumSortOption) {
        _albumSortOption.value = option
    }

    // --- Selection Logic ---
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

    // --- Deletion Logic ---
    fun deleteSelectedMedia() {
        val idsToDelete = _selectedMediaIds.value.toList()
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val allMedia = _videoList.value + _audioList.value
            val filesToDelete = allMedia.filter { idsToDelete.contains(it.id) }
            val uris = filesToDelete.map { it.uri }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent: PendingIntent =
                        MediaStore.createDeleteRequest(app.contentResolver, uris)
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
        val ids = _selectedMediaIds.value.toList()
        onDeleteSuccess(ids)
    }

    private fun onDeleteSuccess(ids: List<Long>) {
        viewModelScope.launch {
            _videoList.value = _videoList.value.filter { !ids.contains(it.id) }
            _audioList.value = _audioList.value.filter { !ids.contains(it.id) }
            val currentPlaylists = playlists.value
            currentPlaylists.forEach { pl ->
                if (pl.mediaIds.any { ids.contains(it) }) {
                    playlistRepository.updatePlaylistTracks(
                            pl.id,
                            pl.mediaIds.filter { !ids.contains(it) }
                    )
                }
            }
            _selectedMediaIds.value = emptySet()
            _isSelectionMode.value = false
        }
    }

    // --- Bookmark Management ---
    fun addBookmark(timestamp: Long, label: String) {
        val track = _currentTrack.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mediaDao.addBookmark(
                    BookmarkEntity(mediaId = track.id, timestamp = timestamp, label = label)
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { mediaDao.deleteBookmark(id) }
    }

    // --- Favorite Management ---
    fun toggleFavorite() {
        val track = _currentTrack.value ?: return
        val isFav = isCurrentTrackFavorite.value

        viewModelScope.launch(Dispatchers.IO) {
            // Find Favorites playlist, or create if missing
            var favPlaylist = playlists.value.find { it.name == "Favorites" && !it.isVideo }
            if (favPlaylist == null) {
                playlistRepository.createPlaylist("Favorites", false)
                // Small delay to ensure DB insertion propagates to Flow before we fetch again
                delay(100)
                favPlaylist = playlists.value.find { it.name == "Favorites" && !it.isVideo }
            }

            if (favPlaylist != null) {
                if (isFav) {
                    playlistRepository.removeSongFromPlaylist(favPlaylist.id, track.id)
                } else {
                    playlistRepository.addSongToPlaylist(favPlaylist.id, track.id)
                }
            }
        }
    }

    // --- Player Initialization ---
    private fun initializeMediaController() {
        val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture?.addListener(
                {
                    try {
                        val controller = controllerFuture?.get()
                        _player.value = controller
                        setupPlayerListener(controller)

                        if (controller != null) {
                            _isPlaying.value = controller.isPlaying
                            _isShuffleEnabled.value = controller.shuffleModeEnabled
                            _repeatMode.value = controller.repeatMode
                            _playbackSpeed.value = controller.playbackParameters.speed
                            _videoSize.value = controller.videoSize
                            updateCurrentTrackFromPlayer(controller)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                MoreExecutors.directExecutor()
        )
    }

    private val _videoSize = MutableStateFlow(androidx.media3.common.VideoSize.UNKNOWN)
    val videoSize = _videoSize.asStateFlow()

    private fun setupPlayerListener(controller: MediaController?) {
        controller?.addListener(
                object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        _videoSize.value = videoSize
                    }

                    override fun onTimelineChanged(
                            timeline: androidx.media3.common.Timeline,
                            reason: Int
                    ) {
                        updateDisplayQueue()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _duration.value = controller.duration.coerceAtLeast(0L)
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _isShuffleEnabled.value = shuffleModeEnabled
                        updateDisplayQueue()
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
                    }

                    override fun onPlaybackParametersChanged(
                            playbackParameters: androidx.media3.common.PlaybackParameters
                    ) {
                        _playbackSpeed.value = playbackParameters.speed
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateCurrentTrackFromPlayer(controller)
                        // Logic moved to heartbeat to ensure duration threshold
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        // Restore saved track selection if pending
                        if (pendingAudioTrackIndex != -1) {
                            val allAudio = getAudioTracks()
                            if (pendingAudioTrackIndex in allAudio.indices) {
                                val track = allAudio[pendingAudioTrackIndex]
                                selectAudioTrack(track.groupIndex, track.trackIndex)
                            }
                            pendingAudioTrackIndex = -1
                        }

                        if (pendingSubtitleTrackIndex != -1) {
                            val allSubs = getSubtitleTracks()
                            if (pendingSubtitleTrackIndex in allSubs.indices) {
                                val track = allSubs[pendingSubtitleTrackIndex]
                                selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            } else if (pendingSubtitleTrackIndex == -2) {
                                disableSubtitles()
                            }
                            pendingSubtitleTrackIndex = -1
                        }
                    }
                }
        )
    }

    private fun recordPlay(mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            // Standard Analytics
            mediaDao.initAnalytics(mediaId, now)
            mediaDao.incrementPlayCount(mediaId, now)

            // Log for "Recent Favorites"
            mediaDao.logPlayEvent(PlayEvent(mediaId = mediaId, timestamp = now))

            // Trigger analytics refresh
            _analyticsUpdateTrigger.emit(now)
        }
    }

    private fun updateCurrentTrackFromPlayer(controller: MediaController) {
        val currentMediaItem = controller.currentMediaItem
        if (currentMediaItem == null) {
            _currentTrack.value = null
            _currentIndex.value = null
            return
        }
        val id = currentMediaItem.mediaId.toLongOrNull()
        if (id != null) {
            val track =
                    _audioList.value.find { it.id == id } ?: _videoList.value.find { it.id == id }
            _currentTrack.value = track
            _currentIndex.value = controller.currentMediaItemIndex

            // Reset Analytics Accumulator for the new track
            currentTrackPlaytimeAccumulator = 0L
            hasLoggedCurrentTrack = false

            // Fix: Only persist queue index if NOT video.
            // This prevents video playback from overwriting the last played music position in the
            // persisted queue.
            if (track != null && !track.isVideo) {
                persistQueueIndex(controller.currentMediaItemIndex)
            }
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob =
                viewModelScope.launch {
                    var saveCounter = 0
                    val today = getNormalizedToday()

                    // Initialize today's row if missing (Fire and forget, or await on IO)
                    // We use launch(IO) so we don't delay the first tick of the slider
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            mediaDao.initDailyPlaytime(today)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Accumulator for playtime logic
                    var accumulatedPlaytime = 0L
                    val updateInterval = 500L

                    while (isActive) {
                        _player.value?.let { player ->
                            try {
                                val pos = player.currentPosition
                                _currentPosition.value = pos
                                val dur = player.duration.coerceAtLeast(0L)
                                _duration.value = dur

                                // ACCUMULATE PLAYTIME
                                if (_isPlaying.value) {
                                    // 1. Total Daily Playtime (Existing)
                                    accumulatedPlaytime += updateInterval

                                    // 2. Track Play Count Threshold Logic (New)
                                    // Ensures we only count a "Play" if user listened for 30s or
                                    // 50% of track (if short)
                                    if (!hasLoggedCurrentTrack) {
                                        currentTrackPlaytimeAccumulator += updateInterval

                                        val threshold =
                                                if (dur > 0) min(30000L, dur / 2) else 30000L
                                        val safeThreshold =
                                                max(
                                                        5000L,
                                                        threshold
                                                ) // Minimum 5s even for very short clips

                                        if (currentTrackPlaytimeAccumulator >= safeThreshold) {
                                            val track = _currentTrack.value
                                            if (track != null) {
                                                recordPlay(track.id)
                                                hasLoggedCurrentTrack = true
                                            }
                                        }
                                    }
                                }

                                // Flush to DB every 30 seconds (60 ticks)
                                if (saveCounter % 60 == 0) {
                                    if (accumulatedPlaytime > 0) {
                                        val timeToSave = accumulatedPlaytime
                                        // Fire and forget IO, DO NOT suspend the loop
                                        viewModelScope.launch(Dispatchers.IO) {
                                            try {
                                                mediaDao.addToDailyPlaytime(today, timeToSave)
                                                // Notify UI to refresh stats
                                                _analyticsUpdateTrigger.emit(
                                                        System.currentTimeMillis()
                                                )
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        accumulatedPlaytime =
                                                0 // Reset local accumulator immediately
                                    }
                                }

                                // Save playback position periodically
                                if (saveCounter % 10 == 0) {
                                    val track = _currentTrack.value
                                    if (track != null) {
                                        savePlaybackState(
                                                track.id,
                                                pos,
                                                track.duration,
                                                track.isVideo
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            saveCounter++
                        }
                        delay(updateInterval)
                    }
                }
    }

    private fun stopPositionUpdates() {
        _currentTrack.value?.let { track ->
            savePlaybackState(track.id, _currentPosition.value, track.duration, track.isVideo)
        }
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun savePlaybackState(mediaId: Long, position: Long, duration: Long, isVideo: Boolean) {
        // Get current track selections
        val audioIndex = if (isVideo) getAudioTracks().indexOfFirst { it.isSelected } else -1
        val subtitleIndex =
                if (isVideo) {
                    if (areSubtitlesDisabled()) -2
                    else getSubtitleTracks().indexOfFirst { it.isSelected }
                } else -1

        viewModelScope.launch(Dispatchers.IO) {
            mediaDao.saveHistory(
                    PlaybackHistory(
                            mediaId = mediaId,
                            position = position,
                            duration = duration,
                            timestamp = System.currentTimeMillis(),
                            mediaType = if (isVideo) "VIDEO" else "AUDIO",
                            audioTrackIndex = audioIndex,
                            subtitleTrackIndex = subtitleIndex
                    )
            )
        }
    }

    // --- Analytics Logic ---
    private fun getNormalizedToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private suspend fun calculateAnalytics(allMedia: List<MediaFile>): RealtimeAnalytics {
        return withContext(Dispatchers.IO) {
            val today = getNormalizedToday()
            val weekStart = today - (6 * 24 * 60 * 60 * 1000) // Last 7 days including today
            val monthStart = today - (29 * 24 * 60 * 60 * 1000) // Last 30 days

            // 1. Playtime Metrics
            val todayMs = mediaDao.getPlaytimeForDay(today).firstOrNull() ?: 0L
            val weekMs = mediaDao.getPlaytimeRange(weekStart, today).firstOrNull() ?: 0L
            val monthMs = mediaDao.getPlaytimeRange(monthStart, today).firstOrNull() ?: 0L

            val avgDailyMs = monthMs / 30

            // 2. Streak Calculation
            val activeDays = mediaDao.getActiveDays().firstOrNull() ?: emptyList()
            var currentStreak = 0
            if (activeDays.isNotEmpty()) {
                val todayCheck = activeDays.first()
                // If the most recent active day is today or yesterday, streak is alive
                if (todayCheck == today || todayCheck == (today - 86400000)) {
                    currentStreak = 1
                    var checkDate = todayCheck
                    for (i in 1 until activeDays.size) {
                        val prevDate = activeDays[i]
                        if (checkDate - prevDate == 86400000L) { // Difference of exactly one day
                            currentStreak++
                            checkDate = prevDate
                        } else {
                            break
                        }
                    }
                }
            }

            // 3. Favorites
            val overallFavId = mediaDao.getOverallFavoriteMediaId()
            val recentFavId = mediaDao.getMostPlayedMediaIdSince(monthStart)

            val overallFav = allMedia.find { it.id == overallFavId }
            val recentFav = allMedia.find { it.id == recentFavId }

            RealtimeAnalytics(
                    todayPlaytimeMinutes = (todayMs / 60000).toInt(),
                    weekPlaytimeMinutes = (weekMs / 60000).toInt(),
                    avgDailyMinutes = (avgDailyMs / 60000).toInt(),
                    streakDays = currentStreak,
                    currentFavorite = recentFav,
                    allTimeFavorite = overallFav
            )
        }
    }

    // --- Media Loading ---
    fun scanMedia() {
        if (_isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                val videos = queryMedia(isVideo = true)
                val audio = queryMedia(isVideo = false)
                // Attach any already-cached thumbnails before publishing the list
                val videosWithCachedThumbs =
                        videos.map { v ->
                            val cached = thumbnailManager.getCachedPath(v)
                            if (cached != null) v.copy(thumbnailPath = cached) else v
                        }
                _videoList.value = videosWithCachedThumbs
                _audioList.value = audio
                _imageList.value = queryImages()
                _albums.value = queryAlbums()

                // RESTORE QUEUE AFTER LOADING
                // Only restore if queue is empty to avoid disrupting playback on refresh
                if (_currentQueue.value.isEmpty()) {
                    restoreQueue(audio + videos)
                }

                // Initial Analytics Calc
                _analyticsUpdateTrigger.emit(System.currentTimeMillis())
            } finally {
                _isRefreshing.value = false
            }

            // Generate missing thumbnails in background (after refreshing flag is cleared)
            val uncachedVideos = _videoList.value.filter { it.thumbnailPath == null }
            if (uncachedVideos.isNotEmpty()) {
                thumbnailManager.generateThumbnails(uncachedVideos).collect { (id, path) ->
                    _videoList.value =
                            _videoList.value.map {
                                if (it.id == id) it.copy(thumbnailPath = path) else it
                            }
                }
            }

            // Clean up thumbnails for deleted videos
            thumbnailManager.cleanStaleThumbnails(_videoList.value)
        }
    }

    // --- Persistent Queue Logic ---
    private suspend fun restoreQueue(allMedia: List<MediaFile>) {
        val savedQueueItems = mediaDao.getSavedQueue()
        if (savedQueueItems.isNotEmpty()) {
            val restoredQueue =
                    savedQueueItems
                            .mapNotNull { item -> allMedia.find { it.id == item.mediaId } }
                            .filter { !it.isVideo }

            var finalQueue = restoredQueue
            var finalIndex = 0
            var finalStartPos = 0L

            if (finalQueue.isNotEmpty()) {
                // Restore Index from Prefs
                val savedIndex = sharedPrefs.getInt("last_queue_index", 0)
                finalIndex = savedIndex.coerceIn(0, finalQueue.size - 1)

                // Fetch last playback position
                val track = finalQueue[finalIndex]
                val history = mediaDao.getHistory(track.id)
                if (history != null &&
                                (history.duration == 0L ||
                                        history.position < (history.duration * 0.99))
                ) {
                    finalStartPos = history.position
                }
            } else {
                // FALLBACK: If queue is empty (or was all videos), try to restore the last played
                // AUDIO track
                val lastAudio = mediaDao.getLastPlayedAudio()
                if (lastAudio != null) {
                    val track = allMedia.find { it.id == lastAudio.mediaId }
                    if (track != null) {
                        finalQueue = listOf(track)
                        finalIndex = 0
                        if (lastAudio.duration == 0L ||
                                        lastAudio.position < (lastAudio.duration * 0.99)
                        ) {
                            finalStartPos = lastAudio.position
                        }
                    }
                }
            }

            if (finalQueue.isNotEmpty()) {
                _currentQueue.value = finalQueue
                _currentIndex.value = finalIndex
                _currentTrack.value = finalQueue[finalIndex]

                // Set to player
                withContext(Dispatchers.Main) {
                    _player.value?.let { controller ->
                        if (controller.mediaItemCount == 0) {
                            val items = finalQueue.map { it.toMediaItem() }
                            controller.setMediaItems(items, finalIndex, finalStartPos)
                            controller.prepare()
                        }
                    }
                }
            }
        }
    }

    private fun persistQueue(queue: List<MediaFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            val entities = queue.mapIndexed { index, media -> QueueItemEntity(media.id, index) }
            mediaDao.replaceQueue(entities)
        }
    }

    private fun persistQueueIndex(index: Int) {
        sharedPrefs.edit { putInt("last_queue_index", index) }
    }

    // --- Query Methods (Unchanged) ---
    private fun queryMedia(isVideo: Boolean): List<MediaFile> {
        val mediaList = mutableListOf<MediaFile>()
        val collection =
                if (Build.VERSION.SDK_INT >= 29) {
                    if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

        val projection =
                if (isVideo) {
                    arrayOf(
                            MediaStore.Video.Media._ID,
                            MediaStore.Video.Media.DISPLAY_NAME,
                            MediaStore.Video.Media.DURATION,
                            MediaStore.Video.Media.BUCKET_ID,
                            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                            MediaStore.Video.Media.SIZE,
                            MediaStore.Video.Media.WIDTH,
                            MediaStore.Video.Media.HEIGHT,
                            MediaStore.Video.Media.DATE_MODIFIED
                    )
                } else {
                    arrayOf(
                            MediaStore.Audio.Media._ID,
                            MediaStore.Audio.Media.TITLE,
                            MediaStore.Audio.Media.ARTIST,
                            MediaStore.Audio.Media.DURATION,
                            MediaStore.Audio.Media.ALBUM_ID
                    )
                }
        val selection = if (!isVideo) "${MediaStore.Audio.Media.IS_MUSIC} != 0" else null

        try {
            app.contentResolver.query(collection, projection, selection, null, null)?.use { cursor
                ->
                val idColumn =
                        cursor.getColumnIndexOrThrow(
                                if (isVideo) MediaStore.Video.Media._ID
                                else MediaStore.Audio.Media._ID
                        )
                val nameColumn =
                        cursor.getColumnIndexOrThrow(
                                if (isVideo) MediaStore.Video.Media.DISPLAY_NAME
                                else MediaStore.Audio.Media.TITLE
                        )
                val durationColumn =
                        cursor.getColumnIndexOrThrow(
                                if (isVideo) MediaStore.Video.Media.DURATION
                                else MediaStore.Audio.Media.DURATION
                        )
                val artistColumn =
                        if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        else -1
                val albumIdColumn =
                        if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                        else -1

                val bucketIdColumn =
                        if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID) else -1
                val bucketNameColumn =
                        if (isVideo)
                                cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                        else -1
                val sizeColumn =
                        if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.SIZE) else -1
                val widthColumn =
                        if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.WIDTH) else -1
                val heightColumn =
                        if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT) else -1
                val dateModifiedColumn =
                        if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                        else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val duration = cursor.getLong(durationColumn)
                    val contentUri = ContentUris.withAppendedId(collection, id)

                    var artist = ""
                    var albumArtUri: Uri? = null
                    var albumId: Long = -1

                    var bucketId = ""
                    var bucketName = ""
                    var size: Long = 0
                    var resolution = ""
                    var dateModified: Long = 0

                    if (isVideo) {
                        bucketId =
                                if (bucketIdColumn != -1) cursor.getString(bucketIdColumn) ?: ""
                                else ""
                        bucketName =
                                if (bucketNameColumn != -1)
                                        cursor.getString(bucketNameColumn) ?: "Unknown"
                                else "Unknown"
                        size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0
                        dateModified =
                                if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn)
                                else 0

                        val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                        val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0

                        resolution =
                                if (height >= 2160) "4K"
                                else if (height >= 1080) "1080P"
                                else if (height >= 720) "720P"
                                else if (height >= 480) "480P"
                                else if (height > 0) "${height}P" else ""
                    } else {
                        artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        albumId = cursor.getLong(albumIdColumn)
                        val sArtworkUri = "content://media/external/audio/albumart".toUri()
                        albumArtUri = ContentUris.withAppendedId(sArtworkUri, albumId)
                    }

                    mediaList.add(
                            MediaFile(
                                    id,
                                    contentUri,
                                    name,
                                    artist,
                                    duration,
                                    isVideo,
                                    false,
                                    albumArtUri,
                                    albumId,
                                    bucketId,
                                    bucketName,
                                    size,
                                    resolution,
                                    dateModified
                            )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return mediaList
    }

    private fun queryImages(): List<MediaFile> {
        val imageList = mutableListOf<MediaFile>()
        val collection =
                if (Build.VERSION.SDK_INT >= 29)
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        try {
            app.contentResolver.query(
                            collection,
                            projection,
                            null,
                            null,
                            "${MediaStore.Images.Media.DATE_ADDED} DESC"
                    )
                    ?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameColumn =
                                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn) ?: "Unknown Image"
                            val contentUri = ContentUris.withAppendedId(collection, id)
                            imageList.add(
                                    MediaFile(
                                            id = id,
                                            uri = contentUri,
                                            title = name,
                                            artist = null,
                                            duration = 0,
                                            isVideo = false,
                                            isImage = true,
                                            albumArtUri = null,
                                            albumId = -1
                                    )
                            )
                        }
                    }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return imageList
    }

    private fun queryAlbums(): List<Album> {
        val albumList = mutableListOf<Album>()
        val collection =
                if (Build.VERSION.SDK_INT >= 29)
                        MediaStore.Audio.Albums.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection =
                arrayOf(
                        MediaStore.Audio.Albums._ID,
                        MediaStore.Audio.Albums.ALBUM,
                        MediaStore.Audio.Albums.ARTIST,
                        MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                        MediaStore.Audio.Albums.FIRST_YEAR
                )
        try {
            app.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
                val countColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
                val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Albums.FIRST_YEAR)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(albumColumn) ?: "Unknown Album"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val count = cursor.getInt(countColumn)
                    val year = if (yearColumn != -1) cursor.getInt(yearColumn) else null
                    val finalYear = if (year != null && year > 1900) year else null
                    val sArtworkUri = "content://media/external/audio/albumart".toUri()
                    val albumArtUri = ContentUris.withAppendedId(sArtworkUri, id)
                    albumList.add(Album(id, name, artist, count, finalYear, albumArtUri))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return albumList
    }

    // --- Playlist Management ---
    fun createPlaylist(name: String, isVideo: Boolean = false) {
        val currentPlaylists = playlists.value
        if (currentPlaylists.any {
                    it.name.equals(name, ignoreCase = true) && it.isVideo == isVideo
                }
        ) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) { playlistRepository.createPlaylist(name, isVideo) }
    }

    fun renamePlaylist(id: String, newName: String) {
        val currentPlaylists = playlists.value
        val playlist = currentPlaylists.find { it.id == id } ?: return

        if (currentPlaylists.any {
                    it.name.equals(newName, ignoreCase = true) &&
                            it.isVideo == playlist.isVideo &&
                            it.id != id
                }
        ) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) { playlistRepository.renamePlaylist(id, newName) }
    }

    fun deletePlaylist(playlistId: String) =
            viewModelScope.launch(Dispatchers.IO) { playlistRepository.deletePlaylist(playlistId) }
    fun addSongToPlaylist(playlistId: String, mediaId: Long) =
            viewModelScope.launch(Dispatchers.IO) {
                playlistRepository.addSongToPlaylist(playlistId, mediaId)
            }
    fun removeSongFromPlaylist(playlistId: String, mediaId: Long) =
            viewModelScope.launch(Dispatchers.IO) {
                playlistRepository.removeSongFromPlaylist(playlistId, mediaId)
            }
    fun toggleAlbumInFavorites(albumSongs: List<MediaFile>) {
        val favPlaylist = playlists.value.find { it.name == "Favorites" && !it.isVideo } ?: return
        val allInFav = albumSongs.all { favPlaylist.mediaIds.contains(it.id) }
        val newMediaIds = favPlaylist.mediaIds.toMutableList()
        if (allInFav) albumSongs.forEach { newMediaIds.remove(it.id) }
        else albumSongs.forEach { if (!newMediaIds.contains(it.id)) newMediaIds.add(it.id) }
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.updatePlaylistTracks(favPlaylist.id, newMediaIds)
        }
    }

    // --- Playback Logic ---
    fun playMedia(media: MediaFile) {
        if (media.isVideo) {
            playVideo(media) // Redirect to new video handler
        } else if (!media.isImage) {
            val currentVisibleList =
                    filteredAudioList.value.takeIf { it.isNotEmpty() } ?: _audioList.value
            val startIndex = currentVisibleList.indexOfFirst { it.id == media.id }
            if (startIndex >= 0) setQueue(currentVisibleList, startIndex, false)
        }
    }

    private fun playVideo(media: MediaFile) {
        // Cancel any pending queue update
        queueUpdateJob?.cancel()

        // OPTIMIZATION: Start playing the target video IMMEDIATELY.
        // Navigate to player with just this item first.
        playVideoFromList(media, listOf(media))

        // Then, load the rest of the folder in the background to enable "Next/Prev"
        queueUpdateJob =
                viewModelScope.launch(Dispatchers.IO) {
                    val folderVideos = _videoList.value.filter { it.bucketId == media.bucketId }
                    if (folderVideos.size > 1) {
                        // Determine start index for the FULL list
                        val newStartIndex =
                                folderVideos.indexOfFirst { it.id == media.id }.coerceAtLeast(0)

                        withContext(Dispatchers.Main) {
                            // SILENTLY update the queue without stopping playback
                            updateQueueInBackground(folderVideos, newStartIndex)
                        }
                    }
                }
    }

    private var queueUpdateJob: Job? = null

    /**
     * Play a video from a context list (folder videos or playlist). Sets the full list as the queue
     * so next/prev navigation works. OPTIMIZATION: Loads only the target video first for instant
     * playback, then loads the rest of the queue in the background.
     */
    fun playVideoFromList(media: MediaFile, list: List<MediaFile>) {
        if (!media.isVideo) return

        // Cancel any pending queue update from a previous click to prevent race conditions
        queueUpdateJob?.cancel()

        // Snapshot Audio State if we are interrupting an active audio session
        val current = _currentTrack.value
        if (_currentQueue.value.isNotEmpty() && current?.isVideo != true) {
            savedAudioState =
                    AudioPlayerState(
                            queue = _currentQueue.value,
                            currentIndex = _currentIndex.value ?: 0,
                            position = _currentPosition.value,
                            isPlaying = _isPlaying.value,
                            isShuffleEnabled = _isShuffleEnabled.value,
                            repeatMode = _repeatMode.value
                    )
        }

        _isPlayerLocked.value = false
        _playbackSpeed.value = 1.0f
        _resizeMode.value = ResizeMode.FIT
        _isVideoPlayerVisible.value = true // Explicitly show player

        viewModelScope.launch(Dispatchers.IO) {
            val history = mediaDao.getHistory(media.id)
            val startPos =
                    if (history != null && history.position < (history.duration * 0.95))
                            history.position
                    else 0L

            // Set pending tracks for restoration
            pendingAudioTrackIndex = history?.audioTrackIndex ?: -1
            pendingSubtitleTrackIndex = history?.subtitleTrackIndex ?: -1

            // 1. Play ONLY the target video immediately
            withContext(Dispatchers.Main) {
                // Pass just the single item list to setQueue for instant start
                setQueue(listOf(media), 0, false, startPos)
            }

            // 2. Queue the rest in background if there's more than one item
            if (list.size > 1) {
                queueUpdateJob = launch {
                    val startIndex = list.indexOfFirst { it.id == media.id }.coerceAtLeast(0)
                    updateQueueInBackground(list, startIndex)
                }
            }
        }
    }

    /**
     * Call this when the Video Player screen is closed. It saves the video position and restores
     * the previous music session.
     */
    fun closeVideo() {
        _isVideoPlayerVisible.value = false // Hide player
        val current = _currentTrack.value
        if (current?.isVideo == true) {
            // Save video position to history for "Continue Watching"
            savePlaybackState(current.id, _currentPosition.value, _duration.value, true)

            // Restore the audio session
            restoreAudioSession()
        }
    }

    private fun restoreAudioSession() {
        val state = savedAudioState
        if (state != null) {
            // Restore internal StateFlows
            _currentQueue.value = state.queue
            _currentIndex.value = state.currentIndex
            _isShuffleEnabled.value = state.isShuffleEnabled
            _repeatMode.value = state.repeatMode

            // Restore Player State
            _player.value?.let { controller ->
                val items = state.queue.map { it.toMediaItem() }
                controller.setMediaItems(items, state.currentIndex, state.position)
                controller.shuffleModeEnabled = state.isShuffleEnabled
                controller.repeatMode = state.repeatMode
                controller.prepare()
                // Conditionally play/pause based on saved state (or pause to avoid sudden blasting)
                if (state.isPlaying) controller.play() else controller.pause()
            }

            // Immediately update the UI track so the miniplayer reappears correctly
            if (state.queue.isNotEmpty() && state.currentIndex < state.queue.size) {
                _currentTrack.value = state.queue[state.currentIndex]
            }

            // Clear the saved state after restoration
            savedAudioState = null
        } else {
            // No state to restore (e.g. video played without prior music), just stop
            _player.value?.stop()
            _player.value?.clearMediaItems()
            _currentTrack.value = null
            _currentQueue.value = emptyList()
        }
    }

    fun playMediaFromList(media: MediaFile, list: List<MediaFile>) {
        val startIndex = list.indexOfFirst { it.id == media.id }
        if (startIndex >= 0) setQueue(list, startIndex, false)
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean) {
        val allMedia = if (playlist.isVideo) _videoList.value else _audioList.value
        val playlistMedia = playlist.mediaIds.mapNotNull { id -> allMedia.find { it.id == id } }
        if (playlistMedia.isNotEmpty()) {
            val startIndex = if (shuffle) (playlistMedia.indices).random() else 0
            if (playlist.isVideo) {
                _isPlayerLocked.value = false
                _playbackSpeed.value = 1.0f
                _resizeMode.value = ResizeMode.FIT
                _isVideoPlayerVisible.value = true // Explicitly show player for video playlists
            }
            setQueue(playlistMedia, startIndex, shuffle)
        }
    }

    fun playAlbum(album: Album, shuffle: Boolean) {
        val albumSongs = _audioList.value.filter { it.albumId == album.id }
        if (albumSongs.isNotEmpty()) {
            val startIndex = if (shuffle) (albumSongs.indices).random() else 0
            setQueue(albumSongs, startIndex, shuffle)
        }
    }

    fun playAll(shuffle: Boolean) {
        val currentList = filteredAudioList.value.takeIf { it.isNotEmpty() } ?: _audioList.value
        if (currentList.isNotEmpty()) {
            val startIndex = if (shuffle) (currentList.indices).random() else 0
            setQueue(currentList, startIndex, shuffle)
        }
    }

    // UPDATED setQueue to delegate to MediaController
    fun setQueue(
            mediaList: List<MediaFile>,
            startIndex: Int,
            shuffle: Boolean = false,
            startPosition: Long = 0L
    ) {
        // Update Local State immediately for UI responsiveness
        _currentQueue.value = mediaList
        _isShuffleEnabled.value = shuffle

        // Launch in background to avoid blocking Main Thread during conversion of large playlists
        viewModelScope.launch(Dispatchers.IO) {
            val mediaItems = mediaList.map { it.toMediaItem() }

            withContext(Dispatchers.Main) {
                _player.value?.let { controller ->
                    controller.setMediaItems(mediaItems, startIndex, startPosition)
                    controller.shuffleModeEnabled = shuffle
                    controller.prepare()
                    controller.play()
                }

                // Update display queue only after controller is set up
                _displayQueue.value = _currentQueue.value
            }

            // Persist
            persistQueue(mediaList)
            withContext(Dispatchers.Main) { persistQueueIndex(startIndex) }
        }
    }

    /**
     * Updates the queue silently without stopping playback. Used for loading large video playlists
     * in the background after playback starts.
     */
    private suspend fun updateQueueInBackground(mediaList: List<MediaFile>, startIndex: Int) {
        // Update Local State so UI shows correct list
        _currentQueue.value = mediaList
        _displayQueue.value = mediaList // Since we are in static mode

        // Update Player: Add items before and after current item
        // We use a simplified strategy: Replace the items but keep the current window/position
        withContext(Dispatchers.Main) { // Ensure MediaController interaction is on Main
            _player.value?.let { controller ->
                // Current State
                val currentMediaId = controller.currentMediaItem?.mediaId
                val currentPos = controller.currentPosition

                // Re-verify that we are still playing the expected item
                val currentIndex = mediaList.indexOfFirst { it.id.toString() == currentMediaId }

                if (currentIndex != -1) {
                    // Offload heavy mapping to IO
                    val mediaItems =
                            withContext(Dispatchers.IO) { mediaList.map { it.toMediaItem() } }
                    // Back on Main to set items
                    controller.setMediaItems(mediaItems, currentIndex, currentPos)
                }
            }
        }

        persistQueue(mediaList)
        withContext(Dispatchers.Main) { persistQueueIndex(startIndex) }
    }

    /**
     * Updates the display queue to reflect the shuffled playback order. When shuffle is disabled,
     * displays the original queue order. When shuffle is enabled, builds the queue order based on
     * Media3's shuffle timeline.
     */
    private fun updateDisplayQueue() {
        // SIMPLIFIED: Always show the original queue order.
        // Shuffle just jumps around this static list.
        _displayQueue.value = _currentQueue.value
    }

    /**
     * Play a specific track from the queue (handles both shuffled and non-shuffled modes). Finds
     * the track in the controller's timeline and seeks to it.
     */
    fun playTrackFromQueue(track: MediaFile) {
        _player.value?.let { controller ->
            // Find the index of this track in the controller's timeline
            for (i in 0 until controller.mediaItemCount) {
                if (controller.getMediaItemAt(i).mediaId == track.id.toString()) {
                    controller.seekTo(i, 0L)
                    controller.play()
                    break
                }
            }
        }
    }

    private fun MediaFile.toMediaItem(): MediaItem {
        val metadata =
                MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setArtworkUri(albumArtUri)
                        .build()
        return MediaItem.Builder()
                .setUri(uri)
                .setMediaId(id.toString())
                .setMediaMetadata(metadata)
                .build()
    }

    // --- Video Specific ---
    fun toggleLock() {
        _isPlayerLocked.value = !_isPlayerLocked.value
    }
    fun toggleResizeMode() {
        val modes = ResizeMode.entries.toTypedArray()
        _resizeMode.value = modes[(_resizeMode.value.ordinal + 1) % modes.size]
    }
    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
        val current = _playbackSpeed.value
        val nextIndex = speeds.indexOfFirst { it > current }
        val newSpeed = if (nextIndex != -1) speeds[nextIndex] else speeds[0]
        _player.value?.setPlaybackSpeed(newSpeed)
        _playbackSpeed.value = newSpeed
    }
    fun setPipMode(isPip: Boolean) {
        _isInPipMode.value = isPip
    }

    // --- Track Selection (Audio & Subtitles) ---

    /** Get available audio tracks for the current video. */
    fun getAudioTracks(): List<TrackInfo> {
        val player = _player.value ?: return emptyList()
        val tracks = player.currentTracks
        val result = mutableListOf<TrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    val language =
                            format.language?.let { java.util.Locale(it).displayLanguage }
                                    ?: "Unknown"
                    val label = format.label ?: "Track ${trackIndex + 1}"
                    val name = if (format.label != null) "$label ($language)" else language

                    result.add(
                            TrackInfo(
                                    groupIndex = groupIndex,
                                    trackIndex = trackIndex,
                                    name = name,
                                    language = format.language,
                                    isSelected = isSelected
                            )
                    )
                }
            }
        }
        return result
    }

    /** Get available subtitle tracks for the current video. */
    fun getSubtitleTracks(): List<TrackInfo> {
        val player = _player.value ?: return emptyList()
        val tracks = player.currentTracks
        val result = mutableListOf<TrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    val language =
                            format.language?.let { java.util.Locale(it).displayLanguage }
                                    ?: "Unknown"
                    val label = format.label ?: "Subtitle ${trackIndex + 1}"
                    val name = if (format.label != null) "$label ($language)" else language

                    result.add(
                            TrackInfo(
                                    groupIndex = groupIndex,
                                    trackIndex = trackIndex,
                                    name = name,
                                    language = format.language,
                                    isSelected = isSelected
                            )
                    )
                }
            }
        }
        return result
    }

    /** Select a specific audio track. */
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val player = _player.value ?: return
        val tracks = player.currentTracks
        if (groupIndex >= tracks.groups.size) return

        val group = tracks.groups[groupIndex]
        val override =
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)

        player.trackSelectionParameters =
                player.trackSelectionParameters.buildUpon().setOverrideForType(override).build()
    }

    /** Select a specific subtitle track. */
    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        val player = _player.value ?: return
        val tracks = player.currentTracks
        if (groupIndex >= tracks.groups.size) return

        val group = tracks.groups[groupIndex]
        val override =
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)

        player.trackSelectionParameters =
                player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(override)
                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                        .build()
    }

    /** Disable all subtitle tracks. */
    fun disableSubtitles() {
        val player = _player.value ?: return
        player.trackSelectionParameters =
                player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                        .build()
    }

    /** Check if subtitles are currently disabled. */
    fun areSubtitlesDisabled(): Boolean {
        val player = _player.value ?: return true
        return player.trackSelectionParameters.disabledTrackTypes.contains(
                androidx.media3.common.C.TRACK_TYPE_TEXT
        )
    }

    // --- Controls ---
    fun playNext() {
        _player.value?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNext()
            }
        }
    }

    fun playPrevious() {
        _player.value?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0)
            } else if (it.hasPreviousMediaItem()) {
                it.seekToPrevious()
            }
        }
    }

    fun togglePlayPause() {
        _player.value?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    fun pauseVideo() {
        _player.value?.pause()
    }
    fun toggleShuffle() {
        _player.value?.let {
            val newMode = !it.shuffleModeEnabled
            it.shuffleModeEnabled = newMode
            _isShuffleEnabled.value = newMode
            // No need to rebuild/reorder queue visual list
            // usage: The queue remains in original order, but playback order changes internally
        }
    }
    fun toggleRepeat() {
        _player.value?.let {
            val newMode =
                    when (it.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
            it.repeatMode = newMode
        }
    }
    fun seekTo(positionMs: Long) {
        _player.value?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }
    fun rewind() {
        _player.value?.let { it.seekTo((it.currentPosition - 10000).coerceAtLeast(0)) }
    }
    fun forward() {
        _player.value?.let { it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration)) }
    }
    fun hasNext(): Boolean {
        return _player.value?.hasNextMediaItem() ?: false
    }
    fun hasPrevious(): Boolean {
        return _player.value?.hasPreviousMediaItem() ?: false
    }

    // --- Queue Management ---
    fun playNext(media: MediaFile) {
        val controller = _player.value
        val queue = _currentQueue.value.toMutableList()
        val currentIdx = _currentIndex.value ?: -1

        if (controller != null && queue.isNotEmpty() && currentIdx >= 0) {
            // Check if media already exists in queue
            val existingIndex = queue.indexOfFirst { it.id == media.id }

            if (existingIndex != -1) {
                // CASE 1: Currently Playing -> Ignore
                if (existingIndex == currentIdx) return

                // CASE 2: In Upcoming List (Index > Current) -> Ignore
                // User requirement: "make sure the song added only once if it is not in the
                // upcoming list"
                // Meaning: If it IS in upcoming, don't change anything.
                if (existingIndex > currentIdx) return

                // CASE 3: In History (Index < Current) -> Move to Next
                // User requirement: "playnext for the song already played should move it"

                // Remove from history
                queue.removeAt(existingIndex)
                controller.removeMediaItem(existingIndex)

                // New insertion point is currentIdx (since everything shifted up by 1)
                // Wait, if we remove at 0, current was 5 -> current becomes 4.
                // We want to insert at current + 1.
                // But `controller` handles index shifts automatically for `currentMediaItemIndex`?
                // Actually, if we remove before current, current index changes?
                // ExoPlayer: removing item before current DECREMENTS current index.
                // Our local `currentIdx` is a snapshot.

                // Let's use the controller's state after removal? No, async.
                // Logic:
                // Old Current: 5. Remove 2. New Current: 4.
                // We want to insert at New Current + 1 = 5.
                // So insert index = Current Index (old) ??

                // Let's do it safely:
                // If existing < current:
                // Remove existing.
                // Insert at currentIdx (which was old currentIdx, but now represents the slot AFTER
                // the *new* current).
                // Example: [0, 1, 2(existing), 3, 4, 5(current), 6].
                // Remove 2 -> [0, 1, 3, 4, 5(current), 6]. (Indices shifted).
                // Old Current was 5. New Current is 4.
                // We want to insert at 5 (after 4).
                // So insert at `currentIdx`.

                val insertIndex = currentIdx
                queue.add(insertIndex, media)
                _currentQueue.value = queue
                controller.addMediaItem(insertIndex, media.toMediaItem())

                // Update current index locally to match reality (decremented by 1)
                // _currentIndex.value = currentIdx - 1
                // BUT ExoPlayer listener will update _currentIndex automatically!
                // We should trust the listener.
            } else {
                // CASE 4: New Song -> Add Next
                // Simply insert at current + 1
                val insertIndex = currentIdx + 1
                queue.add(insertIndex, media)
                _currentQueue.value = queue
                controller.addMediaItem(insertIndex, media.toMediaItem())
            }

            // Update UI
            _displayQueue.value = _currentQueue.value
            persistQueue(queue)
        } else {
            playMedia(media)
        }
    }

    fun addToQueue(media: MediaFile) {
        val controller = _player.value
        val queue = _currentQueue.value.toMutableList()
        val currentIdx = _currentIndex.value ?: -1

        if (controller != null && queue.isNotEmpty()) {
            val existingIndex = queue.indexOfFirst { it.id == media.id }

            if (existingIndex != -1) {
                // CASE 1: Currently Playing -> Ignore
                if (existingIndex == currentIdx) return

                // CASE 2: In Upcoming List (Index > Current) -> Ignore
                if (existingIndex > currentIdx) return

                // CASE 3: In History (Index < Current) -> Move to End
                // User requirement: "similarly for the add to queue as well" (move if played)
                queue.removeAt(existingIndex)
                controller.removeMediaItem(existingIndex)

                // Add to end
                queue.add(media)
                _currentQueue.value = queue
                controller.addMediaItem(media.toMediaItem())
            } else {
                // CASE 4: New Song -> Add to End
                queue.add(media)
                _currentQueue.value = queue
                controller.addMediaItem(media.toMediaItem())
            }

            // Update UI
            _displayQueue.value = _currentQueue.value
            persistQueue(queue)
        } else {
            playMedia(media)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
