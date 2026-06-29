
package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog
import com.local.offlinemediaplayer.ui.components.RenamePlaylistDialog
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel
import com.local.offlinemediaplayer.viewmodel.SmartPlaylistType
import com.local.offlinemediaplayer.viewmodel.SmartPlaylistViewModel

@Composable
fun PlaylistListScreen(
    viewModel: PlaybackViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    onPlaylistClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    isVideo: Boolean = false, // Added flag to distinguish list source
    onRename: ((String, String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    onSmartPlaylistClick: ((String) -> Unit)? = null,
    smartPlaylistViewModel: SmartPlaylistViewModel = hiltViewModel()
) {
    // Observe specific list based on flag
    val playlists by if (isVideo) {
        playlistViewModel.videoPlaylists.collectAsStateWithLifecycle()
    } else {
        playlistViewModel.audioPlaylists.collectAsStateWithLifecycle()
    }

    // Smart (auto-generated) playlists are an audio-only concept. The video path below is
    // intentionally left untouched.
    val showSmart = !isVideo && onSmartPlaylistClick != null

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isMiniPlayerVisible = currentTrack != null && !currentTrack!!.isVideo
    val bottomPadding = if (isMiniPlayerVisible) 100.dp else 16.dp

    // State for actions
    var playlistToRename by remember { mutableStateOf<Pair<String, String>?>(null) } // id, currentName
    var playlistToDelete by remember { mutableStateOf<String?>(null) } // id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Header with Add Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isVideo) "Video Playlists" else "Playlists",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            // Add Button (Top Right)
            Card(
                onClick = onCreateClick,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(40.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Playlist",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        // 1b. Smart / Auto playlists (audio only) — derived from existing analytics, read-only.
        if (showSmart) {
            val smartPlaylists by smartPlaylistViewModel.smartPlaylists.collectAsStateWithLifecycle()
            SmartPlaylistsSection(
                smartPlaylists = smartPlaylists,
                onClick = { typeId -> onSmartPlaylistClick?.invoke(typeId) }
            )
        }

        // 2. Playlist List
        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No playlists created",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(
                        onClick = onCreateClick,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Create your first playlist", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding) // Space for MiniPlayer
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistListItem(
                        name = playlist.name,
                        count = playlist.mediaIds.size,
                        isVideo = isVideo,
                        onClick = { onPlaylistClick(playlist.id) },
                        onRename = if (onRename != null) { { playlistToRename = playlist.id to playlist.name } } else null,
                        onDelete = if (onDelete != null) { { playlistToDelete = playlist.id } } else null
                    )
                }
            }
        }
    }

    // Dialogs
    if (playlistToRename != null && onRename != null) {
        RenamePlaylistDialog(
            currentName = playlistToRename!!.second,
            onDismiss = { playlistToRename = null },
            onRename = { newName ->
                onRename(playlistToRename!!.first, newName)
                playlistToRename = null
            }
        )
    }

    if (playlistToDelete != null && onDelete != null) {
        DeleteConfirmationDialog(
            count = 1, // Playlist itself
            onConfirm = {
                onDelete(playlistToDelete!!)
                playlistToDelete = null
            },
            onDismiss = { playlistToDelete = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistListItem(
    name: String,
    count: Int,
    isVideo: Boolean = false,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (onRename != null || onDelete != null) {
                        { showMenu = true }
                    } else null
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon / Placeholder
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(56.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$count ${if (isVideo) "video" else "song"}${if (count != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Dropdown Menu for Context Actions
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showMenu = false
                        onRename()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.onSurface) }
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }

    // Divider
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant,
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/** Icon for each auto-generated playlist type (UI concern kept out of the ViewModel). */
private fun SmartPlaylistType.icon(): ImageVector = when (this) {
    SmartPlaylistType.MOST_PLAYED -> Icons.Default.TrendingUp
    SmartPlaylistType.RECENTLY_ADDED -> Icons.Default.FiberNew
    SmartPlaylistType.FORGOTTEN -> Icons.Default.History
    SmartPlaylistType.NEVER_PLAYED -> Icons.Default.MusicOff
    SmartPlaylistType.MOST_SKIPPED -> Icons.Default.SkipNext
}

/**
 * "Made for you" grid of auto-generated playlists. Always shows all types (with their live
 * counts) for discoverability; tapping an empty one simply opens an empty detail view.
 */
@Composable
private fun SmartPlaylistsSection(
    smartPlaylists: Map<SmartPlaylistType, List<MediaFile>>,
    onClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "MADE FOR YOU",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        // Two-column grid laid out as rows (cheap; the set is fixed at 5 items).
        SmartPlaylistType.entries.chunked(2).forEach { rowTypes ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowTypes.forEach { type ->
                    SmartPlaylistCard(
                        type = type,
                        count = smartPlaylists[type]?.size ?: 0,
                        onClick = { onClick(type.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keep the last odd card half-width by padding the row with empty space.
                if (rowTypes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Text(
            text = "YOUR PLAYLISTS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun SmartPlaylistCard(
    type: SmartPlaylistType,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = type.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "$count song${if (count != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
