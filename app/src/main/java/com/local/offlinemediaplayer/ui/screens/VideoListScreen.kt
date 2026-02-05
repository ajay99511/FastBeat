
package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.MainViewModel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: MainViewModel,
    onVideoClick: (MediaFile) -> Unit,
    videoListOverride: List<MediaFile>? = null,
    title: String? = null,
    onBack: (() -> Unit)? = null
) {
    val videosState by viewModel.videoList.collectAsStateWithLifecycle()
    val videos = videoListOverride ?: videosState
    val primaryAccent = LocalAppTheme.current.primaryColor

    // Default to Grid View (Large Cards)
    var isGridView by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredVideos = if (searchQuery.isEmpty()) {
        videos
    } else {
        videos.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val backgroundColor = Color(0xFF0B0B0F)

    Column(
        modifier = Modifier.fillMaxSize().background(backgroundColor)
    ) {
        // Custom Header logic for "Folder View"
        if (title != null && onBack != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Top Row: Back | Search | Toggle Icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button (Circle BG)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color(0xFF1E1E24), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = primaryAccent)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Search Bar (Expanded)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF16161D))
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search in ${title}...",
                                    color = Color(0xFF475569),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, null, tint = Color(0xFF475569))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, null, tint = Color.Gray)
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = primaryAccent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // View Toggle Button
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Change View",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 2. Breadcrumbs: "Folders > Movie"
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Folders",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryAccent
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Divider(color = Color(0xFF1E1E24), thickness = 1.dp)
            }
        }

        if (filteredVideos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color(0xFF1E1E24), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF475569)
                        )
                    }
                    Text(
                        "No videos found here",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF475569)
                    )
                    Button(
                        onClick = { viewModel.scanMedia() },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                    ) {
                        Text("Rescan Library")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items = filteredVideos, key = { it.id }) { video ->
                    if (isGridView) {
                        VideoCardItem(video, onVideoClick, primaryAccent)
                    } else {
                        VideoListItem(video, onVideoClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoListItem(video: MediaFile, onVideoClick: (MediaFile) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onVideoClick(video) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(45.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray)
        ) {
            AsyncImage(
                model = video.uri,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                maxLines = 1,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatDuration(video.duration),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun VideoCardItem(video: MediaFile, onVideoClick: (MediaFile) -> Unit, accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onVideoClick(video) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E24))
        ) {
            AsyncImage(
                model = video.uri,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = formatDuration(video.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (video.resolution.isNotEmpty()) {
                        Text(
                            text = video.resolution,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (video.size > 0) {
                        Text(
                            text = formatSize(video.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            IconButton(onClick = { /* TODO: Video Options */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.Gray
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = millis / (1000 * 60 * 60)

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
