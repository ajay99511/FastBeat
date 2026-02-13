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
    val thumbnailPath: String? = null
)
