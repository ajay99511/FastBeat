package com.local.offlinemediaplayer.ui.screens.video

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.R
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel

/**
 * The video player's transport chrome: the top bar, the centre transport row and the bottom seek
 * bar, wrapped by [VideoPlayerControls] which fades them in and out together.
 */
@Composable
internal fun VideoPlayerControls(
    viewModel: PlaybackViewModel,
    isVisible: Boolean,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onRotate: () -> Unit,
    onShowBookmarks: () -> Unit,
    onShowAudioTracks: () -> Unit,
    onShowSubtitleTracks: () -> Unit,
    onShowAddToPlaylist: () -> Unit,
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
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.75f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.9f),
                                    ),
                                ),
                            ),
                )
            }
        }

        // Lock button floats at the left-center of the video surface in both modes.
        if (isVisible || isLocked) {
            IconButton(
                onClick = { viewModel.toggleLock() },
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 24.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .size(48.dp),
            ) {
                Icon(
                    imageVector =
                        if (isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = if (isLocked) "Unlock controls" else "Lock controls",
                    tint = Color.White,
                )
            }
        }

        AnimatedVisibility(
            visible = isVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.align(Alignment.TopCenter),
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
                    modifier = Modifier.align(Alignment.BottomCenter),
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(top = 12.dp, start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                        fontWeight = FontWeight.Bold,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Title in a rounded pill/chip.
            Box(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(50))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.3f),
                            RoundedCornerShape(50),
                        ).background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
    ) {
        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                FormatUtils.formatDuration(position),
                color = primaryAccent,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                if (showRemainingTime) {
                    "-${FormatUtils.formatDuration(duration - position)}"
                } else {
                    FormatUtils.formatDuration(duration)
                },
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(
                            onClickLabel =
                                stringResource(
                                    if (showRemainingTime) {
                                        R.string.video_time_show_total
                                    } else {
                                        R.string.video_time_show_remaining
                                    },
                                ),
                            onClick = onToggleRemaining,
                        ),
            )
        }

        // Two-state slider: tracks position locally during drag to avoid stuttering
        var isSeeking by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableFloatStateOf(0f) }
        Slider(
            value =
                if (isSeeking) {
                    seekPosition
                } else if (duration > 0) {
                    position.toFloat()
                } else {
                    0f
                },
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
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.5f),
                ),
            modifier = Modifier.height(20.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Transport row: playback controls on the left, aux controls on the right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(onClick = onRewind) {
                    Icon(
                        Icons.Default.Replay10,
                        "Rewind 10 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(2.dp, primaryAccent, CircleShape)
                            .semantics {
                                role = Role.Button
                                contentDescription =
                                    if (isPlaying) "Pause" else "Play"
                            }.clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            if (isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = primaryAccent,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = onForward) {
                    Icon(
                        Icons.Default.Forward10,
                        "Forward 10 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        "Next",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Speed (with current-speed text overlay)
                val speedDescription =
                    stringResource(R.string.video_speed_description, playbackSpeed.toString())
                val cycleSpeedLabel = stringResource(R.string.video_speed_click_label)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .minimumInteractiveComponentSize()
                            .size(44.dp)
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = speedDescription
                            }.clickable(
                                onClickLabel = cycleSpeedLabel,
                                onClick = onCycleSpeed,
                            ),
                ) {
                    Icon(
                        Icons.Outlined.Speed,
                        // Labelled by the merged Box above; a second description here would be
                        // announced twice and would omit the current speed.
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    if (playbackSpeed != 1.0f) {
                        Text(
                            text = "${playbackSpeed}x",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                ),
                            color = primaryAccent,
                            modifier = Modifier.offset(y = 14.dp),
                        )
                    }
                }
                IconButton(onClick = onRotate) {
                    Icon(Icons.Outlined.ScreenRotation, "Rotate", tint = Color.White)
                }
            }
        }
    }
}
