package com.local.offlinemediaplayer.ui.screens.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.AddToPlaylistDialog
import com.local.offlinemediaplayer.ui.components.CreatePlaylistDialog
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog
import com.local.offlinemediaplayer.ui.components.MediaPropertiesDialog
import com.local.offlinemediaplayer.ui.components.RenameMediaDialog
import com.local.offlinemediaplayer.ui.components.SortDropdownMenu
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel
import com.local.offlinemediaplayer.viewmodel.SortField

/** The "Movies" tab of the video library: its own sort, layout toggle and item dialogs. */
@Composable
internal fun MoviesListContent(
    libraryViewModel: LibraryViewModel,
    onVideoClick: (MediaFile, List<MediaFile>) -> Unit,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val movies by libraryViewModel.sortedMovies.collectAsStateWithLifecycle()
    val movieSortState by libraryViewModel.movieSortState.collectAsStateWithLifecycle()
    val searchQuery by
        libraryViewModel.folderSearchQuery.collectAsStateWithLifecycle() // Reuse existing search query
    val watchProgress by libraryViewModel.watchProgressMap.collectAsStateWithLifecycle()

    // Properties Dialog State
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var selectedVideoForProperties by remember { mutableStateOf<MediaFile?>(null) }
    // Rename Dialog State
    var selectedVideoForRename by remember { mutableStateOf<MediaFile?>(null) }
    // Delete Dialog
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Playlist Dialog State
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedVideoForPlaylist by remember { mutableStateOf<MediaFile?>(null) }

    var showSortMenu by remember { mutableStateOf(false) }

    val filteredMovies =
        remember(movies, searchQuery) {
            if (searchQuery.isEmpty()) {
                movies
            } else {
                movies.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Row (Count + Sort + View)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${filteredMovies.size} MOVIES (>1h)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sort Menu
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SortDropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        fields = SortField.entries,
                        sortState = movieSortState,
                        onSortChange = { libraryViewModel.updateMovieSort(it) },
                    )
                }

                // View Toggle
                IconButton(onClick = onToggleView) {
                    Icon(
                        imageVector =
                            if (isGridView) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Default.GridView
                            },
                        contentDescription = "Change View",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (filteredMovies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text =
                        if (searchQuery.isNotEmpty()) {
                            "No movies found matching search"
                        } else {
                            "No videos longer than 1h found"
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val primaryAccent = LocalAppTheme.current.primaryColor
            if (isGridView) {
                val widthClass = com.local.offlinemediaplayer.ui.adaptive.LocalWindowSizeClass.current
                LazyVerticalGrid(
                    columns =
                        GridCells.Fixed(
                            com.local.offlinemediaplayer.ui.adaptive
                                .adaptiveGridColumns(widthClass),
                        ),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredMovies) { movie ->
                        // Reusing VideoCardItem from VideoListScreen (made public)
                        VideoCardItem(
                            video = movie,
                            onVideoClick = { onVideoClick(movie, filteredMovies) },
                            onLongClick = {}, // No selection mode in Movies tab for simplicity
                            accentColor = primaryAccent,
                            onAddToPlaylist = {
                                selectedVideoForPlaylist = movie
                                showAddToPlaylistDialog = true
                            },
                            isSelectionMode = false,
                            isSelected = false,
                            onDelete = {
                                libraryViewModel.toggleSelectionMode(true)
                                libraryViewModel.selectAll(listOf(movie.id))
                                showDeleteConfirmDialog = true
                            },
                            onProperties = {
                                selectedVideoForProperties = movie
                                showPropertiesDialog = true
                            },
                            onRename = { selectedVideoForRename = movie },
                            progress = watchProgress[movie.id] ?: 0f,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredMovies) { movie ->
                        // Reusing VideoListItem from VideoListScreen (made public)
                        VideoListItem(
                            video = movie,
                            onVideoClick = { onVideoClick(movie, filteredMovies) },
                            onLongClick = {},
                            onAddToPlaylist = {
                                selectedVideoForPlaylist = movie
                                showAddToPlaylistDialog = true
                            },
                            isSelectionMode = false,
                            isSelected = false,
                            onDelete = {
                                libraryViewModel.toggleSelectionMode(true)
                                libraryViewModel.selectAll(listOf(movie.id))
                                showDeleteConfirmDialog = true
                            },
                            onProperties = {
                                selectedVideoForProperties = movie
                                showPropertiesDialog = true
                            },
                            onRename = { selectedVideoForRename = movie },
                            progress = watchProgress[movie.id] ?: 0f,
                        )
                    }
                }
            }
        }
    }

    if (showPropertiesDialog && selectedVideoForProperties != null) {
        MediaPropertiesDialog(
            mediaFile = selectedVideoForProperties!!,
            onDismiss = { showPropertiesDialog = false },
        )
    }

    selectedVideoForRename?.let { video ->
        RenameMediaDialog(
            file = video,
            onDismiss = { selectedVideoForRename = null },
            onRename = { newName -> libraryViewModel.renameMedia(video, newName) },
        )
    }

    if (showDeleteConfirmDialog) {
        val selectedIds by libraryViewModel.selectedMediaIds.collectAsStateWithLifecycle()
        DeleteConfirmationDialog(
            count = selectedIds.size,
            onConfirm = { libraryViewModel.deleteSelectedMedia() },
            onDismiss = { showDeleteConfirmDialog = false },
        )
    }

    if (showAddToPlaylistDialog && selectedVideoForPlaylist != null) {
        AddToPlaylistDialog(
            song = selectedVideoForPlaylist!!,
            playlistViewModel = playlistViewModel,
            onDismiss = { showAddToPlaylistDialog = false },
            onCreateNew = { showCreatePlaylistDialog = true },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name -> playlistViewModel.createPlaylist(name, isVideo = true) },
        )
    }
}
