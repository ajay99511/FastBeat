
package com.local.offlinemediaplayer.model

import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mediaIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val isVideo: Boolean = false
)
