
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
import androidx.compose.material.icons.outlined.Shuffle
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
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun VideoPlaylistDetailScreen(
    playlistId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: (MediaFile, List<MediaFile>) -> Unit
) {
    val playlists by viewModel.videoPlaylists.collectAsStateWithLifecycle()
    val allVideos by viewModel.videoList.collectAsStateWithLifecycle()

    val playlist = playlists.find { it.id == playlistId }

    // Safety check if playlist was deleted
    if (playlist == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val videos = playlist.mediaIds.mapNotNull { id -> allVideos.find { it.id == id } }
    val thumbUri = videos.firstOrNull()?.uri

    // Colors
    val primaryAccent = LocalAppTheme.current.primaryColor

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // CONTENT
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }

                IconButton(
                    onClick = {
                        viewModel.deletePlaylist(playlistId)
                        onBack()
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Header Info
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Thumbnail
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(10.dp),
                            modifier = Modifier.width(240.dp).aspectRatio(16f/9f)
                        ) {
                            if (thumbUri != null) {
                                AsyncImage(
                                    model = thumbUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Title
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stats
                        Text(
                            text = "${videos.size} Videos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Play All
                            Button(
                                onClick = {
                                    if (videos.isNotEmpty()) {
                                        viewModel.playPlaylist(playlist, false)
                                        onNavigateToPlayer(videos[0], videos)
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                                modifier = Modifier
                                    .height(50.dp)
                                    .weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Play All", fontWeight = FontWeight.Bold)
                            }

                            // Shuffle
                            Button(
                                onClick = {
                                    if (videos.isNotEmpty()) {
                                        viewModel.playPlaylist(playlist, true)
                                        // Since shuffled, we don't know the first song easily here without exposing it from VM, 
                                        // but playPlaylist sets the queue. The player needs a target media to open.
                                        // We can just open the first one for animation purposes, VM handles the actual source.
                                        // Actually, VM.playPlaylist updates the currentTrack internally.
                                        // We can observe currentTrack in NavHost to navigate, OR just navigate to a dummy and let VM play.
                                        // Ideally, pass the first item of the shuffled list.
                                        // For simplicity, we just pass the first video of the *unshuffled* list 
                                        // because the PlayerScreen will sync with the VM's currentTrack anyway.
                                        onNavigateToPlayer(videos[0], videos)
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .height(50.dp)
                                    .weight(1f)
                            ) {
                                Icon(Icons.Outlined.Shuffle, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Shuffle", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Video List
                if (videos.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No videos yet.\nAdd some videos from folders!",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    itemsIndexed(videos) { index, video ->
                        VideoPlaylistItem(
                            index = index + 1,
                            video = video,
                            onClick = {
                                // For playlist items, simply pass context, MainScreen will handle playVideoFromList
                                onNavigateToPlayer(video, videos)
                            },
                            onRemove = {
                                viewModel.removeSongFromPlaylist(playlistId, video.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlaylistItem(
    index: Int,
    video: MediaFile,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp)
        )

        // Thumb
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(45.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = video.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatDuration(video.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Remove Button
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
