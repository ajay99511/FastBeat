
package com.local.offlinemediaplayer.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
//import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryMusic
//import androidx.compose.material.icons.outlined.Queue
//import androidx.compose.material.icons.outlined.QueueMusic
//import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.AddToPlaylistDialog
import com.local.offlinemediaplayer.ui.components.CreatePlaylistDialog
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlaybackViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isCurrentTrackFavorite.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val sleepTimerEnd by viewModel.sleepTimerEndMillis.collectAsStateWithLifecycle()

    // Queue State - uses displayQueue which shows shuffled order when shuffle is enabled
    val displayQueue by viewModel.displayQueue.collectAsStateWithLifecycle()
    val displayQueueIndex by viewModel.displayQueueIndex.collectAsStateWithLifecycle()

    // Bottom Sheet State
    var showQueueSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Menu and Dialog State
    var showMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }

    // Delete Intent Launcher
    val context = LocalContext.current
    val intentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onCurrentTrackDeleteSuccess()
        }
    }

    // Listen for delete completion to navigate back
    LaunchedEffect(Unit) {
        viewModel.onDeleteTrackComplete.collect {
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deleteIntentEvent.collect { intentSender ->
            intentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    // Colors from Theme
    val primaryAccent = LocalAppTheme.current.primaryColor
    val secondaryAccent = Color(0xFF8B51E6)

    val playButtonGradient = Brush.verticalGradient(
        colors = listOf(primaryAccent, secondaryAccent)
    )
    val progressBarGradient = Brush.horizontalGradient(
        colors = listOf(primaryAccent, Color(0xFFE44CD8), Color(0xFF42E8E0))
    )

    if (currentTrack == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Text("Nothing Playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NOW PLAYING",
                        color = primaryAccent,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "From \"${currentTrack?.artist ?: "Unknown"}\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Queue Button
                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(
                        imageVector = Icons.Outlined.LibraryMusic,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // More Options Menu (3-dots)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist") },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Outlined.PlaylistAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                showAddToPlaylistDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Playback speed (${formatSpeed(playbackSpeed)})") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                showSpeedDialog = true
                            }
                        )
                        // Sleep timer is night-only; show it inside the 10 PM–5 AM window,
                        // or whenever a timer is currently running (so it can be cancelled).
                        if (viewModel.isSleepTimerAllowed() || sleepTimerEnd != null) {
                            DropdownMenuItem(
                                text = { Text(if (sleepTimerEnd != null) "Sleep timer (on)" else "Sleep timer") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Bedtime,
                                        contentDescription = null,
                                        tint = if (sleepTimerEnd != null) primaryAccent else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showSleepTimerDialog = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Album Art with drop shadow
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = primaryAccent.copy(alpha = 0.25f)
                    )
                    .clip(RoundedCornerShape(28.dp))
            ) {
                AsyncImage(
                    model = currentTrack?.albumArtUri ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Track Info Row (Title + Like)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack?.title ?: "",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTrack?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { viewModel.toggleFavorite() }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gradient Progress Bar (Extracted to prevent full screen recomposition)
            PlaybackControlsWithProgress(
                currentPositionFlow = viewModel.currentPosition,
                duration = duration,
                progressBarGradient = progressBarGradient,
                onSeek = { viewModel.seekTo(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = { viewModel.playPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause (Gradient Circle with premium glow)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(24.dp, CircleShape, spotColor = primaryAccent.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(playButtonGradient)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                IconButton(onClick = { viewModel.playNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat
                    val tint = if (repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Queue Bottom Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            QueueSheetContent(
                queue = displayQueue,
                currentIndex = displayQueueIndex ?: 0,
                onTrackClick = { index ->
                    // Use playTrackFromQueue to properly handle shuffled playback
                    displayQueue.getOrNull(index)?.let { track ->
                        viewModel.playTrackFromQueue(track)
                    }
                },
                onRemove = { track -> viewModel.removeFromQueue(track) },
                onClear = { viewModel.clearQueueExceptCurrent() },
                onSaveAsPlaylist = { showSaveQueueDialog = true }
            )
        }
    }

    // Add to Playlist Dialog
    if (showAddToPlaylistDialog && currentTrack != null) {
        AddToPlaylistDialog(
            song = currentTrack!!,
            playlistViewModel = playlistViewModel,
            onDismiss = { showAddToPlaylistDialog = false },
            onCreateNew = { showCreateDialog = true }
        )
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name -> playlistViewModel.createPlaylist(name, currentTrack?.isVideo ?: false) }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        DeleteConfirmationDialog(
            count = 1,
            onConfirm = {
                viewModel.deleteCurrentTrack()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = playbackSpeed,
            onSelect = { speed ->
                viewModel.setPlaybackSpeed(speed)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    // Save Queue as Playlist Dialog
    if (showSaveQueueDialog) {
        CreatePlaylistDialog(
            onDismiss = { showSaveQueueDialog = false },
            onCreate = { name -> viewModel.saveQueueAsPlaylist(name) }
        )
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            isActive = sleepTimerEnd != null,
            onSelect = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                viewModel.cancelSleepTimer()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
}

@Composable
private fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val primaryAccent = LocalAppTheme.current.primaryColor
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("Playback speed", color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column {
                speeds.forEach { speed ->
                    val isSelected = kotlin.math.abs(speed - currentSpeed) < 0.001f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(speed) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatSpeed(speed) + if (speed == 1.0f) " (Normal)" else "",
                            color = if (isSelected) primaryAccent else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = primaryAccent)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun SleepTimerDialog(
    isActive: Boolean,
    onSelect: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(15, 30, 45, 60)
    val primaryAccent = LocalAppTheme.current.primaryColor
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("Sleep timer", color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column {
                Text(
                    "Pause playback after:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("$minutes minutes", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Available only between 10 PM and 5 AM.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (isActive) {
                TextButton(onClick = onCancelTimer) {
                    Text("Cancel timer", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun QueueSheetContent(
    queue: List<MediaFile>,
    currentIndex: Int,
    onTrackClick: (Int) -> Unit,
    onRemove: (MediaFile) -> Unit,
    onClear: () -> Unit,
    onSaveAsPlaylist: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to current track
    LaunchedEffect(Unit) {
        if (currentIndex in queue.indices) {
            listState.scrollToItem(currentIndex)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Playing Queue",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (queue.isNotEmpty()) {
                    IconButton(onClick = onSaveAsPlaylist) {
                        Icon(
                            Icons.AutoMirrored.Outlined.PlaylistAdd,
                            contentDescription = "Save queue as playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onClear) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(queue, key = { _, track -> track.id }) { index, track ->
                val isPlaying = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .clickable { onTrackClick(index) }
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPlaying) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = track.artist ?: "Unknown Artist",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Remove from queue (not shown for the currently playing track)
                    if (!isPlaying) {
                        IconButton(onClick = { onRemove(track) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove from queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun PlaybackControlsWithProgress(
    currentPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    duration: Long,
    progressBarGradient: Brush,
    onSeek: (Long) -> Unit
) {
    val currentPosition by currentPositionFlow.collectAsStateWithLifecycle()

    val thumbColor = MaterialTheme.colorScheme.primary

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(progress.coerceAtLeast(0.001f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(progressBarGradient)
                )
                Spacer(modifier = Modifier.weight((1f - progress).coerceAtLeast(0.001f)))
            }

            Slider(
                value = if (duration > 0) currentPosition.toFloat() else 0f,
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = thumbColor,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatDuration(duration),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
