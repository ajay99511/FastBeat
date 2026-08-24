package com.local.offlinemediaplayer.model

import android.net.Uri

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val firstYear: Int? = null,
    val albumArtUri: Uri?,
)
