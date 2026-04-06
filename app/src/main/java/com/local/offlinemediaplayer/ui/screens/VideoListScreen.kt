package com.local.offlinemediaplayer.ui.screens

// import androidx.compose.foundation.clickable
// import androidx.compose.material.icons.filled.ArrowBack
// import androidx.compose.material.icons.filled.ArrowBackIosNew
// import androidx.compose.material.icons.filled.CheckCircle
// import androidx.compose.material.icons.filled.ChevronRight
// import androidx.compose.material.icons.filled.Close
// import androidx.compose.material.icons.filled.Delete
// import androidx.compose.material.icons.filled.FormatListNumbered
// import androidx.compose.material.icons.filled.GridView
// import androidx.compose.material.icons.filled.MoreVert
// import androidx.compose.material.icons.filled.PlaylistAdd
// import androidx.compose.material.icons.filled.RadioButtonUnchecked
// import androidx.compose.material.icons.filled.ViewList
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.ui.components.AddToPlaylistDialog
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.ui.components.CreatePlaylistDialog
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog
import com.local.offlinemediaplayer.ui.components.MediaPropertiesDialog
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.SortOption
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
        viewModel: PlaybackViewModel,
        libraryViewModel: LibraryViewModel = hiltViewModel(),
        playlistViewModel: PlaylistViewModel = hiltViewModel(),
        onVideoClick: (MediaFile, List<MediaFile>) -> Unit,
        videoListOverride: List<MediaFile>? = null,
        title: String? = null,
        onBack: (() -> Unit)? = null
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

        // Default to Grid View
        var isGridView by remember { mutableStateOf(true) }
        // Local Search State for this folder view
        var searchQuery by remember { mutableStateOf("") }
        var isSearchVisible by remember { mutableStateOf(false) }

        // Playlist states
        var showAddToPlaylistDialog by remember { mutableStateOf(false) }
        var showCreatePlaylistDialog by remember { mutableStateOf(false) }
        var selectedVideoForPlaylist by remember { mutableStateOf<MediaFile?>(null) }

        // Properties Dialog State
        var showPropertiesDialog by remember { mutableStateOf(false) }
        var selectedVideoForProperties by remember { mutableStateOf<MediaFile?>(null) }

        // Delete Dialog
        var showDeleteConfirmDialog by remember { mutableStateOf(false) }

        // Sort state
        val videoSortOption by libraryViewModel.videoSortOption.collectAsStateWithLifecycle()
        var showSortMenu by remember { mutableStateOf(false) }

        // Back Handler to exit selection mode
        BackHandler(enabled = isSelectionMode) { libraryViewModel.toggleSelectionMode(false) }

        val filteredVideos = remember(videos, searchQuery, videoSortOption) {
                var result = if (searchQuery.isEmpty()) {
                        videos
                } else {
                        videos.filter { it.title.contains(searchQuery, ignoreCase = true) }
                }
                
                when (videoSortOption) {
                        SortOption.TITLE_ASC -> result.sortedBy { it.title.lowercase() }
                        SortOption.TITLE_DESC -> result.sortedByDescending { it.title.lowercase() }
                        SortOption.DURATION_ASC -> result.sortedBy { it.duration }
                        SortOption.DURATION_DESC -> result.sortedByDescending { it.duration }
                        SortOption.DATE_ADDED_DESC -> result.sortedByDescending { it.id }
                }
        }

        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // Custom Header logic for "Folder View"
                if (title != null && onBack != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .statusBarsPadding()
                                                        .padding(
                                                                horizontal = 8.dp,
                                                                vertical = 8.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        if (isSelectionMode) {
                                                // SELECTION MODE HEADER
                                                IconButton(
                                                        onClick = {
                                                                libraryViewModel.toggleSelectionMode(false)
                                                        },
                                                        modifier =
                                                                Modifier.background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface,
                                                                                CircleShape
                                                                        )
                                                                        .size(40.dp)
                                                ) {
                                                        Icon(
                                                                Icons.Default.Close,
                                                                contentDescription = "Close",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
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
                                                                                                .Bold
                                                                        ),
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onBackground,
                                                        modifier = Modifier.weight(1f)
                                                )

                                                IconButton(
                                                        onClick = {
                                                                showDeleteConfirmDialog = true
                                                        },
                                                        modifier =
                                                                Modifier.background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface,
                                                                                CircleShape
                                                                        )
                                                                        .size(40.dp)
                                                ) {
                                                        Icon(
                                                                Icons.Outlined.Delete,
                                                                contentDescription = "Delete",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .error
                                                        )
                                                }
                                        } else {
                                                // NORMAL HEADER
                                                IconButton(
                                                        onClick = onBack,
                                                        modifier =
                                                                Modifier.background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface,
                                                                                CircleShape
                                                                        )
                                                                        .size(40.dp)
                                                ) {
                                                        Icon(
                                                                Icons.Default.ArrowBackIosNew,
                                                                contentDescription = "Back",
                                                                tint = primaryAccent
                                                        )
                                                }

                                                Spacer(modifier = Modifier.width(16.dp))

                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                ) {
                                                        Text(
                                                                text = "Folders",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium.copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                                color = primaryAccent
                                                        )
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.ChevronRight,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                        Text(
                                                                text = title,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium.copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onBackground,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )
                                                }

                                                IconButton(
                                                        onClick = {
                                                                isSearchVisible = !isSearchVisible
                                                        },
                                                        modifier =
                                                                Modifier.background(
                                                                                if (isSearchVisible)
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .surface
                                                                                else
                                                                                        Color.Transparent,
                                                                                CircleShape
                                                                        )
                                                                        .size(40.dp)
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Outlined.Search,
                                                                contentDescription = "Search",
                                                                tint =
                                                                        if (isSearchVisible)
                                                                                primaryAccent
                                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                }

                                                IconButton(
                                                        onClick = { isGridView = !isGridView },
                                                        modifier = Modifier.size(40.dp)
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        if (isGridView)
                                                                                Icons.Default
                                                                                        .FormatListNumbered
                                                                        else Icons.Default.GridView,
                                                                // Alt to viewlist icon
                                                                contentDescription = "Change View",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(28.dp)
                                                        )
                                                }
                                        }
                                }

                                HorizontalDivider(
                                        color = MaterialTheme.colorScheme.surface,
                                        thickness = 1.dp
                                )
                        }
                }

                // Collapsible Search Box
                CollapsibleSearchBox(
                        isVisible = isSearchVisible && !isSelectionMode,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = "Search in ${title ?: "Videos"}..."
                )

                if (!isSelectionMode && filteredVideos.isNotEmpty()) {
                        Row(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                        text = "${filteredVideos.size} VIDEOS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                )

                                Box {
                                        Row(
                                                modifier = Modifier.clickable { showSortMenu = true },
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(Icons.Default.SwapVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                        text = "Sort: ${getVideoSortLabel(videoSortOption)}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                        }

                                        DropdownMenu(
                                                expanded = showSortMenu,
                                                onDismissRequest = { showSortMenu = false },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                        ) {
                                                DropdownMenuItem(text = { Text("Latest") }, onClick = { libraryViewModel.updateVideoSortOption(SortOption.DATE_ADDED_DESC); showSortMenu = false })
                                                DropdownMenuItem(text = { Text("Title (A-Z)") }, onClick = { libraryViewModel.updateVideoSortOption(SortOption.TITLE_ASC); showSortMenu = false })
                                                DropdownMenuItem(text = { Text("Title (Z-A)") }, onClick = { libraryViewModel.updateVideoSortOption(SortOption.TITLE_DESC); showSortMenu = false })
                                                DropdownMenuItem(text = { Text("Runtime (Shortest)") }, onClick = { libraryViewModel.updateVideoSortOption(SortOption.DURATION_ASC); showSortMenu = false })
                                                DropdownMenuItem(text = { Text("Runtime (Longest)") }, onClick = { libraryViewModel.updateVideoSortOption(SortOption.DURATION_DESC); showSortMenu = false })
                                        }
                                }
                        }
                }

                // Nested Scroll Container
                Box(modifier = Modifier.weight(1f)) {
                        if (filteredVideos.isEmpty()) {
                                // Empty state
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .verticalScroll(rememberScrollState()),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(100.dp)
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface,
                                                                                CircleShape
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Outlined.VideoLibrary,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(48.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                }
                                                Text(
                                                        if (searchQuery.isNotEmpty())
                                                                "No results found"
                                                        else "No videos found here",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (searchQuery.isEmpty()) {
                                                        Button(
                                                                onClick = { libraryViewModel.scanMedia() },
                                                                colors =
                                                                        ButtonDefaults.buttonColors(
                                                                                containerColor =
                                                                                        primaryAccent
                                                                        )
                                                        ) { Text("Rescan Library") }
                                                }
                                        }
                                }
                        } else {
                                if (isGridView) {
                                        LazyVerticalGrid(
                                                columns = GridCells.Fixed(2),
                                                contentPadding = PaddingValues(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                modifier = Modifier.fillMaxSize()
                                        ) {
                                                items(items = filteredVideos, key = { it.id }) {
                                                        video ->
                                                        val isSelected =
                                                                selectedIds.contains(video.id)
                                                        VideoCardItem(
                                                                video = video,
                                                                onVideoClick = {
                                                                        if (isSelectionMode)
                                                                                libraryViewModel
                                                                                        .toggleSelection(
                                                                                                video.id
                                                                                        )
                                                                        else
                                                                                onVideoClick(
                                                                                        video,
                                                                                        filteredVideos
                                                                                )
                                                                },
                                                                onLongClick = {
                                                                        libraryViewModel
                                                                                .toggleSelectionMode(
                                                                                        true
                                                                                )
                                                                        libraryViewModel.toggleSelection(
                                                                                video.id
                                                                        )
                                                                },
                                                                accentColor = primaryAccent,
                                                                onAddToPlaylist = {
                                                                        selectedVideoForPlaylist =
                                                                                video
                                                                        showAddToPlaylistDialog =
                                                                                true
                                                                },
                                                                isSelectionMode = isSelectionMode,
                                                                isSelected = isSelected,
                                                                onDelete = {
                                                                        libraryViewModel
                                                                                .toggleSelectionMode(
                                                                                        true
                                                                                )
                                                                        libraryViewModel.selectAll(
                                                                                listOf(video.id)
                                                                        )
                                                                        showDeleteConfirmDialog =
                                                                                true
                                                                },
                                                                onProperties = {
                                                                        selectedVideoForProperties =
                                                                                video
                                                                        showPropertiesDialog = true
                                                                }
                                                        )
                                                }
                                        }
                                } else {
                                        LazyColumn(
                                                contentPadding = PaddingValues(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                                modifier = Modifier.fillMaxSize()
                                        ) {
                                                items(items = filteredVideos, key = { it.id }) {
                                                        video ->
                                                        val isSelected =
                                                                selectedIds.contains(video.id)
                                                        VideoListItem(
                                                                video = video,
                                                                onVideoClick = {
                                                                        if (isSelectionMode)
                                                                                libraryViewModel
                                                                                        .toggleSelection(
                                                                                                video.id
                                                                                        )
                                                                        else
                                                                                onVideoClick(
                                                                                        video,
                                                                                        filteredVideos
                                                                                )
                                                                },
                                                                onLongClick = {
                                                                        libraryViewModel
                                                                                .toggleSelectionMode(
                                                                                        true
                                                                                )
                                                                        libraryViewModel.toggleSelection(
                                                                                video.id
                                                                        )
                                                                },
                                                                onAddToPlaylist = {
                                                                        selectedVideoForPlaylist =
                                                                                video
                                                                        showAddToPlaylistDialog =
                                                                                true
                                                                },
                                                                isSelectionMode = isSelectionMode,
                                                                isSelected = isSelected,
                                                                onDelete = {
                                                                        libraryViewModel
                                                                                .toggleSelectionMode(
                                                                                        true
                                                                                )
                                                                        libraryViewModel.selectAll(
                                                                                listOf(video.id)
                                                                        )
                                                                        showDeleteConfirmDialog =
                                                                                true
                                                                },
                                                                onProperties = {
                                                                        selectedVideoForProperties =
                                                                                video
                                                                        showPropertiesDialog = true
                                                                }
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
                        onDismiss = { showDeleteConfirmDialog = false }
                )
        }

        if (showCreatePlaylistDialog) {
                CreatePlaylistDialog(
                        onDismiss = { showCreatePlaylistDialog = false },
                        onCreate = { name -> playlistViewModel.createPlaylist(name, isVideo = true) }
                )
        }

        if (showAddToPlaylistDialog && selectedVideoForPlaylist != null) {
                AddToPlaylistDialog(
                        song = selectedVideoForPlaylist!!,
                        onDismiss = { showAddToPlaylistDialog = false },
                        onCreateNew = { showCreatePlaylistDialog = true }
                )
        }

        if (showPropertiesDialog && selectedVideoForProperties != null) {
                MediaPropertiesDialog(
                        mediaFile = selectedVideoForProperties!!,
                        onDismiss = { showPropertiesDialog = false }
                )
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
        video: MediaFile,
        onVideoClick: () -> Unit,
        onLongClick: () -> Unit,
        onAddToPlaylist: () -> Unit,
        isSelectionMode: Boolean,
        isSelected: Boolean,
        onDelete: () -> Unit,
        onProperties: () -> Unit
) {
        var showMenu by remember { mutableStateOf(false) }

        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                        if (isSelected)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else Color.Transparent
                                )
                                .combinedClickable(
                                        onClick = onVideoClick,
                                        onLongClick = onLongClick
                                )
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                if (isSelectionMode) {
                        Icon(
                                imageVector =
                                        if (isSelected) Icons.Default.CheckCircle
                                        else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint =
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Gray,
                                modifier = Modifier.padding(end = 16.dp).size(24.dp)
                        )
                }

                Box(
                        modifier =
                                Modifier.width(96.dp)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                        AsyncImage(
                                model = video.thumbnailPath?.let { File(it) } ?: video.uri,
                                contentDescription = video.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                        )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = video.title,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                text = FormatUtils.formatDuration(video.duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }

                if (!isSelectionMode) {
                        Box {
                                IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        modifier =
                                                Modifier.background(
                                                        MaterialTheme.colorScheme.surface
                                                )
                                ) {
                                        DropdownMenuItem(
                                                text = {
                                                        Text(
                                                                "Add to Playlist",
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                        )
                                                },
                                                onClick = {
                                                        showMenu = false
                                                        onAddToPlaylist()
                                                },
                                                leadingIcon = {
                                                        Icon(
                                                                Icons.Default.PlaylistAddCircle,
                                                                null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                        )
                                                }
                                        )
                                        DropdownMenuItem(
                                                text = {
                                                        Text(
                                                                "Delete",
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .error
                                                        )
                                                },
                                                onClick = {
                                                        showMenu = false
                                                        onDelete()
                                                },
                                                leadingIcon = {
                                                        Icon(
                                                                Icons.Default.Delete,
                                                                null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .error
                                                        )
                                                }
                                        )
                                }
                        }
                }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCardItem(
        video: MediaFile,
        onVideoClick: () -> Unit,
        onLongClick: () -> Unit,
        accentColor: Color,
        onAddToPlaylist: () -> Unit,
        isSelectionMode: Boolean,
        isSelected: Boolean,
        onDelete: () -> Unit,
        onProperties: () -> Unit
) {
        var showMenu by remember { mutableStateOf(false) }

        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                        if (isSelected) accentColor.copy(alpha = 0.1f)
                                        else Color.Transparent
                                )
                                .combinedClickable(
                                        onClick = onVideoClick,
                                        onLongClick = onLongClick
                                )
                                .padding(4.dp)
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                        AsyncImage(
                                model = video.thumbnailPath?.let { File(it) } ?: video.uri,
                                contentDescription = video.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                        )

                        // Selection Overlay
                        if (isSelectionMode) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isSelected) Icons.Default.CheckCircle
                                                        else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (isSelected) accentColor else Color.White,
                                                modifier = Modifier.size(48.dp)
                                        )
                                }
                        } else {
                                Surface(
                                        color = Color.Black.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                ) {
                                        Text(
                                                text = FormatUtils.formatDuration(video.duration),
                                                color = Color.White,
                                                style =
                                                        MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold
                                                        ),
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 6.dp,
                                                                vertical = 2.dp
                                                        )
                                        )
                                }
                        }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = video.title,
                                        style =
                                                MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (video.size > 0) {
                                                Text(
                                                        text = FormatUtils.formatSize(video.size),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                        }
                                }
                        }

                        if (!isSelectionMode) {
                                Box {
                                        IconButton(onClick = { showMenu = true }) {
                                                Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "Options",
                                                        tint = Color.Gray
                                                )
                                        }

                                        DropdownMenu(
                                                expanded = showMenu,
                                                onDismissRequest = { showMenu = false },
                                                modifier =
                                                        Modifier.background(
                                                                MaterialTheme.colorScheme.surface
                                                        )
                                        ) {
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Properties",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        },
                                                        onClick = {
                                                                showMenu = false
                                                                onProperties()
                                                        },
                                                        leadingIcon = {
                                                                Icon(
                                                                        Icons.Default.Info,
                                                                        null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Add to Playlist",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        },
                                                        onClick = {
                                                                showMenu = false
                                                                onAddToPlaylist()
                                                        },
                                                        leadingIcon = {
                                                                Icon(
                                                                        Icons.Default
                                                                                .PlaylistAddCircle,
                                                                        null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Delete",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error
                                                                )
                                                        },
                                                        onClick = {
                                                                showMenu = false
                                                                onDelete()
                                                        },
                                                        leadingIcon = {
                                                                Icon(
                                                                        Icons.Default.Delete,
                                                                        null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error
                                                                )
                                                        }
                                                )
                                        }
                                }
                        }
                }
}
}

private fun getVideoSortLabel(option: SortOption): String {
    return when(option) {
        SortOption.TITLE_ASC -> "Title (A-Z)"
        SortOption.TITLE_DESC -> "Title (Z-A)"
        SortOption.DURATION_ASC -> "Runtime (Shortest)"
        SortOption.DURATION_DESC -> "Runtime (Longest)"
        SortOption.DATE_ADDED_DESC -> "Latest"
    }
}
