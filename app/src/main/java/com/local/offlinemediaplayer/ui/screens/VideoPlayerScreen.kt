package com.local.offlinemediaplayer.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.local.offlinemediaplayer.viewmodel.MainViewModel
import com.local.offlinemediaplayer.viewmodel.ResizeMode
import kotlinx.coroutines.delay
import java.util.Formatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Enum for tracking active gesture
private enum class GestureMode { NONE, VOLUME, BRIGHTNESS, SEEK }

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val player by viewModel.player.collectAsStateWithLifecycle()
    val resizeMode by viewModel.resizeMode.collectAsStateWithLifecycle()
    val isLocked by viewModel.isPlayerLocked.collectAsStateWithLifecycle()
    val isInPip by viewModel.isInPipMode.collectAsStateWithLifecycle()

    // Player State
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()

    // Gesture States
    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }
    var gestureValue by remember { mutableFloatStateOf(0f) } // 0-1 for Vol/Bright, Seconds for Seek
    var gestureText by remember { mutableStateOf("") }

    // Internal Gesture Calculation State
    var initialVolume by remember { mutableIntStateOf(0) }
    var initialBrightness by remember { mutableFloatStateOf(0f) }
    var initialSeekPosition by remember { mutableLongStateOf(0L) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    // Controls Visibility State
    var isControlsVisible by remember { mutableStateOf(true) }

    // Audio Manager
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Screen Dimensions for gesture calculations
    val configuration = LocalConfiguration.current
    val screenWidth = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    // Force Landscape on Entry, restore on Exit
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemBars(activity)

        onDispose {
            activity?.requestedOrientation = originalOrientation
            showSystemBars(activity)
            // Reset brightness override on exit
            val layoutParams = activity?.window?.attributes
            layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity?.window?.attributes = layoutParams
        }
    }

    // Handle Back Press
    BackHandler {
        if (isLocked) {
            // Toast or hint could go here
        } else {
            onBack()
        }
    }

    // PiP Listener (Update ViewModel when PiP changes)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (activity?.isInPictureInPictureMode == true) {
                    viewModel.setPipMode(true)
                }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (activity?.isInPictureInPictureMode == false) {
                    viewModel.setPipMode(false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-hide controls logic moved here to coordinate with gestures
    LaunchedEffect(isControlsVisible, player?.isPlaying) {
        if (isControlsVisible && player?.isPlaying == true) {
            delay(3000)
            isControlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // GESTURE INPUT HANDLING
            .pointerInput(Unit) {
                if (isLocked) return@pointerInput // Disable gestures if locked

                detectTapGestures(
                    onTap = {
                        isControlsVisible = !isControlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (offset.x > screenWidth / 2) {
                            viewModel.forward()
                        } else {
                            viewModel.rewind()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                if (isLocked) return@pointerInput

                        detectDragGestures(
                            onDragStart = { offset ->
                                accumulatedDragX = 0f
                                accumulatedDragY = 0f
                                gestureMode = GestureMode.NONE

                                // Initialize values
                                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                                val layoutParams = activity?.window?.attributes
                                // If override is none (-1), try to get system brightness or default to 0.5
                                var bright = layoutParams?.screenBrightness ?: -1f
                                if (bright < 0) bright = 0.5f // Fallback estimate
                                initialBrightness = bright

                                initialSeekPosition = currentPosition
                            },
                            onDragEnd = {
                                if (gestureMode == GestureMode.SEEK) {
                                    // Commit seek
                                    viewModel.seekTo(initialSeekPosition)
                                }
                                gestureMode = GestureMode.NONE
                                isControlsVisible = true // Show controls after interaction
                            },
                            onDragCancel = {
                                gestureMode = GestureMode.NONE
                            },
                            onDrag = { change, dragAmount ->
                                change.consume() // Consume the event
                                accumulatedDragX += dragAmount.x
                                accumulatedDragY += dragAmount.y

                                // Determine Gesture Type if strictly None
                                if (gestureMode == GestureMode.NONE) {
                                    if (abs(accumulatedDragX) > abs(accumulatedDragY)) {
                                        if (abs(accumulatedDragX) > 20) { // Threshold
                                            gestureMode = GestureMode.SEEK
                                        }
                                    } else {
                                        if (abs(accumulatedDragY) > 20) { // Threshold
                                            // Left side = Volume, Right side = Brightness
                                            gestureMode = if (change.position.x < screenWidth / 2) {
                                                GestureMode.VOLUME
                                            } else {
                                                GestureMode.BRIGHTNESS
                                            }
                                        }
                                    }
                                }

                                // Apply Logic based on Type
                                when (gestureMode) {
                                    GestureMode.VOLUME -> {
                                        // Drag Up (negative Y) increases volume
                                        val deltaPercent = -accumulatedDragY / screenHeight
                                        val newVol = (initialVolume + (deltaPercent * maxVolume * 3)).toInt() // *3 for sensitivity
                                        val clampedVol = newVol.coerceIn(0, maxVolume)

                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clampedVol, 0)
                                        gestureValue = clampedVol.toFloat() / maxVolume.toFloat()
                                        gestureText = "${(gestureValue * 100).toInt()}%"
                                    }
                                    GestureMode.BRIGHTNESS -> {
                                        // Drag Up increases brightness
                                        val deltaPercent = -accumulatedDragY / screenHeight
                                        val newBright = (initialBrightness + (deltaPercent * 2)).coerceIn(0.01f, 1f) // *2 sensitivity

                                        val layoutParams = activity?.window?.attributes
                                        layoutParams?.screenBrightness = newBright
                                        activity?.window?.attributes = layoutParams

                                        gestureValue = newBright
                                        gestureText = "${(newBright * 100).toInt()}%"
                                    }
                                    GestureMode.SEEK -> {
                                        val deltaPercent = accumulatedDragX / screenWidth
                                        val seekChange = (deltaPercent * 90000).toLong() // +/- 90 seconds swipe
                                        val newPos = (initialSeekPosition + seekChange).coerceIn(0, duration)

                                        initialSeekPosition = newPos // Update local tracking
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
                        this.useController = false // We build our own
                        this.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Keep screen on
                        this.keepScreenOn = true
                    }
                },
                update = { playerView ->
                    playerView.player = player
                    playerView.resizeMode = when (resizeMode) {
                        ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // VISUAL FEEDBACK FOR GESTURES
        CenterGestureOverlay(
            mode = gestureMode,
            value = gestureValue,
            text = gestureText
        )

        // Custom Overlay Controls (Hidden in PiP)
        if (!isInPip) {
            VideoPlayerControls(
                viewModel = viewModel,
                isVisible = isControlsVisible, // Pass state down
                onToggleVisibility = { isControlsVisible = !isControlsVisible }, // Pass toggle up (though tap logic is mostly handled by parent)
                onBack = onBack,
                onPip = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val params = PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .build()
                        activity?.enterPictureInPictureMode(params)
                    }
                },
                onRotate = {
                    if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    } else {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
            )
        }
    }
}

@Composable
private fun CenterGestureOverlay(
    mode: GestureMode,
    value: Float, // 0-1 for vol/bright
    text: String
) {
    if (mode == GestureMode.NONE) return

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // ICON
                val icon = when (mode) {
                    GestureMode.VOLUME -> Icons.Outlined.VolumeUp
                    GestureMode.BRIGHTNESS -> Icons.Outlined.BrightnessHigh
                    GestureMode.SEEK -> if (text.contains("-")) Icons.Default.FastRewind else Icons.Default.FastForward
                    else -> Icons.Default.Info
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // TEXT
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // PROGRESS BAR (Only for Vol/Bright)
                if (mode == GestureMode.VOLUME || mode == GestureMode.BRIGHTNESS) {
                    LinearProgressIndicator(
                        progress = { value.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Color(0xFF00E5FF),
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayerControls(
    viewModel: MainViewModel,
    isVisible: Boolean, // Received from parent
    onToggleVisibility: () -> Unit,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onRotate: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val position by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isLocked by viewModel.isPlayerLocked.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val resizeMode by viewModel.resizeMode.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
        // We do NOT add clickable here anymore to avoid conflicting with the parent pointerInput
        // The parent handles tapping for visibility
    ) {
        // Gradient Overlays (Top and Bottom) for visibility
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                )
                // Bottom Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                )
            }
        }

        // LOCK BUTTON (Always visible if locked, on left side)
        if (isVisible || isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            ) {
                IconButton(
                    onClick = { viewModel.toggleLock() },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        contentDescription = "Lock",
                        tint = Color.White
                    )
                }
            }
        }

        // --- FULL CONTROLS (Only when visible and NOT locked) ---
        AnimatedVisibility(
            visible = isVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // 1. Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Back", tint = Color.White)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack?.title ?: "Video",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Text(
                            text = "${currentTrack?.resolution ?: "Unknown"} • ${formatSize(currentTrack?.size ?: 0)}",
                            color = Color(0xFF00E5FF), // Cyan Accent
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // PiP Button
                    IconButton(onClick = onPip) {
                        Icon(Icons.Default.PictureInPictureAlt, "PiP", tint = Color.White)
                    }

                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }

                    IconButton(onClick = { /* More */ }) {
                        Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                    }
                }

                // 2. Center Controls (Prev, Rewind, Play, Forward, Next)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    IconButton(onClick = { viewModel.playPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(onClick = { viewModel.rewind() }) {
                        Icon(Icons.Default.Replay10, "Rewind", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    // Big Play Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(2.dp, Color(0xFF00E5FF), CircleShape) // Cyan border
                            .clickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.forward() }) {
                        Icon(Icons.Default.Forward10, "Forward", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // 3. Bottom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                ) {
                    // Time and Duration Labels (Above Seekbar)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(position), color = Color(0xFF00E5FF), style = MaterialTheme.typography.labelMedium) // Current Cyan
                        Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    // Seek Bar
                    Slider(
                        value = if (duration > 0) position.toFloat() else 0f,
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.height(20.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom Action Row (Resize, Speed, Rotate, Playlist)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Resize Button
                            ControlPill(
                                icon = Icons.Outlined.AspectRatio,
                                text = when(resizeMode) {
                                    ResizeMode.FIT -> "Fit"
                                    ResizeMode.FILL -> "Fill"
                                    ResizeMode.ZOOM -> "Zoom"
                                },
                                onClick = { viewModel.toggleResizeMode() }
                            )

                            // Speed Button
                            ControlPill(
                                icon = Icons.Outlined.Speed,
                                text = "Speed: ${playbackSpeed}x",
                                onClick = { viewModel.cyclePlaybackSpeed() }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Removed duplicate lock button here
                            IconButton(onClick = onRotate) {
                                Icon(Icons.Outlined.ScreenRotation, "Rotate", tint = Color.Gray)
                            }
                            IconButton(onClick = { /* Queue list */ }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, "Queue", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.15f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// Helpers
private fun hideSystemBars(activity: Activity?) {
    activity?.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private fun showSystemBars(activity: Activity?) {
    activity?.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    val mFormatter = Formatter(StringBuilder(), Locale.getDefault())
    return if (hours > 0) {
        mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
    } else {
        mFormatter.format("%02d:%02d", minutes, seconds).toString()
    }
}

private fun formatSeekTime(currentMs: Long, totalMs: Long): String {
    return "${formatTime(currentMs)} / ${formatTime(totalMs)}"
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return java.text.DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
