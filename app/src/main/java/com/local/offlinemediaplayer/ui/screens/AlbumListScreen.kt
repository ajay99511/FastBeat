
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
//import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.GridView
//import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
//import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.local.offlinemediaplayer.viewmodel.AlbumSortOption
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Close

@Composable
fun AlbumListScreen(
    viewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onAlbumClick: (Long) -> Unit,
    onAddMultipleToPlaylist: (List<MediaFile>) -> Unit,
    isSearchVisible: Boolean
) {
    val albums by libraryViewModel.filteredAlbums.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.albumSearchQuery.collectAsStateWithLifecycle()
    val sortOption by libraryViewModel.albumSortOption.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isMiniPlayerVisible = currentTrack != null && !currentTrack!!.isVideo
    val bottomPadding = if (isMiniPlayerVisible) 100.dp else 16.dp

    // Local state for view mode (Grid vs List) - Persisted across recompositions but not app restarts
    var isListView by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val isAlbumSelectionMode by libraryViewModel.isAlbumSelectionMode.collectAsStateWithLifecycle()
    val selectedAlbumIds by libraryViewModel.selectedAlbumIds.collectAsStateWithLifecycle()
    val allAudio by libraryViewModel.audioList.collectAsStateWithLifecycle()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Intent Event Launcher for Deletion
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            libraryViewModel.onAlbumDeleteSuccess()
        }
    }

    LaunchedEffect(Unit) {
        libraryViewModel.deleteIntentEvent.collect { intentSender ->
            val request = IntentSenderRequest.Builder(intentSender).build()
            launcher.launch(request)
        }
    }

    BackHandler(enabled = isAlbumSelectionMode) {
        libraryViewModel.toggleAlbumSelectionMode(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Collapsible Search Bar
        CollapsibleSearchBox(
            isVisible = isSearchVisible,
            query = searchQuery,
            onQueryChange = { libraryViewModel.updateAlbumSearchQuery(it) },
            placeholderText = "Search albums..."
        )

        // 2. Control Row (Count + Actions) or Selection Header
        if (isAlbumSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { libraryViewModel.toggleAlbumSelectionMode(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Selection", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = "${selectedAlbumIds.size} Selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Select All Toggle
                    val allSelected = albums.isNotEmpty() && selectedAlbumIds.size == albums.size
                    IconButton(onClick = {
                        if (allSelected) {
                            libraryViewModel.toggleAlbumSelectionMode(false)
                        } else {
                            libraryViewModel.selectAllAlbums(albums.map { it.id })
                        }
                    }) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    // Add to Playlist
                    IconButton(onClick = {
                        val selectedSongs = allAudio.filter { selectedAlbumIds.contains(it.albumId) }
                        if (selectedSongs.isNotEmpty()) {
                            onAddMultipleToPlaylist(selectedSongs)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist", tint = MaterialTheme.colorScheme.primary)
                    }
                    // Delete
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${albums.size} ALBUMS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                // View Toggle
                IconButton(onClick = { isListView = !isListView }) {
                    Icon(
                        imageVector = if (isListView) Icons.Default.GridView else Icons.AutoMirrored.Filled.List,
                        contentDescription = if (isListView) "Grid View" else "List View",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sort Button
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort Albums",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (A-Z)") },
                            onClick = {
                                libraryViewModel.updateAlbumSortOption(AlbumSortOption.NAME_ASC)
                                showSortMenu = false
                            },
                            trailingIcon = {
                                if (sortOption == AlbumSortOption.NAME_ASC) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow, // Checkmark proxy or similar if needed, or just highlight
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Artist (A-Z)") },
                            onClick = {
                                libraryViewModel.updateAlbumSortOption(AlbumSortOption.ARTIST_ASC)
                                showSortMenu = false
                            },
                             trailingIcon = {
                                if (sortOption == AlbumSortOption.ARTIST_ASC) {
                                     Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Year (Newest)") },
                            onClick = {
                                libraryViewModel.updateAlbumSortOption(AlbumSortOption.YEAR_DESC)
                                showSortMenu = false
                            },
                             trailingIcon = {
                                if (sortOption == AlbumSortOption.YEAR_DESC) {
                                     Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Song Count") },
                            onClick = {
                                libraryViewModel.updateAlbumSortOption(AlbumSortOption.SONG_COUNT_DESC)
                                showSortMenu = false
                            },
                             trailingIcon = {
                                if (sortOption == AlbumSortOption.SONG_COUNT_DESC) {
                                     Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
        }

        // 3. Album List / Grid
        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isNotEmpty()) "No results found" else "No albums found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (isListView) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomPadding), // Padding for MiniPlayer
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albums) { album ->
                        val isSelected = selectedAlbumIds.contains(album.id)
                        AlbumListItem(
                            album = album,
                            isSelectionMode = isAlbumSelectionMode,
                            isSelected = isSelected,
                            onClick = { 
                                if (isAlbumSelectionMode) {
                                    libraryViewModel.toggleAlbumSelection(album.id)
                                } else {
                                    onAlbumClick(album.id) 
                                }
                            },
                            onLongClick = {
                                libraryViewModel.toggleAlbumSelectionMode(true)
                                libraryViewModel.toggleAlbumSelection(album.id)
                            },
                            onPlayClick = {
                                viewModel.playAlbum(album, false)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 88.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
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
                        val isSelected = selectedAlbumIds.contains(album.id)
                        AlbumItemStyled(
                            album = album,
                            isSelectionMode = isAlbumSelectionMode,
                            isSelected = isSelected,
                            onClick = { 
                                if (isAlbumSelectionMode) {
                                    libraryViewModel.toggleAlbumSelection(album.id)
                                } else {
                                    onAlbumClick(album.id) 
                                }
                            },
                            onLongClick = {
                                libraryViewModel.toggleAlbumSelectionMode(true)
                                libraryViewModel.toggleAlbumSelection(album.id)
                            },
                            onPlayClick = {
                                viewModel.playAlbum(album, false)
                            }
                        )
                    }
                    // Padding for MiniPlayer
                    item { Spacer(modifier = Modifier.height(if (isMiniPlayerVisible) 70.dp else 0.dp)) }
                    item { Spacer(modifier = Modifier.height(if (isMiniPlayerVisible) 70.dp else 0.dp)) }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        DeleteConfirmationDialog(
            count = selectedAlbumIds.size,
            onConfirm = { libraryViewModel.deleteSelectedAlbums() },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumListItem(
    album: Album,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val primaryAccent = Color(0xFFE11D48)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = "Select",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Thumbnail
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = album.albumArtUri ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )
             // Play Button Overlay (Mini)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .background(primaryAccent.copy(alpha = 0.7f), CircleShape)
                    .clickable { onPlayClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Album",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Meta (Year / Count)
        Column(horizontalAlignment = Alignment.End) {
             if (album.firstYear != null) {
                Text(
                    text = album.firstYear.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
             }
            Text(
                text = "${album.songCount} songs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumItemStyled(
    album: Album,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val primaryAccent = Color(0xFFE11D48)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
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

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Select",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}
