package com.local.offlinemediaplayer.ui.screens.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The transient overlays the video player draws on top of the surface: gesture feedback, the
 * buffering spinner, the error state and the speed-boost badge.
 *
 * All four are stateless and take only what they draw, so each can be previewed on its own.
 */
@Composable
internal fun CenterGestureOverlay(
    mode: GestureMode,
    value: Float,
    text: String,
    accentColor: Color,
) {
    if (mode == GestureMode.NONE) return
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(16.dp),
                    ).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val icon =
                    when (mode) {
                        GestureMode.VOLUME -> Icons.AutoMirrored.Outlined.VolumeUp
                        GestureMode.BRIGHTNESS -> Icons.Outlined.BrightnessHigh
                        GestureMode.SEEK ->
                            if (text.contains("-")) {
                                Icons.Default.FastRewind
                            } else {
                                Icons.Default.FastForward
                            }
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
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (mode == GestureMode.VOLUME || mode == GestureMode.BRIGHTNESS) {
                    LinearProgressIndicator(
                        progress = { value.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BufferingOverlay(isBuffering: Boolean) {
    AnimatedVisibility(
        visible = isBuffering,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color.White,
                strokeWidth = 3.dp,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
internal fun ErrorOverlay(
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    accentColor: Color,
) {
    if (error == null) return

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .background(
                            Color.White.copy(alpha = 0.1f),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Playback error",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Playback Error",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                        ),
                ) { Text("Dismiss") }
                Button(
                    onClick = onRetry,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                        ),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
internal fun SpeedBoostOverlay(isActive: Boolean) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 80.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Row(
                modifier =
                    Modifier
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(20.dp),
                        ).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Speed boost active",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "2x",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
