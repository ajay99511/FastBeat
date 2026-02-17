package com.local.offlinemediaplayer.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
//import androidx.compose.material.icons.filled.PlaylistPlay
//import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.MiniPlayer
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun AlbumDetailScreen(
        albumId: Long,
        viewModel: MainViewModel,
        onBack: () -> Unit,
        onNavigateToPlayer: () -> Unit
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val allAudio by viewModel.audioList.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val album = albums.find { it.id == albumId }
    val albumSongs = allAudio.filter { it.albumId == albumId }

    // Check if album is "Favorited" (i.e., all its songs are in Favorites playlist)
    val favPlaylist = playlists.find { it.name == "Favorites" }
    val isFavorite =
            if (favPlaylist != null && albumSongs.isNotEmpty()) {
                albumSongs.all { favPlaylist.mediaIds.contains(it.id) }
            } else false

    if (album == null) {
        onBack()
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // CONTENT (Removed full screen background image and gradient overlay to support Light Mode)

        // 3. Content
        Column(modifier = Modifier.fillMaxSize()) {
            // Custom Top Bar
            Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button with theme-aware background
                IconButton(
                        onClick = onBack,
                        modifier =
                                Modifier.background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                )
                ) {
                    Icon(
                            imageVector = Icons.Default.ArrowBackIosNew,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp) // Space for MiniPlayer
            ) {
                // Header Content
                item {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Small Album Art Card
                        Card(
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(8.dp),
                                modifier = Modifier.size(140.dp)
                        ) {
                            AsyncImage(
                                    model = album.albumArtUri
                                                    ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // "ALBUM" Label
                        Text(
                                text = "ALBUM",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary, // Theme primary
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Album Title
                        Text(
                                text = album.name,
                                style =
                                        MaterialTheme.typography.displaySmall.copy(
                                                fontWeight = FontWeight.Bold
                                        ),
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 40.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Metadata Row (Artist Image + Info)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Artist Avatar (Placeholder or re-use album art)
                            AsyncImage(
                                    model = album.albumArtUri,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    text = "${album.artist} • ${album.songCount} Songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Buttons Row (Play, Heart, More)
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            // Big Primary Play Button
                            Button(
                                    onClick = {
                                        if (albumSongs.isNotEmpty()) {
                                            viewModel.playAlbum(album, false)
                                        }
                                    },
                                    shape = CircleShape,
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor =
                                                            MaterialTheme.colorScheme.primary
                                            ),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            // Heart Icon (Favorites)
                            IconButton(onClick = { viewModel.toggleAlbumInFavorites(albumSongs) }) {
                                Icon(
                                        imageVector =
                                                if (isFavorite) Icons.Default.Favorite
                                                else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint =
                                                if (isFavorite) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                )
                            }

                            // More Options
                            IconButton(onClick = { /* TODO: More options */}) {
                                Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    // List Headers
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                text = "#",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(32.dp)
                        )
                        Text(
                                text = "TITLE",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                        )
                        Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Duration",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        thickness = DividerDefaults.Thickness,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                }

                // Song List
                itemsIndexed(albumSongs) { index, song ->
                    AlbumSongRow(
                            index = index + 1,
                            song = song,
                            onClick = {
                                viewModel.playMedia(song)
                            },
                            onPlayNext = { viewModel.playNext(song) },
                            onAddToQueue = { viewModel.addToQueue(song) }
                    )
                }
            }
        }
        MiniPlayer(
                viewModel = viewModel,
                onTap = onNavigateToPlayer,
                modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun AlbumSongRow(
        index: Int,
        song: MediaFile,
        onClick: () -> Unit,
        onPlayNext: () -> Unit,
        onAddToQueue: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // Index
        Text(
                text = "$index",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(32.dp)
        )

        // Title & Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = song.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
            Text(
                    text = song.artist ?: "Unknown",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }

        // Duration (Hide if menu is shown? No, just keep it)
        Text(
                text = formatDuration(song.duration),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.width(8.dp))

        // More Menu
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                        text = { Text("Play Next", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            onPlayNext()
                        },
                        leadingIcon = {
                            Icon(
                                    Icons.Default.PlayArrow,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                )
                DropdownMenuItem(
                        text = {
                            Text("Add to Queue", color = MaterialTheme.colorScheme.onSurface)
                        },
                        onClick = {
                            showMenu = false
                            onAddToQueue()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                )
                // Commenting out Add to Playlist for this iteration as strictly requested
                // `AlbumDetailScreen` doesn't have the callback wired yet
                // and I want to avoid breaking the helper signature chain right now.
                // Wait, I can't just ignore it if the user asked for it.
                // "Implement add to queue option ... from all the above screen"
                // The explicit request was "Play Next" and "Add to Queue". "Add to Playlist" was
                // already in my plan but might be scope creep if not careful.
                // I will add the menu item but leaving the callback empty or TODO if I can't easily
                // wire it.
                // Actually, let's just implement Play Next and Add to Queue as PRIMARY requests.
            }
        }
    }
}

@SuppressLint("DefaultLocale")
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
