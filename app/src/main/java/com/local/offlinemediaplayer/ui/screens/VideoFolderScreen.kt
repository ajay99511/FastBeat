
package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
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
import com.local.offlinemediaplayer.model.VideoFolder
import com.local.offlinemediaplayer.ui.components.CreatePlaylistDialog
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun VideoFolderScreen(
    viewModel: MainViewModel,
    onFolderClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    // 0 = Folders, 1 = Playlists
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val folders by viewModel.videoFolders.collectAsStateWithLifecycle()
    val searchQuery by viewModel.folderSearchQuery.collectAsStateWithLifecycle()
    val primaryAccent = LocalAppTheme.current.primaryColor

    val backgroundColor = Color(0xFF0B0B0F)
    val searchBarBg = Color(0xFF16161D)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // 1. Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(50))
                    .background(searchBarBg)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateFolderSearchQuery(it) },
                    placeholder = {
                        Text(
                            "Search ${if (selectedTab == 0) "folders" else "playlists"}...",
                            color = Color(0xFF475569),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = Color(0xFF475569)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateFolderSearchQuery("") }) {
                                Icon(Icons.Default.Close, null, tint = Color.Gray)
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = primaryAccent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "View",
                tint = Color(0xFF475569),
                modifier = Modifier.size(24.dp)
            )
        }

        // 2. Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color.White,
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
                Divider(color = Color.White.copy(alpha = 0.1f))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            val tabs = listOf("FOLDERS", "PLAYLISTS")
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            letterSpacing = 1.sp,
                            color = if (selectedTab == index) primaryAccent else Color.Gray
                        )
                    }
                )
            }
        }

        // 3. Content
        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                // FOLDERS GRID
                val filteredFolders = if (searchQuery.isEmpty()) {
                    folders
                } else {
                    folders.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }

                if (filteredFolders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No folders found", color = Color.Gray)
                    }
                } else {
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
                }
            } else {
                // PLAYLISTS LIST
                PlaylistListScreen(
                    viewModel = viewModel,
                    onPlaylistClick = onPlaylistClick,
                    onCreateClick = { showCreateDialog = true },
                    isVideo = true // Show video playlists
                )
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
                .background(Color(0xFF1E1E24))
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
            color = Color.White,
            maxLines = 1
        )

        Text(
            text = "${folder.videoCount} videos",
            style = MaterialTheme.typography.bodySmall,
            color = accentColor.copy(alpha = 0.8f) // Using accent for consistency
        )
    }
}
