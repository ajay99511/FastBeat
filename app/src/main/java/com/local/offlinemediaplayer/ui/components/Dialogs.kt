package com.local.offlinemediaplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.model.MediaFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.offlinemediaplayer.viewmodel.PlaylistViewModel

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim())
                        onDismiss()
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && name != currentName) {
                        onRename(name.trim())
                        onDismiss()
                    }
                }
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
    // Collect all playlists
    val allPlaylists by playlistViewModel.playlists.collectAsStateWithLifecycle()

    // Filter based on the media type being added
    val filteredPlaylists = remember(allPlaylists, song) {
        allPlaylists.filter { it.isVideo == song.isVideo }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to ${if(song.isVideo) "Video" else "Audio"} Playlist") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                item {
                    TextButton(
                        onClick = onCreateNew,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("New Playlist")
                    }
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
                if (filteredPlaylists.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("No matching playlists found", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    items(filteredPlaylists) { playlist ->
                        val isAdded = playlist.mediaIds.contains(song.id)
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            trailingContent = { if (isAdded) Icon(Icons.Default.Check, null) },
                            modifier = Modifier.clickable(enabled = !isAdded) {
                                playlistViewModel.addSongToPlaylist(playlist.id, song.id)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(text = "Delete File${if (count > 1) "s" else ""}?") },
        text = {
            Text("Are you sure you want to permanently delete ${if (count > 1) "$count files" else "this file"} from your device? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
