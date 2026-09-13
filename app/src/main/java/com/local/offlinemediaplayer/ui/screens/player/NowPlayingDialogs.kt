package com.local.offlinemediaplayer.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme

/** The playback-speed and sleep-timer dialogs reachable from the Now Playing overflow menu. */
internal fun formatSpeed(speed: Float): String = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

@Composable
internal fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
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
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(speed) }
                                .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatSpeed(speed) + if (speed == 1.0f) " (Normal)" else "",
                            color = if (isSelected) primaryAccent else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
        },
    )
}

@Composable
internal fun SleepTimerDialog(
    isActive: Boolean,
    onSelect: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { minutes ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(minutes) }
                                .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("$minutes minutes", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Available only between 10 PM and 5 AM.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        },
    )
}
