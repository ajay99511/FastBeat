package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.ui.components.MiniPlayer
import com.local.offlinemediaplayer.ui.components.RenamePlaylistDialog
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel

// Sort options for audio playlist
enum class AudioSortOption(val label: String) {
    DEFAULT("Default"),
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Duration"),
    SIZE("Size"),
    DATE_MODIFIED("Date Modified"),
    MOST_PLAYED("Most Played")
}

@Composable
fun PlaylistDetailScreen(
        playlistId: String,
        viewModel: PlaybackViewModel,
        libraryViewModel: LibraryViewModel = hiltViewModel(),
        playlistViewModel: PlaylistViewModel = hiltViewModel(),
        onBack: () -> Unit,
        onNavigateToPlayer: () -> Unit
) {
    val playlists by playlistViewModel.audioPlaylists.collectAsStateWithLifecycle()
    val allAudio by libraryViewModel.audioList.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isMiniPlayerVisible = currentTrack != null && !currentTrack!!.isVideo
    val bottomPadding = if (isMiniPlayerVisible) 120.dp else 16.dp

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

    val songs = playlist.mediaIds.mapNotNull { id -> allAudio.find { it.id == id } }

    // Colors
    val primaryAccent = LocalAppTheme.current.primaryColor

    // UI States
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var selectedSort by remember { mutableStateOf(AudioSortOption.DEFAULT) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Fetch analytics for Most Played sort
    var playCountMap by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            val analytics = playlistViewModel.getAnalyticsForIds(songs.map { it.id })
            playCountMap = analytics.associate { it.mediaId to it.playCount }
        }
    }

    // Sort + Filter
    val sortedAndFilteredSongs = remember(songs, searchQuery, selectedSort, sortAscending, playCountMap) {
        val filtered = if (searchQuery.isEmpty()) songs
        else songs.filter { it.title.contains(searchQuery, ignoreCase = true) }

        val sorted = when (selectedSort) {
            AudioSortOption.DEFAULT -> filtered
            AudioSortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
            AudioSortOption.ARTIST -> filtered.sortedBy { (it.artist ?: "Unknown").lowercase() }
            AudioSortOption.DURATION -> filtered.sortedBy { it.duration }
            AudioSortOption.SIZE -> filtered.sortedBy { it.size }
            AudioSortOption.DATE_MODIFIED -> filtered.sortedBy { it.dateModified }
            AudioSortOption.MOST_PLAYED -> filtered.sortedByDescending { playCountMap[it.id] ?: 0 }
        }

        if (selectedSort != AudioSortOption.DEFAULT && selectedSort != AudioSortOption.MOST_PLAYED && !sortAscending) {
            sorted.reversed()
        } else sorted
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
            // ── Top Bar: Back + Search + Sort + Options Menu ──
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

                // Sort Toggle
                Box {
                    IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort",
                                tint = if (selectedSort != AudioSortOption.DEFAULT) primaryAccent
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        AudioSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                    text = {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                    option.label,
                                                    color = if (selectedSort == option) primaryAccent
                                                    else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (selectedSort == option && option != AudioSortOption.DEFAULT) {
                                                Icon(
                                                        if (option == AudioSortOption.MOST_PLAYED || !sortAscending)
                                                            Icons.Default.ArrowDownward
                                                        else Icons.Default.ArrowUpward,
                                                        contentDescription = null,
                                                        tint = primaryAccent,
                                                        modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        if (selectedSort == option && option != AudioSortOption.DEFAULT && option != AudioSortOption.MOST_PLAYED) {
                                            sortAscending = !sortAscending
                                        } else {
                                            selectedSort = option
                                            sortAscending = true
                                        }
                                        showSortMenu = false
                                    }
                            )
                        }
                    }
                }

                // Options Menu (Rename / Delete)
                Box {
                    IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            CircleShape
                                    )
                    ) {
                        Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
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
                                            "Rename",
                                            color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                            Icons.Outlined.Edit,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                        )
                        DropdownMenuItem(
                                text = {
                                    Text("Delete", color = Color(0xFFFF8A80))
                                },
                                onClick = {
                                    showMenu = false
                                    playlistViewModel.deletePlaylist(playlistId)
                                    onBack()
                                },
                                leadingIcon = {
                                    Icon(
                                            Icons.Outlined.Delete,
                                            null,
                                            tint = Color(0xFFFF8A80)
                                    )
                                }
                        )
                    }
                }
            }

            // ── Header Row: Playlist Name + Count + Play/Shuffle ──
            Row(
                    modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                                text = "${songs.size} Song${if (songs.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Sort indicator chip
                        if (selectedSort != AudioSortOption.DEFAULT) {
                            AssistChip(
                                    onClick = {
                                        selectedSort = AudioSortOption.DEFAULT
                                    },
                                    label = {
                                        Text(
                                                selectedSort.label,
                                                style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear Sort",
                                                modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                            containerColor = primaryAccent.copy(alpha = 0.15f),
                                            labelColor = primaryAccent
                                    ),
                                    border = null
                            )
                        }
                    }
                }

                // Play / Shuffle Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Play All
                    FilledIconButton(
                            onClick = {
                                if (sortedAndFilteredSongs.isNotEmpty()) {
                                    viewModel.playPlaylist(playlist, sortedAndFilteredSongs, false)
                                }
                            },
                            modifier = Modifier.size(42.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = primaryAccent,
                                    contentColor = Color.White
                            )
                    ) {
                        Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play All",
                                modifier = Modifier.size(22.dp)
                        )
                    }

                    // Shuffle
                    FilledIconButton(
                            onClick = {
                                if (sortedAndFilteredSongs.isNotEmpty()) {
                                    viewModel.playPlaylist(playlist, sortedAndFilteredSongs, true)
                                }
                            },
                            modifier = Modifier.size(42.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                            )
                    ) {
                        Icon(
                                Icons.Outlined.Shuffle,
                                contentDescription = "Shuffle",
                                modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── Song List ──
            if (sortedAndFilteredSongs.isEmpty()) {
                Box(
                        modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                            text = if (searchQuery.isNotEmpty()) "No results found"
                            else "No songs yet.\nAdd some tracks from the library!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 4.dp,
                                bottom = bottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(sortedAndFilteredSongs) { index, song ->
                        AudioPlaylistItemCard(
                                song = song,
                                accentColor = primaryAccent,
                                onClick = {
                                    // Use the original songs list for queue, not filtered
                                    val originalIndex = songs.indexOfFirst { it.id == song.id }
                                    if (originalIndex >= 0) {
                                        viewModel.setQueue(songs, originalIndex, false)
                                    }
                                },
                                onRemove = {
                                    playlistViewModel.removeSongFromPlaylist(playlistId, song.id)
                                },
                                onPlayNext = { viewModel.playNext(song) },
                                onAddToQueue = { viewModel.addToQueue(song) }
                        )
                    }
                }
            }
        }

        if (showRenameDialog) {
            RenamePlaylistDialog(
                    currentName = playlist.name,
                    onDismiss = { showRenameDialog = false },
                    onRename = { newName -> playlistViewModel.renamePlaylist(playlistId, newName) }
            )
        }

        MiniPlayer(
                viewModel = viewModel,
                onTap = onNavigateToPlayer,
                modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun AudioPlaylistItemCard(
        song: MediaFile,
        accentColor: Color,
        onClick: () -> Unit,
        onRemove: () -> Unit,
        onPlayNext: () -> Unit,
        onAddToQueue: () -> Unit
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
            // Album art with play overlay
            Box(
                    modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                        model = song.albumArtUri
                                ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                        contentDescription = null,
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
                                    .size(24.dp)
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
                                modifier = Modifier.size(16.dp)
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
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                        ),
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Artist + File size row
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = song.artist ?: "Unknown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                    )

                    if (song.size > 0) {
                        Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                                text = FormatUtils.formatSize(song.size),
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
                                        "Play Next",
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                            },
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
                                Text(
                                        "Add to Queue",
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                onAddToQueue()
                            },
                            leadingIcon = {
                                Icon(
                                        Icons.Default.QueuePlayNext,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                    )
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
                                        Icons.Default.Delete,
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
