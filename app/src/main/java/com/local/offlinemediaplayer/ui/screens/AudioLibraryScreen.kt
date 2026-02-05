
package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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

@Composable
fun AudioLibraryScreen(
    viewModel: MainViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToAlbum: (Long) -> Unit
) {
    // 0 = Tracks, 1 = Albums, 2 = Playlists
    var selectedSubTab by remember { mutableIntStateOf(0) }

    // Add to Playlist Dialog State
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var songToAdd by remember { mutableStateOf<MediaFile?>(null) }

    // Create Playlist Dialog State
    var showCreateDialog by remember { mutableStateOf(false) }

    // App Theme Colors
    val backgroundColor = Color(0xFF12121A) // Ink Dark
    val primaryAccent = Color(0xFFE11D48)   // Ember Red
    val inactiveColor = Color.Gray

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        Column {
            // Styled Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = Color.Transparent, // Transparent to show background
                contentColor = Color.White,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    if (selectedSubTab < tabPositions.size) {
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[selectedSubTab])
                                .height(3.dp)
                                .background(primaryAccent)
                        )
                    }
                },
                divider = {
                    Divider(color = Color.White.copy(alpha = 0.1f))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                val tabs = listOf("TRACKS", "ALBUMS", "PLAYLISTS")
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedSubTab == index,
                        onClick = { selectedSubTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 1.sp,
                                color = if (selectedSubTab == index) primaryAccent else inactiveColor
                            )
                        }
                    )
                }
            }

            // Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (selectedSubTab) {
                    0 -> {
                        // TRACKS VIEW
                        AudioListScreen(
                            viewModel = viewModel,
                            onAudioClick = { file ->
                                viewModel.playMedia(file)
                                onNavigateToPlayer()
                            },
                            onAddToPlaylist = { file ->
                                songToAdd = file
                                showAddToPlaylistDialog = true
                            }
                        )
                    }
                    1 -> {
                        // ALBUMS VIEW
                        AlbumListScreen(
                            viewModel = viewModel,
                            onAlbumClick = onNavigateToAlbum
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
