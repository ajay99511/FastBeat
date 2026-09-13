package com.local.offlinemediaplayer.ui.screens.video

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.AddToPlaylistDialog
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.ui.components.CreatePlaylistDialog
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog
import com.local.offlinemediaplayer.ui.components.MediaPropertiesDialog
import com.local.offlinemediaplayer.ui.components.RenameMediaDialog
import com.local.offlinemediaplayer.ui.components.SortDropdownMenu
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel
import com.local.offlinemediaplayer.viewmodel.SortField
import com.local.offlinemediaplayer.viewmodel.applySort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    onVideoClick: (MediaFile, List<MediaFile>) -> Unit,
    videoListOverride: List<MediaFile>? = null,
    title: String? = null,
    onBack: (() -> Unit)? = null,
) {
    val videosState by libraryViewModel.videoList.collectAsStateWithLifecycle()
    val videos = videoListOverride ?: videosState
    val primaryAccent = LocalAppTheme.current.primaryColor

    // Selection State
    val isSelectionMode by libraryViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by libraryViewModel.selectedMediaIds.collectAsStateWithLifecycle()

    // Deletion Flow Handling
    val intentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                libraryViewModel.onDeleteSuccess()
            } else {
                libraryViewModel.onDeleteCancelled()
            }
        }

    LaunchedEffect(Unit) {
        libraryViewModel.deleteIntentEvent.collect { intentSender ->
            intentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    // Rename Flow Handling
    val renameIntentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                libraryViewModel.onRenamePermissionGranted()
            } else {
                libraryViewModel.onRenameDenied()
            }
        }

    LaunchedEffect(Unit) {
        libraryViewModel.renameIntentEvent.collect { intentSender ->
            renameIntentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        libraryViewModel.userMessage.collect { msg ->
            android.widget.Toast
                .makeText(context, msg.resolve(context), android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    // Grid/List view (persisted in preferences via LibraryViewModel)
    val isGridView by libraryViewModel.videoGridView.collectAsStateWithLifecycle()
    // Local Search State for this folder view
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    // Playlist states
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var videosToAddToPlaylist by remember { mutableStateOf<List<MediaFile>>(emptyList()) }

    // Properties Dialog State
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var selectedVideoForProperties by remember { mutableStateOf<MediaFile?>(null) }

    // Rename Dialog State
    var selectedVideoForRename by remember { mutableStateOf<MediaFile?>(null) }

    // Delete Dialog
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Sort state
    val videoSortState by libraryViewModel.videoSortState.collectAsStateWithLifecycle()
    val videoPlayCounts by libraryViewModel.videoPlayCountMap.collectAsStateWithLifecycle()
    val watchProgress by libraryViewModel.watchProgressMap.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }

    // Back Handler to exit selection mode
    BackHandler(enabled = isSelectionMode) { libraryViewModel.toggleSelectionMode(false) }

    val filteredVideos =
        remember(videos, searchQuery, videoSortState, videoPlayCounts) {
            val result =
                if (searchQuery.isEmpty()) {
                    videos
                } else {
                    videos.filter { it.title.contains(searchQuery, ignoreCase = true) }
                }
            result.applySort(videoSortState, videoPlayCounts)
        }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Custom Header logic for "Folder View"
        if (title != null && onBack != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(
                                horizontal = 8.dp,
                                vertical = 8.dp,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelectionMode) {
                        // SELECTION MODE HEADER
                        IconButton(
                            onClick = {
                                libraryViewModel.toggleSelectionMode(false)
                            },
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme
                                            .colorScheme
                                            .surface,
                                        CircleShape,
                                    ).size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint =
                                    MaterialTheme.colorScheme
                                        .onSurface,
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = "${selectedIds.size} Selected",
                            style =
                                MaterialTheme.typography.titleMedium
                                    .copy(
                                        fontWeight =
                                            FontWeight
                                                .Bold,
                                    ),
                            color =
                                MaterialTheme.colorScheme
                                    .onBackground,
                            modifier = Modifier.weight(1f),
                        )

                        IconButton(
                            onClick = {
                                val selected =
                                    videos.filter {
                                        selectedIds.contains(
                                            it.id,
                                        )
                                    }
                                if (selected.isNotEmpty()) {
                                    videosToAddToPlaylist =
                                        selected
                                    showAddToPlaylistDialog =
                                        true
                                }
                            },
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme
                                            .colorScheme
                                            .surface,
                                        CircleShape,
                                    ).size(40.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled
                                    .PlaylistAdd,
                                contentDescription =
                                    "Add to Playlist",
                                tint =
                                    MaterialTheme.colorScheme
                                        .primary,
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                showDeleteConfirmDialog = true
                            },
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme
                                            .colorScheme
                                            .surface,
                                        CircleShape,
                                    ).size(40.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint =
                                    MaterialTheme.colorScheme
                                        .error,
                            )
                        }
                    } else {
                        // NORMAL HEADER
                        IconButton(
                            onClick = onBack,
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme
                                            .colorScheme
                                            .surface,
                                        CircleShape,
                                    ).size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.ArrowBackIosNew,
                                contentDescription = "Back",
                                tint = primaryAccent,
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Folders",
                                style =
                                    MaterialTheme.typography
                                        .titleMedium
                                        .copy(
                                            fontWeight =
                                                FontWeight
                                                    .Bold,
                                        ),
                                color = primaryAccent,
                            )
                            Icon(
                                imageVector =
                                    Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = title,
                                style =
                                    MaterialTheme.typography
                                        .titleMedium
                                        .copy(
                                            fontWeight =
                                                FontWeight
                                                    .Bold,
                                        ),
                                color =
                                    MaterialTheme.colorScheme
                                        .onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                            },
                            modifier =
                                Modifier
                                    .background(
                                        if (isSearchVisible) {
                                            MaterialTheme
                                                .colorScheme
                                                .surface
                                        } else {
                                            Color.Transparent
                                        },
                                        CircleShape,
                                    ).size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint =
                                    if (isSearchVisible) {
                                        primaryAccent
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }

                        IconButton(
                            onClick = {
                                libraryViewModel
                                    .toggleVideoGridView()
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (isGridView) {
                                        Icons.Default
                                            .FormatListNumbered
                                    } else {
                                        Icons.Default.GridView
                                    },
                                // Alt to viewlist icon
                                contentDescription = "Change View",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surface,
                    thickness = 1.dp,
                )
            }
        }

        // Collapsible Search Box
        CollapsibleSearchBox(
            isVisible = isSearchVisible && !isSelectionMode,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholderText = "Search in ${title ?: "Videos"}...",
        )

        if (!isSelectionMode && filteredVideos.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${filteredVideos.size} VIDEOS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )

                Box {
                    Row(
                        modifier = Modifier.clickable { showSortMenu = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector =
                                if (videoSortState.ascending) {
                                    Icons.Default.ArrowUpward
                                } else {
                                    Icons.Default.ArrowDownward
                                },
                            contentDescription =
                                if (videoSortState.ascending) {
                                    "Sorted ascending"
                                } else {
                                    "Sorted descending"
                                },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Sort: ${videoSortState.field.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SortDropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        fields = SortField.entries,
                        sortState = videoSortState,
                        onSortChange = { libraryViewModel.updateVideoSort(it) },
                    )
                }
            }
        }

        // Nested Scroll Container
        Box(modifier = Modifier.weight(1f)) {
            if (filteredVideos.isEmpty()) {
                // Empty state
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(100.dp)
                                    .background(
                                        MaterialTheme
                                            .colorScheme
                                            .surface,
                                        CircleShape,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Outlined.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (searchQuery.isNotEmpty()) {
                                "No results found"
                            } else {
                                "No videos found here"
                            },
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (searchQuery.isEmpty()) {
                            Button(
                                onClick = { libraryViewModel.scanMedia() },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                        primaryAccent,
                                    ),
                            ) { Text("Rescan Library") }
                        }
                    }
                }
            } else {
                if (isGridView) {
                    val widthClass = com.local.offlinemediaplayer.ui.adaptive.LocalWindowSizeClass.current
                    LazyVerticalGrid(
                        columns =
                            GridCells.Fixed(
                                com.local.offlinemediaplayer.ui.adaptive.adaptiveGridColumns(
                                    widthClass,
                                ),
                            ),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = filteredVideos, key = { it.id }) { video ->
                            val isSelected =
                                selectedIds.contains(video.id)
                            VideoCardItem(
                                video = video,
                                onVideoClick = {
                                    if (isSelectionMode) {
                                        libraryViewModel
                                            .toggleSelection(
                                                video.id,
                                            )
                                    } else {
                                        onVideoClick(
                                            video,
                                            filteredVideos,
                                        )
                                    }
                                },
                                onLongClick = {
                                    libraryViewModel
                                        .toggleSelectionMode(
                                            true,
                                        )
                                    libraryViewModel.toggleSelection(
                                        video.id,
                                    )
                                },
                                accentColor = primaryAccent,
                                onAddToPlaylist = {
                                    videosToAddToPlaylist =
                                        listOf(video)
                                    showAddToPlaylistDialog =
                                        true
                                },
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onDelete = {
                                    libraryViewModel
                                        .toggleSelectionMode(
                                            true,
                                        )
                                    libraryViewModel.selectAll(
                                        listOf(video.id),
                                    )
                                    showDeleteConfirmDialog =
                                        true
                                },
                                onProperties = {
                                    selectedVideoForProperties =
                                        video
                                    showPropertiesDialog = true
                                },
                                onRename = {
                                    selectedVideoForRename =
                                        video
                                },
                                progress = watchProgress[video.id] ?: 0f,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = filteredVideos, key = { it.id }) { video ->
                            val isSelected =
                                selectedIds.contains(video.id)
                            VideoListItem(
                                video = video,
                                onVideoClick = {
                                    if (isSelectionMode) {
                                        libraryViewModel
                                            .toggleSelection(
                                                video.id,
                                            )
                                    } else {
                                        onVideoClick(
                                            video,
                                            filteredVideos,
                                        )
                                    }
                                },
                                onLongClick = {
                                    libraryViewModel
                                        .toggleSelectionMode(
                                            true,
                                        )
                                    libraryViewModel.toggleSelection(
                                        video.id,
                                    )
                                },
                                onAddToPlaylist = {
                                    videosToAddToPlaylist =
                                        listOf(video)
                                    showAddToPlaylistDialog =
                                        true
                                },
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onDelete = {
                                    libraryViewModel
                                        .toggleSelectionMode(
                                            true,
                                        )
                                    libraryViewModel.selectAll(
                                        listOf(video.id),
                                    )
                                    showDeleteConfirmDialog =
                                        true
                                },
                                onProperties = {
                                    selectedVideoForProperties =
                                        video
                                    showPropertiesDialog = true
                                },
                                onRename = {
                                    selectedVideoForRename =
                                        video
                                },
                                progress = watchProgress[video.id] ?: 0f,
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showDeleteConfirmDialog) {
        DeleteConfirmationDialog(
            count = selectedIds.size,
            onConfirm = { libraryViewModel.deleteSelectedMedia() },
            onDismiss = { showDeleteConfirmDialog = false },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name -> playlistViewModel.createPlaylist(name, isVideo = true) },
        )
    }

    if (showAddToPlaylistDialog && videosToAddToPlaylist.isNotEmpty()) {
        AddToPlaylistDialog(
            songs = videosToAddToPlaylist,
            onDismiss = { showAddToPlaylistDialog = false },
            onCreateNew = { showCreatePlaylistDialog = true },
        )
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
}
