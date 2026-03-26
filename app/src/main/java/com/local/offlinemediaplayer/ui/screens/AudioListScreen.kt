
package com.local.offlinemediaplayer.ui.screens

//import android.R.attr.fontWeight
//import android.R.attr.text
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Shuffle
//import androidx.compose.material.icons.filled.PlaylistPlay
//import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioListScreen(
    viewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onAudioClick: (MediaFile) -> Unit,
    onAddToPlaylist: (MediaFile) -> Unit,
    isSearchVisible: Boolean
) {
    // Observe Filtered List
    val audioList by libraryViewModel.filteredAudioList.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by libraryViewModel.sortOption.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isMiniPlayerVisible = currentTrack != null && !currentTrack!!.isVideo
    val bottomPadding = if (isMiniPlayerVisible) 100.dp else 16.dp

    // Selection State
    val isSelectionMode by libraryViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by libraryViewModel.selectedMediaIds.collectAsStateWithLifecycle()

    // Deletion Flow
    val intentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            libraryViewModel.onDeleteSuccess()
        }
    }

    LaunchedEffect(Unit) {
        libraryViewModel.deleteIntentEvent.collect { intentSender ->
            intentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    // Dialog State
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Colors from Theme
    val primaryAccent = LocalAppTheme.current.primaryColor
    val cardBg = MaterialTheme.colorScheme.surface

    // Refresh State
    val isRefreshing by libraryViewModel.isRefreshing.collectAsStateWithLifecycle()

    // Back Handler for Selection Mode
    BackHandler(enabled = isSelectionMode) {
        libraryViewModel.toggleSelectionMode(false)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { libraryViewModel.scanMedia() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding), // Space for MiniPlayer
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Collapsible Search Bar (Hide in selection mode)
            item {
                CollapsibleSearchBox(
                    isVisible = isSearchVisible && !isSelectionMode,
                    query = searchQuery,
                    onQueryChange = { libraryViewModel.updateSearchQuery(it) },
                    placeholderText = "Search tracks..."
                )
            }

            // 2. Selection Header (Changes logic based on mode)
            if (isSelectionMode) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { libraryViewModel.toggleSelectionMode(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Selection", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                text = "${selectedIds.size} Selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                // 2. Play All & Shuffle Buttons
                if (audioList.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp), // Adjusted top padding
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Play All (Filled Theme Color)
                            Button(
                                onClick = {
                                    if (audioList.isNotEmpty()) {
                                        viewModel.setQueue(audioList, 0, false)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                                shape = RoundedCornerShape(50),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Play All", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }

                            // Shuffle (Outlined Dark)
                            Button(
                                onClick = {
                                    if (audioList.isNotEmpty()) {
                                        val randomIndex = (audioList.indices).random()
                                        viewModel.setQueue(audioList, randomIndex, true)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(50),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Icon(Icons.Outlined.Shuffle, null, tint = primaryAccent)
                                Spacer(Modifier.width(8.dp))
                                Text("Shuffle", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 3. Count & Sort Row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${audioList.size} SONGS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Box {
                                Row(
                                    modifier = Modifier.clickable { showSortMenu = true },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.SwapVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Sort: ${getSortLabel(sortOption)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.background(cardBg)
                                ) {
                                    SortMenuItem("Latest", SortOption.DATE_ADDED_DESC, libraryViewModel) { showSortMenu = false }
                                    SortMenuItem("Title (A-Z)", SortOption.TITLE_ASC, libraryViewModel) { showSortMenu = false }
                                    SortMenuItem("Title (Z-A)", SortOption.TITLE_DESC, libraryViewModel) { showSortMenu = false }
                                    SortMenuItem("Runtime (Shortest)", SortOption.DURATION_ASC, libraryViewModel) { showSortMenu = false }
                                    SortMenuItem("Runtime (Longest)", SortOption.DURATION_DESC, libraryViewModel) { showSortMenu = false }
                                }
                            }
                        }
                    }
                }
            }

            // 4. List Items
            if (audioList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LibraryMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                if (searchQuery.isNotEmpty()) "No results found" else "No music found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (searchQuery.isEmpty()) {
                                Button(
                                    onClick = { libraryViewModel.scanMedia() },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                                ) {
                                    Text("Rescan Library")
                                }
                            }
                        }
                    }
                }
            } else {
                items(items = audioList, key = { it.id }) { song ->
                    val isSelected = selectedIds.contains(song.id)
                    AudioListItemStyled(
                        song = song,
                        onClick = {
                            if (isSelectionMode) {
                                libraryViewModel.toggleSelection(song.id)
                            } else {
                                val startIndex = audioList.indexOfFirst { it.id == song.id }
                                if (startIndex >= 0) {
                                    viewModel.setQueue(audioList, startIndex, false)
                                }
                            }
                        },
                        isPlaying = song.id == currentTrack?.id,
                        onLongClick = {
                            libraryViewModel.toggleSelectionMode(true)
                            libraryViewModel.toggleSelection(song.id)
                        },
                        onAddToPlaylist = onAddToPlaylist,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onDelete = {
                            libraryViewModel.toggleSelectionMode(true)
                            libraryViewModel.selectAll(listOf(song.id))
                            showDeleteConfirmDialog = true
                        },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        DeleteConfirmationDialog(
            count = selectedIds.size,
            onConfirm = { libraryViewModel.deleteSelectedMedia() },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioListItemStyled(
    song: MediaFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAddToPlaylist: (MediaFile) -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isPlaying: Boolean = false,
    onDelete: () -> Unit,
    onPlayNext: (MediaFile) -> Unit,
    onAddToQueue: (MediaFile) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp).size(24.dp)
            )
        }

        // Album Art
        Card(
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(56.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            AsyncImage(
                model = song.albumArtUri ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${song.artist ?: "Unknown"} • ${formatDuration(song.duration)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Action Menu
        if (!isSelectionMode) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            onPlayNext(song)
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onSurface) }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            onAddToQueue(song)
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurface) }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist(song)
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = MaterialTheme.colorScheme.onSurface) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }

    // Thin divider
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SortMenuItem(
    label: String,
    option: SortOption,
    libraryViewModel: LibraryViewModel,
    onSelect: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
        onClick = {
            libraryViewModel.updateSortOption(option)
            onSelect()
        }
    )
}

private fun getSortLabel(option: SortOption): String {
    return when(option) {
        SortOption.TITLE_ASC -> "title"
        SortOption.TITLE_DESC -> "title"
        SortOption.DURATION_ASC -> "runtime"
        SortOption.DURATION_DESC -> "runtime"
        SortOption.DATE_ADDED_DESC -> "date"
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = millis / (1000 * 60 * 60)

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
