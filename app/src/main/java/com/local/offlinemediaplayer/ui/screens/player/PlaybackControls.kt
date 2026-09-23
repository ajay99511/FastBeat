package com.local.offlinemediaplayer.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.ui.common.FormatUtils

/** The seek bar and its elapsed/remaining labels, shared by the Now Playing layouts. */
@Composable
internal fun PlaybackControlsWithProgress(
    currentPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    duration: Long,
    progressBarGradient: Brush,
    onSeek: (Long) -> Unit,
) {
    val currentPosition by currentPositionFlow.collectAsStateWithLifecycle()

    val thumbColor = MaterialTheme.colorScheme.primary

    Column {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                        Modifier
                            .weight(progress.coerceAtLeast(0.001f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(progressBarGradient),
                )
                Spacer(modifier = Modifier.weight((1f - progress).coerceAtLeast(0.001f)))
            }

            Slider(
                value = if (duration > 0) currentPosition.toFloat() else 0f,
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors =
                    SliderDefaults.colors(
                        thumbColor = thumbColor,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = FormatUtils.formatDuration(currentPosition),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = FormatUtils.formatDuration(duration),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
