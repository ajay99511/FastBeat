package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.viewmodel.LibraryStats

/**
 * "LIBRARY STATS" — the counts-and-storage summary near the middle of the Me tab.
 *
 * [StatCard] is the leaf used only by this section and stays file-private; the section itself is
 * `internal` because [MeScreen] calls it from a sibling file.
 *
 * Images are counted here alongside audio and video. They are part of the library — the app indexes
 * them and gives them their own tab — and leaving them out was what made the storage headline
 * under-report. See [StorageCard].
 */
@Composable
internal fun LibraryStatsSection(
    stats: LibraryStats,
    primaryColor: Color,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Bar chart icon using stacked bars
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(18.dp),
            ) {
                listOf(10.dp, 18.dp, 14.dp).forEach { height ->
                    Box(
                        modifier =
                            Modifier
                                .width(4.dp)
                                .height(height)
                                .background(
                                    primaryColor,
                                    RoundedCornerShape(1.dp),
                                ),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LIBRARY STATS",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Four tiles over two rows rather than one row of four: at 28.sp a five-figure song count
        // does not fit a quarter-width card on a small phone, and a truncated number is worse than
        // a taller section.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.MusicNote,
                count = stats.songCount,
                label = "SONGS",
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.VideoLibrary,
                count = stats.videoCount,
                label = "VIDEOS",
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Image,
                count = stats.imageCount,
                label = "IMAGES",
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Star,
                count = stats.playlistCount,
                label = "PLAYLISTS",
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        StorageCard(stats = stats)
    }
}

/**
 * The storage total, and the breakdown that accounts for it.
 *
 * The breakdown is not decoration. The headline used to read "Total Storage Used" while summing
 * audio and video only, so a library with thousands of photos reported a total that matched nothing
 * the user could check. Printing the three parts makes the figure falsifiable: if it looks wrong,
 * the line underneath says which media type is responsible.
 */
@Composable
private fun StorageCard(stats: LibraryStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total Storage Used",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = FormatUtils.formatSize(stats.totalStorageBytes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text =
                    listOf(
                        "Audio ${FormatUtils.formatSize(stats.audioStorageBytes)}",
                        "Video ${FormatUtils.formatSize(stats.videoStorageBytes)}",
                        "Images ${FormatUtils.formatSize(stats.imageStorageBytes)}",
                    ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    count: Int,
    label: String,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$count",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
        }
    }
}
