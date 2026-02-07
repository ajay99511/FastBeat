package com.local.offlinemediaplayer.ui.screens

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
//import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.ui.components.CreatePlaylistDialog
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.MainViewModel
import com.local.offlinemediaplayer.viewmodel.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFolderScreen(
    viewModel: MainViewModel,
    onFolderClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onVideoClick: (MediaFile) -> Unit,
    isSearchVisible: Boolean
) {
    // 0 = Folders, 1 = Movies, 2 = Playlists
    // Use rememberSaveable to persist selection when navigating back from details
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // View Mode State: True = Grid, False = List
    var isGridView by rememberSaveable { mutableStateOf(true) }

    // Movies Tab Specific State
    var isMoviesGridView by rememberSaveable { mutableStateOf(true) }

    val folders by viewModel.videoFolders.collectAsStateWithLifecycle()
    val searchQuery by viewModel.folderSearchQuery.collectAsStateWithLifecycle()
    val primaryAccent = LocalAppTheme.current.primaryColor

    // Refresh State
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Collapsible Search Box
        CollapsibleSearchBox(
            isVisible = isSearchVisible,
            query = searchQuery,
            onQueryChange = { viewModel.updateFolderSearchQuery(it) },
            placeholderText = "Search ${if (selectedTab == 0) "folders" else if (selectedTab == 1) "movies" else "playlists"}..."
        )

        // 2. Tabs + View Toggle (Combined Row to eliminate extra spacing)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tabs occupy remaining space
            Box(modifier = Modifier.weight(1f)) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            Box(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[selectedTab])
                                    .height(3.dp)
                                    .background(primaryAccent)
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tabs = listOf("FOLDERS", "MOVIES", "PLAYLISTS")
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    letterSpacing = 1.sp,
                                    color = if (selectedTab == index) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }

            // View Toggle Button (Only visible on Folders tab)
            if (selectedTab == 0) {
                IconButton(
                    onClick = { isGridView = !isGridView },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
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
            onRefresh = { viewModel.scanMedia() },
            modifier = Modifier.weight(1f)
        ) {
            when (selectedTab) {
                0 -> {
                    // FOLDERS VIEW
                    val filteredFolders = if (searchQuery.isEmpty()) {
                        folders
                    } else {
                        folders.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    if (filteredFolders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No folders found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        if (isGridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredFolders) { folder ->
                                    FolderItem(folder, onFolderClick, primaryAccent)
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredFolders) { folder ->
                                    FolderListItem(folder, onFolderClick, primaryAccent)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // MOVIES VIEW (Videos > 1h)
                    MoviesListContent(
                        viewModel = viewModel,
                        onVideoClick = onVideoClick,
                        isGridView = isMoviesGridView,
                        onToggleView = { isMoviesGridView = !isMoviesGridView }
                    )
                }
                2 -> {
                    // PLAYLISTS LIST
                    PlaylistListScreen(
                        viewModel = viewModel,
                        onPlaylistClick = onPlaylistClick,
                        onCreateClick = { showCreateDialog = true },
                        isVideo = true, // Show video playlists
                        onRename = { id, newName -> viewModel.renamePlaylist(id, newName) },
                        onDelete = { id -> viewModel.deletePlaylist(id) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name -> viewModel.createPlaylist(name, isVideo = true) }
        )
    }
}

@Composable
private fun MoviesListContent(
    viewModel: MainViewModel,
    onVideoClick: (MediaFile) -> Unit,
    isGridView: Boolean,
    onToggleView: () -> Unit
) {
    val movies by viewModel.sortedMovies.collectAsStateWithLifecycle()
    val sortOption by viewModel.movieSortOption.collectAsStateWithLifecycle()
    val searchQuery by viewModel.folderSearchQuery.collectAsStateWithLifecycle() // Reuse existing search query

    var showSortMenu by remember { mutableStateOf(false) }

    val filteredMovies = if (searchQuery.isEmpty()) {
        movies
    } else {
        movies.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Row (Count + Sort + View)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${filteredMovies.size} MOVIES (>1h)",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sort Menu
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.Gray)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E24))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Latest Added", color = Color.White) },
                            onClick = {
                                viewModel.updateMovieSortOption(SortOption.DATE_ADDED_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Longest", color = Color.White) },
                            onClick = {
                                viewModel.updateMovieSortOption(SortOption.DURATION_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Shortest", color = Color.White) },
                            onClick = {
                                viewModel.updateMovieSortOption(SortOption.DURATION_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("A-Z", color = Color.White) },
                            onClick = {
                                viewModel.updateMovieSortOption(SortOption.TITLE_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Z-A", color = Color.White) },
                            onClick = {
                                viewModel.updateMovieSortOption(SortOption.TITLE_DESC)
                                showSortMenu = false
                            }
                        )
                    }
                }

                // View Toggle
                IconButton(onClick = onToggleView) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Change View",
                        tint = Color.Gray
                    )
                }
            }
        }

        if (filteredMovies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if(searchQuery.isNotEmpty()) "No movies found matching search" else "No videos longer than 1h found",
                    color = Color.Gray
                )
            }
        } else {
            val primaryAccent = LocalAppTheme.current.primaryColor
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredMovies) { movie ->
                    if (isGridView) {
                        // Reusing VideoCardItem from VideoListScreen (made public)
                        VideoCardItem(
                            video = movie,
                            onVideoClick = { onVideoClick(movie) },
                            onLongClick = {}, // No selection mode in Movies tab for simplicity
                            accentColor = primaryAccent,
                            onAddToPlaylist = {}, // Simplification
                            isSelectionMode = false,
                            isSelected = false,
                            onDelete = {}
                        )
                    } else {
                        // Reusing VideoListItem from VideoListScreen (made public)
                        VideoListItem(
                            video = movie,
                            onVideoClick = { onVideoClick(movie) },
                            onLongClick = {},
                            onAddToPlaylist = {},
                            isSelectionMode = false,
                            isSelected = false,
                            onDelete = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderItem(
    folder: VideoFolder,
    onClick: (String) -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(folder.id) }
    ) {
        // Card Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AsyncImage(
                model = folder.thumbnailUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.4f
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
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
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
fun FolderListItem(
    folder: VideoFolder,
    onClick: (String) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick(folder.id) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            AsyncImage(
                model = folder.thumbnailUri,
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
