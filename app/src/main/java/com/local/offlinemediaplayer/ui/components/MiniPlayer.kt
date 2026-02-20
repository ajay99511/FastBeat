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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun MiniPlayer(viewModel: MainViewModel, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val primaryAccent = LocalAppTheme.current.primaryColor
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    // Gradient brush matching theme
    val gradientBrush =
            Brush.horizontalGradient(
                    colors = listOf(primaryAccent, Color(0xFF9656CE), Color(0xFFE44CD8))
            )

    // Current position isn't needed at the top level anymore, we pass the flow directly to the progress bar to isolate recomposition
    // val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()

    currentTrack?.let { track ->
        // Architecture Fix: MiniPlayer should never show video tracks
        if (track.isVideo) return

        // In order to not observe currentPosition here, we'll only compute progress in the extracted component.
        // We'll leave `progress` calculation to the progress bar exclusively.

        Surface(
                modifier = modifier.fillMaxWidth().height(80.dp).clickable(onClick = onTap),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top Gradient Progress Bar
                MiniPlayerProgressBar(
                    currentPositionFlow = viewModel.currentPosition,
                    duration = duration,
                    gradientBrush = gradientBrush
                )

                // 2. Main Content
                Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(48.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surfaceVariant
                                    ),
                            elevation = CardDefaults.cardElevation(0.dp)
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

                    Spacer(modifier = Modifier.width(16.dp))

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
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Previous Button
                        IconButton(
                                onClick = { viewModel.playPrevious() },
                                enabled = viewModel.hasPrevious()
                        ) {
                            Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(32.dp)
                            )
                        }

                        // Play/Pause Button (Gradient Circle)
                        Box(
                                modifier =
                                        Modifier.size(48.dp)
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
                                    modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                                onClick = { viewModel.playNext() },
                                enabled = viewModel.hasNext()
                        ) {
                            Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(32.dp)
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
                .height(2.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
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
