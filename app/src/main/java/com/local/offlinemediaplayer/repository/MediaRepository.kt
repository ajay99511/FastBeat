package com.local.offlinemediaplayer.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.local.offlinemediaplayer.data.ThumbnailManager
import com.local.offlinemediaplayer.model.Album
import com.local.offlinemediaplayer.model.MediaFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Columns read for every image.
 *
 * This was `_ID`, `DISPLAY_NAME` and `SIZE` — three columns, which is why the Images tab had no
 * dates to group or sort by, no folders, and an info button in the viewer with nothing behind it
 * and therefore no click handler (DS-7.4). Widening it is one change that unblocks four features.
 */
private val IMAGE_PROJECTION =
    arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
    )

/**
 * Maps a MediaStore images cursor onto [MediaFile]s.
 *
 * Separate from the query so it can be tested against a `MatrixCursor` — column mapping is string
 * keys and index arithmetic, which the compiler cannot check and which fails silently by producing
 * plausible-looking wrong values. The same argument `MediaDaoTest` makes for SQL literals.
 *
 * Every column past the first two is read through `getColumnIndex`, not `getColumnIndexOrThrow`:
 * MediaStore does not guarantee a column exists on every OEM build or volume, and a missing
 * *optional* column should cost that one field, not the entire image list.
 */
internal fun readImages(
    cursor: Cursor,
    collection: Uri,
): List<MediaFile> {
    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
    val sizeColumn = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
    val dateAddedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
    val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
    val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
    val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
    val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
    val bucketIdColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
    val bucketNameColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

    val images = ArrayList<MediaFile>(cursor.count)
    while (cursor.moveToNext()) {
        val id = cursor.getLong(idColumn)
        val name = cursor.getString(nameColumn) ?: "Unknown Image"
        images +=
            MediaFile(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                title = name,
                displayName = name,
                artist = null,
                duration = 0,
                isVideo = false,
                isImage = true,
                albumArtUri = null,
                albumId = -1,
                size = cursor.longOr(sizeColumn),
                dateAdded = cursor.longOr(dateAddedColumn),
                dateModified = cursor.longOr(dateModifiedColumn),
                width = cursor.intOr(widthColumn),
                height = cursor.intOr(heightColumn),
                mimeType = cursor.stringOr(mimeTypeColumn),
                bucketId = cursor.stringOr(bucketIdColumn),
                // Files at the volume root have no bucket name. "Unknown" matches what the video
                // query already substitutes, so the two media types group the same way.
                bucketName = cursor.stringOr(bucketNameColumn).ifEmpty { "Unknown" },
            )
    }
    return images
}

/** Reads a column that may be absent or NULL, which MediaStore permits for optional columns. */
private fun Cursor.longOr(column: Int): Long = if (column != -1 && !isNull(column)) getLong(column) else 0L

private fun Cursor.intOr(column: Int): Int = if (column != -1 && !isNull(column)) getInt(column) else 0

private fun Cursor.stringOr(column: Int): String = if (column != -1 && !isNull(column)) getString(column) ?: "" else ""

