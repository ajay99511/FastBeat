package com.local.offlinemediaplayer.ui.screens

// import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
//import androidx.compose.material.icons.filled.Sort
//import androidx.compose.material.icons.filled.ViewList
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.model.VideoFolder
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.viewmodel.ContinueWatchingItem
import com.local.offlinemediaplayer.ui.components.AddToPlaylistDialog
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.ui.components.CreatePlaylistDialog
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog
import com.local.offlinemediaplayer.ui.components.MediaPropertiesDialog
import com.local.offlinemediaplayer.ui.components.SortDropdownMenu
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel
import com.local.offlinemediaplayer.viewmodel.SortField
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFolderScreen(
        viewModel: PlaybackViewModel,
        libraryViewModel: LibraryViewModel = hiltViewModel(),
        playlistViewModel: PlaylistViewModel = hiltViewModel(),
        onFolderClick: (String) -> Unit,
        onPlaylistClick: (String) -> Unit,
        onVideoClick: (MediaFile, List<MediaFile>) -> Unit,
        isSearchVisible: Boolean
) {
    // 0 = Folders, 1 = Movies, 2 = Playlists
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }

    // View Mode State: True = Grid, False = List
    var isGridView by rememberSaveable { mutableStateOf(true) }

    // Movies Tab Specific State
    var isMoviesGridView by rememberSaveable { mutableStateOf(true) }

    val folders by libraryViewModel.videoFolders.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.folderSearchQuery.collectAsStateWithLifecycle()
    val continueWatching by libraryViewModel.continueWatching.collectAsStateWithLifecycle()
    val primaryAccent = LocalAppTheme.current.primaryColor

    // Refresh State
    val isRefreshing by libraryViewModel.isRefreshing.collectAsStateWithLifecycle()

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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 1. Collapsible Search Box
        CollapsibleSearchBox(
                isVisible = isSearchVisible,
                query = searchQuery,
                onQueryChange = { libraryViewModel.updateFolderSearchQuery(it) },
                placeholderText =
                        "Search ${if (pagerState.currentPage == 0) "folders" else if (pagerState.currentPage == 1) "movies" else "playlists"}..."
        )

        // 2. Tabs + View Toggle (Combined Row to eliminate extra spacing)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Tabs occupy remaining space
            Box(modifier = Modifier.weight(1f)) {
                ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        edgePadding = 0.dp,
                        indicator = { tabPositions ->
                            Box(
                                    Modifier.tabIndicatorOffset(
                                                    tabPositions[pagerState.currentPage]
                                            )
                                            .height(3.dp)
                                            .background(primaryAccent)
                            )
                        },
                        divider = {
                            HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                ) {
                    val tabs = listOf("FOLDERS", "MOVIES", "PLAYLISTS")
                    tabs.forEachIndexed { index, title ->
                        Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = {
                                    Text(
                                            text = title,
                                            fontWeight =
                                                    if (pagerState.currentPage == index)
                                                            FontWeight.Bold
                                                    else FontWeight.Normal,
                                            letterSpacing = 1.sp,
                                            color =
                                                    if (pagerState.currentPage == index)
                                                            primaryAccent
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                        )
                    }
                }
            }

            // View Toggle Button (Only visible on Folders tab)
            if (pagerState.currentPage == 0) {
                IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                            imageVector =
                                    if (isGridView) Icons.AutoMirrored.Filled.ViewList
                                    else Icons.Default.GridView,
                            contentDescription = "Change View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 3. Content
        PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { libraryViewModel.scanMedia() },
                modifier = Modifier.weight(1f)
        ) {
            androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> {
                        // FOLDERS VIEW
                        val filteredFolders =
                                remember(folders, searchQuery) {
                                    if (searchQuery.isEmpty()) {
                                        folders
                                    } else {
                                        folders.filter {
                                            it.name.contains(searchQuery, ignoreCase = true)
                                        }
                                    }
                                }

                        // Continue Watching is only shown on the unfiltered folder view
                        val showContinueWatching =
                                searchQuery.isEmpty() && continueWatching.isNotEmpty()

                        if (filteredFolders.isEmpty() && !showContinueWatching) {
                            Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                            ) {
                                Text(
                                        "No folders found",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            if (isGridView) {
                                val widthClass = com.local.offlinemediaplayer.ui.adaptive.LocalWindowSizeClass.current
                                LazyVerticalGrid(
                                columns = GridCells.Fixed(com.local.offlinemediaplayer.ui.adaptive.adaptiveGridColumns(widthClass)),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(24.dp),
                                        modifier = Modifier.fillMaxSize()
                                ) {
                                    if (showContinueWatching) {
                                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                            ContinueWatchingRow(
                                                    items = continueWatching,
                                                    accentColor = primaryAccent,
                                                    onVideoClick = onVideoClick
                                            )
                                        }
                                    }
                                    items(filteredFolders, key = { it.id }) { folder ->
                                        FolderItem(folder, onFolderClick, primaryAccent)
                                    }
                                }
                            } else {
                                LazyColumn(
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                ) {
                                    if (showContinueWatching) {
                                        item {
                                            ContinueWatchingRow(
                                                    items = continueWatching,
                                                    accentColor = primaryAccent,
                                                    onVideoClick = onVideoClick
                                            )
                                        }
                                    }
                                    items(filteredFolders, key = { it.id }) { folder ->
                                        FolderListItem(folder, onFolderClick, primaryAccent)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // MOVIES VIEW (Videos > 1h)
                        MoviesListContent(
                                libraryViewModel = libraryViewModel,
                                onVideoClick = onVideoClick,
                                isGridView = isMoviesGridView,
                                onToggleView = { isMoviesGridView = !isMoviesGridView }
                        )
                    }
                    2 -> {
                        // PLAYLISTS LIST
                        PlaylistListScreen(
                                viewModel = viewModel, // Keep PlaybackViewModel for now if required by child components
                                onPlaylistClick = onPlaylistClick,
                                onCreateClick = { showCreateDialog = true },
                                isVideo = true, // Show video playlists
                                onRename = { id, newName -> playlistViewModel.renamePlaylist(id, newName) },
                                onDelete = { id -> playlistViewModel.deletePlaylist(id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name -> playlistViewModel.createPlaylist(name, isVideo = true) }
        )
    }
}

@Composable
private fun MoviesListContent(
        libraryViewModel: LibraryViewModel,
        onVideoClick: (MediaFile, List<MediaFile>) -> Unit,
        isGridView: Boolean,
        onToggleView: () -> Unit,
        playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val movies by libraryViewModel.sortedMovies.collectAsStateWithLifecycle()
    val movieSortState by libraryViewModel.movieSortState.collectAsStateWithLifecycle()
    val searchQuery by
            libraryViewModel.folderSearchQuery.collectAsStateWithLifecycle() // Reuse existing search query
    val watchProgress by libraryViewModel.watchProgressMap.collectAsStateWithLifecycle()

    // Properties Dialog State
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var selectedVideoForProperties by remember { mutableStateOf<MediaFile?>(null) }
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
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                    text = "${filteredMovies.size} MOVIES (>1h)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sort Menu
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SortDropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            fields = SortField.entries,
                            sortState = movieSortState,
                            onSortChange = { libraryViewModel.updateMovieSort(it) }
                    )
                }

                // View Toggle
                IconButton(onClick = onToggleView) {
                    Icon(
                            imageVector =
                                    if (isGridView) Icons.AutoMirrored.Filled.ViewList
                                    else Icons.Default.GridView,
                            contentDescription = "Change View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (filteredMovies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                        text =
                                if (searchQuery.isNotEmpty()) "No movies found matching search"
                                else "No videos longer than 1h found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val primaryAccent = LocalAppTheme.current.primaryColor
            if (isGridView) {
                val widthClass = com.local.offlinemediaplayer.ui.adaptive.LocalWindowSizeClass.current
                LazyVerticalGrid(
                        columns = GridCells.Fixed(com.local.offlinemediaplayer.ui.adaptive.adaptiveGridColumns(widthClass)),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
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
                                progress = watchProgress[movie.id] ?: 0f
                        )
                    }
                }
            } else {
                LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
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
                                progress = watchProgress[movie.id] ?: 0f
                        )
                    }
                }
            }
        }
    }

    if (showPropertiesDialog && selectedVideoForProperties != null) {
        MediaPropertiesDialog(
                mediaFile = selectedVideoForProperties!!,
                onDismiss = { showPropertiesDialog = false }
        )
    }

    if (showDeleteConfirmDialog) {
        val selectedIds by libraryViewModel.selectedMediaIds.collectAsStateWithLifecycle()
        DeleteConfirmationDialog(
                count = selectedIds.size,
                onConfirm = { libraryViewModel.deleteSelectedMedia() },
                onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    if (showAddToPlaylistDialog && selectedVideoForPlaylist != null) {
        AddToPlaylistDialog(
                song = selectedVideoForPlaylist!!,
                playlistViewModel = playlistViewModel,
                onDismiss = { showAddToPlaylistDialog = false },
                onCreateNew = { showCreatePlaylistDialog = true }
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { name -> playlistViewModel.createPlaylist(name, isVideo = true) }
        )
    }
}

@Composable
private fun ContinueWatchingRow(
        items: List<ContinueWatchingItem>,
        accentColor: Color,
        onVideoClick: (MediaFile, List<MediaFile>) -> Unit
) {
    val playlist = remember(items) { items.map { it.media } }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
                text = "CONTINUE WATCHING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.media.id }) { item ->
                ContinueWatchingCard(
                        item = item,
                        accentColor = accentColor,
                        onClick = { onVideoClick(item.media, playlist) }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
        item: ContinueWatchingItem,
        accentColor: Color,
        onClick: () -> Unit
) {
    val video = item.media
    Column(modifier = Modifier.width(180.dp).clickable(onClick = onClick)) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                    model = video.thumbnailPath?.let { File(it) } ?: video.uri,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
            )

            // Play affordance
            Box(
                    modifier =
                            Modifier.align(Alignment.Center)
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                )
            }

            // Remaining time badge
            val remaining = (item.duration - item.position).coerceAtLeast(0L)
            Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
            ) {
                Text(
                        text = "${FormatUtils.formatDuration(remaining)} left",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (item.progress > 0f) {
                LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp),
                        color = accentColor,
                        trackColor = Color.Black.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FolderItem(folder: VideoFolder, onClick: (String) -> Unit, accentColor: Color) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick(folder.id) }) {
        // Card Area
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .aspectRatio(1.4f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
        ) {
            AsyncImage(
                    model = folder.thumbnailPath?.let { File(it) } ?: folder.thumbnailUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.4f
            )

            Box(
                    modifier =
                            Modifier.fillMaxSize()
                                    .background(
                                            Brush.verticalGradient(
                                                    listOf(
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = 0.6f)
                                                    )
                                            )
                                    )
            )

            Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(48.dp).align(Alignment.Center)
            )

            Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            ) {
                Text(
                        text = "${folder.videoCount} items",
                        color = Color.White,
                        style =
                                MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
        )

        Text(
                text = "${folder.videoCount} videos",
                style = MaterialTheme.typography.bodySmall,
                color = accentColor.copy(alpha = 0.8f) // Using accent for consistency
        )
    }
}

@Composable
fun FolderListItem(folder: VideoFolder, onClick: (String) -> Unit, accentColor: Color) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onClick(folder.id) }
                            .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
                modifier =
                        Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)
        ) {
            AsyncImage(
                    model = folder.thumbnailPath?.let { File(it) } ?: folder.thumbnailUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.5f
            )
            Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.align(Alignment.Center).size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
            )
            Text(
                    text = "${folder.videoCount} videos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
