package com.local.offlinemediaplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel

@Composable
fun MiniPlayer(viewModel: PlaybackViewModel, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val primaryAccent = LocalAppTheme.current.primaryColor
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    // Gradient brush matching theme
    val gradientBrush =
            Brush.horizontalGradient(
                    colors = listOf(primaryAccent, Color(0xFF9656CE), Color(0xFFE44CD8))
            )

    currentTrack?.let { track ->
        // Architecture Fix: MiniPlayer should never show video tracks
        if (track.isVideo) return

        Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        clip = false,
                        spotColor = Color.Black.copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(onClick = onTap),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Top Gradient Progress Bar
                MiniPlayerProgressBar(
                    currentPositionFlow = viewModel.currentPosition,
                    duration = duration,
                    gradientBrush = gradientBrush
                )

                // 2. Main Content
                Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(48.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surfaceVariant
                                    ),
                            elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        AsyncImage(
                                model = track.albumArtUri
                                                ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                                contentDescription = track.title,
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                                text = track.artist ?: "Unknown Artist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Previous Button
                        IconButton(
                                onClick = { viewModel.playPrevious() },
                                enabled = viewModel.hasPrevious(),
                                modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                            )
                        }

                        // Play/Pause Button (Gradient Circle)
                        Box(
                                modifier =
                                        Modifier.size(44.dp)
                                                .shadow(6.dp, CircleShape, spotColor = primaryAccent.copy(alpha = 0.4f))
                                                .clip(CircleShape)
                                                .background(gradientBrush)
                                                .clickable { viewModel.togglePlayPause() },
                                contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                    imageVector =
                                            if (isPlaying) Icons.Default.Pause
                                            else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                                onClick = { viewModel.playNext() },
                                enabled = viewModel.hasNext(),
                                modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayerProgressBar(
    currentPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    duration: Long,
    gradientBrush: Brush
) {
    val currentPosition by currentPositionFlow.collectAsStateWithLifecycle()
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(3.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
    ) {
        Box(
            modifier =
                Modifier.fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(gradientBrush)
        )
    }
}
