package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.offlinemediaplayer.domain.ActivityBucket
import com.local.offlinemediaplayer.domain.StatsRange
import com.local.offlinemediaplayer.ui.theme.OfflineMediaPlayerTheme
import com.local.offlinemediaplayer.viewmodel.ListeningTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S-6.7 — the statistics surface is usable without sight, and says something before there is
 * anything to say.
 *
 * Runs under Robolectric in `src/test` for the reason `MiniPlayerTest` documents: no emulator on
 * CI. Nothing here draws artwork, so `performClick()` works — the Coil caveat from F-44 does not
 * apply.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun bucket(
        label: String,
        minutes: Int,
    ) = ActivityBucket(label = label, playtimeMinutes = minutes)

    // ------------------------------------------------------------------ chart semantics

    /**
     * The regression this task exists for. A `Canvas` contributes no semantics, so the chart was
     * invisible to a screen reader in an app that ships an accessibility guide.
     */
    @Test
    fun theChartIsDescribedToAScreenReader() {
        val week = listOf(bucket("MON", 30), bucket("TUE", 90), bucket("WED", 0))

        composeRule.setContent {
            OfflineMediaPlayerTheme {
                ActivityTrendsSection(
                    buckets = week,
                    selectedRange = StatsRange.WEEK,
                    onRangeSelected = {},
                    primaryColor = Color.Blue,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(chartDescription(week, StatsRange.WEEK))
            .assertIsDisplayed()
    }

    @Test
    fun theDescriptionNamesTheRangeTheTotalAndTheBusiestBar() {
        val description = chartDescription(listOf(bucket("MON", 30), bucket("TUE", 90)), StatsRange.WEEK)

        assertTrue(description, description.contains("week"))
        assertTrue(description, description.contains("2h")) // 30 + 90 minutes
        assertTrue(description, description.contains("TUE"))
        assertTrue(description, description.contains("1h 30m"))
    }

    /** An empty period says so, rather than claiming a most-played bar with no plays behind it. */
    @Test
    fun aPeriodWithNoPlaytimeSaysSoInsteadOfNamingAWinner() {
        val description = chartDescription(listOf(bucket("MON", 0), bucket("TUE", 0)), StatsRange.MONTH)

        assertTrue(description, description.contains("nothing played"))
        assertFalse(description, description.contains("Most played"))
    }

    @Test
    fun aChartWithNoBarsIsDescribedAsHavingNoData() {
        assertEquals("year activity chart, no data", chartDescription(emptyList(), StatsRange.YEAR))
    }

    // ------------------------------------------------------------------ range selector

    /**
     * The selector has to report *which* option is chosen, not just be tappable — a screen reader
     * announcing three identical buttons cannot say which range is on screen.
     */
    @Test
    fun theSelectedRangeIsExposedAsSelected() {
        composeRule.setContent {
            OfflineMediaPlayerTheme {
                ActivityTrendsSection(
                    buckets = listOf(bucket("MON", 10)),
                    selectedRange = StatsRange.MONTH,
                    onRangeSelected = {},
                    primaryColor = Color.Blue,
                )
            }
        }

        composeRule.onNodeWithText("Month").assertIsSelected()
    }

    @Test
    fun choosingARangeReportsIt() {
        var chosen: StatsRange? = null

        composeRule.setContent {
            OfflineMediaPlayerTheme {
                ActivityTrendsSection(
                    buckets = listOf(bucket("MON", 10)),
                    selectedRange = StatsRange.WEEK,
                    onRangeSelected = { chosen = it },
                    primaryColor = Color.Blue,
                )
            }
        }

        composeRule.onNodeWithText("Year").performClick()

        assertEquals(StatsRange.YEAR, chosen)
    }

    // ------------------------------------------------------------------ empty state

    @Test
    fun theEmptyStateExplainsHowStatisticsAccrue() {
        composeRule.setContent {
            OfflineMediaPlayerTheme { EmptyStatsCard(primaryColor = Color.Blue) }
        }

        composeRule.onNodeWithText("No listening history yet").assertIsDisplayed()
    }

    @Test
    fun aFreshInstallHasNoListeningHistory() {
        assertTrue(hasNoListeningHistory(ListeningTotals()))
    }

    /**
     * A user who only ever skips accrues playtime without crossing the play threshold, so the two
     * signals disagree. They have history and must see their chart rather than an introduction.
     */
    @Test
    fun playtimeWithoutAnyCountedPlayStillCountsAsHistory() {
        val skipper = ListeningTotals(lifetimeMinutes = 12, lifetimePlays = 0, firstActiveDay = 1_700_000_000_000L)

        assertFalse(hasNoListeningHistory(skipper))
    }

    @Test
    fun aSinglePlayIsEnoughToLeaveTheEmptyState() {
        assertFalse(hasNoListeningHistory(ListeningTotals(lifetimePlays = 1)))
    }
}
