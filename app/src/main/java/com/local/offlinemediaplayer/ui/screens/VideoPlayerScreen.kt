package com.local.offlinemediaplayer.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.ui.components.AddToPlaylistDialog
import com.local.offlinemediaplayer.ui.components.CreatePlaylistDialog
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel
import com.local.offlinemediaplayer.viewmodel.ResizeMode
import com.local.offlinemediaplayer.viewmodel.TrackInfo
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.delay

private enum class GestureMode {
    NONE,
    VOLUME,
    BRIGHTNESS,
    SEEK
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
        viewModel: PlaybackViewModel,
        playlistViewModel: PlaylistViewModel = hiltViewModel(),
        onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val player by viewModel.player.collectAsStateWithLifecycle()
    val resizeMode by viewModel.resizeMode.collectAsStateWithLifecycle()
    val isLocked by viewModel.isPlayerLocked.collectAsStateWithLifecycle()
    val isInPip by viewModel.isInPipMode.collectAsStateWithLifecycle()
    val videoSize by viewModel.videoSize.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val primaryAccent = LocalAppTheme.current.primaryColor

    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val playerError by viewModel.playerError.collectAsStateWithLifecycle()

    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var gestureText by remember { mutableStateOf("") }
    var isSpeedBoosting by remember { mutableStateOf(false) }

    var initialVolume by remember { mutableIntStateOf(0) }
    var initialBrightness by remember { mutableFloatStateOf(0f) }
    var initialSeekPosition by remember { mutableLongStateOf(0L) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    var isControlsVisible by remember { mutableStateOf(true) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleTrackDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // External subtitle file picker
    val subtitlePickerLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    viewModel.addExternalSubtitle(uri)
                    showSubtitleTrackDialog = false
                }
            }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val gestureThresholdPx = with(density) { 12.dp.toPx() }

    // Track if we have valid metadata to prevent premature rotation (0x0 -> Landscape -> Portrait)
    var isVideoMetadataLoaded by remember { mutableStateOf(false) }

    // Debounce state to avoid rapid flickering if dimensions report weirdly at start
    LaunchedEffect(videoSize) {
        if (videoSize.width > 0 && videoSize.height > 0) {
            if (!isVideoMetadataLoaded) {
                // Small buffer to ensure stable readout, though >0 check is usually enough
                delay(100)
                isVideoMetadataLoaded = true
            }
        }
    }

