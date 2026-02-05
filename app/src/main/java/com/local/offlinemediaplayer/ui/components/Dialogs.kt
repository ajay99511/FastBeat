
package com.local.offlinemediaplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.viewmodel.MainViewModel

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
                        onCreate(name)
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
fun AddToPlaylistDialog(
    song: MediaFile,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit
) {
    // Collect all playlists
    val allPlaylists by viewModel.playlists.collectAsStateWithLifecycle()

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
                    Divider()
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
                                viewModel.addSongToPlaylist(playlist.id, song.id)
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
