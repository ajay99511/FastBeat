package com.local.offlinemediaplayer.ui.screens

// import androidx.compose.ui.platform.LocalLifecycleOwner
// import androidx.lifecycle.compose.LocalLifecycleOwner
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.ResizeMode
import com.local.offlinemediaplayer.viewmodel.TrackInfo
import kotlin.math.abs
import kotlinx.coroutines.delay

private enum class GestureMode {
    NONE,
    VOLUME,
    BRIGHTNESS,
    SEEK
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(viewModel: PlaybackViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val player by viewModel.player.collectAsStateWithLifecycle()
    val resizeMode by viewModel.resizeMode.collectAsStateWithLifecycle()
    val isLocked by viewModel.isPlayerLocked.collectAsStateWithLifecycle()
    val isInPip by viewModel.isInPipMode.collectAsStateWithLifecycle()
    val videoSize by viewModel.videoSize.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val primaryAccent = LocalAppTheme.current.primaryColor

    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()

    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var gestureText by remember { mutableStateOf("") }

    var initialVolume by remember { mutableIntStateOf(0) }
    var initialBrightness by remember { mutableFloatStateOf(0f) }
    var initialSeekPosition by remember { mutableLongStateOf(0L) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    var isControlsVisible by remember { mutableStateOf(true) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleTrackDialog by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val configuration = LocalConfiguration.current
    val screenWidth = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

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
        // Use observed videoSize instead of player?.videoSize
        val aspectRatio =
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                    val clampedRatio = ratio.coerceIn(0.41841f, 2.39f)
                    if (ratio == clampedRatio) {
                        Rational(videoSize.width, videoSize.height)
                    } else {
                        // Adjust width to match clamped ratio
                        Rational((videoSize.height * clampedRatio).toInt(), videoSize.height)
                    }
                } else {
                    Rational(16, 9)
                }

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
                            .pointerInput(Unit) {
                                if (isLocked || showBookmarksDialog) return@pointerInput
                                detectTapGestures(
                                        onTap = { isControlsVisible = !isControlsVisible },
                                        onDoubleTap = { offset ->
                                            if (offset.x > screenWidth / 2) viewModel.forward()
                                            else viewModel.rewind()
                                        }
                                )
                            }
                            .pointerInput(Unit) {
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
                                            if (bright < 0) bright = 0.5f
                                            initialBrightness = bright
                                            initialSeekPosition = currentPosition
                                        },
                                        onDragEnd = {
                                            if (gestureMode == GestureMode.SEEK)
                                                    viewModel.seekTo(initialSeekPosition)
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
                                                    if (abs(accumulatedDragX) > 20)
                                                            gestureMode = GestureMode.SEEK
                                                } else {
                                                    if (abs(accumulatedDragY) > 20) {
                                                        gestureMode =
                                                                if (change.position.x <
                                                                                screenWidth / 2
                                                                )
                                                                        GestureMode.VOLUME
                                                                else GestureMode.BRIGHTNESS
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
                                                    val seekChange = (deltaPercent * 90000).toLong()
                                                    val newPos =
                                                            (initialSeekPosition + seekChange)
                                                                    .coerceIn(0, duration)
                                                    initialSeekPosition = newPos
                                                    gestureValue = newPos.toFloat()
                                                    gestureText = formatSeekTime(newPos, duration)
                                                }
                                                else -> {}
                                            }
                                        }
                                )
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

