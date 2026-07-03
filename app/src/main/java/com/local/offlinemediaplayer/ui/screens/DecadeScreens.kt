package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.Decade
import com.local.offlinemediaplayer.ui.adaptive.LocalWindowSizeClass
import com.local.offlinemediaplayer.ui.adaptive.adaptiveGridColumns
import com.local.offlinemediaplayer.ui.components.MiniPlayer
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel

/**
 * Browse songs grouped into decade buckets (derived from MediaFile.year). Tapping a decade
 * opens [DecadeDetailScreen], which lists the songs of that era.
 */
@Composable
fun DecadeListScreen(
    viewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onDecadeClick: (Int) -> Unit
) {
    val decades by libraryViewModel.decades.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isMiniPlayerVisible = currentTrack != null && !currentTrack!!.isVideo
    val bottomPadding = if (isMiniPlayerVisible) 100.dp else 16.dp
    val widthClass = LocalWindowSizeClass.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "${decades.size} DECADE${if (decades.size != 1) "S" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (decades.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No songs found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(adaptiveGridColumns(widthClass)),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(decades, key = { it.startYear }) { decade ->
                    DecadeCard(decade = decade, onClick = { onDecadeClick(decade.startYear) })
                }
            }
        }
    }
}

@Composable
private fun DecadeCard(decade: Decade, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = decade.albumArtUri
                        ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                    contentDescription = decade.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                // Dark scrim + decade label so the era reads clearly over any art.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )
                Text(
                    text = decade.label,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${decade.songCount} song${if (decade.songCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Songs for a single decade, following the PlaylistDetailScreen pattern: search, a sort menu
 * whose active option toggles ascending/descending on re-tap (persisted per decade), a reset
 * chip, and Play All / Shuffle actions.
 */
@Composable
fun DecadeDetailScreen(
    decadeStart: Int,
    viewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val allAudio by libraryViewModel.audioList.collectAsStateWithLifecycle()
    val playCountMap by libraryViewModel.playCountMap.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isMiniPlayerVisible = currentTrack != null && !currentTrack!!.isVideo
    val bottomPadding = if (isMiniPlayerVisible) 120.dp else 16.dp

    val title = remember(decadeStart) { if (decadeStart <= 0) "Unknown" else "${decadeStart}s" }

    // Default order: chronological within the decade, then by title.
    val songs = remember(allAudio, decadeStart) {
        allAudio
            .filter { (it.year ?: 0) / 10 * 10 == decadeStart }
            .sortedWith(compareBy({ it.year ?: 0 }, { it.title.lowercase() }))
    }

    // If the underlying library no longer has songs for this decade, return.
    LaunchedEffect(allAudio, decadeStart) {
        if (allAudio.isNotEmpty() && songs.isEmpty()) onBack()
    }

    // Colors
    val primaryAccent = LocalAppTheme.current.primaryColor

    // UI States
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    // Sort state — restored from persistence, keyed per decade
    val sortKey = "decade_$decadeStart"
    val (persistedSort, persistedAsc) = playlistViewModel.getAudioPlaylistSort(sortKey)
    var selectedSort by remember { mutableStateOf(persistedSort) }
    var sortAscending by remember { mutableStateOf(persistedAsc) }

    // Persist sort changes immediately
    fun persistSortState(sort: AudioSortOption, ascending: Boolean) {
        selectedSort = sort
        sortAscending = ascending
        playlistViewModel.saveAudioPlaylistSort(sortKey, sort, ascending)
    }

    // Reset sort state when the decade changes
    LaunchedEffect(decadeStart) {
        val (sort, asc) = playlistViewModel.getAudioPlaylistSort(sortKey)
        selectedSort = sort
        sortAscending = asc
    }

    // Sort + Filter
    val sortedAndFilteredSongs = remember(songs, searchQuery, selectedSort, sortAscending, playCountMap) {
        val filtered = if (searchQuery.isEmpty()) songs
        else songs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                (it.artist?.contains(searchQuery, ignoreCase = true) == true)
        }

        val sorted = when (selectedSort) {
            AudioSortOption.DEFAULT -> filtered
            AudioSortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
            AudioSortOption.ARTIST -> filtered.sortedBy { (it.artist ?: "Unknown").lowercase() }
            AudioSortOption.DURATION -> filtered.sortedBy { it.duration }
            AudioSortOption.SIZE -> filtered.sortedBy { it.size }
            AudioSortOption.DATE_MODIFIED -> filtered.sortedBy { it.dateModified }
            AudioSortOption.MOST_PLAYED -> filtered.sortedBy { playCountMap[it.id] ?: 0 }
            AudioSortOption.LATEST -> filtered.sortedBy { it.dateAdded }
        }

        if (selectedSort != AudioSortOption.DEFAULT && !sortAscending) {
            sorted.reversed()
        } else sorted
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Warm gradient overlay at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = 0.28f),
                            primaryAccent.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar: Back + Search + Sort ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Search Field
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search $title...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = primaryAccent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                )

                // Sort Toggle
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = if (selectedSort != AudioSortOption.DEFAULT) primaryAccent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        AudioSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            option.label,
                                            color = if (selectedSort == option) primaryAccent
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (selectedSort == option && option != AudioSortOption.DEFAULT) {
                                            Icon(
                                                if (!sortAscending)
                                                    Icons.Default.ArrowDownward
                                                else Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = primaryAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (selectedSort == option && option != AudioSortOption.DEFAULT) {
                                        persistSortState(option, !sortAscending)
                                    } else {
                                        persistSortState(option, true)
                                    }
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // ── Header Row: Decade + Count + Play/Shuffle ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${songs.size} Song${if (songs.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Sort indicator chip
                        if (selectedSort != AudioSortOption.DEFAULT) {
                            AssistChip(
                                onClick = {
                                    persistSortState(AudioSortOption.DEFAULT, true)
                                },
                                label = {
                                    Text(
                                        selectedSort.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear Sort",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = primaryAccent.copy(alpha = 0.15f),
                                    labelColor = primaryAccent
                                ),
                                border = null
                            )
                        }
                    }
                }

                // Play / Shuffle Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Play All
                    FilledIconButton(
                        onClick = {
                            if (sortedAndFilteredSongs.isNotEmpty()) {
                                viewModel.setQueue(sortedAndFilteredSongs, 0, false)
                            }
                        },
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = primaryAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play All",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Shuffle
                    FilledIconButton(
                        onClick = {
                            if (sortedAndFilteredSongs.isNotEmpty()) {
                                viewModel.playAll(sortedAndFilteredSongs, shuffle = true)
                            }
                        },
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            Icons.Outlined.Shuffle,
                            contentDescription = "Shuffle",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── Song List ──
            if (sortedAndFilteredSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No results found"
                        else "No songs in this decade",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 4.dp,
                        bottom = bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(sortedAndFilteredSongs, key = { _, song -> song.id }) { index, song ->
                        AudioPlaylistItemCard(
                            song = song,
                            accentColor = primaryAccent,
                            onClick = {
                                viewModel.setQueue(sortedAndFilteredSongs, index, false)
                            },
                            onPlayNext = { viewModel.playNext(song) },
                            onAddToQueue = { viewModel.addToQueue(song) }
                        )
                    }
                }
            }
        }

        MiniPlayer(
            viewModel = viewModel,
            onTap = onNavigateToPlayer,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
