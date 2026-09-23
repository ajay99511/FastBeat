package com.local.offlinemediaplayer.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.components.dragHandle
import com.local.offlinemediaplayer.ui.components.rememberDragDropState

/** The draggable play-queue sheet on the Now Playing screen. */
@Composable
internal fun QueueSheetContent(
    queue: List<MediaFile>,
    currentIndex: Int,
    isReorderEnabled: Boolean,
    onTrackClick: (MediaFile) -> Unit,
    onRemove: (MediaFile) -> Unit,
    onReorder: (track: MediaFile, fromIndex: Int, toIndex: Int) -> Unit,
    onClear: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Local working copy so items can be rearranged live while dragging; the final order is
    // committed to the ViewModel once, when the drag ends. Resyncs whenever the real queue
    // changes (reorder commit, removal, track change, ...).
    val localQueue = remember(queue) { queue.toMutableStateList() }
    val dragDropState =
        rememberDragDropState(
            lazyListState = listState,
            onMove = { from, to ->
                if (from in localQueue.indices && to in localQueue.indices) {
                    localQueue.add(to, localQueue.removeAt(from))
                }
            },
            onDragEnd = { key, from, to ->
                // Resolve the moved track by its key, not by index: if the queue changed while
                // the drag was in progress, an index lookup could name the wrong track. The
                // ViewModel additionally verifies the track is still at `from` before committing.
                localQueue.firstOrNull { it.id == key }?.let { track -> onReorder(track, from, to) }
            },
        )

    // Highlight by id (not index) so it stays correct while a drag is rearranging localQueue.
    val currentTrackId = queue.getOrNull(currentIndex)?.id

    // Auto-scroll to current track
    LaunchedEffect(Unit) {
        if (currentIndex in queue.indices) {
            listState.scrollToItem(currentIndex)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Playing Queue",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (queue.isNotEmpty()) {
                    IconButton(onClick = onSaveAsPlaylist) {
                        Icon(
                            Icons.AutoMirrored.Outlined.PlaylistAdd,
                            contentDescription = "Save queue as playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onClear) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            itemsIndexed(localQueue, key = { _, track -> track.id }) { index, track ->
                val isPlaying = track.id == currentTrackId
                val isDragging = track.id == dragDropState.draggingItemKey
                Row(
                    modifier =
                        Modifier
                            .then(
                                if (isDragging) {
                                    Modifier
                                        .zIndex(1f)
                                        .graphicsLayer {
                                            translationY = dragDropState.draggingItemOffset
                                            shadowElevation = 8.dp.toPx()
                                        }
                                } else {
                                    Modifier.animateItem()
                                },
                            ).fillMaxWidth()
                            .background(
                                when {
                                    isDragging -> MaterialTheme.colorScheme.surfaceVariant
                                    isPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else -> Color.Transparent
                                },
                            ).clickable { onTrackClick(track) }
                            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isPlaying) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color =
                                if (isPlaying) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            text = track.artist ?: "Unknown Artist",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Remove from queue (not shown for the currently playing track)
                    if (!isPlaying) {
                        IconButton(onClick = { onRemove(track) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove from queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Drag handle for reordering (hidden while shuffle is on, since the
                    // shuffled order can't be rearranged)
                    if (isReorderEnabled) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .dragHandle(dragDropState, key = track.id),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
