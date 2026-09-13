package com.local.offlinemediaplayer.ui.screens.video

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAddCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import java.io.File

/**
 * The two ways a video is drawn in a list: [VideoListItem] for the row layout and [VideoCardItem]
 * for the grid.
 *
 * These live in their own file because they are the one genuinely shared pair in this package —
 * [VideoListScreen] and [MoviesListContent] both render them, so neither screen can own them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VideoListItem(
    video: MediaFile,
    onVideoClick: () -> Unit,
    onLongClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onRename: () -> Unit = {},
    progress: Float = 0f,
) {
    var showMenu by remember { mutableStateOf(false) }
    val accentColor = LocalAppTheme.current.primaryColor

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    } else {
                        Color.Transparent
                    },
                ).combinedClickable(
                    onClick = onVideoClick,
                    onLongClick = onLongClick,
                ).padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector =
                    if (isSelected) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.RadioButtonUnchecked
                    },
                contentDescription = null,
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Gray
                    },
                modifier = Modifier.padding(end = 16.dp).size(24.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .width(96.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = video.thumbnailPath?.let { File(it) } ?: video.uri,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // Resume progress bar (bottom edge)
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                    color = accentColor,
                    trackColor = Color.Black.copy(alpha = 0.4f),
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = FormatUtils.formatDuration(video.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (video.size > 0L) {
                    Text(
                        text = " • ${FormatUtils.formatSize(video.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (video.resolution.isNotEmpty()) {
                    Text(
                        text = " • ${video.resolution}",
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                    )
                }
            }
        }

        if (!isSelectionMode) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier =
                        Modifier.background(
                            MaterialTheme.colorScheme.surface,
                        ),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Add to Playlist",
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurface,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.PlaylistAddCircle,
                                null,
                                tint =
                                    MaterialTheme.colorScheme
                                        .onSurface,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Rename",
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurface,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Edit,
                                null,
                                tint =
                                    MaterialTheme.colorScheme
                                        .onSurface,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Properties",
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurface,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProperties()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Info,
                                null,
                                tint =
                                    MaterialTheme.colorScheme
                                        .onSurface,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete",
                                color =
                                    MaterialTheme.colorScheme
                                        .error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint =
                                    MaterialTheme.colorScheme
                                        .error,
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VideoCardItem(
    video: MediaFile,
    onVideoClick: () -> Unit,
    onLongClick: () -> Unit,
    accentColor: Color,
    onAddToPlaylist: () -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onRename: () -> Unit = {},
    progress: Float = 0f,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) {
                        accentColor.copy(alpha = 0.1f)
                    } else {
                        Color.Transparent
                    },
                ).combinedClickable(
                    onClick = onVideoClick,
                    onLongClick = onLongClick,
                ).padding(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = video.thumbnailPath?.let { File(it) } ?: video.uri,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // Selection Overlay
            if (isSelectionMode) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            if (isSelected) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.RadioButtonUnchecked
                            },
                        contentDescription = null,
                        tint = if (isSelected) accentColor else Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            } else {
                // Resolution badge (bottom-start)
                if (video.resolution.isNotEmpty()) {
                    Surface(
                        color = accentColor,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    ) {
                        Text(
                            text = video.resolution,
                            color = Color.White,
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            modifier =
                                Modifier.padding(
                                    horizontal = 6.dp,
                                    vertical = 2.dp,
                                ),
                        )
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                ) {
                    Text(
                        text = FormatUtils.formatDuration(video.duration),
                        color = Color.White,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        modifier =
                            Modifier.padding(
                                horizontal = 6.dp,
                                vertical = 2.dp,
                            ),
                    )
                }

                // Resume progress bar (bottom edge)
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp),
                        color = accentColor,
                        trackColor = Color.Black.copy(alpha = 0.4f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (video.size > 0) {
                        Text(
                            text = FormatUtils.formatSize(video.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.Gray,
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier =
                            Modifier.background(
                                MaterialTheme.colorScheme.surface,
                            ),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Properties",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onProperties()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Info,
                                    null,
                                    tint =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Rename",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    null,
                                    tint =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Add to Playlist",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default
                                        .PlaylistAddCircle,
                                    null,
                                    tint =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint =
                                        MaterialTheme
                                            .colorScheme
                                            .error,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
