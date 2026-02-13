
package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.AddToPlaylistDialog
import com.local.offlinemediaplayer.ui.components.CreatePlaylistDialog
import com.local.offlinemediaplayer.ui.components.MiniPlayer
import com.local.offlinemediaplayer.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun AudioLibraryScreen(
    viewModel: MainViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    isSearchVisible: Boolean
) {
    // 0 = Tracks, 1 = Albums, 2 = Playlists
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    // Add to Playlist Dialog State
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var songToAdd by remember { mutableStateOf<MediaFile?>(null) }

    // Create Playlist Dialog State
    var showCreateDialog by remember { mutableStateOf(false) }

    // App Theme Colors
    val primaryAccent = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column {
            // Styled Tab Row
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent, // Transparent to show background
                contentColor = MaterialTheme.colorScheme.onBackground,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                            .height(3.dp)
                            .background(primaryAccent)
                    )
                },
                divider = {
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                val tabs = listOf("TRACKS", "ALBUMS", "PLAYLISTS")
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 1.sp,
                                color = if (pagerState.currentPage == index) primaryAccent else inactiveColor
                            )
                        }
                    )
                }
            }

            // Content Area
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        // TRACKS VIEW
                        AudioListScreen(
                            viewModel = viewModel,
                            onAudioClick = { file ->
                                viewModel.playMedia(file)
                            },
                            onAddToPlaylist = { file ->
                                songToAdd = file
                                showAddToPlaylistDialog = true
                            },
                            isSearchVisible = isSearchVisible
                        )
                    }
                    1 -> {
                        // ALBUMS VIEW
                        AlbumListScreen(
                            viewModel = viewModel,
                            onAlbumClick = onNavigateToAlbum,
                            isSearchVisible = isSearchVisible
                        )
                    }
                    2 -> {
                        // PLAYLISTS VIEW
                        PlaylistListScreen(
                            viewModel = viewModel,
                            onPlaylistClick = onNavigateToPlaylist,
                            onCreateClick = { showCreateDialog = true },
                            isVideo = false // Explicitly Audio
                        )
                    }
                }
            }
        }

        // Mini Player
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            MiniPlayer(
                viewModel = viewModel,
                onTap = onNavigateToPlayer
            )
        }
    }

    // Dialogs
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name -> viewModel.createPlaylist(name, isVideo = false) }
        )
    }

    if (showAddToPlaylistDialog && songToAdd != null) {
        AddToPlaylistDialog(
            song = songToAdd!!,
            viewModel = viewModel,
            onDismiss = { showAddToPlaylistDialog = false },
            onCreateNew = { showCreateDialog = true } // Stack dialogs
        )
    }
}
