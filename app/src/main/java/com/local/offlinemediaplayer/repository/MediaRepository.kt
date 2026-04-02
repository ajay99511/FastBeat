package com.local.offlinemediaplayer.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.local.offlinemediaplayer.data.ThumbnailManager
import com.local.offlinemediaplayer.model.Album
import com.local.offlinemediaplayer.model.MediaFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val thumbnailManager: ThumbnailManager
) {
    companion object {
        private const val TAG = "MediaRepository"
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _videoList = MutableStateFlow<List<MediaFile>>(emptyList())
    val videoList = _videoList.asStateFlow()

    private val _audioList = MutableStateFlow<List<MediaFile>>(emptyList())
    val audioList = _audioList.asStateFlow()

    private val _imageList = MutableStateFlow<List<MediaFile>>(emptyList())
    val imageList = _imageList.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums = _albums.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    suspend fun scanMedia(): Pair<List<MediaFile>, List<MediaFile>> {
        if (_isRefreshing.value) return Pair(_videoList.value, _audioList.value)

        return withContext(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                val videos = queryMedia(isVideo = true)
                val audio = queryMedia(isVideo = false)

                val videosWithCachedThumbs = videos.map { v ->
                    val cached = thumbnailManager.getCachedPath(v)
                    if (cached != null) v.copy(thumbnailPath = cached) else v
                }
                
                _videoList.value = videosWithCachedThumbs
                _audioList.value = audio
                _imageList.value = queryImages()
                _albums.value = queryAlbums()

                // Generate missing thumbnails in background
                val uncachedVideos = _videoList.value.filter { it.thumbnailPath == null }
                if (uncachedVideos.isNotEmpty()) {
                    repositoryScope.launch {
                        thumbnailManager.generateThumbnails(uncachedVideos).collect { (id, path) ->
                            _videoList.update { list ->
                                list.map { if (it.id == id) it.copy(thumbnailPath = path) else it }
                            }
                        }
                    }
                }

                // Clean up stale thumbnails
                repositoryScope.launch {
                    thumbnailManager.cleanStaleThumbnails(_videoList.value)
                }

                Pair(videosWithCachedThumbs, audio)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

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
                            MediaStore.Audio.Media.ALBUM_ID,
                            MediaStore.Audio.Media.SIZE
                    )
                }
        val selection = if (!isVideo) "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 45000" else null

        try {
            context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media._ID else MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Audio.Media.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION)
                val artistColumn = if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST) else -1
                val albumIdColumn = if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID) else -1
                val audioSizeColumn = if (!isVideo) cursor.getColumnIndex(MediaStore.Audio.Media.SIZE) else -1

                val bucketIdColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID) else -1
                val bucketNameColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME) else -1
                val sizeColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.SIZE) else -1
                val widthColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.WIDTH) else -1
                val heightColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT) else -1
                val dateModifiedColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED) else -1

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
                        bucketId = if (bucketIdColumn != -1) cursor.getString(bucketIdColumn) ?: "" else ""
                        bucketName = if (bucketNameColumn != -1) cursor.getString(bucketNameColumn) ?: "Unknown" else "Unknown"
                        size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0
                        dateModified = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0

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
                        size = if (audioSizeColumn != -1) cursor.getLong(audioSizeColumn) else 0
                        val sArtworkUri = "content://media/external/audio/albumart".toUri()
                        albumArtUri = ContentUris.withAppendedId(sArtworkUri, albumId)
                    }

                    mediaList.add(
                            MediaFile(
                                    id, contentUri, name, artist, duration, isVideo, false, albumArtUri, albumId, bucketId, bucketName, size, resolution, dateModified
                            )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query media", e)
        }
        return mediaList
    }

    private fun queryImages(): List<MediaFile> {
        val imageList = mutableListOf<MediaFile>()
        val collection =
                if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        try {
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Unknown Image"
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    imageList.add(
                            MediaFile(
                                    id = id, uri = contentUri, title = name, artist = null, duration = 0, isVideo = false, isImage = true, albumArtUri = null, albumId = -1
                            )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query media", e)
        }
        return imageList
    }

    private fun queryAlbums(): List<Album> {
        val albumList = mutableListOf<Album>()
        val collection =
                if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Albums.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
                MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.NUMBER_OF_SONGS, MediaStore.Audio.Albums.FIRST_YEAR
        )
        try {
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
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
            Log.e(TAG, "Failed to query media", e)
        }
        return albumList
    }

    fun removeMediaIds(ids: List<Long>) {
        val idSet = ids.toSet()
        _videoList.update { list -> list.filter { it.id !in idSet } }
        _audioList.update { list -> list.filter { it.id !in idSet } }
        _imageList.update { list -> list.filter { it.id !in idSet } }
    }

    /**
     * Cancel the internal background scope.
     * Call this when the repository is no longer needed to prevent resource leaks.
     */
    fun cleanup() {
        repositoryScope.cancel()
    }
}
