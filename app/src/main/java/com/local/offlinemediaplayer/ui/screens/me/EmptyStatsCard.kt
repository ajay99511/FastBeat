package com.local.offlinemediaplayer.ui.screens.me

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
import com.local.offlinemediaplayer.viewmodel.ListeningTotals

/**
 * True when nothing has ever been recorded, so the statistics have nothing to describe.
 *
 * Both conditions are checked because they can disagree, and the disagreement is meaningful: a user
 * who has only ever skipped through tracks accrues playtime without ever crossing the play
 * threshold, so `lifetimePlays` stays at zero while `firstActiveDay` is set. That user *does* have
 * history and should see their chart, not an introduction.
 *
 * A pure function rather than an expression inside [StatsSections] so the rule is testable without
 * standing up a ViewModel.
 */
internal fun hasNoListeningHistory(totals: ListeningTotals): Boolean =
    totals.firstActiveDay == null && totals.lifetimePlays == 0 && totals.lifetimeMinutes == 0

/**
 * What the statistics surface shows before there is anything to show.
 *
 * The alternative — and what shipped until now — is five tiles reading `0m`, a `0 Days` streak, an
 * empty chart and "Keep Listening…", which tells a new user nothing about why it is all empty or
 * what would change it.
 *
 * It also states the two rules that are otherwise completely invisible. A track has to be played
 * for a while before it counts, and a day needs more than a token amount of listening before it
 * extends a streak; without saying so, a user who plays three songs and sees "0 plays" has been
 * given a bug report rather than an explanation.
 *
 * **The thresholds quoted here live in two other places** — `PlaybackAnalyticsTracker`'s
 * `PLAY_COUNT_THRESHOLD_MS` and the `> 60000` predicate in `MediaDao.getActiveDays`. They are
 * restated rather than referenced because one is `private` and the other is inside a SQL string
 * literal. If either is ever retuned, this copy has to move with it; that risk is accepted here
 * because the numbers are stable and describing them vaguely would waste the explanation.
 */
@Composable
internal fun EmptyStatsCard(primaryColor: Color) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Insights,
                    null,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "No listening history yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text =
                    "Play something and this fills in on its own — daily activity, your top tracks " +
                        "and artists, streaks and personal bests.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text =
                    "A track counts as played after about 30 seconds, so skipping through an album " +
                        "will not fill this up. A day joins your streak once you have listened for a minute.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
