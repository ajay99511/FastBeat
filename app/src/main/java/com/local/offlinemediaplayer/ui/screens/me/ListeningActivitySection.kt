package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.viewmodel.RealtimeAnalytics
import kotlinx.coroutines.flow.StateFlow

/**
 * "LISTENING ACTIVITY" — the four stat tiles and the favourites card on the Me tab.
 *
 * [currentTrack] and [lastPlayedAudio] arrive as flows rather than as already-collected values on
 * purpose: they are collected inside [FavoritesCard], which is the only thing that reads them. That
 * keeps a track change from invalidating the whole Me tab, which is what collecting them in
 * `MeScreen` would have done.
 */
@Composable
internal fun ListeningActivitySection(
    analytics: RealtimeAnalytics,
    currentTrack: StateFlow<MediaFile?>,
    lastPlayedAudio: StateFlow<MediaFile?>,
    primaryColor: Color,
    onPlayMedia: (MediaFile) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Speed,
                null,
                tint = primaryColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LISTENING ACTIVITY",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Row 1: Today & Streak
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AnalyticsCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Today,
                iconColor = primaryColor,
                label = "Today",
                value =
                    FormatUtils.formatMinutesToHours(
                        analytics.todayPlaytimeMinutes,
                    ),
                subtext = "Active Listening",
            )

            AnalyticsCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.LocalFireDepartment,
                iconColor = Color(0xFFFF5500), // Fire Orange
                label = "Streak",
                value =
                    "${analytics.streakDays} Day${if (analytics.streakDays != 1) "s" else ""}",
                subtext = "Consecutive",
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Row 2: Last Week & Average
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AnalyticsCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.DateRange,
                iconColor = Color(0xFF22C55E), // Green
                label = "Last 7 Days",
                value =
                    FormatUtils.formatMinutesToHours(
                        analytics.weekPlaytimeMinutes,
                    ),
                subtext = "Total Playtime",
            )

            AnalyticsCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                iconColor = Color(0xFF00E5FF), // Blue
                label = "Daily Avg",
                value =
                    FormatUtils.formatMinutesToHours(
                        analytics.avgDailyMinutes,
                    ),
                subtext = "Last 30 Days",
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FavoritesCard(
            analytics = analytics,
            currentTrackFlow = currentTrack,
            lastPlayedAudioFlow = lastPlayedAudio,
            primaryColor = primaryColor,
            onPlayMedia = onPlayMedia,
        )
    }
}

/**
 * The card holding the now-playing summary and the two favourite rows.
 *
 * The flows are collected here rather than by the caller so that a track change invalidates only
 * this card. See the note on [ListeningActivitySection].
 */
@Composable
private fun FavoritesCard(
    analytics: RealtimeAnalytics,
    currentTrackFlow: StateFlow<MediaFile?>,
    lastPlayedAudioFlow: StateFlow<MediaFile?>,
    primaryColor: Color,
    onPlayMedia: (MediaFile) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val currentTrack by currentTrackFlow.collectAsStateWithLifecycle()
            val lastPlayedAudio by lastPlayedAudioFlow.collectAsStateWithLifecycle()

            // Show the current track when one is active AND it is audio, otherwise the last played
            // track from the database. An active *video* is filtered out deliberately: this row is
            // about the last played song alone.
            val activeAudio = currentTrack?.takeIf { !it.isVideo }
            val displayTrack = activeAudio ?: lastPlayedAudio

            if (displayTrack != null) {
                NowPlayingSummaryRow(
                    track = displayTrack,
                    isPlayingNow = currentTrack != null,
                    primaryColor = primaryColor,
                    // Plays it either way: for "Last Played" that starts it, for "Current" it
                    // opens the player.
                    onClick = { onPlayMedia(displayTrack) },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color =
                        MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.1f),
                )
            }

            // Current Favorite (Last 30 days)
            FavoriteTrackRow(
                track = analytics.currentFavorite,
                icon = Icons.Outlined.FavoriteBorder,
                accentColor = primaryColor,
                label = "Current Obsession",
                playCount = analytics.currentFavoritePlayCount,
                onPlay = onPlayMedia,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color =
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.1f,
                    ),
            )

            // All Time Favorite
            FavoriteTrackRow(
                track = analytics.allTimeFavorite,
                icon = Icons.Filled.Star,
                accentColor = Color(0xFFFFD700), // Gold
                label = "All Time #1",
                playCount = analytics.allTimeFavoritePlayCount,
                onPlay = onPlayMedia,
            )
        }
    }
}

/** The "Current Playing" / "Last Played" row at the top of [FavoritesCard]. */
@Composable
private fun NowPlayingSummaryRow(
    track: MediaFile,
    isPlayingNow: Boolean,
    primaryColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(
                        primaryColor.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MusicNote,
                null,
                tint = primaryColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                if (isPlayingNow) "Current Playing" else "Last Played",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One favourite-track row.
 *
 * The "Current Obsession" and "All Time #1" rows were two 91-line blocks that differed only in
 * their track, icon, accent colour, label and play count, with identical markup otherwise. They are
 * one composable with five parameters rather than two copies, so a change to the row layout cannot
 * land on one and miss the other.
 *
 * A null [track] renders the placeholder state rather than nothing, which is what both blocks did.
 */
@Composable
private fun FavoriteTrackRow(
    track: MediaFile?,
    icon: ImageVector,
    accentColor: Color,
    label: String,
    playCount: Int,
    onPlay: (MediaFile) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = track != null) {
                    track?.let(onPlay)
                }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.05f),
                        RoundedCornerShape(8.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = track?.title ?: "Keep Listening...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track != null) {
                Text(
                    text = track.artist ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (playCount > 0) {
            Text(
                text = "$playCount plays",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun AnalyticsCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    subtext: String,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface
                                    .copy(alpha = 0.05f),
                                RoundedCornerShape(8.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = label.uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtext,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}
