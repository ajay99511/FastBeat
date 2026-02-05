
package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun NowPlayingScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()

    // Colors from Theme
    val primaryAccent = LocalAppTheme.current.primaryColor
    val secondaryAccent = Color(0xFF8B51E6)

    val playButtonGradient = Brush.verticalGradient(
        colors = listOf(primaryAccent, secondaryAccent)
    )
    val progressBarGradient = Brush.horizontalGradient(
        colors = listOf(primaryAccent, Color(0xFFE44CD8), Color(0xFF42E8E0))
    )

    if (currentTrack == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Nothing Playing", color = Color.Gray)
        }
        return
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color(0xFF2C2C2C), CircleShape).size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NOW PLAYING",
                        color = primaryAccent,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "From \"${currentTrack?.artist ?: "Unknown"}\"",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                IconButton(onClick = { /* More options */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.Gray
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Album Art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                AsyncImage(
                    model = currentTrack?.albumArtUri ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                // Removed fake "AI" badge for cleaner look
            }

            Spacer(modifier = Modifier.weight(1f))

            // Track Info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = currentTrack?.title ?: "",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentTrack?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gradient Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0xFF2C2C2C), RoundedCornerShape(2.dp))
                )

                val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(progress.coerceAtLeast(0.001f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(progressBarGradient)
                    )
                    Spacer(modifier = Modifier.weight((1f - progress).coerceAtLeast(0.001f)))
                }

                Slider(
                    value = if (duration > 0) currentPosition.toFloat() else 0f,
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPosition),
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatDuration(duration),
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) Color.White else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = { viewModel.playPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause (Gradient Circle)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(16.dp, CircleShape, spotColor = primaryAccent)
                        .clip(CircleShape)
                        .background(playButtonGradient)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(onClick = { viewModel.playNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat
                    val tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.Gray else Color.White
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
