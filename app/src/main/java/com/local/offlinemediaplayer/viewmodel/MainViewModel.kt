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
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
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
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.max

enum class SortOption {
    TITLE_ASC, TITLE_DESC, DURATION_ASC, DURATION_DESC, DATE_ADDED_DESC
}

enum class ResizeMode {
    FIT, FILL, ZOOM
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

@OptIn(UnstableApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val playlistRepository: PlaylistRepository,
    private val mediaDao: MediaDao
) : AndroidViewModel(app) {

    // --- STATE PRESERVATION ---
    private var savedAudioState: AudioPlayerState? = null

    // --- ANALYTICS INTERNAL STATE ---
    // Tracks accumulated listening time for the CURRENT track to determine if it counts as a "play"
    private var currentTrackPlaytimeAccumulator = 0L
    private var hasLoggedCurrentTrack = false

    // --- THEMING STATE ---
    private val themes = mapOf(
        "blue" to AppThemeConfig("blue", Color(0xFF00E5FF), "DIGITAL WAVES", "Quick Mix"),
        "green" to AppThemeConfig("green", Color(0xFF22C55E), "ECO FREQUENCY", "Fresh Finds"),
        "orange" to AppThemeConfig("orange", Color(0xFFFF5500), "AMBER HORIZON", "Jump Back In")
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
        sharedPrefs.edit().putString("current_theme_id", themeId).apply()
    }

    fun toggleThemeMode() {
        val newMode = !_isDarkTheme.value
        _isDarkTheme.value = newMode
        sharedPrefs.edit().putBoolean("is_dark_mode", newMode).apply()
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

    val realtimeAnalytics = combine(
        _analyticsUpdateTrigger,
        _audioList,
        _videoList
    ) { _, audio, videos ->
        calculateAnalytics(audio + videos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RealtimeAnalytics())

    // --- MOVIES TAB STATE (Videos > 1 Hour) ---
    private val _movieSortOption = MutableStateFlow(SortOption.DATE_ADDED_DESC)
    val movieSortOption = _movieSortOption.asStateFlow()

    val moviesList = _videoList.map { list ->
        list.filter { it.duration >= 3600000 } // 1 Hour = 3,600,000 ms
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedMovies = combine(moviesList, _movieSortOption) { list, sort ->
        when(sort) {
            SortOption.TITLE_ASC -> list.sortedBy { it.title }
            SortOption.TITLE_DESC -> list.sortedByDescending { it.title }
            SortOption.DURATION_ASC -> list.sortedBy { it.duration }
            SortOption.DURATION_DESC -> list.sortedByDescending { it.duration }
            SortOption.DATE_ADDED_DESC -> list.sortedByDescending { it.id } // Proxy for latest
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    val continueWatchingList = combine(_videoList, mediaDao.getContinueWatching()) { videos, historyItems ->
        historyItems.mapNotNull { history ->
            val video = videos.find { it.id == history.mediaId }
            if (video != null) {
                video to history
            } else null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived State: Video Folders
    val videoFolders = _videoList.map { videos ->
        videos.groupBy { it.bucketId }.map { (bucketId, bucketVideos) ->
            VideoFolder(
                id = bucketId,
                name = bucketVideos.firstOrNull()?.bucketName ?: "Unknown",
                videoCount = bucketVideos.size,
                thumbnailUri = bucketVideos.firstOrNull()?.uri ?: Uri.EMPTY
            )
        }.sortedBy { it.name }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Playlist State
    val playlists = playlistRepository.playlistsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val audioPlaylists = playlists.map { list -> list.filter { !it.isVideo } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoPlaylists = playlists.map { list -> list.filter { it.isVideo } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search and Sort State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _albumSearchQuery = MutableStateFlow("")
    val albumSearchQuery = _albumSearchQuery.asStateFlow()

    private val _folderSearchQuery = MutableStateFlow("")
    val folderSearchQuery = _folderSearchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_ADDED_DESC)
    val sortOption = _sortOption.asStateFlow()

    val filteredAudioList = combine(_audioList, _searchQuery, _sortOption) { list, query, sort ->
        var result = list
        if (query.isNotEmpty()) {
            result = result.filter {
                it.title.contains(query, ignoreCase = true) ||
                        (it.artist?.contains(query, ignoreCase = true) == true)
            }
        }
        when(sort) {
            SortOption.TITLE_ASC -> result.sortedBy { it.title }
            SortOption.TITLE_DESC -> result.sortedByDescending { it.title }
            SortOption.DURATION_ASC -> result.sortedBy { it.duration }
            SortOption.DURATION_DESC -> result.sortedByDescending { it.duration }
            SortOption.DATE_ADDED_DESC -> result.sortedByDescending { it.id }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredAlbums = combine(_albums, _albumSearchQuery) { list, query ->
        if (query.isEmpty()) list
        else list.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(
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



    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionUpdateJob: Job? = null

    // --- BOOKMARKS FLOW ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentBookmarks = _currentTrack.flatMapLatest { track ->
        if (track != null) {
            mediaDao.getBookmarks(track.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- FAVORITES FLOW (Is Current Track Liked?) ---
    val isCurrentTrackFavorite = combine(_currentTrack, playlists) { track, allPlaylists ->
        if (track == null) return@combine false
        val favPlaylist = allPlaylists.find { it.name == "Favorites" && !it.isVideo }
        favPlaylist != null && favPlaylist.mediaIds.contains(track.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Track current position in display queue (for highlighting current track in shuffled view)
    val displayQueueIndex = combine(_currentTrack, _displayQueue) { track, queue ->
        if (track == null) null
        else queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateAlbumSearchQuery(query: String) { _albumSearchQuery.value = query }
    fun updateFolderSearchQuery(query: String) { _folderSearchQuery.value = query }
    fun updateSortOption(option: SortOption) { _sortOption.value = option }

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

    fun selectAll(ids: List<Long>) { _selectedMediaIds.value = ids.toSet() }

    // --- Deletion Logic ---
    fun deleteSelectedMedia() {
        val idsToDelete = _selectedMediaIds.value.toList()
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val allMedia = _videoList.value + _audioList.value
            val filesToDelete = allMedia.filter { idsToDelete.contains(it.id) }
            val uris = filesToDelete.map { it.uri }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(app.contentResolver, uris)
                _deleteIntentEvent.emit(pendingIntent.intentSender)
            } else {
                try {
                    for (file in filesToDelete) app.contentResolver.delete(file.uri, null, null)
                    onDeleteSuccess(idsToDelete)
                } catch (e: Exception) { e.printStackTrace() }
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
                    playlistRepository.updatePlaylistTracks(pl.id, pl.mediaIds.filter { !ids.contains(it) })
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
                BookmarkEntity(
                    mediaId = track.id,
                    timestamp = timestamp,
                    label = label
                )
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaDao.deleteBookmark(id)
        }
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
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                _player.value = controller
                setupPlayerListener(controller)

                if (controller != null) {
                    _isPlaying.value = controller.isPlaying
                    _isShuffleEnabled.value = controller.shuffleModeEnabled
                    _repeatMode.value = controller.repeatMode
                    _playbackSpeed.value = controller.playbackParameters.speed
                    updateCurrentTrackFromPlayer(controller)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener(controller: MediaController?) {
        controller?.addListener(object : Player.Listener {
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

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentTrackFromPlayer(controller)
                // Logic moved to heartbeat to ensure duration threshold
            }
        })
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
            val track = _audioList.value.find { it.id == id } ?: _videoList.value.find { it.id == id }
            _currentTrack.value = track
            _currentIndex.value = controller.currentMediaItemIndex

            // Reset Analytics Accumulator for the new track
            currentTrackPlaytimeAccumulator = 0L
            hasLoggedCurrentTrack = false

            // Fix: Only persist queue index if NOT video.
            // This prevents video playback from overwriting the last played music position in the persisted queue.
            if (track != null && !track.isVideo) {
                persistQueueIndex(controller.currentMediaItemIndex)
            }
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = viewModelScope.launch {
            var saveCounter = 0
            val today = getNormalizedToday()

            // Initialize today's row if missing
            mediaDao.initDailyPlaytime(today)

            // Accumulator for playtime logic
            var accumulatedPlaytime = 0L
            val updateInterval = 500L

            while (isActive) {
                _player.value?.let { player ->
                    val pos = player.currentPosition
                    _currentPosition.value = pos
                    val dur = player.duration.coerceAtLeast(0L)
                    _duration.value = dur

                    // ACCUMULATE PLAYTIME
                    if (_isPlaying.value) {
                        // 1. Total Daily Playtime (Existing)
                        accumulatedPlaytime += updateInterval

                        // 2. Track Play Count Threshold Logic (New)
                        // Ensures we only count a "Play" if user listened for 30s or 50% of track (if short)
                        if (!hasLoggedCurrentTrack) {
                            currentTrackPlaytimeAccumulator += updateInterval

                            val threshold = if (dur > 0) min(30000L, dur / 2) else 30000L
                            val safeThreshold = max(5000L, threshold) // Minimum 5s even for very short clips

                            if (currentTrackPlaytimeAccumulator >= safeThreshold) {
                                _currentTrack.value?.let { track ->
                                    recordPlay(track.id)
                                    hasLoggedCurrentTrack = true
                                }
                            }
                        }
                    }

                    // Flush to DB every 30 seconds (60 ticks)
                    if (saveCounter % 60 == 0) {
                        if (accumulatedPlaytime > 0) {
                            mediaDao.addToDailyPlaytime(getNormalizedToday(), accumulatedPlaytime)
                            accumulatedPlaytime = 0
                            // Notify UI to refresh stats
                            _analyticsUpdateTrigger.emit(System.currentTimeMillis())
                        }
                    }

                    // Save playback position periodically
                    if (saveCounter % 10 == 0) {
                        _currentTrack.value?.let { track ->
                            savePlaybackState(track.id, pos, track.duration, track.isVideo)
                        }
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
        viewModelScope.launch(Dispatchers.IO) {
            mediaDao.saveHistory(
                PlaybackHistory(
                    mediaId = mediaId,
                    position = position,
                    duration = duration,
                    timestamp = System.currentTimeMillis(),
                    mediaType = if (isVideo) "VIDEO" else "AUDIO"
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
                _videoList.value = videos
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
        }
    }

    // --- Persistent Queue Logic ---
    private suspend fun restoreQueue(allMedia: List<MediaFile>) {
        val savedQueueItems = mediaDao.getSavedQueue()
        if (savedQueueItems.isNotEmpty()) {
            val restoredQueue = savedQueueItems.mapNotNull { item ->
                allMedia.find { it.id == item.mediaId }
            }
            if (restoredQueue.isNotEmpty()) {
                _currentQueue.value = restoredQueue

                // Restore Index from Prefs
                val savedIndex = sharedPrefs.getInt("last_queue_index", 0)
                val safeIndex = savedIndex.coerceIn(0, restoredQueue.size - 1)
                _currentIndex.value = safeIndex

                // Restore UI state
                val track = restoredQueue[safeIndex]
                _currentTrack.value = track

                // Fetch last playback position to resume
                var startPos = 0L
                val history = mediaDao.getHistory(track.id)
                if (history != null) {
                    // Resume unless track was basically finished (e.g. > 99%)
                    if (history.duration == 0L || history.position < (history.duration * 0.99)) {
                        startPos = history.position
                    }
                }

                // Set to player
                withContext(Dispatchers.Main) {
                    _player.value?.let { controller ->
                        if (controller.mediaItemCount == 0) {
                            val items = restoredQueue.map { it.toMediaItem() }
                            // Restore queue at correct index and position
                            controller.setMediaItems(items, safeIndex, startPos)
                            controller.prepare()
                            // Don't call play() on restore
                        }
                    }
                }
            }
        }
    }

    private fun persistQueue(queue: List<MediaFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            val entities = queue.mapIndexed { index, media ->
                QueueItemEntity(media.id, index)
            }
            mediaDao.replaceQueue(entities)
        }
    }

    private fun persistQueueIndex(index: Int) {
        sharedPrefs.edit().putInt("last_queue_index", index).apply()
    }

    // --- Query Methods (Unchanged) ---
    private fun queryMedia(isVideo: Boolean): List<MediaFile> {
        val mediaList = mutableListOf<MediaFile>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= 29) {
            if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = if (isVideo) {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
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
            app.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media._ID else MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Audio.Media.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION)
                val artistColumn = if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST) else -1
                val albumIdColumn = if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID) else -1

                val bucketIdColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID) else -1
                val bucketNameColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME) else -1
                val sizeColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.SIZE) else -1
                val widthColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.WIDTH) else -1
                val heightColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT) else -1

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

                    if (isVideo) {
                        bucketId = if(bucketIdColumn != -1) cursor.getString(bucketIdColumn) ?: "" else ""
                        bucketName = if(bucketNameColumn != -1) cursor.getString(bucketNameColumn) ?: "Unknown" else "Unknown"
                        size = if(sizeColumn != -1) cursor.getLong(sizeColumn) else 0

                        val width = if(widthColumn != -1) cursor.getInt(widthColumn) else 0
                        val height = if(heightColumn != -1) cursor.getInt(heightColumn) else 0

                        resolution = if(height >= 2160) "4K"
                        else if(height >= 1080) "1080P"
                        else if(height >= 720) "720P"
                        else if(height >= 480) "480P"
                        else if (height > 0) "${height}P"
                        else ""
                    } else {
                        artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        albumId = cursor.getLong(albumIdColumn)
                        val sArtworkUri = "content://media/external/audio/albumart".toUri()
                        albumArtUri = ContentUris.withAppendedId(sArtworkUri, albumId)
                    }

                    mediaList.add(MediaFile(id, contentUri, name, artist, duration, isVideo, false, albumArtUri, albumId, bucketId, bucketName, size, resolution))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return mediaList
    }

    private fun queryImages(): List<MediaFile> {
        val imageList = mutableListOf<MediaFile>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        try {
            app.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Unknown Image"
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    imageList.add(MediaFile(id = id, uri = contentUri, title = name, artist = null, duration = 0, isVideo = false, isImage = true, albumArtUri = null, albumId = -1))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return imageList
    }

    private fun queryAlbums(): List<Album> {
        val albumList = mutableListOf<Album>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Albums.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.NUMBER_OF_SONGS, MediaStore.Audio.Albums.FIRST_YEAR)
        try {
            app.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
                val countColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
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
        } catch (e: Exception) { e.printStackTrace() }
        return albumList
    }

    // --- Playlist Management ---
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

        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.renamePlaylist(id, newName)
        }
    }

    fun deletePlaylist(playlistId: String) = viewModelScope.launch(Dispatchers.IO) { playlistRepository.deletePlaylist(playlistId) }
    fun addSongToPlaylist(playlistId: String, mediaId: Long) = viewModelScope.launch(Dispatchers.IO) { playlistRepository.addSongToPlaylist(playlistId, mediaId) }
    fun removeSongFromPlaylist(playlistId: String, mediaId: Long) = viewModelScope.launch(Dispatchers.IO) { playlistRepository.removeSongFromPlaylist(playlistId, mediaId) }
    fun toggleAlbumInFavorites(albumSongs: List<MediaFile>) {
        val favPlaylist = playlists.value.find { it.name == "Favorites" && !it.isVideo } ?: return
        val allInFav = albumSongs.all { favPlaylist.mediaIds.contains(it.id) }
        val newMediaIds = favPlaylist.mediaIds.toMutableList()
        if (allInFav) albumSongs.forEach { newMediaIds.remove(it.id) } else albumSongs.forEach { if (!newMediaIds.contains(it.id)) newMediaIds.add(it.id) }
        viewModelScope.launch(Dispatchers.IO) { playlistRepository.updatePlaylistTracks(favPlaylist.id, newMediaIds) }
    }

    // --- Playback Logic ---
    fun playMedia(media: MediaFile) {
        if (media.isVideo) {
            playVideo(media) // Redirect to new video handler
        } else if (!media.isImage) {
            val currentVisibleList = filteredAudioList.value.takeIf { it.isNotEmpty() } ?: _audioList.value
            val startIndex = currentVisibleList.indexOfFirst { it.id == media.id }
            if (startIndex >= 0) setQueue(currentVisibleList, startIndex, false)
        }
    }

    private fun playVideo(media: MediaFile) {
        // Use folder context as default - find all videos in same folder
        val folderVideos = _videoList.value.filter { it.bucketId == media.bucketId }
        val contextList = if (folderVideos.size > 1) folderVideos else listOf(media)
        playVideoFromList(media, contextList)
    }

    /**
     * Play a video from a context list (folder videos or playlist).
     * Sets the full list as the queue so next/prev navigation works.
     */
    fun playVideoFromList(media: MediaFile, list: List<MediaFile>) {
        if (!media.isVideo) return

        // Snapshot Audio State if we are interrupting an active audio session
        val current = _currentTrack.value
        if (_currentQueue.value.isNotEmpty() && current?.isVideo != true) {
            savedAudioState = AudioPlayerState(
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

        viewModelScope.launch(Dispatchers.IO) {
            val history = mediaDao.getHistory(media.id)
            val startPos = if (history != null && history.position < (history.duration * 0.95)) history.position else 0L
            val startIndex = list.indexOfFirst { it.id == media.id }.coerceAtLeast(0)

            withContext(Dispatchers.Main) {
                setQueue(list, startIndex, false, startPos)
            }
        }
    }

    /**
     * Call this when the Video Player screen is closed.
     * It saves the video position and restores the previous music session.
     */
    fun closeVideo() {
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
    fun setQueue(mediaList: List<MediaFile>, startIndex: Int, shuffle: Boolean = false, startPosition: Long = 0L) {
        // Update Local State
        _currentQueue.value = mediaList
        _isShuffleEnabled.value = shuffle

        // Pass entire queue to Controller
        _player.value?.let { controller ->
            val mediaItems = mediaList.map { it.toMediaItem() }
            controller.setMediaItems(mediaItems, startIndex, startPosition)
            controller.shuffleModeEnabled = shuffle
            controller.prepare()
            controller.play()
        }

        // Update display queue after controller is set up
        updateDisplayQueue()

        // Persist
        persistQueue(mediaList)
        persistQueueIndex(startIndex)
    }

    /**
     * Updates the display queue to reflect the shuffled playback order.
     * When shuffle is disabled, displays the original queue order.
     * When shuffle is enabled, builds the queue order based on Media3's shuffle timeline.
     */
    private fun updateDisplayQueue() {
        val controller = _player.value
        val originalQueue = _currentQueue.value

        if (controller == null || originalQueue.isEmpty()) {
            _displayQueue.value = originalQueue
            return
        }

        if (!controller.shuffleModeEnabled) {
            _displayQueue.value = originalQueue
            return
        }

        // Build shuffled queue from Media3's timeline - starting from current position
        val shuffledList = mutableListOf<MediaFile>()
        val count = controller.mediaItemCount
        val currentIdx = controller.currentMediaItemIndex

        // First, add tracks from current position to end (in shuffle order)
        var idx = currentIdx
        val visited = mutableSetOf<Int>()

        while (idx in 0 until count && !visited.contains(idx)) {
            visited.add(idx)
            val mediaId = controller.getMediaItemAt(idx).mediaId.toLongOrNull()
            val track = originalQueue.find { it.id == mediaId }
            if (track != null) shuffledList.add(track)
            
            // Get next in shuffle sequence
            val nextIdx = controller.nextMediaItemIndex
            if (nextIdx == -1 || visited.contains(nextIdx)) break
            idx = nextIdx
        }

        // Add remaining tracks that weren't visited (before current in shuffle sequence)
        for (i in 0 until count) {
            if (!visited.contains(i)) {
                val mediaId = controller.getMediaItemAt(i).mediaId.toLongOrNull()
                val track = originalQueue.find { it.id == mediaId }
                if (track != null) shuffledList.add(track)
            }
        }

        _displayQueue.value = shuffledList
    }

    /**
     * Play a specific track from the queue (handles both shuffled and non-shuffled modes).
     * Finds the track in the controller's timeline and seeks to it.
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
        val metadata = MediaMetadata.Builder()
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
    fun toggleLock() { _isPlayerLocked.value = !_isPlayerLocked.value }
    fun toggleResizeMode() {
        val modes = ResizeMode.values()
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
    fun setPipMode(isPip: Boolean) { _isInPipMode.value = isPip }

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

    fun togglePlayPause() { _player.value?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun toggleShuffle() {
        _player.value?.let {
            val newMode = !it.shuffleModeEnabled
            it.shuffleModeEnabled = newMode
            _isShuffleEnabled.value = newMode
            updateDisplayQueue()
        }
    }
    fun toggleRepeat() {
        _player.value?.let {
            val newMode = when (it.repeatMode) {
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
    fun rewind() { _player.value?.let { it.seekTo((it.currentPosition - 10000).coerceAtLeast(0)) } }
    fun forward() { _player.value?.let { it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration)) } }
    fun hasNext(): Boolean {
        return _player.value?.hasNextMediaItem() ?: false
    }
    fun hasPrevious(): Boolean {
        return _player.value?.hasPreviousMediaItem() ?: false
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}