package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.domain.ContinueWatchingItem
import com.local.offlinemediaplayer.model.MediaFile

/**
 * "CONTINUE WATCHING" — the horizontal shelf of part-watched videos on the Me tab.
 *
 * Emits nothing when [entries] is empty, which is exactly what the `if` guard in `MeScreen` did
 * before this was extracted; the caller no longer needs the guard.
 */
@Composable
internal fun ContinueWatchingSection(
    entries: List<ContinueWatchingItem>,
    primaryColor: Color,
    onPlayMedia: (MediaFile) -> Unit,
) {
    if (entries.isEmpty()) return

    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.History,
            null,
            tint = primaryColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "CONTINUE WATCHING",
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().height(140.dp),
    ) {
        items(entries) { item ->
            ContinueWatchingCard(
                item = item,
                primaryColor = primaryColor,
                onClick = { onPlayMedia(item.media) },
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    primaryColor: Color,
    onClick: () -> Unit,
) {
    val video = item.media

    // `progress` now comes from the shared use case, so this row and the Video library agree. They
    // did not before: this screen showed 0 % whenever the history row had no duration, while the
    // library fell back to the media's.
    val progress = item.progress

    Card(
        modifier =
            Modifier
                .width(180.dp)
                .height(130.dp)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme
                        .surface,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black
                                .copy(
                                    alpha =
                                    0.3f,
                                ),
                        ),
            )

            // Play Icon Center
            Icon(
                imageVector =
                    Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier =
                    Modifier
                        .align(
                            Alignment
                                .Center,
                        ).size(32.dp),
            )

            // Progress Bar Bottom
            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .align(
                            Alignment
                                .BottomCenter,
                        ).fillMaxWidth()
                        .height(4.dp),
                color = primaryColor,
                trackColor =
                    Color.White.copy(
                        alpha = 0.3f,
                    ),
            )

            // Title Overlay
            Box(
                modifier =
                    Modifier
                        .align(
                            Alignment
                                .BottomStart,
                        ).fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black,
                                ),
                            ),
                        ).padding(
                            bottom =
                                8.dp,
                            start =
                                8.dp,
                            top = 20.dp,
                            end = 8.dp,
                        ),
            ) {
                Text(
                    text = video.title,
                    color = Color.White,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    maxLines = 1,
                    overflow =
                        TextOverflow
                            .Ellipsis,
                )
            }
        }
    }
}
