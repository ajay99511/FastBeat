
package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
//import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.GridView
//import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
//import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.Album
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.viewmodel.AlbumSortOption
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun AlbumListScreen(
    viewModel: MainViewModel,
    onAlbumClick: (Long) -> Unit,
    isSearchVisible: Boolean
) {
    val albums by viewModel.filteredAlbums.collectAsStateWithLifecycle()
    val searchQuery by viewModel.albumSearchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.albumSortOption.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isMiniPlayerVisible = currentTrack != null && !currentTrack!!.isVideo
    val bottomPadding = if (isMiniPlayerVisible) 100.dp else 16.dp

    // Local state for view mode (Grid vs List) - Persisted across recompositions but not app restarts
    var isListView by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Collapsible Search Bar
        CollapsibleSearchBox(
            isVisible = isSearchVisible,
            query = searchQuery,
            onQueryChange = { viewModel.updateAlbumSearchQuery(it) },
            placeholderText = "Search albums..."
        )

        // 2. Control Row (Count + Actions)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${albums.size} ALBUMS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // View Toggle
                IconButton(onClick = { isListView = !isListView }) {
                    Icon(
                        imageVector = if (isListView) Icons.Default.GridView else Icons.AutoMirrored.Filled.List,
                        contentDescription = if (isListView) "Grid View" else "List View",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sort Button
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort Albums",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (A-Z)") },
                            onClick = {
                                viewModel.updateAlbumSortOption(AlbumSortOption.NAME_ASC)
                                showSortMenu = false
                            },
                            trailingIcon = {
                                if (sortOption == AlbumSortOption.NAME_ASC) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow, // Checkmark proxy or similar if needed, or just highlight
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Artist (A-Z)") },
                            onClick = {
                                viewModel.updateAlbumSortOption(AlbumSortOption.ARTIST_ASC)
                                showSortMenu = false
                            },
                             trailingIcon = {
                                if (sortOption == AlbumSortOption.ARTIST_ASC) {
                                     Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Year (Newest)") },
                            onClick = {
                                viewModel.updateAlbumSortOption(AlbumSortOption.YEAR_DESC)
                                showSortMenu = false
                            },
                             trailingIcon = {
                                if (sortOption == AlbumSortOption.YEAR_DESC) {
                                     Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Song Count") },
                            onClick = {
                                viewModel.updateAlbumSortOption(AlbumSortOption.SONG_COUNT_DESC)
                                showSortMenu = false
                            },
                             trailingIcon = {
                                if (sortOption == AlbumSortOption.SONG_COUNT_DESC) {
                                     Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // 3. Album List / Grid
        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isNotEmpty()) "No results found" else "No albums found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (isListView) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomPadding), // Padding for MiniPlayer
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albums) { album ->
                        AlbumListItem(
                            album = album,
                            onClick = { onAlbumClick(album.id) },
                            onPlayClick = {
                                viewModel.playAlbum(album, false)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 88.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albums) { album ->
                        AlbumItemStyled(
                            album = album,
                            onClick = { onAlbumClick(album.id) },
                            onPlayClick = {
                                viewModel.playAlbum(album, false)
                            }
                        )
                    }
                    // Padding for MiniPlayer
                    item { Spacer(modifier = Modifier.height(if (isMiniPlayerVisible) 70.dp else 0.dp)) }
                    item { Spacer(modifier = Modifier.height(if (isMiniPlayerVisible) 70.dp else 0.dp)) }
                }
            }
        }
    }
}

@Composable
fun AlbumListItem(
    album: Album,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val primaryAccent = Color(0xFFE11D48)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = album.albumArtUri ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )
             // Play Button Overlay (Mini)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .background(primaryAccent.copy(alpha = 0.7f), CircleShape)
                    .clickable { onPlayClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Album",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Meta (Year / Count)
        Column(horizontalAlignment = Alignment.End) {
             if (album.firstYear != null) {
                Text(
                    text = album.firstYear.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
             }
            Text(
                text = "${album.songCount} songs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun AlbumItemStyled(
    album: Album,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val primaryAccent = Color(0xFFE11D48)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = null
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Album Art Box with Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = album.albumArtUri ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                    contentDescription = album.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )

                // Play Button Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(primaryAccent.copy(alpha = 0.9f), CircleShape)
                        .clickable { onPlayClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Album",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Year • Song Count
            val metaText = buildString {
                if (album.firstYear != null) {
                    append("${album.firstYear} • ")
                }
                append("${album.songCount} Songs")
            }

            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}
