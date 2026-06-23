package com.local.offlinemediaplayer.model

import android.net.Uri

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val albumArtUri: Uri?
)
