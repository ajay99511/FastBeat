
package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.Album
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun AlbumListScreen(
    viewModel: MainViewModel,
    onAlbumClick: (Long) -> Unit,
    isSearchVisible: Boolean
) {
    val albums by viewModel.filteredAlbums.collectAsStateWithLifecycle()
    val searchQuery by viewModel.albumSearchQuery.collectAsStateWithLifecycle()

    // Colors
    val backgroundColor = Color(0xFF12121A) // Ink Dark
    val cardBg = Color(0xFF1E1E24)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // 1. Collapsible Search Bar
        CollapsibleSearchBox(
            isVisible = isSearchVisible,
            query = searchQuery,
            onQueryChange = { viewModel.updateAlbumSearchQuery(it) },
            placeholderText = "Search albums..."
        )

        // 2. Count Text
        Text(
            text = "${albums.size} ALBUMS",
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 3. Album Grid
        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isNotEmpty()) "No results found" else "No albums found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(albums) { album ->
                    AlbumItemStyled(
                        album = album,
                        onClick = { onAlbumClick(album.id) },
                        onPlayClick = {
                            viewModel.playAlbum(album, false)
                        }
                    )
                }
                // Padding for MiniPlayer
                item { Spacer(modifier = Modifier.height(70.dp)) }
                item { Spacer(modifier = Modifier.height(70.dp)) }
            }
        }
    }
}

@Composable
fun AlbumItemStyled(
    album: Album,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val primaryAccent = Color(0xFFE11D48)
    val cardBg = Color(0xFF1E1E24)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        border = null
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Album Art Box with Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = album.albumArtUri ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                    contentDescription = album.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )

                // Play Button Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(primaryAccent.copy(alpha = 0.9f), CircleShape)
                        .clickable { onPlayClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Album",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Year • Song Count
            val metaText = buildString {
                if (album.firstYear != null) {
                    append("${album.firstYear} • ")
                }
                append("${album.songCount} Songs")
            }

            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray, // Slightly darker than artist text
                maxLines = 1
            )
        }
    }
}
