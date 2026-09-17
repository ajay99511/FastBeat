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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.offlinemediaplayer.domain.StatsRange
import com.local.offlinemediaplayer.domain.TopEntry
import com.local.offlinemediaplayer.domain.TopListsSnapshot
import com.local.offlinemediaplayer.model.MediaFile

/** Which ranking is on screen. */
private enum class TopListTab(
    val label: String,
) {
    TRACKS("Tracks"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
}

/**
 * "TOP THIS WEEK / MONTH / YEAR" — the three rankings, one at a time.
 *
 * The screen used to show two tracks: a current favourite and an all-time first place. Everything
 * below first place was computed and discarded, which meant the honest summary of a year of
 * listening was one song title.
 *
 * The window is the chart's, not this section's, which is why the heading names it. Two independent
 * period pickers on one screen would let a reader compare a chart of this year against a top list
 * of this week without noticing.
 *
 * One list is shown at a time rather than three stacked. Three lists of five would add fifteen rows
 * to a tab that is already long, and the tabs make it obvious the other two exist.
 */
@Composable
internal fun TopListsSection(
    lists: TopListsSnapshot,
    range: StatsRange,
    primaryColor: Color,
    onPlayMedia: (MediaFile) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(TopListTab.TRACKS) }

    val entries =
        when (tab) {
            TopListTab.TRACKS -> lists.tracks
            TopListTab.ARTISTS -> lists.artists
            TopListTab.ALBUMS -> lists.albums
        }

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Outlined.TrendingUp,
                null,
                tint = primaryColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "TOP THIS ${range.label.uppercase()}",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopListTab.entries.forEach { candidate ->
                TabChip(
                    label = candidate.label,
                    isSelected = candidate == tab,
                    primaryColor = primaryColor,
                    onClick = { tab = candidate },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                if (entries.isEmpty()) {
                    Text(
                        text = "Nothing played in this period yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    entries.forEachIndexed { index, entry ->
                        TopRow(
                            position = index + 1,
                            entry = entry,
                            primaryColor = primaryColor,
                            onClick = entry.media?.let { { onPlayMedia(it) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    isSelected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) {
                        primaryColor.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ).selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One ranked row.
 *
 * [onClick] is null for artist and album rows. They are not clickable because there is nothing
 * honest to do with a tap here: playing "everything by this artist" is a queue decision that
 * belongs to the library screens, and a row that looks tappable and does nothing is worse than one
 * that plainly is not.
 */
@Composable
private fun TopRow(
    position: Int,
    entry: TopEntry,
    primaryColor: Color,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$position",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (position == 1) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (entry.plays == 1) "1 play" else "${entry.plays} plays",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
