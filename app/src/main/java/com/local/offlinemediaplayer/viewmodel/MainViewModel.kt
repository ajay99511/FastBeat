
package com.local.offlinemediaplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.net.Uri
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
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.data.db.PlaybackHistory
import com.local.offlinemediaplayer.model.Album
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption {
    TITLE_ASC, TITLE_DESC, DURATION_ASC, DURATION_DESC, DATE_ADDED_DESC
}

enum class ResizeMode {
    FIT, FILL, ZOOM
}

@OptIn(UnstableApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val playlistRepository: PlaylistRepository,
    private val mediaDao: MediaDao
) : AndroidViewModel(app) {

    // --- THEMING STATE ---
    private val themes = mapOf(
        "blue" to AppThemeConfig("blue", Color(0xFF00E5FF), "DIGITAL WAVES", "Quick Mix"),
        "green" to AppThemeConfig("green", Color(0xFF22C55E), "ECO FREQUENCY", "Fresh Finds"),
        "orange" to AppThemeConfig("orange", Color(0xFFFF5500), "AMBER HORIZON", "Jump Back In")
    )

    private val _currentTheme = MutableStateFlow(themes["orange"]!!)
    val currentTheme = _currentTheme.asStateFlow()

    fun updateTheme(themeId: String) {
        _currentTheme.value = themes[themeId] ?: themes["orange"]!!
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
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    // Search and Sort State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _albumSearchQuery = MutableStateFlow("")
    val albumSearchQuery = _albumSearchQuery.asStateFlow()

    // Video Folder Search
    private val _folderSearchQuery = MutableStateFlow("")
    val folderSearchQuery = _folderSearchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_ADDED_DESC)
    val sortOption = _sortOption.asStateFlow()

    // Computed Flow for Filtered/Sorted Audio
    val filteredAudioList = combine(_audioList, _searchQuery, _sortOption) { list, query, sort ->
        var result = list
        // Filter
        if (query.isNotEmpty()) {
            result = result.filter {
                it.title.contains(query, ignoreCase = true) ||
                        (it.artist?.contains(query, ignoreCase = true) == true)
            }
        }
        // Sort
        when(sort) {
            SortOption.TITLE_ASC -> result.sortedBy { it.title }
            SortOption.TITLE_DESC -> result.sortedByDescending { it.title }
            SortOption.DURATION_ASC -> result.sortedBy { it.duration }
            SortOption.DURATION_DESC -> result.sortedByDescending { it.duration }
            SortOption.DATE_ADDED_DESC -> result.sortedByDescending { it.id } // ID is a proxy for date added
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Computed Flow for Filtered Albums
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

    // Repeat Mode
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    // --- NEW VIDEO PLAYER STATES ---
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

    init {
        initializeMediaController()
        loadPlaylists()
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

    private fun initializeMediaController() {
        val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                _player.value = controller
                setupPlayerListener(controller)

                // Initialize state from controller if it's already running
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
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentTrackFromPlayer(controller)
                // When track changes, track a play count for the new track
                mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    recordPlay(id)
                }
            }
        })
    }

    // Record analytics safely in background
    private fun recordPlay(mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            mediaDao.initAnalytics(mediaId, now)
            mediaDao.incrementPlayCount(mediaId, now)
        }
    }

    private fun updateCurrentTrackFromPlayer(controller: MediaController) {
        val currentMediaItem = controller.currentMediaItem
        if (currentMediaItem == null) {
            _currentTrack.value = null
            return
        }

        val idString = currentMediaItem.mediaId
        val id = idString.toLongOrNull()

        if (id != null) {
            val track = _audioList.value.find { it.id == id }
                ?: _videoList.value.find { it.id == id }
            _currentTrack.value = track
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = viewModelScope.launch {
            var saveCounter = 0
            while (isActive) {
                _player.value?.let { player ->
                    val pos = player.currentPosition
                    _currentPosition.value = pos
                    _duration.value = player.duration.coerceAtLeast(0L)

                    // Save playback state every ~5 seconds (10 * 500ms)
                    if (saveCounter++ % 10 == 0) {
                        _currentTrack.value?.let { track ->
                            savePlaybackState(track.id, pos, track.isVideo)
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        // One final save on pause/stop
        _currentTrack.value?.let { track ->
            savePlaybackState(track.id, _currentPosition.value, track.isVideo)
        }
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun savePlaybackState(mediaId: Long, position: Long, isVideo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaDao.saveHistory(
                PlaybackHistory(
                    mediaId = mediaId,
                    position = position,
                    timestamp = System.currentTimeMillis(),
                    mediaType = if (isVideo) "VIDEO" else "AUDIO"
                )
            )
        }
    }

    // --- Media Loading ---

    fun scanMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _videoList.value = queryMedia(isVideo = true)
            _audioList.value = queryMedia(isVideo = false)
            _imageList.value = queryImages()
            _albums.value = queryAlbums()
        }
    }

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

        try {
            app.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media._ID else MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Audio.Media.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION)
                val artistColumn = if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST) else -1
                val albumIdColumn = if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID) else -1

                // Video Specific Columns
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return mediaList
    }

    private fun queryImages(): List<MediaFile> {
        val imageList = mutableListOf<MediaFile>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        try {
            app.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Unknown Image"
                    val contentUri = ContentUris.withAppendedId(collection, id)

                    // Construct MediaFile with isImage = true
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
        val collection = if (android.os.Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Albums.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return albumList
    }

    // --- Playlist Management ---

    private fun loadPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _playlists.value = playlistRepository.getPlaylists()
        }
    }

    fun createPlaylist(name: String) {
        val newPlaylist = Playlist(name = name)
        val updated = _playlists.value + newPlaylist
        _playlists.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.savePlaylists(updated)
        }
    }

    fun deletePlaylist(playlistId: String) {
        val updated = _playlists.value.filter { it.id != playlistId }
        _playlists.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.savePlaylists(updated)
        }
    }

    fun addSongToPlaylist(playlistId: String, mediaId: Long) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        if (playlist.mediaIds.contains(mediaId)) return

        val updatedPlaylist = playlist.copy(mediaIds = playlist.mediaIds + mediaId)
        val updatedList = _playlists.value.map { if (it.id == playlistId) updatedPlaylist else it }

        _playlists.value = updatedList
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.savePlaylists(updatedList)
        }
    }

    fun removeSongFromPlaylist(playlistId: String, mediaId: Long) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        val updatedPlaylist = playlist.copy(mediaIds = playlist.mediaIds - mediaId)
        val updatedList = _playlists.value.map { if (it.id == playlistId) updatedPlaylist else it }

        _playlists.value = updatedList
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.savePlaylists(updatedList)
        }
    }

    fun toggleAlbumInFavorites(albumSongs: List<MediaFile>) {
        val favName = "Favorites"
        var favPlaylist = _playlists.value.find { it.name == favName }

        if (favPlaylist == null) {
            createPlaylist(favName)
            favPlaylist = _playlists.value.find { it.name == favName }
        }

        if (favPlaylist == null) return

        val actualFav = favPlaylist
        val allInFav = albumSongs.all { actualFav.mediaIds.contains(it.id) }
        val newMediaIds = actualFav.mediaIds.toMutableList()

        if (allInFav) {
            albumSongs.forEach { newMediaIds.remove(it.id) }
        } else {
            albumSongs.forEach {
                if (!newMediaIds.contains(it.id)) newMediaIds.add(it.id)
            }
        }

        val updatedPlaylist = actualFav.copy(mediaIds = newMediaIds)
        val updatedList = _playlists.value.map { if (it.id == actualFav.id) updatedPlaylist else it }

        _playlists.value = updatedList
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.savePlaylists(updatedList)
        }
    }

    // --- Playback Logic ---

    fun playMedia(media: MediaFile) {
        if (media.isVideo) {
            // Reset player state for new video
            _isPlayerLocked.value = false
            _playbackSpeed.value = 1.0f
            _resizeMode.value = ResizeMode.FIT
            playSingleMedia(media)
        } else if (media.isImage) {
            // No-op for now
        } else {
            // Audio playback - set up queue
            val currentVisibleList = filteredAudioList.value.takeIf { it.isNotEmpty() } ?: _audioList.value

            val startIndex = currentVisibleList.indexOfFirst { it.id == media.id }
            if (startIndex >= 0) {
                setQueue(currentVisibleList, startIndex, false)
            }
        }
    }

    fun playMediaFromList(media: MediaFile, list: List<MediaFile>) {
        val startIndex = list.indexOfFirst { it.id == media.id }
        if (startIndex >= 0) {
            setQueue(list, startIndex, false)
        }
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean) {
        val allAudio = _audioList.value
        val playlistSongs = playlist.mediaIds.mapNotNull { id ->
            allAudio.find { it.id == id }
        }
        if (playlistSongs.isNotEmpty()) {
            val startIndex = if (shuffle) (playlistSongs.indices).random() else 0
            setQueue(playlistSongs, startIndex, shuffle)
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

    private fun playSingleMedia(media: MediaFile) {
        viewModelScope.launch(Dispatchers.IO) {
            // Optional: restore position from history if it exists
            // val history = mediaDao.getHistory(media.id)
            // val startPos = history?.position ?: 0L

            // For now, start from 0 to avoid confusing "basic init"
            // In future update, uncomment above and use startPos
        }
        _player.value?.let { controller ->
            controller.setMediaItem(media.toMediaItem())
            controller.prepare()
            controller.play()
        }
    }

    fun setQueue(songs: List<MediaFile>, startIndex: Int, shuffle: Boolean = false) {
        _player.value?.let { controller ->
            val mediaItems = songs.map { it.toMediaItem() }
            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.shuffleModeEnabled = shuffle
            controller.prepare()
            controller.play()
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

    // --- Video Player Specific Actions ---

    fun toggleLock() {
        _isPlayerLocked.value = !_isPlayerLocked.value
    }

    fun toggleResizeMode() {
        val modes = ResizeMode.values()
        val nextIndex = (_resizeMode.value.ordinal + 1) % modes.size
        _resizeMode.value = modes[nextIndex]
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
        val current = _playbackSpeed.value
        val nextIndex = speeds.indexOfFirst { it > current }
        val newSpeed = if (nextIndex != -1) speeds[nextIndex] else speeds[0]

        _player.value?.setPlaybackSpeed(newSpeed)
        _playbackSpeed.value = newSpeed // Listener updates too, but this is immediate for UI
    }

    fun setPipMode(isPip: Boolean) {
        _isInPipMode.value = isPip
    }

    // --- Controls ---

    fun playNext() {
        _player.value?.let { if (it.hasNextMediaItem()) it.seekToNext() }
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

    fun toggleShuffle() {
        _player.value?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
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

    fun rewind() {
        _player.value?.let {
            it.seekTo((it.currentPosition - 10000).coerceAtLeast(0))
        }
    }

    fun forward() {
        _player.value?.let {
            it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration))
        }
    }

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
