package com.local.offlinemediaplayer.viewmodel

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.local.offlinemediaplayer.R
import com.local.offlinemediaplayer.audio.AudioEffectsManager
import com.local.offlinemediaplayer.data.AppPreferencesManager
import com.local.offlinemediaplayer.data.ThumbnailManager
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.data.db.PlaybackHistory
import com.local.offlinemediaplayer.data.db.QueueItemEntity
import com.local.offlinemediaplayer.model.Album
import com.local.offlinemediaplayer.model.AppError
import com.local.offlinemediaplayer.model.AudioPlayerState
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.model.Playlist
import com.local.offlinemediaplayer.model.UserMessage
import com.local.offlinemediaplayer.playback.BookmarkManager
import com.local.offlinemediaplayer.playback.DeletionKind
import com.local.offlinemediaplayer.playback.MediaControllerBinder
import com.local.offlinemediaplayer.playback.MediaDeletionHandler
import com.local.offlinemediaplayer.playback.PlaybackAnalyticsTracker
import com.local.offlinemediaplayer.playback.QueuePersistence
import com.local.offlinemediaplayer.playback.QueuePolicy
import com.local.offlinemediaplayer.repository.MediaRepository
import com.local.offlinemediaplayer.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

// Legacy combined field+direction sort enums. Superseded by SortState (see
// Sorting.kt); retained only so persisted preference ordinals can be migrated
// in LibraryViewModel. Do not reorder or remove entries.
enum class SortOption {
    TITLE_ASC,
    TITLE_DESC,
    DURATION_ASC,
    DURATION_DESC,
    DATE_ADDED_DESC,
    MOST_PLAYED,
}

enum class AlbumSortOption {
    NAME_ASC,
    ARTIST_ASC,
    YEAR_DESC,
    SONG_COUNT_DESC,
}

enum class ResizeMode {
    FIT,
    FILL,
    ZOOM,
}

// Data class for UI consumption
data class RealtimeAnalytics(
    val todayPlaytimeMinutes: Int = 0,
    val weekPlaytimeMinutes: Int = 0,
    val avgDailyMinutes: Int = 0,
    val streakDays: Int = 0,
    val currentFavorite: MediaFile? = null,
    val allTimeFavorite: MediaFile? = null,
    val currentFavoritePlayCount: Int = 0,
    val allTimeFavoritePlayCount: Int = 0,
)

// Data class for audio/subtitle track info
data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val language: String?,
    val isSelected: Boolean,
)

