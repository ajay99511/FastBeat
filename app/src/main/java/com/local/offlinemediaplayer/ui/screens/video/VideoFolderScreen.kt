package com.local.offlinemediaplayer.ui.screens.video

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.ui.components.CreatePlaylistDialog
import com.local.offlinemediaplayer.ui.screens.playlist.PlaylistListScreen
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel
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
    isSearchVisible: Boolean,
) {
    // 0 = Folders, 1 = Movies, 2 = Playlists
    val pagerState =
        androidx.compose.foundation.pager
            .rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }

    // View Mode State: True = Grid, False = List (persisted in preferences via LibraryViewModel)
    val isGridView by libraryViewModel.folderGridView.collectAsStateWithLifecycle()

    // Movies Tab Specific State (persisted in preferences via LibraryViewModel)
    val isMoviesGridView by libraryViewModel.movieGridView.collectAsStateWithLifecycle()

    val folders by libraryViewModel.videoFolders.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.folderSearchQuery.collectAsStateWithLifecycle()
    val continueWatching by libraryViewModel.continueWatching.collectAsStateWithLifecycle()
    val primaryAccent = LocalAppTheme.current.primaryColor

    // Refresh State
    val isRefreshing by libraryViewModel.isRefreshing.collectAsStateWithLifecycle()

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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 1. Collapsible Search Box
        CollapsibleSearchBox(
            isVisible = isSearchVisible,
            query = searchQuery,
            onQueryChange = { libraryViewModel.updateFolderSearchQuery(it) },
            placeholderText =
                "Search ${if (pagerState.currentPage == 0) {
                    "folders"
                } else if (pagerState.currentPage == 1) {
                    "movies"
                } else {
                    "playlists"
                }}...",
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
                            Modifier
                                .tabIndicatorOffset(
                                    tabPositions[pagerState.currentPage],
                                ).height(3.dp)
                                .background(primaryAccent),
                        )
                    },
                    divider = {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                                        if (pagerState.currentPage == index) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    letterSpacing = 1.sp,
                                    color =
                                        if (pagerState.currentPage == index) {
                                            primaryAccent
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            },
                        )
                    }
                }
            }

            // View Toggle Button (Only visible on Folders tab)
            if (pagerState.currentPage == 0) {
                IconButton(
                    onClick = { libraryViewModel.toggleFolderGridView() },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(
                        imageVector =
                            if (isGridView) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Default.GridView
                            },
                        contentDescription = "Change View",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // 3. Content
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { libraryViewModel.scanMedia() },
            modifier = Modifier.weight(1f),
        ) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
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
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No folders found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
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
                                    verticalArrangement = Arrangement.spacedBy(24.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    if (showContinueWatching) {
                                        item(
                                            span = {
                                                androidx.compose.foundation.lazy.grid
                                                    .GridItemSpan(maxLineSpan)
                                            },
                                        ) {
                                            ContinueWatchingRow(
                                                items = continueWatching,
                                                accentColor = primaryAccent,
                                                onVideoClick = onVideoClick,
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
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    if (showContinueWatching) {
                                        item {
                                            ContinueWatchingRow(
                                                items = continueWatching,
                                                accentColor = primaryAccent,
                                                onVideoClick = onVideoClick,
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
                            onToggleView = { libraryViewModel.toggleMovieGridView() },
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
                            onDelete = { id -> playlistViewModel.deletePlaylist(id) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name -> playlistViewModel.createPlaylist(name, isVideo = true) },
        )
    }
}
