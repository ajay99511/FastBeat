package com.local.offlinemediaplayer.model
import android.net.Uri

data class MediaFile(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String? = null,
    val duration: Long,
    val isVideo: Boolean,
    val isImage: Boolean = false,
    val albumArtUri: Uri? = null,
    val albumId: Long = -1,
    val bucketId: String = "",
    val bucketName: String = "",
    val size: Long = 0,
    val resolution: String = "",
    val dateModified: Long = 0,
    val dateAdded: Long = 0,
    val thumbnailPath: String? = null,
    /** Release year from the audio tag, null when missing/implausible. Audio only. */
    val year: Int? = null,
    /** File name on disk including extension (MediaStore DISPLAY_NAME). */
    val displayName: String = "",
    /** Absolute file path on disk (MediaStore DATA), empty when unavailable. */
    val path: String = "",
    /** MIME type, e.g. "video/mp4" or "audio/mpeg". */
    val mimeType: String = "",
    /** Album name from the audio tag. Audio only. */
    val album: String? = null,
    /**
     * Pixel dimensions, 0 when unknown. Images only for now.
     *
     * Kept separate from [resolution], which for video is a *quality bucket* ("4K", "1080P") rather
     * than a size. A photo's interesting fact is its actual dimensions, and overloading one field
     * with two meanings is how a field ends up having to be pulled apart later. Video already reads
     * these columns and discards them after bucketing; it can adopt these when something needs them.
     */
    val width: Int = 0,
    val height: Int = 0,
)
