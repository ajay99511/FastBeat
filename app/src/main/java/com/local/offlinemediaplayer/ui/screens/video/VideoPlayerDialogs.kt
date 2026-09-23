package com.local.offlinemediaplayer.ui.screens.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.TrackInfo
import kotlin.math.min

/**
 * The two side panels the video player can slide in: bookmarks, and audio/subtitle track selection.
 */
@Composable
internal fun BookmarksDialog(
    viewModel: PlaybackViewModel,
    currentPosition: Long,
    onDismiss: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val bookmarks by viewModel.currentBookmarks.collectAsStateWithLifecycle()
    var newLabel by remember { mutableStateOf("") }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .widthIn(min = 280.dp, max = 400.dp)
                    .fillMaxWidth(0.45f)
                    .background(Color(0xFF1E1E24))
                    .clickable(enabled = false) {} // Prevent click through
                    .padding(16.dp),
        ) {
            Text(
                "Bookmarks / Chapters",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Add Bookmark Input
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    placeholder = { Text("Chapter Name", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray,
                        ),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val label =
                            if (newLabel.isBlank()) {
                                "Chapter at ${FormatUtils.formatDuration(currentPosition)}"
                            } else {
                                newLabel
                            }
                        viewModel.addBookmark(currentPosition, label)
                        newLabel = ""
                    },
                ) { Icon(Icons.Default.Add, null) }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                Modifier,
                DividerDefaults.Thickness,
                color = Color.Gray.copy(alpha = 0.3f),
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(bookmarks) { bookmark ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    Color.Black.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp),
                                ).clickable { onSeek(bookmark.timestamp) }
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bookmark.label, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                FormatUtils.formatDuration(bookmark.timestamp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete bookmark",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Track Selection Dialog - slides in from the right side like Bookmarks panel. Used for audio and
 * subtitle track selection.
 */
@Composable
internal fun TrackSelectionDialog(
    title: String,
    tracks: List<TrackInfo>,
    showOffOption: Boolean,
    isOffSelected: Boolean,
    onTrackSelected: (groupIndex: Int, trackIndex: Int) -> Unit,
    onOffSelected: () -> Unit,
    onDismiss: () -> Unit,
    onAddExternal: (() -> Unit)? = null,
) {
    val primaryAccent = LocalAppTheme.current.primaryColor

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onDismiss),
    ) {
        // Right side panel
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .widthIn(min = 240.dp, max = 360.dp)
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight()
                    .background(
                        Color(0xFF1C1C1E),
                        shape =
                            RoundedCornerShape(
                                topStart = 16.dp,
                                bottomStart = 16.dp,
                            ),
                    ).clickable(enabled = false) {}, // Prevent clicks from closing dialog
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                TrackSelectionHeader(title = title, primaryAccent = primaryAccent)

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                // Track list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // "Off" option for subtitles
                    if (showOffOption) {
                        item {
                            TrackItem(
                                name = "Off",
                                isSelected = isOffSelected,
                                primaryAccent = primaryAccent,
                                onClick = onOffSelected,
                            )
                        }
                    }

                    // Available tracks
                    if (tracks.isEmpty()) {
                        item {
                            Text(
                                text =
                                    if (title == "Subtitles") {
                                        "No subtitles available"
                                    } else {
                                        "No audio tracks available"
                                    },
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        items(tracks) { track ->
                            TrackItem(
                                name = track.name,
                                isSelected = track.isSelected && !isOffSelected,
                                primaryAccent = primaryAccent,
                                onClick = {
                                    onTrackSelected(track.groupIndex, track.trackIndex)
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                // Load external subtitle file (subtitles dialog only)
                if (onAddExternal != null) {
                    OutlinedButton(
                        onClick = onAddExternal,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryAccent),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add subtitle file")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Close", color = primaryAccent) }
            }
        }
    }
}

@Composable
private fun TrackItem(
    name: String,
    isSelected: Boolean,
    primaryAccent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) {
                        primaryAccent.copy(alpha = 0.2f)
                    } else {
                        Color.Transparent
                    },
                ).clickable(onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                if (isSelected) {
                    Icons.Default.RadioButtonChecked
                } else {
                    Icons.Default.RadioButtonUnchecked
                },
            contentDescription = if (isSelected) "Selected" else "Not selected",
            tint = if (isSelected) primaryAccent else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Icon-and-title header shared by the audio and subtitle variants of [TrackSelectionDialog]. */
@Composable
private fun TrackSelectionHeader(
    title: String,
    primaryAccent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                if (title == "Subtitles") {
                    Icons.Default.Subtitles
                } else {
                    Icons.Default.Audiotrack
                },
            contentDescription = title,
            tint = primaryAccent,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
        )
    }
}
