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
import androidx.compose.material.icons.outlined.Edit
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
import com.local.offlinemediaplayer.ui.components.RenamePlaylistDialog
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val allAudio by viewModel.audioList.collectAsStateWithLifecycle()

    val playlist = playlists.find { it.id == playlistId }

    // Safety check if playlist was deleted
    if (playlist == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val songs = playlist.mediaIds.mapNotNull { id -> allAudio.find { it.id == id } }
    val coverArtUri = songs.firstOrNull()?.albumArtUri

    // Colors
    val primaryAccent = Color(0xFFE11D48) // FastBeat Red

    // UI States
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. Background (Blurred/Darkened first song art or Gradient)
        if (coverArtUri != null) {
            AsyncImage(
                model = coverArtUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.6f // Dimmed for readability
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2B2930), Color.Black)
                        )
                    )
            )
        }

        // Gradient Overlay for readability (Fade to black at bottom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.9f),
                            Color.Black
                        ),
                        startY = 0f,
                        endY = 1400f
                    )
                )
        )

        // 2. Content
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
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.MoreVert, "Options", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF2B2930))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = Color.White) },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = Color.White) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFFF8A80)) },
                            onClick = {
                                showMenu = false
                                viewModel.deletePlaylist(playlistId)
                                onBack()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Color(0xFFFF8A80)) }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp) // Space for MiniPlayer
            ) {
                // Header Info
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Cover Art
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(10.dp),
                            modifier = Modifier.size(180.dp)
                        ) {
                            if (coverArtUri != null) {
                                AsyncImage(
                                    model = coverArtUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF333333)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
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
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stats
                        Text(
                            text = "Playlist • ${songs.size} Songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
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
                                    if (songs.isNotEmpty()) {
                                        viewModel.playPlaylist(playlist, false)
                                        onNavigateToPlayer()
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
                                Text("Play", fontWeight = FontWeight.Bold)
                            }

                            // Shuffle
                            Button(
                                onClick = {
                                    if (songs.isNotEmpty()) {
                                        viewModel.playPlaylist(playlist, true)
                                        onNavigateToPlayer()
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2B2930),
                                    contentColor = Color.White
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

                // Song List
                if (songs.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No songs yet.\nAdd some tracks from the library!",
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    itemsIndexed(songs) { index, song ->
                        PlaylistItemStyled(
                            index = index + 1,
                            song = song,
                            onClick = {
                                viewModel.setQueue(songs, index, false)
                                onNavigateToPlayer()
                            },
                            onRemove = {
                                viewModel.removeSongFromPlaylist(playlistId, song.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                viewModel.renamePlaylist(playlistId, newName)
            }
        )
    }
}

@Composable
fun PlaylistItemStyled(
    index: Int,
    song: MediaFile,
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
            color = Color.Gray,
            modifier = Modifier.width(30.dp)
        )

        // Art
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(48.dp),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
        ) {
            AsyncImage(
                model = song.albumArtUri ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist ?: "Unknown",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Remove Button
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.DarkGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