    DisposableEffect(isVideoMetadataLoaded, videoSize) {
        val originalOrientation =
                activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // Only enforce orientation if we have valid metadata
        if (isVideoMetadataLoaded && videoSize.width > 0 && videoSize.height > 0) {
            val isPortraitVideo = videoSize.height > videoSize.width

            if (isPortraitVideo) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
//                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        hideSystemBars(activity)

        onDispose {
            activity?.requestedOrientation = originalOrientation
            showSystemBars(activity)
            val layoutParams = activity?.window?.attributes
            layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity?.window?.attributes = layoutParams
        }
    }

    // Re-apply the user's persisted brightness when the player opens, so the choice
    // survives app relaunches. A sentinel of -1f means "never set" -> follow system.
    LaunchedEffect(Unit) {
        val saved = viewModel.videoBrightness.value
        if (saved >= 0f) {
            val layoutParams = activity?.window?.attributes
            layoutParams?.screenBrightness = saved.coerceIn(0.01f, 1f)
            activity?.window?.attributes = layoutParams
        }
    }

    BackHandler {
        if (showBookmarksDialog) {
            showBookmarksDialog = false
        } else if (isLocked) {
            // Do nothing
        } else {
            onBack()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Update PiP Params for Android 12+ (Auto-Enter)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val aspectRatio = calculatePipAspectRatio(videoSize)

        // Key off isPlaying and aspectRatio state
        LaunchedEffect(isPlaying, aspectRatio) {
            // Re-verify range to be absolutely safe against 0 or negative values
            if (aspectRatio.numerator > 0 && aspectRatio.denominator > 0) {
                try {
                    val params =
                            PictureInPictureParams.Builder()
                                    .setAspectRatio(aspectRatio)
                                    .setAutoEnterEnabled(isPlaying)
                                    .build()
                    activity?.setPictureInPictureParams(params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (activity?.isInPictureInPictureMode == true) {
                    viewModel.setPipMode(true)
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                if (activity?.isInPictureInPictureMode == true) {
                    // In PiP mode, let it keep playing
                } else {
                    // Not in PiP mode, explicitly pause video so audio doesn't leak into background
                    viewModel.pauseVideo()
                }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (activity?.isInPictureInPictureMode == false) {
                    viewModel.setPipMode(false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isControlsVisible, player?.isPlaying, showBookmarksDialog) {
        if (isControlsVisible && player?.isPlaying == true && !showBookmarksDialog) {
            delay(3000)
            isControlsVisible = false
        }
    }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(Color.Black)
                            .pointerInput(isLocked, showBookmarksDialog) {
                                if (isLocked || showBookmarksDialog) return@pointerInput
                                detectTapGestures(
                                        onTap = { isControlsVisible = !isControlsVisible },
                                        onDoubleTap = { offset ->
                                            if (offset.x > screenWidth / 2) viewModel.forward()
                                            else viewModel.rewind()
                                        },
                                        onLongPress = {
                                            if (isPlaying) {
                                                isSpeedBoosting = true
                                                viewModel.startSpeedBoost()
                                            }
                                        }
                                )
                            }
                            .pointerInput(isLocked, showBookmarksDialog) {
                                if (isLocked || showBookmarksDialog) return@pointerInput
                                detectDragGestures(
                                        onDragStart = { offset ->
                                            accumulatedDragX = 0f
                                            accumulatedDragY = 0f
                                            gestureMode = GestureMode.NONE
                                            initialVolume =
                                                    audioManager.getStreamVolume(
                                                            AudioManager.STREAM_MUSIC
                                                    )
                                            val layoutParams = activity?.window?.attributes
                                            var bright = layoutParams?.screenBrightness ?: -1f
                                            if (bright < 0)
                                                    bright =
                                                            viewModel.videoBrightness.value.takeIf {
                                                                it >= 0f
                                                            }
                                                                    ?: 0.5f
                                            initialBrightness = bright
                                            initialSeekPosition = currentPosition
                                        },
                                        onDragEnd = {
                                            if (gestureMode == GestureMode.SEEK)
                                                    viewModel.seekTo(initialSeekPosition)
                                            // Persist brightness once the gesture finishes so it
                                            // survives relaunches, without writing on every frame.
                                            if (gestureMode == GestureMode.BRIGHTNESS) {
                                                val bright =
                                                        activity?.window?.attributes?.screenBrightness
                                                                ?: -1f
                                                if (bright >= 0f) viewModel.setVideoBrightness(bright)
                                            }
                                            gestureMode = GestureMode.NONE
                                            isControlsVisible = true
                                        },
                                        onDragCancel = { gestureMode = GestureMode.NONE },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            accumulatedDragX += dragAmount.x
                                            accumulatedDragY += dragAmount.y

                                            if (gestureMode == GestureMode.NONE) {
                                                if (abs(accumulatedDragX) > abs(accumulatedDragY)) {
                                                    if (abs(accumulatedDragX) > gestureThresholdPx)
                                                            gestureMode = GestureMode.SEEK
                                                } else {
                                                    if (abs(accumulatedDragY) > gestureThresholdPx) {
                                                        // Left = Brightness, Right = Volume (matches VLC/MX Player convention)
                                                        gestureMode =
                                                                if (change.position.x <
                                                                                screenWidth / 2
                                                                )
                                                                        GestureMode.BRIGHTNESS
                                                                else GestureMode.VOLUME
                                                    }
                                                }
                                            }

                                            when (gestureMode) {
                                                GestureMode.VOLUME -> {
                                                    val deltaPercent =
                                                            -accumulatedDragY / screenHeight
                                                    val newVol =
                                                            (initialVolume +
                                                                            (deltaPercent *
                                                                                    maxVolume *
                                                                                    3))
                                                                    .toInt()
                                                                    .coerceIn(0, maxVolume)
                                                    audioManager.setStreamVolume(
                                                            AudioManager.STREAM_MUSIC,
                                                            newVol,
                                                            0
                                                    )
                                                    gestureValue =
                                                            newVol.toFloat() / maxVolume.toFloat()
                                                    gestureText = "${(gestureValue * 100).toInt()}%"
                                                }
                                                GestureMode.BRIGHTNESS -> {
                                                    val deltaPercent =
                                                            -accumulatedDragY / screenHeight
                                                    val newBright =
                                                            (initialBrightness + (deltaPercent * 2))
                                                                    .coerceIn(0.01f, 1f)
                                                    val layoutParams = activity?.window?.attributes
                                                    layoutParams?.screenBrightness = newBright
                                                    activity?.window?.attributes = layoutParams
                                                    gestureValue = newBright
                                                    gestureText = "${(newBright * 100).toInt()}%"
                                                }
                                                GestureMode.SEEK -> {
                                                    val deltaPercent =
                                                            accumulatedDragX / screenWidth
                                                    // Scale seek range based on video duration: 15% of total, capped at 120s, min 30s
                                                    val maxSeekRange = min((duration * 0.15).toLong(), 120_000L).coerceAtLeast(30_000L)
                                                    val seekChange = (deltaPercent * maxSeekRange).toLong()
                                                    val newPos =
                                                            (initialSeekPosition + seekChange)
                                                                    .coerceIn(0, duration)
                                                    initialSeekPosition = newPos
                                                    gestureValue = newPos.toFloat()
                                                    gestureText = FormatUtils.formatSeekTime(newPos, duration)
                                                }
                                                else -> {}
                                            }
                                        }
                                )
                            }
                            .pointerInput(isLocked, showBookmarksDialog, isSpeedBoosting) {
                                if (isLocked || showBookmarksDialog) return@pointerInput
                                awaitEachGesture {
                                    // Wait for any finger down
                                    awaitPointerEvent()
                                    // Then keep consuming events until all fingers are lifted
                                    do {
                                        val event = awaitPointerEvent()
                                        if (isSpeedBoosting && event.changes.all { it.changedToUp() }) {
                                            isSpeedBoosting = false
                                            viewModel.stopSpeedBoost()
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
    ) {
        if (player != null) {
            AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            this.useController = false
                            this.layoutParams =
                                    FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                            this.keepScreenOn = true
                        }
                    },
                    update = { playerView ->
                        playerView.player = player
                        playerView.resizeMode =
                                when (resizeMode) {
                                    ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                    },
                    modifier = Modifier.fillMaxSize()
            )
        }

        CenterGestureOverlay(
                mode = gestureMode,
                value = gestureValue,
                text = gestureText,
                primaryAccent
        )

        // Buffering Indicator
        BufferingOverlay(isBuffering = isBuffering && playerError == null)

        // Error Overlay
        ErrorOverlay(
                error = playerError,
                onDismiss = { viewModel.dismissPlayerError() },
                onRetry = {
                    viewModel.dismissPlayerError()
                    player?.prepare()
                    player?.play()
                },
                accentColor = primaryAccent
        )

        // Speed Boost Indicator
        SpeedBoostOverlay(isActive = isSpeedBoosting)

        if (!isInPip) {
            VideoPlayerControls(
                    viewModel = viewModel,
                    isVisible = isControlsVisible,
                    onBack = onBack,
                    onPip = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val aspectRatio = calculatePipAspectRatio(videoSize)
                            try {
                                val params =
                                        PictureInPictureParams.Builder()
                                                .setAspectRatio(aspectRatio)
                                                .build()
                                activity?.enterPictureInPictureMode(params)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onRotate = {
                        if (activity?.requestedOrientation ==
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        ) {
                            activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        } else {
                            activity?.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                    onShowBookmarks = {
                        viewModel.pauseVideo()
                        showBookmarksDialog = true
                    },
                    onShowAudioTracks = { showAudioTrackDialog = true },
                    onShowSubtitleTracks = { showSubtitleTrackDialog = true },
                    onShowAddToPlaylist = { showAddToPlaylistDialog = true }
            )
        }

        // Bookmarks Overlay
        if (showBookmarksDialog) {
            BookmarksDialog(
                    viewModel = viewModel,
                    currentPosition = currentPosition,
                    onDismiss = { showBookmarksDialog = false },
                    onSeek = { pos ->
                        viewModel.seekTo(pos)
                        showBookmarksDialog = false
                        player?.play()
                    }
            )
        }

        // Audio Track Selection Dialog
        if (showAudioTrackDialog) {
            TrackSelectionDialog(
                    title = "Audio Tracks",
                    tracks = viewModel.getAudioTracks(),
                    showOffOption = false,
                    isOffSelected = false,
                    onTrackSelected = { groupIndex, trackIndex ->
                        viewModel.selectAudioTrack(groupIndex, trackIndex)
                        showAudioTrackDialog = false
                    },
                    onOffSelected = {},
                    onDismiss = { showAudioTrackDialog = false }
            )
        }

        // Subtitle Track Selection Dialog
        if (showSubtitleTrackDialog) {
            TrackSelectionDialog(
                    title = "Subtitles",
                    tracks = viewModel.getSubtitleTracks(),
                    showOffOption = true,
                    isOffSelected = viewModel.areSubtitlesDisabled(),
                    onTrackSelected = { groupIndex, trackIndex ->
                        viewModel.selectSubtitleTrack(groupIndex, trackIndex)
                        showSubtitleTrackDialog = false
                    },
                    onOffSelected = {
                        viewModel.disableSubtitles()
                        showSubtitleTrackDialog = false
                    },
                    onDismiss = { showSubtitleTrackDialog = false },
                    onAddExternal = {
                        // Allow any file type — .srt MIME reporting is inconsistent across providers
                        subtitlePickerLauncher.launch(arrayOf("*/*"))
                    }
            )
        }

        // Add to Playlist Dialog
        if (showAddToPlaylistDialog && currentTrack != null) {
            AddToPlaylistDialog(
                    song = currentTrack!!,
                    playlistViewModel = playlistViewModel,
                    onDismiss = { showAddToPlaylistDialog = false },
                    onCreateNew = { showCreatePlaylistDialog = true }
            )
        }

        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                    onDismiss = { showCreatePlaylistDialog = false },
                    onCreate = { name ->
                        playlistViewModel.createPlaylist(name, currentTrack?.isVideo ?: true)
                    }
            )
        }
    }
}

@Composable
fun BookmarksDialog(
        viewModel: PlaybackViewModel,
        currentPosition: Long,
        onDismiss: () -> Unit,
        onSeek: (Long) -> Unit
) {
    val bookmarks by viewModel.currentBookmarks.collectAsStateWithLifecycle()
    var newLabel by remember { mutableStateOf("") }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .clickable(onClick = onDismiss),
            contentAlignment = Alignment.CenterEnd
    ) {
        Column(
                modifier =
                        Modifier.fillMaxHeight()
                                .widthIn(min = 280.dp, max = 400.dp)
                                .fillMaxWidth(0.45f)
                                .background(Color(0xFF1E1E24))
                                .clickable(enabled = false) {} // Prevent click through
                                .padding(16.dp)
        ) {
            Text(
                    "Bookmarks / Chapters",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Add Bookmark Input
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        placeholder = { Text("Chapter Name", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.Gray
                                ),
                        singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                        onClick = {
                            val label =
                                    if (newLabel.isBlank())
                                            "Chapter at ${FormatUtils.formatDuration(currentPosition)}"
                                    else newLabel
                            viewModel.addBookmark(currentPosition, label)
                            newLabel = ""
                        }
                ) { Icon(Icons.Default.Add, null) }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                Modifier,
                DividerDefaults.Thickness,
                color = Color.Gray.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
            ) {
                items(bookmarks) { bookmark ->
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(
                                                    Color.Black.copy(alpha = 0.3f),
                                                    RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onSeek(bookmark.timestamp) }
                                            .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bookmark.label, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                    FormatUtils.formatDuration(bookmark.timestamp),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                            Icon(
                                    Icons.Default.Delete,
                                    "Delete bookmark",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterGestureOverlay(
        mode: GestureMode,
        value: Float,
        text: String,
        accentColor: Color
) {
    if (mode == GestureMode.NONE) return
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
                modifier =
                        Modifier.size(120.dp)
                                .background(
                                        Color.Black.copy(alpha = 0.7f),
                                        RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp),
                contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val icon =
                        when (mode) {
                            GestureMode.VOLUME -> Icons.AutoMirrored.Outlined.VolumeUp
                            GestureMode.BRIGHTNESS -> Icons.Outlined.BrightnessHigh
                            GestureMode.SEEK ->
                                    if (text.contains("-")) Icons.Default.FastRewind
                                    else Icons.Default.FastForward
                            else -> Icons.Default.Info
                        }
                val iconDescription =
                        when (mode) {
                            GestureMode.VOLUME -> "Volume"
                            GestureMode.BRIGHTNESS -> "Brightness"
                            GestureMode.SEEK -> "Seek"
                            else -> null
                        }
                Icon(
                        imageVector = icon,
                        contentDescription = iconDescription,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = text,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (mode == GestureMode.VOLUME || mode == GestureMode.BRIGHTNESS) {
                    LinearProgressIndicator(
                            progress = { value.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = accentColor,
                            trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BufferingOverlay(isBuffering: Boolean) {
    AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
    ) {
        Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White,
                    strokeWidth = 3.dp,
                    trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun ErrorOverlay(
        error: String?,
        onDismiss: () -> Unit,
        onRetry: () -> Unit,
        accentColor: Color
) {
    if (error == null) return

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
    ) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
        ) {
            Box(
                    modifier =
                            Modifier.size(64.dp)
                                    .background(
                                            Color.White.copy(alpha = 0.1f),
                                            CircleShape
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = "Playback error",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                    text = "Playback Error",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                    text = error,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                        onClick = onDismiss,
                        colors =
                                ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                )
                ) { Text("Dismiss") }
                Button(
                        onClick = onRetry,
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = accentColor
                                )
                ) {
                    Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun SpeedBoostOverlay(isActive: Boolean) {
    AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
    ) {
        Box(
                modifier = Modifier.fillMaxSize().padding(top = 80.dp),
                contentAlignment = Alignment.TopCenter
        ) {
            Row(
                    modifier =
                            Modifier.background(
                                            Color.Black.copy(alpha = 0.6f),
                                            RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Speed boost active",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                )
                Text(
                        text = "2x",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun VideoPlayerControls(
        viewModel: PlaybackViewModel,
        isVisible: Boolean,
        onBack: () -> Unit,
        onPip: () -> Unit,
        onRotate: () -> Unit,
        onShowBookmarks: () -> Unit,
        onShowAudioTracks: () -> Unit,
        onShowSubtitleTracks: () -> Unit,
        onShowAddToPlaylist: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val position by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isLocked by viewModel.isPlayerLocked.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val resizeMode by viewModel.resizeMode.collectAsStateWithLifecycle()

    var showRemainingTime by remember { mutableStateOf(false) }

    val primaryAccent = LocalAppTheme.current.primaryColor
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // Gradient scrims — kept in both orientations so controls stay legible over any
        // video aspect ratio (letterbox height varies with content).
        AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(120.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                                Brush.verticalGradient(
                                                        listOf(
                                                                Color.Black.copy(alpha = 0.75f),
                                                                Color.Transparent
                                                        )
                                                )
                                        )
                )
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(200.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                                Brush.verticalGradient(
                                                        listOf(
                                                                Color.Transparent,
                                                                Color.Black.copy(alpha = 0.9f)
                                                        )
                                                )
                                        )
                )
            }
        }

        // Lock button floats at the left-center of the video surface in both modes.
        if (isVisible || isLocked) {
            IconButton(
                    onClick = { viewModel.toggleLock() },
                    modifier =
                            Modifier.align(Alignment.CenterStart)
                                    .windowInsetsPadding(WindowInsets.displayCutout)
                                    .padding(start = 24.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .size(48.dp)
            ) {
                Icon(
                        imageVector =
                                if (isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        contentDescription = if (isLocked) "Unlock controls" else "Lock controls",
                        tint = Color.White
                )
            }
        }

        AnimatedVisibility(
                visible = isVisible && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                PlayerTopBar(
                        title = currentTrack?.title ?: "Video",
                        isLandscape = isLandscape,
                        onBack = onBack,
                        onResize = { viewModel.toggleResizeMode() },
                        onShowBookmarks = onShowBookmarks,
                        onShowSubtitleTracks = onShowSubtitleTracks,
                        onShowAudioTracks = onShowAudioTracks,
                        onPip = onPip,
                        onShowAddToPlaylist = onShowAddToPlaylist,
                        modifier = Modifier.align(Alignment.TopCenter)
                )

                PlayerBottomControls(
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        playbackSpeed = playbackSpeed,
                        showRemainingTime = showRemainingTime,
                        onToggleRemaining = { showRemainingTime = !showRemainingTime },
                        primaryAccent = primaryAccent,
                        onSeek = { viewModel.seekTo(it) },
                        onPrevious = { viewModel.playPrevious() },
                        onRewind = { viewModel.rewind() },
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onForward = { viewModel.forward() },
                        onNext = { viewModel.playNext() },
                        onCycleSpeed = { viewModel.cyclePlaybackSpeed() },
                        onRotate = onRotate,
                        modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
        title: String,
        isLandscape: Boolean,
        onBack: () -> Unit,
        onResize: () -> Unit,
        onShowBookmarks: () -> Unit,
        onShowSubtitleTracks: () -> Unit,
        onShowAudioTracks: () -> Unit,
        onPip: () -> Unit,
        onShowAddToPlaylist: () -> Unit,
        modifier: Modifier = Modifier
) {
    Row(
            modifier =
                    modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.displayCutout)
                            .padding(top = 12.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Color.White)
        }
        if (isLandscape) {
            // Plain title text on the left.
            Text(
                    text = title,
                    color = Color.White,
                    style =
                            MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                            ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
            )
        } else {
            // Title in a rounded pill/chip.
            Box(
                    modifier =
                            Modifier.weight(1f, fill = false)
                                    .clip(RoundedCornerShape(50))
                                    .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.3f),
                                            RoundedCornerShape(50)
                                    )
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                        text = title,
                        color = Color.White,
                        style =
                                MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
        }

        // Action icons — full set kept in both orientations to preserve every function.
        IconButton(onClick = onResize) {
            Icon(Icons.Outlined.AspectRatio, "Resize", tint = Color.White)
        }
        IconButton(onClick = onShowBookmarks) {
            Icon(Icons.Default.Bookmarks, "Bookmarks", tint = Color.White)
        }
        IconButton(onClick = onShowSubtitleTracks) {
            Icon(Icons.Default.Subtitles, "Subtitles", tint = Color.White)
        }
        IconButton(onClick = onShowAudioTracks) {
            Icon(Icons.Default.Audiotrack, "Audio tracks", tint = Color.White)
        }
        IconButton(onClick = onPip) {
            Icon(Icons.Default.PictureInPictureAlt, "PiP", tint = Color.White)
        }
        IconButton(onClick = onShowAddToPlaylist) {
            Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to playlist", tint = Color.White)
        }
    }
}

@Composable
private fun PlayerBottomControls(
        isPlaying: Boolean,
        position: Long,
        duration: Long,
        playbackSpeed: Float,
        showRemainingTime: Boolean,
        onToggleRemaining: () -> Unit,
        primaryAccent: Color,
        onSeek: (Long) -> Unit,
        onPrevious: () -> Unit,
        onRewind: () -> Unit,
        onTogglePlay: () -> Unit,
        onForward: () -> Unit,
        onNext: () -> Unit,
        onCycleSpeed: () -> Unit,
        onRotate: () -> Unit,
        modifier: Modifier = Modifier
) {
    Column(
            modifier =
                    modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.displayCutout)
                            .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
    ) {
        // Time labels
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                    FormatUtils.formatDuration(position),
                    color = primaryAccent,
                    style = MaterialTheme.typography.labelMedium
            )
            Text(
                    if (showRemainingTime) {
                        "-${FormatUtils.formatDuration(duration - position)}"
                    } else {
                        FormatUtils.formatDuration(duration)
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { onToggleRemaining() }
            )
        }

        // Two-state slider: tracks position locally during drag to avoid stuttering
        var isSeeking by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableFloatStateOf(0f) }
        Slider(
                value =
                        if (isSeeking) seekPosition
                        else if (duration > 0) position.toFloat() else 0f,
                onValueChange = {
                    isSeeking = true
                    seekPosition = it
                },
                onValueChangeFinished = {
                    onSeek(seekPosition.toLong())
                    isSeeking = false
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors =
                        SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = primaryAccent,
                                inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                modifier = Modifier.height(20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Transport row: playback controls on the left, aux controls on the right.
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                    Icon(
                            Icons.Default.SkipPrevious,
                            "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = onRewind, modifier = Modifier.size(40.dp)) {
                    Icon(
                            Icons.Default.Replay10,
                            "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                    )
                }
                Box(
                        modifier =
                                Modifier.size(52.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, primaryAccent, CircleShape)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription =
                                                    if (isPlaying) "Pause" else "Play"
                                        }
                                        .clickable { onTogglePlay() },
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector =
                                    if (isPlaying) Icons.Default.Pause
                                    else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = primaryAccent,
                            modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(onClick = onForward, modifier = Modifier.size(40.dp)) {
                    Icon(
                            Icons.Default.Forward10,
                            "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                    Icon(
                            Icons.Default.SkipNext,
                            "Next",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                    )
                }
            }

            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Speed (with current-speed text overlay)
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp).clickable { onCycleSpeed() }
                ) {
                    Icon(
                            Icons.Outlined.Speed,
                            "Speed",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                    )
                    if (playbackSpeed != 1.0f) {
                        Text(
                                text = "${playbackSpeed}x",
                                style =
                                        MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp
                                        ),
                                color = primaryAccent,
                                modifier = Modifier.offset(y = 14.dp)
                        )
                    }
                }
                IconButton(onClick = onRotate, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.ScreenRotation, "Rotate", tint = Color.White)
                }
            }
        }
    }
}

private fun hideSystemBars(activity: Activity?) {
    activity?.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private fun showSystemBars(activity: Activity?) {
    activity?.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
                .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
}

private fun calculatePipAspectRatio(videoSize: VideoSize): Rational {
    return if (videoSize.width > 0 && videoSize.height > 0) {
        val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
        val clampedRatio = ratio.coerceIn(0.41841f, 2.39f)
        if (ratio == clampedRatio) {
            Rational(videoSize.width, videoSize.height)
        } else {
            Rational((videoSize.height * clampedRatio).toInt(), videoSize.height)
        }
    } else {
        Rational(16, 9)
    }
}

/**
 * Track Selection Dialog - slides in from the right side like Bookmarks panel. Used for audio and
 * subtitle track selection.
 */
@Composable
fun TrackSelectionDialog(
        title: String,
        tracks: List<TrackInfo>,
        showOffOption: Boolean,
        isOffSelected: Boolean,
        onTrackSelected: (groupIndex: Int, trackIndex: Int) -> Unit,
        onOffSelected: () -> Unit,
        onDismiss: () -> Unit,
        onAddExternal: (() -> Unit)? = null
) {
    val primaryAccent = LocalAppTheme.current.primaryColor

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(onClick = onDismiss)
    ) {
        // Right side panel
        Box(
                modifier =
                        Modifier.align(Alignment.CenterEnd)
                                .widthIn(min = 240.dp, max = 360.dp)
                                .fillMaxWidth(0.4f)
                                .fillMaxHeight()
                                .background(
                                        Color(0xFF1C1C1E),
                                        shape =
                                                RoundedCornerShape(
                                                        topStart = 16.dp,
                                                        bottomStart = 16.dp
                                                )
                                )
                                .clickable(enabled = false) {} // Prevent clicks from closing dialog
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector =
                                    if (title == "Subtitles") Icons.Default.Subtitles
                                    else Icons.Default.Audiotrack,
                            contentDescription = title,
                            tint = primaryAccent,
                            modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                            text = title,
                            color = Color.White,
                            style =
                                    MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                    )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                // Track list
                LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // "Off" option for subtitles
                    if (showOffOption) {
                        item {
                            TrackItem(
                                    name = "Off",
                                    isSelected = isOffSelected,
                                    primaryAccent = primaryAccent,
                                    onClick = onOffSelected
                            )
                        }
                    }

                    // Available tracks
                    if (tracks.isEmpty()) {
                        item {
                            Text(
                                    text =
                                            if (title == "Subtitles") "No subtitles available"
                                            else "No audio tracks available",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(tracks) { track ->
                            TrackItem(
                                    name = track.name,
                                    isSelected = track.isSelected && !isOffSelected,
                                    primaryAccent = primaryAccent,
                                    onClick = {
                                        onTrackSelected(track.groupIndex, track.trackIndex)
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                // Load external subtitle file (subtitles dialog only)
                if (onAddExternal != null) {
                    OutlinedButton(
                            onClick = onAddExternal,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryAccent)
                    ) {
                        Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add subtitle file")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Close button
                TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Close", color = primaryAccent) }
            }
        }
    }
}

@Composable
private fun TrackItem(
        name: String,
        isSelected: Boolean,
        primaryAccent: Color,
        onClick: () -> Unit
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                    if (isSelected) primaryAccent.copy(alpha = 0.2f)
                                    else Color.Transparent
                            )
                            .clickable(onClick = onClick)
                            .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                imageVector =
                        if (isSelected) Icons.Default.RadioButtonChecked
                        else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) primaryAccent else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
                text = name,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
