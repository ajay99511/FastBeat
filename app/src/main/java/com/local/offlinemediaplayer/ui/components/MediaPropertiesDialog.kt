package com.local.offlinemediaplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme

@Composable
fun MediaPropertiesDialog(mediaFile: MediaFile, onDismiss: () -> Unit) {
    val primaryAccent = LocalAppTheme.current.primaryColor

    Dialog(onDismissRequest = onDismiss) {
        Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                // Header
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                            text = "Properties",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Properties List
                PropertyItem(label = "Title", value = mediaFile.title)

                if (!mediaFile.artist.isNullOrBlank() && mediaFile.artist != "<unknown>") {
                    PropertyItem(label = "Artist", value = mediaFile.artist)
                }

                PropertyItem(label = "Path", value = mediaFile.uri.path ?: "Unknown")

                if (mediaFile.size > 0) {
                    PropertyItem(label = "Size", value = FormatUtils.formatSize(mediaFile.size))
                }

                if (mediaFile.duration > 0) {
                    PropertyItem(
                            label = "Duration",
                            value = FormatUtils.formatDuration(mediaFile.duration)
                    )
                }

                if (mediaFile.resolution.isNotEmpty()) {
                    PropertyItem(label = "Resolution", value = mediaFile.resolution)
                }

                if (mediaFile.bucketName.isNotEmpty()) {
                    PropertyItem(label = "Folder", value = mediaFile.bucketName)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Close Button
                Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                ) { Text("Close") }
            }
        }
    }
}

@Composable
private fun PropertyItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
        )
    }
}
