
package com.local.offlinemediaplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun MeScreen(
    viewModel: MainViewModel,
    onPlayTrack: (MediaFile) -> Unit
) {
    val theme = LocalAppTheme.current
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val audioList by viewModel.audioList.collectAsStateWithLifecycle()

    // Select real random songs if available, otherwise use a placeholder list
    val randomSongs = remember(audioList) {
        if (audioList.isNotEmpty()) {
            audioList.asSequence().shuffled().take(50).toList()
        } else {
            emptyList()
        }
    }

    var isExpanded by remember { mutableStateOf(false) }
    val displayedSongs = if (isExpanded) randomSongs else randomSongs.take(5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp) // Space for MiniPlayer
    ) {

        // 1. Theme Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeButton(
                icon = Icons.Filled.Bolt,
                color = Color(0xFFFF5500),
                isActive = currentTheme.id == "orange",
                onClick = { viewModel.updateTheme("orange") }
            )
            Spacer(modifier = Modifier.width(24.dp))
            ThemeButton(
                icon = Icons.Filled.Star,
                color = Color(0xFF00E5FF),
                isActive = currentTheme.id == "blue",
                onClick = { viewModel.updateTheme("blue") }
            )
            Spacer(modifier = Modifier.width(24.dp))
            ThemeButton(
                icon = Icons.Filled.OpenWith,
                color = Color(0xFF22C55E),
                isActive = currentTheme.id == "green",
                onClick = { viewModel.updateTheme("green") }
            )
        }

        // 2. Brand Header (Typography Update)
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "FAST",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 40.sp,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    text = "BEAT",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 40.sp,
                        color = theme.primaryColor,
                        letterSpacing = 1.sp,
                        shadow = Shadow(
                            color = theme.primaryColor.copy(alpha = 0.5f),
                            blurRadius = 30f,
                            offset = Offset(0f, 0f)
                        )
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = theme.subtitle,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color(0xFF475569),
                    letterSpacing = 4.sp
                )
            )
        }

        // 3. Search Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(50))
                .background(Color(0xFF16161D))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = Color(0xFF475569))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Search music, 4K cinema, or folder paths...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF475569)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Section Header
        if (randomSongs.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    null,
                    tint = theme.primaryColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = theme.curatedTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            // 5. Active Stream Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Actual album art if available
                    val featuredSong = randomSongs.firstOrNull()
                    if (featuredSong?.albumArtUri != null) {
                        AsyncImage(
                            model = featuredSong.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E24)))
                    }

                    // Softened Gradient Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )

                    // Content
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(theme.primaryColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SUGGESTED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = theme.primaryColor
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = featuredSong?.title ?: "Mix",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Play Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { viewModel.playAll(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PLAY ALL", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.playAll(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Shuffle, null, tint = theme.primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SHUFFLE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 7. Divider with Gradient
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .width(120.dp)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(theme.primaryColor, Color.Transparent)
                        ),
                        RoundedCornerShape(2.dp)
                    )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 8. Collapsible List (Real Data)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                displayedSongs.forEachIndexed { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPlayTrack(song) }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF2B2930), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.albumArtUri != null) {
                                AsyncImage(
                                    model = song.albumArtUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = song.artist ?: "Unknown",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (!isExpanded && randomSongs.size > 5) {
                    TextButton(
                        onClick = { isExpanded = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            "VIEW ALL TRACKS",
                            color = theme.primaryColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        } else {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No music found on device.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // 9. Analytics Dashboard
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, null, tint = theme.primaryColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Listening Activity",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Playtime Card
                AnalyticsCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Schedule,
                    iconColor = theme.primaryColor,
                    label = "Total Time",
                    value = "124h",
                    subtext = "Video & Music"
                )

                // Genre Card
                AnalyticsCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.MusicNote,
                    iconColor = Color(0xFF9656CE),
                    label = "Top Genre",
                    value = "Synth",
                    subtext = "450 Tracks"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vibe Score Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                shape = RoundedCornerShape(16.dp),
                border = null
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A+",
                            color = theme.primaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vibe Score", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Based on offline activity", color = Color.Gray, fontSize = 10.sp)
                    }

                    // Progress bar mini
                    Column(modifier = Modifier.width(100.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Intensity", color = Color.Gray, fontSize = 10.sp)
                            Text("High", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { 0.85f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                            color = theme.primaryColor,
                            trackColor = Color(0xFF333333)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeButton(
    icon: ImageVector,
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .shadow(
                elevation = if (isActive) 16.dp else 0.dp,
                spotColor = if (isActive) color else Color.Transparent,
                shape = CircleShape
            )
            .background(if (isActive) color else Color(0xFF1E1E24), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) Color.White else Color(0xFF64748B)
        )
    }
}

@Composable
fun AnalyticsCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    subtext: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
        shape = RoundedCornerShape(16.dp),
        border = null // BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = label.uppercase(),
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtext,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}