@Singleton
class MediaRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val thumbnailManager: ThumbnailManager,
    ) {
        companion object {
            private const val TAG = "MediaRepository"

            /** Audio shorter than this is a ringtone or a voice memo, not a track. */
            private const val MIN_AUDIO_DURATION_MS = 45_000
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

        // Shared id -> MediaFile index across audio/video/image, so callers can resolve a media item
        // in O(1) instead of repeatedly scanning the lists with find{}.
        val mediaById: StateFlow<Map<Long, MediaFile>> =
            combine(_audioList, _videoList, _imageList) { audio, video, images ->
                (audio + video + images).associateBy { it.id }
            }.stateIn(repositoryScope, SharingStarted.Eagerly, emptyMap())

        suspend fun scanMedia(): Pair<List<MediaFile>, List<MediaFile>> {
            // Atomic guard — only one scan can run at a time (prevents TOCTOU race)
            if (!_isRefreshing.compareAndSet(expect = false, update = true)) {
                return Pair(_videoList.value, _audioList.value)
            }

            return withContext(Dispatchers.IO) {
                try {
                    val videos = queryMedia(isVideo = true)
                    val audio = queryMedia(isVideo = false)

                    val videosWithCachedThumbs =
                        videos.map { v ->
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
                    if (isVideo) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    }
                } else {
                    if (isVideo) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
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
                        MediaStore.Video.Media.DATE_MODIFIED,
                        MediaStore.Video.Media.DATE_ADDED,
                        MediaStore.Video.Media.DATA,
                        MediaStore.Video.Media.MIME_TYPE,
                    )
                } else {
                    arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.DATE_MODIFIED,
                        MediaStore.Audio.Media.DATE_ADDED,
                        MediaStore.Audio.Media.YEAR,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.MIME_TYPE,
                    )
                }
            // Music only, and long enough not to be a notification tone or voice memo.
            val selection =
                if (isVideo) {
                    null
                } else {
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                        "${MediaStore.Audio.Media.DURATION} >= $MIN_AUDIO_DURATION_MS"
                }

            try {
                context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                    val idColumn =
                        cursor.getColumnIndexOrThrow(
                            if (isVideo) MediaStore.Video.Media._ID else MediaStore.Audio.Media._ID,
                        )
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(
                            if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Audio.Media.TITLE,
                        )
                    val durationColumn =
                        cursor.getColumnIndexOrThrow(
                            if (isVideo) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION,
                        )
                    val artistColumn = if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST) else -1
                    val albumIdColumn =
                        if (!isVideo) {
                            cursor.getColumnIndexOrThrow(
                                MediaStore.Audio.Media.ALBUM_ID,
                            )
                        } else {
                            -1
                        }
                    val audioSizeColumn = if (!isVideo) cursor.getColumnIndex(MediaStore.Audio.Media.SIZE) else -1
                    val audioDateModifiedColumn =
                        if (!isVideo) {
                            cursor.getColumnIndex(
                                MediaStore.Audio.Media.DATE_MODIFIED,
                            )
                        } else {
                            -1
                        }
                    val audioDateAddedColumn =
                        if (!isVideo) {
                            cursor.getColumnIndex(
                                MediaStore.Audio.Media.DATE_ADDED,
                            )
                        } else {
                            -1
                        }
                    val audioYearColumn = if (!isVideo) cursor.getColumnIndex(MediaStore.Audio.Media.YEAR) else -1
                    val audioDisplayNameColumn =
                        if (!isVideo) {
                            cursor.getColumnIndex(
                                MediaStore.Audio.Media.DISPLAY_NAME,
                            )
                        } else {
                            -1
                        }
                    val audioAlbumColumn = if (!isVideo) cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM) else -1

                    // DISPLAY_NAME/DATA/MIME_TYPE share column names across audio and video.
                    val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val mimeTypeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                    val bucketIdColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID) else -1
                    val bucketNameColumn =
                        if (isVideo) {
                            cursor.getColumnIndex(
                                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                            )
                        } else {
                            -1
                        }
                    val sizeColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.SIZE) else -1
                    val widthColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.WIDTH) else -1
                    val heightColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT) else -1
                    val dateModifiedColumn =
                        if (isVideo) {
                            cursor.getColumnIndex(
                                MediaStore.Video.Media.DATE_MODIFIED,
                            )
                        } else {
                            -1
                        }
                    val dateAddedColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED) else -1

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
                        var dateAdded: Long = 0
                        var year: Int? = null
                        var album: String? = null

                        val path = if (dataColumn != -1) cursor.getString(dataColumn) ?: "" else ""
                        val mimeType = if (mimeTypeColumn != -1) cursor.getString(mimeTypeColumn) ?: "" else ""
                        // Video "title" is already the display name; audio has a separate column.
                        var displayName = if (isVideo) name else ""

                        if (isVideo) {
                            bucketId = if (bucketIdColumn != -1) cursor.getString(bucketIdColumn) ?: "" else ""
                            bucketName =
                                if (bucketNameColumn !=
                                    -1
                                ) {
                                    cursor.getString(bucketNameColumn) ?: "Unknown"
                                } else {
                                    "Unknown"
                                }
                            size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0
                            dateModified = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0
                            dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn) else 0

                            val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                            val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0

                            resolution =
                                if (height >= 2160) {
                                    "4K"
                                } else if (height >= 1080) {
                                    "1080P"
                                } else if (height >= 720) {
                                    "720P"
                                } else if (height >= 480) {
                                    "480P"
                                } else if (height > 0) {
                                    "${height}P"
                                } else {
                                    ""
                                }
                        } else {
                            artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                            albumId = cursor.getLong(albumIdColumn)
                            size = if (audioSizeColumn != -1) cursor.getLong(audioSizeColumn) else 0
                            dateModified =
                                if (audioDateModifiedColumn != -1) cursor.getLong(audioDateModifiedColumn) else 0
                            dateAdded = if (audioDateAddedColumn != -1) cursor.getLong(audioDateAddedColumn) else 0
                            // Same plausibility floor used for Album.firstYear.
                            val rawYear = if (audioYearColumn != -1) cursor.getInt(audioYearColumn) else 0
                            year = if (rawYear > 1900) rawYear else null
                            displayName =
                                if (audioDisplayNameColumn != -1) cursor.getString(audioDisplayNameColumn) ?: "" else ""
                            album = if (audioAlbumColumn != -1) cursor.getString(audioAlbumColumn) else null
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
                                dateModified,
                                dateAdded,
                                year = year,
                                displayName = displayName,
                                path = path,
                                mimeType = mimeType,
                                album = album,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query media", e)
            }
            return mediaList
        }

        private fun queryImages(): List<MediaFile> {
            val collection =
                if (Build.VERSION.SDK_INT >= 29) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            return try {
                context.contentResolver
                    .query(
                        collection,
                        IMAGE_PROJECTION,
                        null,
                        null,
                        "${MediaStore.Images.Media.DATE_ADDED} DESC",
                    )?.use { cursor -> readImages(cursor, collection) } ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query images", e)
                emptyList()
            }
        }

        private fun queryAlbums(): List<Album> {
            val albumList = mutableListOf<Album>()
            val collection =
                if (Build.VERSION.SDK_INT >= 29) {
                    MediaStore.Audio.Albums.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
                }
            val projection =
                arrayOf(
                    MediaStore.Audio.Albums._ID,
                    MediaStore.Audio.Albums.ALBUM,
                    MediaStore.Audio.Albums.ARTIST,
                    MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                    MediaStore.Audio.Albums.FIRST_YEAR,
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

        /**
         * Reflects a successful on-disk rename in the in-memory lists so the UI
         * updates without a full rescan. Video titles are display names, so they
         * follow the file; audio titles come from the tag and only follow when
         * they previously mirrored the old file name (untagged files).
         */
        fun applyRename(
            id: Long,
            newDisplayName: String,
        ) {
            fun MediaFile.renamed(): MediaFile {
                val newPath =
                    if (path.isNotEmpty()) {
                        val parent = path.substringBeforeLast('/', "")
                        if (parent.isEmpty()) newDisplayName else "$parent/$newDisplayName"
                    } else {
                        path
                    }
                val oldBaseName = displayName.substringBeforeLast('.')
                val newBaseName = newDisplayName.substringBeforeLast('.')
                val newTitle =
                    when {
                        isVideo -> newDisplayName
                        title == displayName || title == oldBaseName -> newBaseName
                        else -> title
                    }
                return copy(title = newTitle, displayName = newDisplayName, path = newPath)
            }
            _videoList.update { list -> list.map { if (it.id == id) it.renamed() else it } }
            _audioList.update { list -> list.map { if (it.id == id) it.renamed() else it } }
        }

        fun removeMediaIds(ids: List<Long>) {
            val idSet = ids.toSet()
            _videoList.update { list -> list.filter { it.id !in idSet } }
            _audioList.update { list -> list.filter { it.id !in idSet } }
            _imageList.update { list -> list.filter { it.id !in idSet } }
        }
    }
