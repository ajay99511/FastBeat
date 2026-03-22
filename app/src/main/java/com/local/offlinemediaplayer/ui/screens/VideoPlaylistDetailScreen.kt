package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel
import java.io.File

@Composable
fun VideoPlaylistDetailScreen(
        playlistId: String,
        viewModel: PlaybackViewModel,
        libraryViewModel: LibraryViewModel = hiltViewModel(),
        playlistViewModel: PlaylistViewModel = hiltViewModel(),
        onBack: () -> Unit,
        onNavigateToPlayer: (MediaFile, List<MediaFile>) -> Unit
) {
    val playlists by playlistViewModel.videoPlaylists.collectAsStateWithLifecycle()
    val allVideos by libraryViewModel.videoList.collectAsStateWithLifecycle()

    // Show loading state if playlists haven't hydrated from DB yet
    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val playlist = playlists.find { it.id == playlistId }

    // Safety check if playlist was deleted
    if (playlist == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val videos = playlist.mediaIds.mapNotNull { id -> allVideos.find { it.id == id } }

    // Colors
    val primaryAccent = LocalAppTheme.current.primaryColor

    // UI States
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }

    // Filter videos by search query
    val filteredVideos = if (searchQuery.isEmpty()) {
        videos
    } else {
        videos.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Warm gradient overlay at top
        Box(
                modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                                Brush.verticalGradient(
                                        colors = listOf(
                                                Color(0xFF3D1E0C),
                                                Color(0xFF2A1408),
                                                MaterialTheme.colorScheme.background
                                        )
                                )
                        )
        )

        // CONTENT
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar: Back + Search + Grid Toggle ──
            Row(
                    modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back Button
                IconButton(
                        onClick = onBack,
                        modifier = Modifier
                                .size(40.dp)
                                .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        CircleShape
                                )
                ) {
                    Icon(
                            Icons.Default.ArrowBackIosNew,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                    )
                }

                // Search Field
                TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                    "Search ${playlist.name}...",
                                    color = Color(0xFF7A7A7A),
                                    style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF7A7A7A),
                                    modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = primaryAccent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                )

                // Grid/List Toggle
                IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                            imageVector = if (isGridView) Icons.Default.FormatListNumbered
                            else Icons.Default.GridView,
                            contentDescription = "Toggle View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ── Header Row: Playlist Name + Count + Add Button ──
            Row(
                    modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                    Text(
                            text = "${videos.size} Video${if (videos.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                        onClick = {
                            if (videos.isNotEmpty()) {
                                viewModel.playPlaylist(playlist, false)
                                onNavigateToPlayer(videos[0], videos)
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                                containerColor = primaryAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                            "Add Videos",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                    )
                }
            }

            // ── Video List ──
            if (filteredVideos.isEmpty()) {
                Box(
                        modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                            text = if (searchQuery.isNotEmpty()) "No results found"
                            else "No videos yet.\nAdd some videos from folders!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filteredVideos) { index, video ->
                        VideoPlaylistItemCard(
                                video = video,
                                accentColor = primaryAccent,
                                onClick = { onNavigateToPlayer(video, videos) },
                                onRemove = {
                                    playlistViewModel.removeSongFromPlaylist(playlistId, video.id)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlaylistItemCard(
        video: MediaFile,
        accentColor: Color,
        onClick: () -> Unit,
        onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
            modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with play overlay + duration badge
            Box(
                    modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                        model = video.thumbnailPath?.let { File(it) } ?: video.uri,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                )

                // Play icon overlay
                Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                ) {
                    Box(
                            modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                            Color.Black.copy(alpha = 0.5f),
                                            CircleShape
                                    ),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Duration badge at bottom-right
                if (video.duration > 0) {
                    Surface(
                            color = Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                    ) {
                        Text(
                                text = FormatUtils.formatDuration(video.duration),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(
                                        horizontal = 4.dp,
                                        vertical = 1.dp
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info column
            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
            ) {
                // Title in accent color
                Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                        ),
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Resolution badge + File size row
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Resolution badge
                    if (video.resolution.isNotEmpty()) {
                        Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = accentColor
                        ) {
                            Text(
                                    text = video.resolution,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(
                                            horizontal = 6.dp,
                                            vertical = 2.dp
                                    )
                            )
                        }
                    }

                    // File size
                    if (video.size > 0) {
                        Text(
                                text = FormatUtils.formatSize(video.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3-dot menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                            text = {
                                Text(
                                        "Remove from Playlist",
                                        color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRemove()
                            },
                            leadingIcon = {
                                Icon(
                                        Icons.Outlined.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                )
                            }
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
