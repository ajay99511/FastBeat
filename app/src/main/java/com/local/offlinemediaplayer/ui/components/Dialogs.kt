package com.local.offlinemediaplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.model.MediaFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val primaryAccent = LocalAppTheme.current.primaryColor
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "New Playlist",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryAccent,
                    focusedLabelColor = primaryAccent,
                    cursorColor = primaryAccent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val primaryAccent = LocalAppTheme.current.primaryColor
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Rename Playlist",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryAccent,
                    focusedLabelColor = primaryAccent,
                    cursorColor = primaryAccent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && name != currentName) {
                        onRename(name.trim())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/**
 * Renames a media file on disk. Edits the base name only — the extension is
 * shown as a fixed suffix and preserved by the caller.
 */
@Composable
fun RenameMediaDialog(file: MediaFile, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val primaryAccent = LocalAppTheme.current.primaryColor
    val currentBaseName = file.displayName.substringBeforeLast('.')
    val extension = file.displayName.substringAfterLast('.', "")
    var name by remember { mutableStateOf(currentBaseName) }

    val invalidChars = remember { Regex("[/\\\\:*?\"<>|]") }
    val hasInvalidChars = invalidChars.containsMatchIn(name)
    val canRename = name.isNotBlank() && !hasInvalidChars && name.trim() != currentBaseName

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Rename File",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File Name") },
                    suffix = if (extension.isNotEmpty()) {
                        { Text(".$extension", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else null,
                    singleLine = true,
                    isError = hasInvalidChars,
                    supportingText = if (hasInvalidChars) {
                        { Text("Cannot contain / \\ : * ? \" < > |") }
                    } else null,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryAccent,
                        focusedLabelColor = primaryAccent,
                        cursorColor = primaryAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Renames the file on your device. Android may ask you to confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canRename) {
                        onRename(name.trim())
                        onDismiss()
                    }
                },
                enabled = canRename,
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    song: MediaFile,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit
) {
    AddToPlaylistDialog(
        songs = listOf(song),
        playlistViewModel = playlistViewModel,
        onDismiss = onDismiss,
        onCreateNew = onCreateNew
    )
}

@Composable
fun AddToPlaylistDialog(
    songs: List<MediaFile>,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit
) {
    if (songs.isEmpty()) return
    val primaryAccent = LocalAppTheme.current.primaryColor
    val isVideo = songs.first().isVideo

    // Collect all playlists
    val allPlaylists by playlistViewModel.playlists.collectAsStateWithLifecycle()

    // Filter based on the media type being added
    val filteredPlaylists = remember(allPlaylists, isVideo) {
        allPlaylists.filter { it.isVideo == isVideo }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Add to ${if(isVideo) "Video" else "Audio"} Playlist",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                item {
                    TextButton(
                        onClick = onCreateNew,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, tint = primaryAccent)
                        Spacer(Modifier.width(8.dp))
                        Text("New Playlist", color = primaryAccent)
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                if (filteredPlaylists.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "No matching playlists found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredPlaylists) { playlist ->
                        val isAdded = songs.all { playlist.mediaIds.contains(it.id) }
                        ListItem(
                            headlineContent = {
                                Text(
                                    playlist.name,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingContent = {
                                if (isAdded) Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = primaryAccent
                                )
                            },
                            modifier = Modifier.clickable(enabled = !isAdded) {
                                playlistViewModel.addSongsToPlaylist(playlist.id, songs.map { it.id })
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/**
 * Multi-select picker for adding tracks to an existing playlist. Songs already in the
 * playlist are filtered out. Returns the chosen media ids via [onConfirm].
 */
@Composable
fun AddSongsToPlaylistDialog(
    allSongs: List<MediaFile>,
    existingIds: Set<Long>,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryAccent = LocalAppTheme.current.primaryColor
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Long>() }

    val available = remember(allSongs, existingIds) { allSongs.filter { it.id !in existingIds } }
    val filtered = remember(available, query) {
        if (query.isBlank()) available
        else available.filter {
            it.title.contains(query, true) || (it.artist?.contains(query, true) == true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Add songs",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search library...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryAccent,
                        cursorColor = primaryAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            if (available.isEmpty()) "All songs are already in this playlist"
                            else "No matching songs",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.id }) { song ->
                            val checked = selected.contains(song.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selected.remove(song.id) else selected.add(song.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (checked) selected.remove(song.id) else selected.add(song.id)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = primaryAccent)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        song.artist ?: "Unknown",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selected.isNotEmpty()) {
                        onConfirm(selected.toList())
                        onDismiss()
                    }
                },
                enabled = selected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (selected.isEmpty()) "Add" else "Add (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = androidx.compose.ui.Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Delete File${if (count > 1) "s" else ""}?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                "Are you sure you want to permanently delete ${if (count > 1) "$count files" else "this file"} from your device? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