@OptIn(UnstableApi::class)
@HiltViewModel
class PlaybackViewModel
    @Inject
    constructor(
        private val playlistRepository: PlaylistRepository,
        private val mediaDao: MediaDao,
        private val thumbnailManager: ThumbnailManager,
        private val mediaRepository: MediaRepository,
        val audioEffects: AudioEffectsManager,
        private val mediaControllerBinder: MediaControllerBinder,
        private val analytics: PlaybackAnalyticsTracker,
        private val deletionHandler: MediaDeletionHandler,
        private val bookmarks: BookmarkManager,
        private val queuePersistence: QueuePersistence,
        private val appPrefs: AppPreferencesManager,
    ) : ViewModel() {
        companion object {
            private const val TAG = "PlaybackViewModel"
            private const val REWIND_THRESHOLD_MS = 3000L
            private const val SEEK_DELTA_MS = 10_000L
            private const val VIDEO_COMPLETION_THRESHOLD = 0.95
            private const val AUDIO_COMPLETION_THRESHOLD = 0.99
            private const val DAY_IN_MILLIS = 86_400_000L
            private const val PLAY_COUNT_THRESHOLD_MS = 30_000L
            private const val MIN_PLAY_THRESHOLD_MS = 5_000L
            private const val POSITION_UPDATE_INTERVAL_MS = 500L
        }

        // --- STATE PRESERVATION ---
        private var savedAudioState: AudioPlayerState? = null

        // --- ANALYTICS INTERNAL STATE ---

        // --- PENDING TRACK RESTORATION ---
        private var pendingAudioTrackIndex: Int = -1
        private var pendingSubtitleTrackIndex: Int = -1

        // --- VIDEO BRIGHTNESS ---
        // User-set video player brightness (0.01f..1f). BRIGHTNESS_UNSET is a sentinel meaning
        // "never set / follow the system brightness" so we don't override on first launch.
        // Starts on the sentinel and is hydrated from DataStore below (P5-C.3); a video cannot be
        // on screen before that read completes, so the stored value still lands before it is used.
        private val _videoBrightness = MutableStateFlow(AppPreferencesManager.BRIGHTNESS_UNSET)
        val videoBrightness = _videoBrightness.asStateFlow()

        fun setVideoBrightness(value: Float) {
            val clamped = value.coerceIn(0.01f, 1f)
            _videoBrightness.value = clamped
            viewModelScope.launch { appPrefs.setVideoBrightness(clamped) }
        }

        // Media Lists

        val audioList = mediaRepository.audioList

        private val _imageList = MutableStateFlow<List<MediaFile>>(emptyList())
        val imageList = _imageList.asStateFlow()

        private val _albums = MutableStateFlow<List<Album>>(emptyList())
        val albums = _albums.asStateFlow()

        // --- REFRESH STATE ---
        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        // --- QUEUE STATE ---
        private val _currentQueue = MutableStateFlow<List<MediaFile>>(emptyList())
        val currentQueue = _currentQueue.asStateFlow()

        private val _currentIndex = MutableStateFlow<Int?>(null)
        val currentIndex = _currentIndex.asStateFlow()

        // Tracks if current playback was initiated from a specific playlist (to prevent autoFill from adding random library songs)
        private val _currentPlaylistContext = MutableStateFlow<String?>(null)
        val currentPlaylistContext = _currentPlaylistContext.asStateFlow()

        // Display queue for UI - reflects shuffled order when shuffle is enabled
        private val _displayQueue = MutableStateFlow<List<MediaFile>>(emptyList())
        val displayQueue = _displayQueue.asStateFlow()

        // Playlist State
        val playlists =
            playlistRepository.playlistsFlow.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        val audioPlaylists =
            playlists
                .map { list -> list.filter { !it.isVideo } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val videoPlaylists =
            playlists
                .map { list -> list.filter { it.isVideo } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Player State
        // The connection itself lives in MediaControllerBinder (P4-E.1). This ViewModel keeps
        // interpreting the controller, but no longer owns building or releasing it.
        val player: StateFlow<MediaController?> = mediaControllerBinder.controller

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

        // --- PLAYBACK STATE FOR UI ---
        private val _isBuffering = MutableStateFlow(false)
        val isBuffering = _isBuffering.asStateFlow()

        // Error state: null means no error. Non-null is a user-facing error message.
        // Errors carry AppError; the UI resolves the wording (P4-F.2).
        private val _playerError = MutableStateFlow<AppError?>(null)
        val playerError = _playerError.asStateFlow()

        // --- VIDEO PLAYER VISIBILITY STATE ---
        // Explicitly tracks if the fullscreen player should be shown.
        // This decouples "Current Track is Video" from "Show Player".
        private val _isVideoPlayerVisible = MutableStateFlow(false)
        val isVideoPlayerVisible = _isVideoPlayerVisible.asStateFlow()

        // --- NAVIGATION STATE ---
        private val _navigateToPlayer = MutableStateFlow(false)
        val navigateToPlayer = _navigateToPlayer.asStateFlow()

        // Informational confirmations, distinct from errors -- see model/UserMessage.kt.
        private val _userMessage = MutableSharedFlow<UserMessage>()
        val userMessage = _userMessage.asSharedFlow()

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
        fun shouldEnterPipMode(): Boolean = _currentTrack.value?.isVideo == true && _isPlaying.value

        private var positionUpdateJob: Job? = null

        // --- BOOKMARKS FLOW --- (owned by BookmarkManager as of P4-E.5)
        val currentBookmarks =
            bookmarks
                .bookmarksFor(_currentTrack.map { it?.id })
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // --- FAVORITES FLOW (Is Current Track Liked?) ---
        val isCurrentTrackFavorite =
            combine(_currentTrack, playlists) { track, allPlaylists ->
                if (track == null) return@combine false
                val favPlaylist =
                    allPlaylists.find { it.name == "Favorites" && !it.isVideo }
                favPlaylist != null && favPlaylist.mediaIds.contains(track.id)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        // --- LAST PLAYED AUDIO FLOW ---
        val lastPlayedAudio =
            combine(audioList, mediaDao.getLastPlayedAudioFlow()) { audioFiles, history ->
                if (history != null) {
                    audioFiles.find { it.id == history.mediaId }
                } else {
                    null
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        // Track current position in display queue (for highlighting current track in shuffled view)
        val displayQueueIndex =
            combine(_currentTrack, _displayQueue) { track, queue ->
                if (track == null) {
                    null
                } else {
                    queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        // Human-readable label for WHERE the current queue is playing from (playlist / album /
        // artist / library). Derived from the persisted playlist context so the Now Playing
        // screen can show the real source instead of the track's artist.
        val queueSourceLabel =
            combine(_currentPlaylistContext, playlists, _albums) { context, allPlaylists, albumList ->
                when {
                    context == null -> "Library"
                    context.startsWith("ALBUM_") -> {
                        val albumId = context.removePrefix("ALBUM_").toLongOrNull()
                        albumList.find { it.id == albumId }?.name ?: "Album"
                    }
                    context.startsWith("ARTIST_") -> context.removePrefix("ARTIST_")
                    context.startsWith("SMART_") ->
                        SmartPlaylistType.fromId(context.removePrefix("SMART_"))?.title
                            ?: "Smart Playlist"
                    else -> allPlaylists.find { it.id == context }?.name ?: "Playlist"
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Library")

        // The scoped-storage consent round-trip lives in MediaDeletionHandler (P4-E.3).
        val deleteIntentEvent = deletionHandler.deleteIntentEvent

        init {
            bindMediaController()
            viewModelScope.launch { _videoBrightness.value = appPrefs.getVideoBrightness() }
            viewModelScope.launch(Dispatchers.IO) {
                playlistRepository.migrateLegacyData()
                playlistRepository.ensureDefaultPlaylists()
            }

            // Sync queue with library deletions in real-time
            viewModelScope.launch {
                audioList.collect { allAudio ->
                    val currentQueueList = _currentQueue.value
                    if (currentQueueList.isEmpty()) return@collect

                    val allIds = allAudio.map { it.id }.toSet()
                    val updatedQueue = currentQueueList.filter { it.id in allIds }

                    if (updatedQueue.size != currentQueueList.size) {
                        _currentQueue.value = updatedQueue
                        _displayQueue.value = updatedQueue

                        withContext(Dispatchers.Main) {
                            player.value?.let { controller ->
                                val itemsToRemove = mutableListOf<Int>()
                                for (i in 0 until controller.mediaItemCount) {
                                    val mId = controller.getMediaItemAt(i).mediaId.toLongOrNull()
                                    if (mId != null && mId !in allIds) {
                                        itemsToRemove.add(i)
                                    }
                                }
                                if (itemsToRemove.isNotEmpty()) {
                                    itemsToRemove.reversed().forEach { idx ->
                                        controller.removeMediaItem(idx)
                                    }
                                    updateCurrentTrackFromPlayer(controller)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Image Deletion ---
        private val _pendingImageDeleteId = MutableStateFlow<Long?>(null)

        fun deleteImage(image: MediaFile) {
            viewModelScope.launch(Dispatchers.IO) {
                _pendingImageDeleteId.value = image.id
                deletionHandler.requestDelete(
                    kind = DeletionKind.IMAGE,
                    uri = image.uri,
                    deleted = { completeImageDelete() },
                    failed = { onLegacyImageDeleteFailed() },
                )
            }
        }

        private suspend fun onLegacyImageDeleteFailed() {
            _pendingImageDeleteId.value = null
            _userMessage.emit(UserMessage.of(R.string.error_delete_failed))
        }

        fun onImageDeleteSuccess() {
            viewModelScope.launch(Dispatchers.IO) {
                deletionHandler.onDeleteConfirmed(DeletionKind.IMAGE)
            }
        }

        private fun completeImageDelete() {
            val id = _pendingImageDeleteId.value ?: return
            _imageList.value = _imageList.value.filter { it.id != id }
            _pendingImageDeleteId.value = null
        }

        /** Call when the user cancels the system delete dialog. */
        fun onDeleteCancelled() {
            deletionHandler.onDeleteCancelled()
            _pendingImageDeleteId.value = null
            _pendingDeleteTrackId.value = null
        }

        // --- Delete Current Track ---
        private val _pendingDeleteTrackId = MutableStateFlow<Long?>(null)
        private val _onDeleteTrackComplete = MutableSharedFlow<Unit>()
        val onDeleteTrackComplete = _onDeleteTrackComplete.asSharedFlow()

        fun deleteCurrentTrack() {
            val track = _currentTrack.value ?: return
            viewModelScope.launch(Dispatchers.IO) {
                _pendingDeleteTrackId.value = track.id
                deletionHandler.requestDelete(
                    kind = DeletionKind.TRACK,
                    uri = track.uri,
                    deleted = { completeCurrentTrackDelete() },
                    failed = { onLegacyTrackDeleteFailed() },
                )
            }
        }

        private suspend fun onLegacyTrackDeleteFailed() {
            _pendingDeleteTrackId.value = null
            _userMessage.emit(UserMessage.of(R.string.error_delete_failed))
        }

        fun onCurrentTrackDeleteSuccess() {
            viewModelScope.launch(Dispatchers.IO) {
                deletionHandler.onDeleteConfirmed(DeletionKind.TRACK)
            }
        }

        private fun completeCurrentTrackDelete() {
            val id = _pendingDeleteTrackId.value ?: return
            viewModelScope.launch {
                // 1. Sync with MediaRepository and clean up database tables (playlists, history, etc.)
                mediaRepository.removeMediaIds(listOf(id))
                playlistRepository.cleanupDeletedMedia(listOf(id))

                // 2. Handle queue: remove the deleted track while keeping the rest of the queue intact
                val queue = _currentQueue.value.toMutableList()
                val deletedIndex = queue.indexOfFirst { it.id == id }

                if (deletedIndex >= 0) {
                    // Update local state and persistence
                    queue.removeAt(deletedIndex)
                    _currentQueue.value = queue
                    _displayQueue.value = queue
                    persistQueue(queue)

                    withContext(Dispatchers.Main) {
                        player.value?.let { controller ->
                            if (deletedIndex < controller.mediaItemCount) {
                                // Safe removal: ExoPlayer handles transition to next track automatically
                                controller.removeMediaItem(deletedIndex)
                            }

                            // Handle empty queue case
                            if (controller.mediaItemCount == 0) {
                                _currentTrack.value = null
                                _isPlaying.value = false
                                _currentIndex.value = null
                            } else {
                                // Sync UI state for track/index shifts
                                updateCurrentTrackFromPlayer(controller)
                            }
                        }
                    }
                }

                _pendingDeleteTrackId.value = null
                _onDeleteTrackComplete.emit(Unit)
            }
        }

        // --- Bookmark Management ---
        fun addBookmark(
            timestamp: Long,
            label: String,
        ) {
            val track = _currentTrack.value ?: return
            bookmarks.addBookmark(track.id, timestamp, label)
        }

        fun deleteBookmark(id: Long) = bookmarks.deleteBookmark(id)

        // --- Favorite Management ---
        fun toggleFavorite() {
            val track = _currentTrack.value ?: return
            val isFav = isCurrentTrackFavorite.value

            viewModelScope.launch(Dispatchers.IO) {
                // Resolve (or create) the Favorites playlist atomically against the DB — no flow race.
                val favId = playlistRepository.getOrCreatePlaylistId("Favorites", false)
                if (isFav) {
                    playlistRepository.removeSongFromPlaylist(favId, track.id)
                } else {
                    playlistRepository.addSongToPlaylist(favId, track.id)
                }
            }
        }

        // --- Player Initialization ---

        /**
         * Asks [MediaControllerBinder] to connect, then attaches this ViewModel's listener and seeds
         * UI state from the controller as soon as one is available.
         *
         * Collected on [viewModelScope], i.e. Dispatchers.Main.immediate, so `addListener` still runs
         * on the main thread exactly as it did when this lived inside the future callback. Because
         * `controller` is a StateFlow, a controller that connected before this collector started is
         * delivered immediately rather than missed.
         */
        private fun bindMediaController() {
            mediaControllerBinder.connect()
            mediaControllerBinder.controller
                .filterNotNull()
                .onEach { controller ->
                    setupPlayerListener(controller)
                    _isPlaying.value = controller.isPlaying
                    _isShuffleEnabled.value = controller.shuffleModeEnabled
                    _repeatMode.value = controller.repeatMode
                    _playbackSpeed.value = controller.playbackParameters.speed
                    _videoSize.value = controller.videoSize
                    updateCurrentTrackFromPlayer(controller)
                }.launchIn(viewModelScope)
        }

        private val _videoSize = MutableStateFlow(androidx.media3.common.VideoSize.UNKNOWN)
        val videoSize = _videoSize.asStateFlow()

        /** Remembered so a re-emitted controller cannot accumulate duplicate listeners. */
        private var playerListener: Player.Listener? = null

        private fun setupPlayerListener(controller: MediaController?) {
            if (controller == null) return
            playerListener?.let { controller.removeListener(it) }
            val listener =
                object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        _videoSize.value = videoSize
                    }

                    override fun onTimelineChanged(
                        timeline: androidx.media3.common.Timeline,
                        reason: Int,
                    ) {
                        updateDisplayQueue()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _isBuffering.value = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_READY) {
                            _playerError.value = null // Clear any previous error on successful load
                            _duration.value = controller.duration.coerceAtLeast(0L)
                        } else if (playbackState == Player.STATE_ENDED) {
                            if (_isShuffleEnabled.value && _currentPlaylistContext.value != null) {
                                // Shuffled playlist/album reached end — re-shuffle and restart
                                // so all songs play again in a new order instead of stopping.
                                reshuffleAndRestart()
                            } else {
                                autoFillQueue(playNext = true)
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _playerError.value = AppError.PlaybackFailed.fromErrorCode(error.errorCode)
                        _isBuffering.value = false
                        Log.e(TAG, "Player error: ${error.errorCode} - ${error.message}", error)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _isShuffleEnabled.value = shuffleModeEnabled
                        persistShuffleMode(shuffleModeEnabled)
                        updateDisplayQueue()
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
                        persistRepeatMode(repeatMode)
                    }

                    override fun onPlaybackParametersChanged(
                        playbackParameters: androidx.media3.common.PlaybackParameters,
                    ) {
                        _playbackSpeed.value = playbackParameters.speed
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int,
                    ) {
                        // Skip detection: a manual jump (SEEK, i.e. next/previous) away from an audio
                        // track that had NOT yet crossed the play-count threshold counts as a skip.
                        // Auto-advance (AUTO), repeat (REPEAT) and new queues (PLAYLIST_CHANGED) are
                        // deliberately excluded so natural end-of-track playback is never a skip.
                        // _currentTrack still holds the OUTGOING track here, before it is replaced.
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK &&
                            analytics.isCurrentTrackUnlogged
                        ) {
                            _currentTrack.value?.let { outgoing ->
                                if (!outgoing.isVideo) analytics.recordSkip(outgoing.id)
                            }
                        }
                        updateCurrentTrackFromPlayer(controller)
                        // Play-count logic stays in the heartbeat to ensure the duration threshold
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
            playerListener = listener
            controller.addListener(listener)
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
                val track = mediaRepository.mediaById.value[id]
                _currentTrack.value = track
                _currentIndex.value = controller.currentMediaItemIndex

                // Reset analytics accumulators for the new track (P4-E.2).
                analytics.onTrackChanged()

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
                    analytics.onSessionStarted()

                    val updateInterval = POSITION_UPDATE_INTERVAL_MS

                    while (isActive) {
                        player.value?.let { player ->
                            try {
                                val pos = player.currentPosition
                                _currentPosition.value = pos
                                val dur = player.duration.coerceAtLeast(0L)
                                _duration.value = dur

                                // Analytics (play counts + daily playtime) live in
                                // PlaybackAnalyticsTracker as of P4-E.2. Only ticks where playback
                                // is actually playing accrue time.
                                if (_isPlaying.value) {
                                    analytics.onPositionUpdate(_currentTrack.value?.id, dur, updateInterval)
                                }

                                // Save playback position periodically
                                if (saveCounter % 10 == 0) {
                                    val track = _currentTrack.value
                                    if (track != null) {
                                        savePlaybackState(
                                            track.id,
                                            pos,
                                            track.duration,
                                            track.isVideo,
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in position update loop", e)
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
            // Persist listening time accrued since the last flush before the loop is cancelled;
            // without this it was discarded on every stop (F-34).
            analytics.onSessionStopped()
            positionUpdateJob?.cancel()
            positionUpdateJob = null
        }

        private fun savePlaybackState(
            mediaId: Long,
            position: Long,
            duration: Long,
            isVideo: Boolean,
        ) {
            // Get current track selections
            val audioIndex = if (isVideo) getAudioTracks().indexOfFirst { it.isSelected } else -1
            val subtitleIndex =
                if (isVideo) {
                    if (areSubtitlesDisabled()) {
                        -2
                    } else {
                        getSubtitleTracks().indexOfFirst { it.isSelected }
                    }
                } else {
                    -1
                }

            viewModelScope.launch(Dispatchers.IO) {
                mediaDao.saveHistory(
                    PlaybackHistory(
                        mediaId = mediaId,
                        position = position,
                        duration = duration,
                        timestamp = System.currentTimeMillis(),
                        mediaType = if (isVideo) "VIDEO" else "AUDIO",
                        audioTrackIndex = audioIndex,
                        subtitleTrackIndex = subtitleIndex,
                    ),
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

        // --- Media Loading ---
        fun scanMedia() {
            if (_isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                try {
                    // Delegate to MediaRepository which handles querying & thumbnail caching
                    val (videos, audio) = mediaRepository.scanMedia()

                    // Sync local state from repository for images/albums which are still internally managed
                    _imageList.value = mediaRepository.imageList.value
                    _albums.value = mediaRepository.albums.value

                    // RESTORE QUEUE AFTER LOADING
                    // Only restore if queue is empty to avoid disrupting playback on refresh
                    if (_currentQueue.value.isEmpty()) {
                        restoreQueue(audio + videos)
                    }
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        // --- Persistent Queue Logic ---
        private suspend fun restoreQueue(allMedia: List<MediaFile>) {
            val mediaById = allMedia.associateBy { it.id }

            // PRECEDENCE: a saved audio session means a video interrupted music and the process
            // died before it could be restored in-app. Recover that full session first and stop —
            // it is the authoritative snapshot of the interrupted music session.
            val savedSession = loadSavedAudioSession(mediaById)
            if (savedSession != null) {
                _currentPlaylistContext.value = queuePersistence.getPlaylistContext()
                applyAudioSession(savedSession)
                clearSavedAudioState()
                return
            }

            val savedQueueItems = mediaDao.getSavedQueue()
            if (savedQueueItems.isNotEmpty()) {
                // Restore rules (drop missing media, filter videos, clamp the saved index) live
                // in QueuePolicy and are pinned by QueuePolicyTest.
                val restored =
                    QueuePolicy.restore(
                        saved = savedQueueItems,
                        mediaById = mediaById,
                        savedIndex = queuePersistence.getQueueIndex(),
                    )

                var finalQueue = restored?.queue ?: emptyList()
                var finalIndex = restored?.index ?: 0
                var finalStartPos = 0L

                if (restored != null) {
                    finalStartPos =
                        queuePersistence.resumePositionFor(restored.queue[restored.index].id)
                } else {
                    // FALLBACK: If queue is empty (or was all videos), try to restore the last played
                    // AUDIO track
                    val lastAudio = mediaDao.getLastPlayedAudio()
                    if (lastAudio != null) {
                        val track = mediaById[lastAudio.mediaId]
                        if (track != null) {
                            finalQueue = listOf(track)
                            finalIndex = 0
                            finalStartPos = QueuePolicy.resumePosition(lastAudio)
                        }
                    }
                }

                if (finalQueue.isNotEmpty()) {
                    _currentQueue.value = finalQueue
                    _displayQueue.value = finalQueue
                    _currentIndex.value = finalIndex
                    _currentTrack.value = finalQueue[finalIndex]

                    // Restore shuffle mode, repeat mode, and playlist context from prefs
                    val savedShuffle = queuePersistence.getShuffleEnabled()
                    val savedRepeatMode = queuePersistence.getRepeatMode()
                    _isShuffleEnabled.value = savedShuffle
                    _repeatMode.value = savedRepeatMode
                    _currentPlaylistContext.value = queuePersistence.getPlaylistContext()

                    // Set to player
                    withContext(Dispatchers.Main) {
                        player.value?.let { controller ->
                            if (controller.mediaItemCount == 0) {
                                val items = finalQueue.map { it.toMediaItem() }
                                controller.setMediaItems(items, finalIndex, finalStartPos)
                                controller.shuffleModeEnabled = savedShuffle
                                controller.repeatMode = savedRepeatMode
                                controller.prepare()
                            }
                        }
                    }
                }
            }
        }

        // --- Persisted audio session --- (owned by QueuePersistence as of P4-E.4 step 2)
        // These remain as private helpers so the ~35 existing call sites are untouched; only the
        // bodies moved. Every write to the saved session now goes through one class.
        private fun persistQueue(queue: List<MediaFile>) {
            viewModelScope.launch(Dispatchers.IO) { queuePersistence.saveQueue(queue) }
        }

        // Since P5-C.3 these writes go to DataStore, which has no synchronous write, so each one
        // is launched. The helpers stay non-suspend so the ~29 call sites — several of them
        // `Player.Listener` callbacks that cannot suspend — are untouched. Ordering is preserved:
        // `viewModelScope` dispatches on Main.immediate, so the writes reach DataStore's
        // single-writer actor in call order.
        private fun persistQueueIndex(index: Int) {
            viewModelScope.launch { queuePersistence.setQueueIndex(index) }
        }

        private fun persistShuffleMode(enabled: Boolean) {
            viewModelScope.launch { queuePersistence.setShuffleEnabled(enabled) }
        }

        private fun persistRepeatMode(mode: Int) {
            viewModelScope.launch { queuePersistence.setRepeatMode(mode) }
        }

        private fun persistPlaylistContext(context: String?) {
            viewModelScope.launch { queuePersistence.setPlaylistContext(context) }
        }

        private fun persistSavedAudioState(state: AudioPlayerState) {
            viewModelScope.launch { queuePersistence.saveAudioSession(state) }
        }

        private fun clearSavedAudioState() {
            viewModelScope.launch { queuePersistence.clearAudioSession() }
        }

        private suspend fun loadSavedAudioSession(mediaById: Map<Long, MediaFile>): AudioPlayerState? =
            queuePersistence.loadAudioSession(mediaById)

        // --- Playback Logic ---
        fun playMedia(media: MediaFile) {
            if (media.isVideo) {
                playVideo(media) // Redirect to new video handler
            } else if (!media.isImage) {
                _currentPlaylistContext.value = null
                persistPlaylistContext(null)
                val currentVisibleList = audioList.value
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
                    val folderVideos = mediaRepository.videoList.value.filter { it.bucketId == media.bucketId }
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
        fun playVideoFromList(
            media: MediaFile,
            list: List<MediaFile>,
        ) {
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
                        repeatMode = _repeatMode.value,
                    )
                // Also persist to disk so the music session survives if the process is killed
                // while the video is open (in-memory savedAudioState would be lost).
                savedAudioState?.let { persistSavedAudioState(it) }
            }

            _isPlayerLocked.value = false
            _playbackSpeed.value = 1.0f
            _resizeMode.value = ResizeMode.FIT
            _playerError.value = null // Clear any previous error
            _isVideoPlayerVisible.value = true // Explicitly show player

            viewModelScope.launch(Dispatchers.IO) {
                val history = mediaDao.getHistory(media.id)
                val startPos =
                    if (history != null && history.position < (history.duration * VIDEO_COMPLETION_THRESHOLD)) {
                        history.position
                    } else {
                        0L
                    }

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
                    queueUpdateJob =
                        launch {
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
                applyAudioSession(state)
                // Clear both the in-memory and on-disk snapshots after a successful restore.
                savedAudioState = null
                clearSavedAudioState()
            } else {
                // No state to restore (e.g. video played without prior music), just stop
                clearSavedAudioState()
                player.value?.stop()
                player.value?.clearMediaItems()
                _currentTrack.value = null
                _currentQueue.value = emptyList()
            }
        }

        /**
         * Applies a saved audio session to both the UI StateFlows and the underlying player.
         * Shared by the in-app "close video" restore and the cold-start recovery path. Player
         * interaction is dispatched to Main; playback only resumes when [state.isPlaying] is true.
         */
        private fun applyAudioSession(state: AudioPlayerState) {
            _currentQueue.value = state.queue
            _displayQueue.value = state.queue
            _currentIndex.value = state.currentIndex
            _isShuffleEnabled.value = state.isShuffleEnabled
            _repeatMode.value = state.repeatMode

            // Immediately update the UI track so the miniplayer reappears correctly
            if (state.queue.isNotEmpty() && state.currentIndex < state.queue.size) {
                _currentTrack.value = state.queue[state.currentIndex]
            }

            viewModelScope.launch(Dispatchers.Main) {
                player.value?.let { controller ->
                    val items = state.queue.map { it.toMediaItem() }
                    controller.setMediaItems(items, state.currentIndex, state.position)
                    controller.shuffleModeEnabled = state.isShuffleEnabled
                    controller.repeatMode = state.repeatMode
                    controller.prepare()
                    if (state.isPlaying) controller.play() else controller.pause()
                }
            }
        }

        fun playMediaFromList(
            media: MediaFile,
            list: List<MediaFile>,
        ) {
            val startIndex = list.indexOfFirst { it.id == media.id }
            if (startIndex >= 0) setQueue(list, startIndex, false)
        }

        fun playPlaylist(
            playlist: Playlist,
            songs: List<MediaFile>,
            shuffle: Boolean,
        ) {
            if (songs.isNotEmpty()) {
                _currentPlaylistContext.value = playlist.id
                persistPlaylistContext(playlist.id)
                val startIndex = 0 // setQueue will handle the starting index if shuffle is enabled
                if (playlist.isVideo) {
                    _isPlayerLocked.value = false
                    _playbackSpeed.value = 1.0f
                    _resizeMode.value = ResizeMode.FIT
                    _isVideoPlayerVisible.value = true // Explicitly show player for video playlists
                }
                setQueue(songs, startIndex, shuffle)
            }
        }

        fun playAlbum(
            album: Album,
            shuffle: Boolean,
        ) {
            val albumSongs = audioList.value.filter { it.albumId == album.id }
            if (albumSongs.isNotEmpty()) {
                _currentPlaylistContext.value = "ALBUM_${album.id}"
                persistPlaylistContext("ALBUM_${album.id}")
                val startIndex = 0
                setQueue(albumSongs, startIndex, shuffle)
            }
        }

        /**
         * Play a specific track from an album context.
         * Sets the album as the playlist context so autoFill won't add random library songs.
         */
        fun playFromAlbum(
            albumId: Long,
            songs: List<MediaFile>,
            startIndex: Int,
        ) {
            if (songs.isNotEmpty() && startIndex in songs.indices) {
                _currentPlaylistContext.value = "ALBUM_$albumId"
                persistPlaylistContext("ALBUM_$albumId")
                setQueue(songs, startIndex, false)
            }
        }

        /**
         * Play all songs by an artist. Sets the artist as the playlist context so autoFill
         * won't append random library songs when the artist's tracks finish.
         */
        fun playArtist(
            artistName: String,
            songs: List<MediaFile>,
            shuffle: Boolean,
        ) {
            if (songs.isNotEmpty()) {
                _currentPlaylistContext.value = "ARTIST_$artistName"
                persistPlaylistContext("ARTIST_$artistName")
                setQueue(songs, 0, shuffle)
            }
        }

        /**
         * Play a specific track from an artist context.
         */
        fun playFromArtist(
            artistName: String,
            songs: List<MediaFile>,
            startIndex: Int,
        ) {
            if (songs.isNotEmpty() && startIndex in songs.indices) {
                _currentPlaylistContext.value = "ARTIST_$artistName"
                persistPlaylistContext("ARTIST_$artistName")
                setQueue(songs, startIndex, false)
            }
        }

        /**
         * Play a specific track from a playlist context.
         * Sets the playlist as the context so autoFill won't add random library songs.
         */
        fun playFromPlaylist(
            playlistId: String,
            songs: List<MediaFile>,
            startIndex: Int,
        ) {
            if (songs.isNotEmpty() && startIndex in songs.indices) {
                _currentPlaylistContext.value = playlistId
                persistPlaylistContext(playlistId)
                setQueue(songs, startIndex, false)
            }
        }

        /**
         * Play all songs from a smart (auto-generated) playlist. Sets a SMART_ context so autoFill
         * won't append random library songs when the queue finishes. [typeId] is SmartPlaylistType.id.
         */
        fun playSmartPlaylist(
            typeId: String,
            songs: List<MediaFile>,
            shuffle: Boolean,
        ) {
            if (songs.isNotEmpty()) {
                _currentPlaylistContext.value = "SMART_$typeId"
                persistPlaylistContext("SMART_$typeId")
                setQueue(songs, 0, shuffle)
            }
        }

        /**
         * Play a specific track from a smart playlist context.
         */
        fun playFromSmartPlaylist(
            typeId: String,
            songs: List<MediaFile>,
            startIndex: Int,
        ) {
            if (songs.isNotEmpty() && startIndex in songs.indices) {
                _currentPlaylistContext.value = "SMART_$typeId"
                persistPlaylistContext("SMART_$typeId")
                setQueue(songs, startIndex, false)
            }
        }

        fun playAll(
            list: List<MediaFile> = audioList.value,
            shuffle: Boolean,
        ) {
            _currentPlaylistContext.value = null
            persistPlaylistContext(null)
            if (list.isNotEmpty()) {
                val startIndex = 0
                setQueue(list, startIndex, shuffle)
            }
        }

        // UPDATED setQueue to delegate to MediaController
        fun setQueue(
            mediaList: List<MediaFile>,
            startIndex: Int,
            shuffle: Boolean = false,
            startPosition: Long = 0L,
        ) {
            // Launch in background to avoid blocking Main Thread during conversion of large playlists
            viewModelScope.launch(Dispatchers.IO) {
                val mediaItems = mediaList.map { it.toMediaItem() }

                withContext(Dispatchers.Main) {
                    // Update Local State synchronously with MediaController to prevent UI flicker
                    _currentQueue.value = mediaList
                    _isShuffleEnabled.value = shuffle

                    player.value?.let { controller ->
                        // Set shuffle mode BEFORE items to ensure the initial playback point
                        // respects the shuffle order if shuffle is enabled.
                        controller.shuffleModeEnabled = shuffle

                        if (shuffle && startPosition == 0L) {
                            // When "Shuffle All" is triggered (startPosition 0), we omit the startIndex.
                            // This tells ExoPlayer to start at the FIRST item of its shuffled timeline,
                            // guaranteeing that every song in the list will be played before stopping.
                            controller.setMediaItems(mediaItems)
                        } else {
                            // For non-shuffled playback or session restoration, we use the specific index.
                            controller.setMediaItems(mediaItems, startIndex, startPosition)
                        }

                        controller.prepare()
                        controller.play()
                    }

                    // Update display queue only after controller is set up
                    updateDisplayQueue()
                }

                // Persist queue + index together, audio only. Writing both in the same IO job (queue
                // first, then index) means a persisted index can never outlive the queue it points
                // into. Video is never written into the audio queue, so an interrupting video can't
                // destroy the saved music session.
                if (mediaList.none { it.isVideo }) {
                    // Starting a fresh audio queue supersedes any pending interrupted session.
                    savedAudioState = null
                    clearSavedAudioState()

                    val entities =
                        mediaList.mapIndexed { index, media -> QueueItemEntity(media.id, index) }
                    mediaDao.replaceQueue(entities)
                    persistQueueIndex(startIndex)
                }
            }
        }

        /**
         * Updates the queue silently without stopping playback. Used for loading large video playlists
         * in the background after playback starts.
         */
        private suspend fun updateQueueInBackground(
            mediaList: List<MediaFile>,
            startIndex: Int,
        ) {
            // Update Local State so UI shows correct list
            _currentQueue.value = mediaList
            _displayQueue.value = mediaList // Since we are in static mode

            // Update Player: Add items before and after current item
            // We use a simplified strategy: Replace the items but keep the current window/position
            withContext(Dispatchers.Main) {
                // Ensure MediaController interaction is on Main
                player.value?.let { controller ->
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

            // Audio only — a video folder/playlist must never overwrite the persisted music queue.
            if (mediaList.none { it.isVideo }) {
                persistQueue(mediaList)
                withContext(Dispatchers.Main) { persistQueueIndex(startIndex) }
            }
        }

        private var displayQueueUpdateJob: Job? = null

        /**
         * Updates the display queue to reflect the shuffled playback order. When shuffle is disabled,
         * displays the original queue order. When shuffle is enabled, builds the queue order based on
         * Media3's shuffle timeline.
         *
         * OPTIMIZATION: Extracts timeline IDs on Main thread (for Media3 thread-safety), then offloads
         * heavy mapping to a background thread to prevent UI jank.
         */
        private fun updateDisplayQueue() {
            displayQueueUpdateJob?.cancel()
            val controller = player.value
            val currentQueueSnapshot = _currentQueue.value

            if (controller == null || currentQueueSnapshot.isEmpty()) {
                _displayQueue.value = currentQueueSnapshot
                return
            }

            val shuffleEnabled = _isShuffleEnabled.value && controller.shuffleModeEnabled

            if (!shuffleEnabled) {
                _displayQueue.value = currentQueueSnapshot
                return
            }

            val timeline = controller.currentTimeline
            if (timeline.isEmpty) {
                _displayQueue.value = currentQueueSnapshot
                return
            }

            // 1. SAFELY extract the shuffled sequence of media IDs on the Main thread.
            // Media3 strictly enforces player state access on the application's main thread.
            val shuffledMediaIds = mutableListOf<String>()
            val window =
                androidx.media3.common.Timeline
                    .Window()

            var windowIndex = timeline.getFirstWindowIndex(true)
            while (windowIndex != androidx.media3.common.C.INDEX_UNSET) {
                timeline.getWindow(windowIndex, window)
                shuffledMediaIds.add(window.mediaItem.mediaId)
                windowIndex = timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)
            }

            // 2. Offload the heavy object mapping to Default dispatcher
            displayQueueUpdateJob =
                viewModelScope.launch(Dispatchers.Default) {
                    val mediaIdToMediaFile = currentQueueSnapshot.associateBy { it.id.toString() }
                    val shuffledQueue = shuffledMediaIds.mapNotNull { mediaIdToMediaFile[it] }

                    withContext(Dispatchers.Main) {
                        _displayQueue.value = shuffledQueue
                    }
                }
        }

        /**
         * Play a specific track from the queue (handles both shuffled and non-shuffled modes). Finds
         * the track in the controller's timeline and seeks to it.
         */
        fun playTrackFromQueue(track: MediaFile) {
            player.value?.let { controller ->
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
                MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(albumArtUri)
                    .build()
            return MediaItem
                .Builder()
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
            player.value?.setPlaybackSpeed(newSpeed)
            _playbackSpeed.value = newSpeed
        }

        fun setPipMode(isPip: Boolean) {
            _isInPipMode.value = isPip
        }

        // --- Long-Press Speed Boost ---
        private var speedBeforeBoost: Float? = null

        fun startSpeedBoost(boostSpeed: Float = 2.0f) {
            if (speedBeforeBoost != null) return // Already boosting
            speedBeforeBoost = _playbackSpeed.value
            player.value?.setPlaybackSpeed(boostSpeed)
            _playbackSpeed.value = boostSpeed
        }

        fun stopSpeedBoost() {
            val original = speedBeforeBoost ?: return
            speedBeforeBoost = null
            player.value?.setPlaybackSpeed(original)
            _playbackSpeed.value = original
        }

        fun dismissPlayerError() {
            _playerError.value = null
        }

        /** Set an exact playback speed (used by the audio speed picker). */
        fun setPlaybackSpeed(speed: Float) {
            player.value?.setPlaybackSpeed(speed)
            _playbackSpeed.value = speed
        }

        // --- Sleep Timer (night-only) ---
        // Per product requirement, the sleep timer is only usable between 10 PM and 5 AM
        // based on the device's local time. The window is detected automatically.
        private val _sleepTimerEndMillis = MutableStateFlow<Long?>(null)
        val sleepTimerEndMillis = _sleepTimerEndMillis.asStateFlow()

        private var sleepTimerJob: Job? = null

        /** True when the current device-local time falls within the night window [22:00, 05:00). */
        fun isSleepTimerAllowed(): Boolean {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return hour >= 22 || hour < 5
        }

        /**
         * Start a sleep timer that pauses playback after [durationMinutes]. Only honored while the
         * device time is inside the night window; otherwise the request is rejected with a message.
         */
        fun setSleepTimer(durationMinutes: Int) {
            if (!isSleepTimerAllowed()) {
                viewModelScope.launch {
                    _userMessage.emit(UserMessage.of(R.string.msg_sleep_timer_window))
                }
                return
            }
            sleepTimerJob?.cancel()
            val durationMs = durationMinutes * 60_000L
            _sleepTimerEndMillis.value = System.currentTimeMillis() + durationMs
            sleepTimerJob =
                viewModelScope.launch {
                    delay(durationMs)
                    withContext(Dispatchers.Main) { player.value?.pause() }
                    _sleepTimerEndMillis.value = null
                    _userMessage.emit(UserMessage.of(R.string.msg_sleep_timer_ended))
                }
            viewModelScope.launch { _userMessage.emit(UserMessage.of(R.string.msg_sleep_timer_set, durationMinutes)) }
        }

        fun cancelSleepTimer() {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            _sleepTimerEndMillis.value = null
        }

        // --- Track Selection (Audio & Subtitles) ---

        /** Get available audio tracks for the current video. */
        fun getAudioTracks(): List<TrackInfo> {
            val player = player.value ?: return emptyList()
            val tracks = player.currentTracks
            val result = mutableListOf<TrackInfo>()

            for (groupIndex in 0 until tracks.groups.size) {
                val group = tracks.groups[groupIndex]
                if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        val isSelected = group.isTrackSelected(trackIndex)
                        val language =
                            format.language?.let {
                                java.util.Locale
                                    .forLanguageTag(it)
                                    .displayLanguage
                            }
                                ?: "Unknown"
                        val label = format.label ?: "Track ${trackIndex + 1}"
                        val name = if (format.label != null) "$label ($language)" else language

                        result.add(
                            TrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                name = name,
                                language = format.language,
                                isSelected = isSelected,
                            ),
                        )
                    }
                }
            }
            return result
        }

        /** Get available subtitle tracks for the current video. */
        fun getSubtitleTracks(): List<TrackInfo> {
            val player = player.value ?: return emptyList()
            val tracks = player.currentTracks
            val result = mutableListOf<TrackInfo>()

            for (groupIndex in 0 until tracks.groups.size) {
                val group = tracks.groups[groupIndex]
                if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        val isSelected = group.isTrackSelected(trackIndex)
                        val language =
                            format.language?.let {
                                java.util.Locale
                                    .forLanguageTag(it)
                                    .displayLanguage
                            }
                                ?: "Unknown"
                        val label = format.label ?: "Subtitle ${trackIndex + 1}"
                        val name = if (format.label != null) "$label ($language)" else language

                        result.add(
                            TrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                name = name,
                                language = format.language,
                                isSelected = isSelected,
                            ),
                        )
                    }
                }
            }
            return result
        }

        /** Select a specific audio track. */
        fun selectAudioTrack(
            groupIndex: Int,
            trackIndex: Int,
        ) {
            val player = player.value ?: return
            val tracks = player.currentTracks
            if (groupIndex >= tracks.groups.size) return

            val group = tracks.groups[groupIndex]
            val override =
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)

            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(override)
                    .build()
        }

        /** Select a specific subtitle track. */
        fun selectSubtitleTrack(
            groupIndex: Int,
            trackIndex: Int,
        ) {
            val player = player.value ?: return
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
            val player = player.value ?: return
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                    .build()
        }

        /** Check if subtitles are currently disabled. */
        fun areSubtitlesDisabled(): Boolean {
            val player = player.value ?: return true
            return player.trackSelectionParameters.disabledTrackTypes.contains(
                androidx.media3.common.C.TRACK_TYPE_TEXT,
            )
        }

        /**
         * Load an external subtitle file (e.g. .srt/.vtt/.ass) and attach it to the currently playing
         * video. The subtitle only applies to the current media item; advancing to the next video drops
         * it, which matches user expectation. Playback position and play/pause state are preserved.
         */
        fun addExternalSubtitle(uri: Uri) {
            val controller = player.value ?: return
            val item = controller.currentMediaItem ?: return
            val index = controller.currentMediaItemIndex
            val position = controller.currentPosition
            val wasPlaying = controller.isPlaying

            val uriStr = uri.toString().lowercase()
            val mimeType =
                when {
                    uriStr.endsWith(".vtt") -> androidx.media3.common.MimeTypes.TEXT_VTT
                    uriStr.endsWith(".ssa") || uriStr.endsWith(".ass") -> androidx.media3.common.MimeTypes.TEXT_SSA
                    uriStr.endsWith(
                        ".ttml",
                    ) ||
                        uriStr.endsWith(".xml") -> androidx.media3.common.MimeTypes.APPLICATION_TTML
                    else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                }

            val subtitle =
                MediaItem.SubtitleConfiguration
                    .Builder(uri)
                    .setMimeType(mimeType)
                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                    .build()

            val newItem =
                item
                    .buildUpon()
                    .setSubtitleConfigurations(listOf(subtitle))
                    .build()

            try {
                controller.replaceMediaItem(index, newItem)
                controller.seekTo(index, position)
                controller.prepare()
                if (wasPlaying) controller.play()
                // Ensure subtitle track type is enabled so the newly added track can show
                controller.trackSelectionParameters =
                    controller.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                        .build()
                viewModelScope.launch { _userMessage.emit(UserMessage.of(R.string.msg_subtitle_added)) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add external subtitle", e)
                viewModelScope.launch { _userMessage.emit(UserMessage.of(R.string.msg_subtitle_load_failed)) }
            }
        }

        // --- Auto Fill Queue Logic ---
        private fun autoFillQueue(
            playNext: Boolean = false,
            playPrevious: Boolean = false,
        ) {
            val currentTrack = _currentTrack.value
            if (currentTrack?.isVideo == true) return

            val controller = player.value ?: return

            // Check if we are playing a specific context (playlist, album)
            // If so, do not automatically jump to random library songs when it finishes.
            // It's confusing if you queue an album and end up listening to the whole library.
            if (_currentPlaylistContext.value != null && !playPrevious) {
                return
            }

            val audioListSnapshot = audioList.value
            if (audioListSnapshot.isEmpty()) return

            val shuffledAudio = audioListSnapshot.shuffled()

            if (playPrevious) {
                val newQueue = _currentQueue.value.let { shuffledAudio + it }
                _currentQueue.value = newQueue
                _displayQueue.value = newQueue

                viewModelScope.launch(Dispatchers.IO) {
                    val mediaItems = shuffledAudio.map { it.toMediaItem() }
                    withContext(Dispatchers.Main) {
                        controller.addMediaItems(0, mediaItems)
                        controller.seekTo(mediaItems.size - 1, 0L)
                        controller.play()
                    }
                    persistQueue(newQueue)
                }
            } else {
                val newQueue = _currentQueue.value + shuffledAudio
                _currentQueue.value = newQueue
                _displayQueue.value = newQueue

                viewModelScope.launch(Dispatchers.IO) {
                    val mediaItems = shuffledAudio.map { it.toMediaItem() }
                    withContext(Dispatchers.Main) {
                        controller.addMediaItems(mediaItems)

                        if (playNext || controller.playbackState == Player.STATE_ENDED) {
                            if (controller.hasNextMediaItem()) {
                                controller.seekToNext()
                                controller.play()
                            }
                        }
                    }
                    persistQueue(newQueue)
                }
            }
        }

        /**
         * Re-shuffles the current queue and restarts playback from the beginning
         * of the new shuffle order. Called when a shuffled playlist/album reaches
         * the end (STATE_ENDED) to provide continuous listening without adding
         * random library songs to a curated context.
         */
        private fun reshuffleAndRestart() {
            val controller = player.value ?: return
            if (controller.mediaItemCount == 0) return

            // Toggle shuffle off then on to generate a fresh shuffle order
            controller.shuffleModeEnabled = false
            controller.shuffleModeEnabled = true

            // Seek to the first item in the new shuffled timeline
            val timeline = controller.currentTimeline
            if (!timeline.isEmpty) {
                val firstShuffledIndex = timeline.getFirstWindowIndex(true)
                if (firstShuffledIndex != androidx.media3.common.C.INDEX_UNSET) {
                    controller.seekTo(firstShuffledIndex, 0L)
                    controller.play()
                }
            }

            // Refresh the display queue to reflect the new shuffle order
            updateDisplayQueue()
        }

        // --- Controls ---
        fun playNext() {
            player.value?.let {
                if (it.hasNextMediaItem()) {
                    it.seekToNext()
                } else {
                    autoFillQueue(playNext = true)
                }
            }
        }

        fun playPrevious() {
            player.value?.let {
                if (it.currentPosition > REWIND_THRESHOLD_MS) {
                    it.seekTo(0)
                } else if (it.hasPreviousMediaItem()) {
                    it.seekToPrevious()
                } else {
                    if (_currentTrack.value?.isVideo == true) {
                        it.seekTo(0)
                    } else {
                        // Start of queue, skip to end if looping/shuffled? Or autoFillQueue.
                        // Actually if we want to play previous and there's no previous item,
                        // we seek to the end of the queue.
                        if (it.mediaItemCount > 0 && !it.shuffleModeEnabled) {
                            it.seekTo(it.mediaItemCount - 1, 0L)
                        } else {
                            autoFillQueue(playPrevious = true)
                        }
                    }
                }
            }
        }

        fun togglePlayPause() {
            player.value?.let { if (it.isPlaying) it.pause() else it.play() }
        }

        fun pauseVideo() {
            player.value?.pause()
        }

        fun toggleShuffle() {
            player.value?.let {
                val newMode = !it.shuffleModeEnabled
                it.shuffleModeEnabled = newMode
                _isShuffleEnabled.value = newMode
            }
        }

        fun toggleRepeat() {
            player.value?.let {
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
            player.value?.seekTo(positionMs)
            _currentPosition.value = positionMs
        }

        fun rewind() {
            player.value?.let { it.seekTo((it.currentPosition - SEEK_DELTA_MS).coerceAtLeast(0)) }
        }

        fun forward() {
            player.value?.let {
                if (it.duration != androidx.media3.common.C.TIME_UNSET && it.duration > 0) {
                    it.seekTo((it.currentPosition + SEEK_DELTA_MS).coerceAtMost(it.duration))
                } else {
                    it.seekTo(it.currentPosition + SEEK_DELTA_MS)
                }
            }
        }

        fun hasNext(): Boolean = player.value?.hasNextMediaItem() ?: false

        fun hasPrevious(): Boolean = player.value?.hasPreviousMediaItem() ?: false

        // --- Queue Management ---
        fun playNext(media: MediaFile) {
            val controller = player.value
            val queue = _currentQueue.value.toMutableList()
            val currentIdx = _currentIndex.value ?: -1

            if (controller != null && queue.isNotEmpty() && currentIdx >= 0) {
                // Check if media already exists in queue
                val existingIndex = queue.indexOfFirst { it.id == media.id }

                if (existingIndex != -1) {
                    // CASE 1: Currently Playing -> Ignore
                    if (existingIndex == currentIdx) return

                    // Remove from history or upcoming
                    queue.removeAt(existingIndex)
                    controller.removeMediaItem(existingIndex)

                    // Calculate insertion index
                    // If existingIndex < currentIdx, the current item shifted up by 1, so new current is currentIdx - 1.
                    // Inserting at currentIdx places it exactly at newCurrent + 1.
                    // If existingIndex > currentIdx, the current item didn't shift.
                    // Inserting at currentIdx + 1 places it exactly at current + 1.
                    val insertIndex = if (existingIndex < currentIdx) currentIdx else currentIdx + 1

                    queue.add(insertIndex, media)
                    _currentQueue.value = queue
                    controller.addMediaItem(insertIndex, media.toMediaItem())
                } else {
                    // CASE 4: New Song -> Add Next
                    // Simply insert at current + 1
                    val insertIndex = currentIdx + 1
                    queue.add(insertIndex, media)
                    _currentQueue.value = queue
                    controller.addMediaItem(insertIndex, media.toMediaItem())
                }

                // Update UI
                updateDisplayQueue()
                persistQueue(queue)

                // Provide Feedback
                viewModelScope.launch {
                    if (controller.shuffleModeEnabled) {
                        _userMessage.emit(UserMessage.of(R.string.msg_added_to_queue_shuffle_note))
                    } else {
                        _userMessage.emit(UserMessage.of(R.string.msg_will_play_next))
                    }
                }
            } else {
                playMedia(media)
            }
        }

        fun addToQueue(media: MediaFile) {
            val controller = player.value
            val queue = _currentQueue.value.toMutableList()
            val currentIdx = _currentIndex.value ?: -1

            if (controller != null && queue.isNotEmpty()) {
                val existingIndex = queue.indexOfFirst { it.id == media.id }

                if (existingIndex != -1) {
                    // CASE 1: Currently Playing -> Ignore
                    if (existingIndex == currentIdx) return

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
                updateDisplayQueue()
                persistQueue(queue)

                viewModelScope.launch {
                    _userMessage.emit(UserMessage.of(R.string.msg_added_to_queue))
                }
            } else {
                playMedia(media)
            }
        }

        /**
         * Adds a list of songs to play immediately after the current track.
         * Moves existing instances to the new position if they are already in the queue.
         */
        fun playNext(songs: List<MediaFile>) {
            val controller = player.value
            val queue = _currentQueue.value.toMutableList()
            val currentIdx = _currentIndex.value ?: -1

            if (controller != null && queue.isNotEmpty() && currentIdx >= 0) {
                val currentTrackId = _currentTrack.value?.id
                val itemsToAdd = songs.filter { it.id != currentTrackId }

                if (itemsToAdd.isNotEmpty()) {
                    val idsToAdd = itemsToAdd.map { it.id }.toSet()

                    // Remove existing instances from controller (reverse order to keep indices stable)
                    for (i in controller.mediaItemCount - 1 downTo 0) {
                        if (i == currentIdx) continue
                        val mediaId = controller.getMediaItemAt(i).mediaId.toLongOrNull()
                        if (mediaId != null && mediaId in idsToAdd) {
                            controller.removeMediaItem(i)
                        }
                    }

                    // Remove from local queue
                    queue.removeAll { it.id in idsToAdd && it.id != currentTrackId }

                    // Recalculate current index and insert after it
                    val updatedCurrentIdx = queue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
                    val insertIndex = updatedCurrentIdx + 1

                    queue.addAll(insertIndex, itemsToAdd)
                    _currentQueue.value = queue

                    val mediaItems = itemsToAdd.map { it.toMediaItem() }
                    controller.addMediaItems(insertIndex, mediaItems)

                    updateDisplayQueue()
                    persistQueue(queue)

                    viewModelScope.launch {
                        _userMessage.emit(UserMessage.of(R.string.msg_added_to_play_next))
                    }
                }
            } else {
                playAll(songs, false)
            }
        }

        /**
         * Adds a list of songs to the end of the current queue.
         * Moves existing instances to the end if they are already in the queue.
         */
        fun addToQueue(songs: List<MediaFile>) {
            val controller = player.value
            val queue = _currentQueue.value.toMutableList()
            val currentIdx = _currentIndex.value ?: -1

            if (controller != null && queue.isNotEmpty()) {
                val currentTrackId = _currentTrack.value?.id
                val itemsToAdd = songs.filter { it.id != currentTrackId }

                if (itemsToAdd.isNotEmpty()) {
                    val idsToAdd = itemsToAdd.map { it.id }.toSet()

                    // Remove existing instances
                    for (i in controller.mediaItemCount - 1 downTo 0) {
                        if (i == currentIdx) continue
                        val mediaId = controller.getMediaItemAt(i).mediaId.toLongOrNull()
                        if (mediaId != null && mediaId in idsToAdd) {
                            controller.removeMediaItem(i)
                        }
                    }
                    queue.removeAll { it.id in idsToAdd && it.id != currentTrackId }

                    // Add to end
                    queue.addAll(itemsToAdd)
                    _currentQueue.value = queue

                    val mediaItems = itemsToAdd.map { it.toMediaItem() }
                    controller.addMediaItems(mediaItems)

                    updateDisplayQueue()
                    persistQueue(queue)

                    viewModelScope.launch {
                        _userMessage.emit(UserMessage.of(R.string.msg_added_to_queue))
                    }
                }
            } else {
                playAll(songs, false)
            }
        }

        /**
         * Move a track to a new position in the queue (drag & drop from the Now Playing queue sheet).
         * Indices are positions in the visible queue. Only supported while shuffle is off: with
         * shuffle on the visible order is Media3's internal shuffle order, which a MediaController
         * cannot rewrite. Keeps the player timeline, local state and the persisted queue in sync.
         */
        fun moveQueueItem(
            track: MediaFile,
            fromIndex: Int,
            toIndex: Int,
        ) {
            val controller = player.value ?: return
            if (fromIndex == toIndex) return
            if (controller.shuffleModeEnabled) {
                viewModelScope.launch { _userMessage.emit(UserMessage.of(R.string.msg_turn_off_shuffle_to_reorder)) }
                return
            }

            val queue = _currentQueue.value.toMutableList()
            if (fromIndex !in queue.indices || toIndex !in queue.indices) return
            if (fromIndex >= controller.mediaItemCount || toIndex >= controller.mediaItemCount) return
            // Stale-drag guard: the queue can change underneath the sheet (e.g. a library deletion
            // mid-drag). Only commit when the dragged item is still where the drag started.
            if (queue[fromIndex].id != track.id) return

            queue.add(toIndex, queue.removeAt(fromIndex))
            _currentQueue.value = queue

            controller.moveMediaItem(fromIndex, toIndex)
            _currentIndex.value = controller.currentMediaItemIndex

            updateDisplayQueue()
            persistQueue(queue)
            // The playing item's position may have shifted; keep the persisted index consistent
            // with the queue that was just persisted (audio sessions only, matching persistQueue).
            if (_currentTrack.value?.isVideo != true) {
                persistQueueIndex(controller.currentMediaItemIndex)
            }
        }

        /**
         * Remove a single track from the queue (cannot remove the currently playing track here).
         * Keeps the player timeline and the local/persisted queue in sync.
         */
        fun removeFromQueue(track: MediaFile) {
            val controller = player.value ?: return
            if (track.id == _currentTrack.value?.id) return // guard: don't drop the playing track

            for (i in 0 until controller.mediaItemCount) {
                if (controller.getMediaItemAt(i).mediaId == track.id.toString()) {
                    controller.removeMediaItem(i)
                    break
                }
            }
            val queue = _currentQueue.value.toMutableList()
            queue.removeAll { it.id == track.id }
            _currentQueue.value = queue
            _currentIndex.value = controller.currentMediaItemIndex
            updateDisplayQueue()
            persistQueue(queue)
        }

        /** Clear all upcoming tracks, keeping only the one currently playing. */
        fun clearQueueExceptCurrent() {
            val controller = player.value ?: return
            val current = _currentTrack.value ?: return

            for (i in controller.mediaItemCount - 1 downTo 0) {
                if (controller.getMediaItemAt(i).mediaId != current.id.toString()) {
                    controller.removeMediaItem(i)
                }
            }
            val newQueue = listOf(current)
            _currentQueue.value = newQueue
            _currentIndex.value = controller.currentMediaItemIndex
            updateDisplayQueue()
            persistQueue(newQueue)
        }

        /** Persist the current queue as a new audio playlist. */
        fun saveQueueAsPlaylist(name: String) {
            val queue = _currentQueue.value
            if (queue.isEmpty() || name.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                playlistRepository.createPlaylist(name.trim(), false)
                val created =
                    playlistRepository.playlistsFlow
                        .firstOrNull()
                        ?.find { it.name.equals(name.trim(), ignoreCase = true) && !it.isVideo }
                if (created != null) {
                    playlistRepository.updatePlaylistTracks(created.id, queue.map { it.id })
                    _userMessage.emit(UserMessage.of(R.string.msg_saved_queue_as, name.trim()))
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            stopPositionUpdates()
            sleepTimerJob?.cancel()
            // Release through the binder so its exposed state resets too. Behaviour is unchanged
            // from when this ViewModel released the future directly: the controller is torn down
            // when the ViewModel dies, and a later connect() builds a fresh one.
            playerListener?.let { l -> player.value?.removeListener(l) }
            playerListener = null
            mediaControllerBinder.release()
            // Note: MediaRepository is @Singleton and outlives this ViewModel.
            // cleanup() is not called here to avoid breaking other ViewModels that share it.
        }
    }
