package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile

/**
 * "Suggested" — the curated shelf at the foot of the Me tab: a featured track, play actions, and a
 * collapsible list of picks.
 *
 * The caller keeps the `randomSongs.isNotEmpty()` guard, because an empty suggestion pool must hide
 * the header too, and that is not derivable from [tracks] alone.
 *
 * [featured] is null while the user is searching, which is what hides the featured block, the play
 * actions and the gradient divider together — one parameter for what was one `searchQuery.isEmpty()`
 * branch.
 */
@Composable
internal fun SuggestedTracksSection(
    tracks: List<MediaFile>,
    featured: MediaFile?,
    showMoreButton: Boolean,
    primaryColor: Color,
    curatedTitle: String,
    onTrackClick: (MediaFile) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Star,
            null,
            tint = primaryColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = curatedTitle,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }

    if (featured != null) {
        FeaturedTrackCard(featured = featured, primaryColor = primaryColor)

        Spacer(modifier = Modifier.height(24.dp))

        PlayActionsRow(
            primaryColor = primaryColor,
            onPlayAll = onPlayAll,
            onShuffleAll = onShuffleAll,
        )

        Spacer(modifier = Modifier.height(24.dp))

        GradientDivider(primaryColor = primaryColor)

        Spacer(modifier = Modifier.height(24.dp))
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (tracks.isEmpty()) {
            Text(
                "No matches found.",
                color = Color.Gray,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            tracks.forEachIndexed { index, song ->
                SuggestedTrackRow(
                    song = song,
                    index = index,
                    onTrackClick = onTrackClick,
                )
            }
        }

        if (showMoreButton) {
            TextButton(
                onClick = onExpand,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    "VIEW ALL TRACKS",
                    color = primaryColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

/** The large artwork card for the first suggested track. */
@Composable
private fun FeaturedTrackCard(
    featured: MediaFile?,
    primaryColor: Color,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (featured?.albumArtUri != null) {
                AsyncImage(
                    model = featured.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme
                                    .colorScheme
                                    .surface,
                            ),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black
                                            .copy(
                                                alpha =
                                                0.7f,
                                            ),
                                    ),
                            ),
                        ),
            )

            Column(
                modifier =
                    Modifier
                        .align(
                            Alignment
                                .BottomStart,
                        ).padding(24.dp),
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .background(
                                    primaryColor,
                                    CircleShape,
                                ),
                    )
                    Spacer(
                        modifier =
                            Modifier.width(8.dp),
                    )
                    Text(
                        text = "SUGGESTED",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = primaryColor,
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = featured?.title ?: "Mix",
                    style =
                        MaterialTheme.typography
                            .headlineSmall
                            .copy(
                                fontWeight =
                                    FontWeight
                                        .Bold,
                            ),
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The Play / Shuffle pair under the featured card. */
@Composable
private fun PlayActionsRow(
    primaryColor: Color,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onPlayAll,
            modifier = Modifier.weight(1f).height(48.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                ),
            shape = RoundedCornerShape(50),
        ) {
            Icon(
                Icons.Default.PlayArrow,
                null,
                tint = Color.Black,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "PLAY ALL",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
            )
        }

        Button(
            onClick = onShuffleAll,
            modifier =
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme
                            .onBackground
                            .copy(
                                alpha = 0.2f,
                            ),
                        RoundedCornerShape(50),
                    ),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        MaterialTheme.colorScheme
                            .onBackground
                            .copy(
                                alpha = 0.05f,
                            ),
                ),
            shape = RoundedCornerShape(50),
        ) {
            Icon(
                Icons.Default.Shuffle,
                null,
                tint = primaryColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "SHUFFLE",
                color =
                    MaterialTheme.colorScheme
                        .onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** The fading rule that separates the featured block from the track list. */
@Composable
private fun GradientDivider(primaryColor: Color) {
    Box(
        modifier =
            Modifier
                .padding(horizontal = 24.dp)
                .width(120.dp)
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                primaryColor,
                                Color.Transparent,
                            ),
                    ),
                    RoundedCornerShape(2.dp),
                ),
    )
}

/** One row in the collapsible suggested-track list. */
@Composable
private fun SuggestedTrackRow(
    song: MediaFile,
    index: Int,
    onTrackClick: (MediaFile) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        12.dp,
                    ),
                ).clickable { onTrackClick(song) }
                .padding(
                    vertical = 8.dp,
                    horizontal = 8.dp,
                ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        SuggestedTrackArtwork(song = song, index = index)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color =
                    MaterialTheme
                        .colorScheme
                        .onBackground,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                fontWeight =
                    FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text =
                    song.artist
                        ?: "Unknown",
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
            )
        }

        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme
                            .colorScheme
                            .onBackground
                            .copy(
                                alpha =
                                0.1f,
                            ),
                        CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                null,
                tint =
                    MaterialTheme
                        .colorScheme
                        .onBackground,
                modifier =
                    Modifier.size(16.dp),
            )
        }
    }
}

/** Album art for a suggested row, falling back to the rank number when there is none. */
@Composable
private fun SuggestedTrackArtwork(
    song: MediaFile,
    index: Int,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .background(
                    MaterialTheme
                        .colorScheme
                        .surfaceContainer,
                    RoundedCornerShape(
                        8.dp,
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model =
                    song.albumArtUri,
                contentDescription =
                null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(
                            RoundedCornerShape(
                                8.dp,
                            ),
                        ),
                contentScale =
                    ContentScale
                        .Crop,
            )
        } else {
            Text(
                text =
                    "${index + 1}",
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                fontWeight =
                    FontWeight
                        .Bold,
            )
        }
    }
}
