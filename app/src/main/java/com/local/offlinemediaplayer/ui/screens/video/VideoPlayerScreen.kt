package com.local.offlinemediaplayer.ui.screens.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

private const val TAG = "VideoPlayerScreen"

internal enum class GestureMode {
    NONE,
    VOLUME,
    BRIGHTNESS,
    SEEK,
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: PlaybackViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    onBack: () -> Unit,
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
                        PictureInPictureParams
                            .Builder()
                            .setAspectRatio(aspectRatio)
                            .setAutoEnterEnabled(isPlaying)
                            .build()
                    activity?.setPictureInPictureParams(params)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update picture-in-picture params for auto-enter", e)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
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
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(isLocked, showBookmarksDialog) {
                    if (isLocked || showBookmarksDialog) return@pointerInput
                    detectTapGestures(
                        onTap = { isControlsVisible = !isControlsVisible },
                        onDoubleTap = { offset ->
                            if (offset.x > screenWidth / 2) {
                                viewModel.forward()
                            } else {
                                viewModel.rewind()
                            }
                        },
                        onLongPress = {
                            if (isPlaying) {
                                isSpeedBoosting = true
                                viewModel.startSpeedBoost()
                            }
                        },
                    )
                }.pointerInput(isLocked, showBookmarksDialog) {
                    if (isLocked || showBookmarksDialog) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            accumulatedDragX = 0f
                            accumulatedDragY = 0f
                            gestureMode = GestureMode.NONE
                            initialVolume =
                                audioManager.getStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                )
                            val layoutParams = activity?.window?.attributes
                            var bright = layoutParams?.screenBrightness ?: -1f
                            if (bright < 0) {
                                bright =
                                    viewModel.videoBrightness.value.takeIf {
                                        it >= 0f
                                    }
                                        ?: 0.5f
                            }
                            initialBrightness = bright
                            initialSeekPosition = currentPosition
                        },
                        onDragEnd = {
                            if (gestureMode == GestureMode.SEEK) {
                                viewModel.seekTo(initialSeekPosition)
                            }
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
                                    if (abs(accumulatedDragX) > gestureThresholdPx) {
                                        gestureMode = GestureMode.SEEK
                                    }
                                } else {
                                    if (abs(accumulatedDragY) > gestureThresholdPx) {
                                        // Left = Brightness, Right = Volume (matches VLC/MX Player convention)
                                        gestureMode =
                                            if (change.position.x <
                                                screenWidth / 2
                                            ) {
                                                GestureMode.BRIGHTNESS
                                            } else {
                                                GestureMode.VOLUME
                                            }
                                    }
                                }
                            }

                            when (gestureMode) {
                                GestureMode.VOLUME -> {
                                    val deltaPercent =
                                        -accumulatedDragY / screenHeight
                                    val newVol =
                                        (
                                            initialVolume +
                                                (
                                                    deltaPercent *
                                                        maxVolume *
                                                        3
                                                )
                                        ).toInt()
                                            .coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        newVol,
                                        0,
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
                                    val maxSeekRange =
                                        min(
                                            (duration * 0.15).toLong(),
                                            120_000L,
                                        ).coerceAtLeast(30_000L)
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
                        },
                    )
                }.pointerInput(isLocked, showBookmarksDialog, isSpeedBoosting) {
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
                },
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
                                ViewGroup.LayoutParams.MATCH_PARENT,
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
                modifier = Modifier.fillMaxSize(),
            )
        }

        CenterGestureOverlay(
            mode = gestureMode,
            value = gestureValue,
            text = gestureText,
            primaryAccent,
        )

        // Buffering Indicator
        BufferingOverlay(isBuffering = isBuffering && playerError == null)

        // Error Overlay
        ErrorOverlay(
            error = playerError?.userMessage(context),
            onDismiss = { viewModel.dismissPlayerError() },
            onRetry = {
                viewModel.dismissPlayerError()
                player?.prepare()
                player?.play()
            },
            accentColor = primaryAccent,
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
                                PictureInPictureParams
                                    .Builder()
                                    .setAspectRatio(aspectRatio)
                                    .build()
                            activity?.enterPictureInPictureMode(params)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to enter picture-in-picture from the player controls", e)
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
                onShowAddToPlaylist = { showAddToPlaylistDialog = true },
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
                },
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
                onDismiss = { showAudioTrackDialog = false },
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
                },
            )
        }

        // Add to Playlist Dialog
        if (showAddToPlaylistDialog && currentTrack != null) {
            AddToPlaylistDialog(
                song = currentTrack!!,
                playlistViewModel = playlistViewModel,
                onDismiss = { showAddToPlaylistDialog = false },
                onCreateNew = { showCreatePlaylistDialog = true },
            )
        }

        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { name ->
                    playlistViewModel.createPlaylist(name, currentTrack?.isVideo ?: true)
                },
            )
        }
    }
}
