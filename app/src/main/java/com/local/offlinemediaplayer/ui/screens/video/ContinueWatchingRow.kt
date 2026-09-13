package com.local.offlinemediaplayer.ui.screens.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.domain.ContinueWatchingItem
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.common.FormatUtils
import java.io.File

/**
 * The "Continue watching" shelf on the video library screen.
 *
 * Named [ContinueWatchingTile] rather than `ContinueWatchingCard` to keep it distinct from the
 * card of that name in `ui/screens/me/`. The two take the same `ContinueWatchingItem` but are
 * different designs — that one is a 180x130 card with the title overlaid on the artwork, this one
 * a 16:9 tile with the title beneath — so they are deliberately not shared.
 */
@Composable
internal fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    accentColor: Color,
    onVideoClick: (MediaFile, List<MediaFile>) -> Unit,
) {
    val playlist = remember(items) { items.map { it.media } }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = "CONTINUE WATCHING",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.media.id }) { item ->
                ContinueWatchingTile(
                    item = item,
                    accentColor = accentColor,
                    onClick = { onVideoClick(item.media, playlist) },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingTile(
    item: ContinueWatchingItem,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val video = item.media
    Column(modifier = Modifier.width(180.dp).clickable(onClick = onClick)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = video.thumbnailPath?.let { File(it) } ?: video.uri,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // Play affordance
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            androidx.compose.foundation.shape.CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Remaining time badge
            val remaining = (item.duration - item.position).coerceAtLeast(0L)
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            ) {
                Text(
                    text = "${FormatUtils.formatDuration(remaining)} left",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            if (item.progress > 0f) {
                LinearProgressIndicator(
                    progress = { item.progress },
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

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