        if (!isInPip) {
            VideoPlayerControls(
                    viewModel = viewModel,
                    isVisible = isControlsVisible,
                    onBack = onBack,
                    onPip = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            // Use observed videoSize state
                            val aspectRatio =
                                    if (videoSize.width > 0 && videoSize.height > 0) {
                                        val ratio =
                                                videoSize.width.toFloat() /
                                                        videoSize.height.toFloat()
                                        val clampedRatio = ratio.coerceIn(0.41841f, 2.39f)
                                        if (ratio == clampedRatio) {
                                            Rational(videoSize.width, videoSize.height)
                                        } else {
                                            Rational(
                                                    (videoSize.height * clampedRatio).toInt(),
                                                    videoSize.height
                                            )
                                        }
                                    } else {
                                        Rational(16, 9)
                                    }
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
                        player?.pause()
                        showBookmarksDialog = true
                    },
                    onShowAudioTracks = { showAudioTrackDialog = true },
                    onShowSubtitleTracks = { showSubtitleTrackDialog = true }
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
                    onDismiss = { showSubtitleTrackDialog = false }
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
                                .width(350.dp)
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
                                    null,
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
                Icon(
                        imageVector = icon,
                        contentDescription = null,
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
fun VideoPlayerControls(
        viewModel: PlaybackViewModel,
        isVisible: Boolean,
        onBack: () -> Unit,
        onPip: () -> Unit,
        onRotate: () -> Unit,
        onShowBookmarks: () -> Unit,
        onShowAudioTracks: () -> Unit,
        onShowSubtitleTracks: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val position by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isLocked by viewModel.isPlayerLocked.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val resizeMode by viewModel.resizeMode.collectAsStateWithLifecycle()

    val primaryAccent = LocalAppTheme.current.primaryColor

    Box(modifier = Modifier.fillMaxSize()) {
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
                                        .height(100.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                                Brush.verticalGradient(
                                                        listOf(
                                                                Color.Black.copy(alpha = 0.8f),
                                                                Color.Transparent
                                                        )
                                                )
                                        )
                )
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(140.dp)
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

        if (isVisible || isLocked) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 100.dp)) {
                IconButton(
                        onClick = { viewModel.toggleLock() },
                        modifier =
                                Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                                        .size(48.dp)
                ) {
                    Icon(
                            imageVector =
                                    if (isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                            contentDescription = "Lock",
                            tint = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
                visible = isVisible && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = currentTrack?.title ?: "Video",
                                color = Color.White,
                                style =
                                        MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                        ),
                                maxLines = 1
                        )
                        Text(
                                text = FormatUtils.formatSize(currentTrack?.size ?: 0),
                                color = primaryAccent,
                                style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = onPip) {
                        Icon(Icons.Default.PictureInPictureAlt, "PiP", tint = Color.White)
                    }
                    IconButton(onClick = onShowBookmarks) {
                        Icon(Icons.Default.Bookmarks, "Bookmarks", tint = Color.White)
                    }
                    IconButton(onClick = onShowSubtitleTracks) {
                        Icon(Icons.Default.Subtitles, "Subtitles", tint = Color.White)
                    }
                    IconButton(onClick = onShowAudioTracks) {
                        Icon(Icons.Default.Audiotrack, "Audio", tint = Color.White)
                    }
                }

                // Center Controls
                Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Previous Video
                    IconButton(
                            onClick = { viewModel.playPrevious() },
                            // Show visible but maybe dim if no prev
                            ) {
                        Icon(
                                Icons.Default.SkipPrevious,
                                "Prev",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.rewind() }) {
                        Icon(
                                Icons.Default.Replay10,
                                "Rewind",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                            modifier =
                                    Modifier.size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color.Transparent)
                                            .border(2.dp, primaryAccent, CircleShape)
                                            .clickable { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                imageVector =
                                        if (isPlaying) Icons.Default.Pause
                                        else Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = primaryAccent,
                                modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.forward() }) {
                        Icon(
                                Icons.Default.Forward10,
                                "Forward",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next Video
                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(
                                Icons.Default.SkipNext,
                                "Next",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom Controls
                Column(
                        modifier =
                                Modifier.align(Alignment.BottomCenter)
                                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                ) {
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
                                FormatUtils.formatDuration(duration),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Slider(
                            value = if (duration > 0) position.toFloat() else 0f,
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors =
                                    SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = primaryAccent,
                                            inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                                    ),
                            modifier = Modifier.height(20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Simplified Options Row
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Aspect Ratio Icon Button
                        IconButton(onClick = { viewModel.toggleResizeMode() }) {
                            Icon(Icons.Outlined.AspectRatio, "Resize", tint = Color.White)
                        }

                        // Speed Icon Button (with text overlay for current speed)
                        Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.clickable { viewModel.cyclePlaybackSpeed() }
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
                                                        fontSize = 8.sp
                                                ),
                                        color = primaryAccent,
                                        modifier = Modifier.offset(y = 14.dp)
                                )
                            }
                        }

                        // Rotate
                        IconButton(onClick = onRotate) {
                            Icon(Icons.Outlined.ScreenRotation, "Rotate", tint = Color.White)
                        }
                    }
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

private fun formatSeekTime(currentMs: Long, totalMs: Long): String {
    return "${FormatUtils.formatDuration(currentMs)} / ${FormatUtils.formatDuration(totalMs)}"
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
        onDismiss: () -> Unit
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
                                .width(280.dp)
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
                contentDescription = null,
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
