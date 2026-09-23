package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.offlinemediaplayer.domain.ListeningRecords
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.viewmodel.ListeningTotals
import java.text.DateFormatSymbols
import java.util.Calendar

/** `FormatUtils.formatDate` takes epoch *seconds*; day keys are epoch milliseconds. */
private const val MILLIS_PER_SECOND = 1000L

/**
 * Everything ever recorded: total time, total plays, and the date the history starts.
 *
 * Every other number on this screen is a window — today, seven days, thirty days — so a library
 * with years of listening in it had nothing to show for any of it. The data was always there;
 * `daily_playtime` and `play_events` are never pruned except when their media is deleted.
 *
 * A null [ListeningTotals.firstActiveDay] means nothing has been played yet, and is rendered as its
 * own line rather than as a zero or a fallback date, because "no history" and "history starting at
 * the epoch" look identical once you print them.
 */
@Composable
internal fun AllTimeCard(
    totals: ListeningTotals,
    primaryColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Insights,
                    null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ALL TIME",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                AllTimeFigure(
                    modifier = Modifier.weight(1f),
                    value = FormatUtils.formatMinutesToHours(totals.lifetimeMinutes),
                    label = "Played",
                )
                AllTimeFigure(
                    modifier = Modifier.weight(1f),
                    value = "${totals.lifetimePlays}",
                    label = if (totals.lifetimePlays == 1) "Track played" else "Tracks played",
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text =
                    totals.firstActiveDay
                        ?.let { "Listening since ${FormatUtils.formatDate(it / MILLIS_PER_SECOND)}" }
                        ?: "Play something and this starts filling in",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AllTimeFigure(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Personal bests: longest streak, best single day, busiest weekday.
 *
 * Gives the streak tile something to measure against. "4 days" means nothing on its own; "4 days,
 * best ever 21" is the difference between a number and a goal.
 *
 * Each row renders its own absence. A record that has not been set yet says so instead of reading
 * "0" or "1 Jan 1970", which is what a non-null default would have produced.
 */
@Composable
internal fun RecordsCard(
    records: ListeningRecords,
    primaryColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.EmojiEvents,
                    null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RECORDS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            RecordRow(
                label = "Longest streak",
                value =
                    if (records.longestStreakDays > 0) {
                        "${records.longestStreakDays} day${if (records.longestStreakDays != 1) "s" else ""}"
                    } else {
                        null
                    },
            )
            RecordRow(
                label = "Best day",
                value =
                    records.bestDay?.let {
                        "${FormatUtils.formatMinutesToHours(records.bestDayMinutes)} · " +
                            FormatUtils.formatDate(it / MILLIS_PER_SECOND)
                    },
            )
            RecordRow(
                label = "Busiest day of the week",
                value =
                    records.busiestWeekday?.let { weekday ->
                        "${weekdayName(weekday)} · " +
                            FormatUtils.formatMinutesToHours(records.busiestWeekdayMinutes)
                    },
            )
        }
    }
}

/** Localised weekday name for a [Calendar.DAY_OF_WEEK] constant. */
private fun weekdayName(weekday: Int): String = DateFormatSymbols.getInstance().weekdays[weekday]

@Composable
private fun RecordRow(
    label: String,
    value: String?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value ?: "Not set yet",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
            color =
                if (value != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
