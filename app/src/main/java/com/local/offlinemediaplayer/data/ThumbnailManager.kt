package com.local.offlinemediaplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import com.local.offlinemediaplayer.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale

/**
 * Manages video thumbnail generation and disk caching.
 *
 * Thumbnails are stored as JPEGs in [cacheDir] and keyed by
 * mediaId + file size + dateModified so they auto-regenerate
 * when the source video changes.
 */
@Singleton
class ThumbnailManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ThumbnailManager"
        private const val THUMBNAIL_DIR = "video_thumbnails"
        private const val MAX_THUMBNAIL_WIDTH = 360
        private const val JPEG_QUALITY = 75
    }

    private val cacheDir: File by lazy {
        File(context.filesDir, THUMBNAIL_DIR).also { it.mkdirs() }
    }

    /**
     * Build a deterministic filename for a video's thumbnail.
     * Changes when the file's size or modification time changes.
     */
    private fun cacheKey(video: MediaFile): String =
        "thumb_${video.id}_${video.size}_${video.dateModified}.jpg"

    /**
     * Returns the cached thumbnail path if it exists, or null.
     */
    fun getCachedPath(video: MediaFile): String? {
        val file = File(cacheDir, cacheKey(video))
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Generates thumbnails for all videos that don't already have a cached thumbnail.
     * Emits (mediaId, path) pairs as each thumbnail is generated.
     * Runs on [Dispatchers.IO] internally.
     */
    fun generateThumbnails(videos: List<MediaFile>): Flow<Pair<Long, String>> = flow {
        for (video in videos) {
            yield() // cooperative cancellation between items

            val cached = File(cacheDir, cacheKey(video))
            if (cached.exists()) {
                emit(video.id to cached.absolutePath)
                continue
            }

            try {
                val path = extractThumbnail(video, cached)
                if (path != null) {
                    emit(video.id to path)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate thumbnail for ${video.title}: ${e.message}")
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Extract a thumbnail for a single video and write it to [outputFile].
     * Tries embedded cover art first, then extracts a frame at ~10% of duration.
     */
    private fun extractThumbnail(video: MediaFile, outputFile: File): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, video.uri)

            // 1. Try embedded cover art (some videos have poster images)
            val embeddedArt = retriever.embeddedPicture
            if (embeddedArt != null) {
                val bitmap = BitmapFactory.decodeByteArray(embeddedArt, 0, embeddedArt.size)
                if (bitmap != null) {
                    val scaled = scaleBitmap(bitmap)
                    writeBitmapToFile(scaled, outputFile)
                    if (scaled !== bitmap) scaled.recycle()
                    bitmap.recycle()
                    return outputFile.absolutePath
                }
            }

            // 2. Extract a frame at ~10% of duration to avoid black intro frames
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: video.duration
            val seekTimeUs = (durationMs * 0.1).toLong() * 1000 // convert ms to µs

            val frame = retriever.getFrameAtTime(
                seekTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            if (frame != null) {
                val scaled = scaleBitmap(frame)
                writeBitmapToFile(scaled, outputFile)
                if (scaled !== frame) scaled.recycle()
                frame.recycle()
                return outputFile.absolutePath
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Retriever error for ${video.title}: ${e.message}")
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Scale a bitmap down to [MAX_THUMBNAIL_WIDTH] pixels wide,
     * preserving aspect ratio. Returns the original if already small enough.
     */
    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_THUMBNAIL_WIDTH) return bitmap
        val ratio = MAX_THUMBNAIL_WIDTH.toFloat() / bitmap.width
        val newHeight = (bitmap.height * ratio).toInt()
        return bitmap.scale(MAX_THUMBNAIL_WIDTH, newHeight)
    }

    /**
     * Compress and write a bitmap to a JPEG file on disk.
     */
    private fun writeBitmapToFile(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
    }

    /**
     * Remove cached thumbnails for videos that are no longer in the library.
     * Call after a media scan completes.
     */
    fun cleanStaleThumbnails(currentVideos: List<MediaFile>) {
        val validKeys = currentVideos.map { cacheKey(it) }.toSet()
        cacheDir.listFiles()?.forEach { file ->
            if (file.name !in validKeys) {
                file.delete()
            }
        }
    }
}
