package com.local.offlinemediaplayer.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The bars behind the activity chart.
 *
 * The property that matters is **total preservation**: every day the query returns lands in exactly
 * one bucket. A chart that drops a day is wrong in the worst possible way — quietly, and looking
 * like behaviour ("I listened less in March") rather than like a bug. So the central tests here
 * check totals rather than individual bars, and they do it by generating a day of playtime for
 * every day in the range and asserting the sum survives.
 *
 * UTC is pinned: where midnight and month boundaries fall decides every assertion.
 */
class ActivityChartTest {
    private lateinit var originalZone: TimeZone

    @Before
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    private fun midnight(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(year, month - 1, day)
        return calendar.timeInMillis
    }

    /** One minute of playtime on every day in the closed range, so totals are easy to reason about. */
    private fun aMinuteEveryDay(
        range: StatsRange,
        today: Long,
    ): Map<Long, Long> {
        val start = ActivityChart.rangeStart(range, today)
        val end = ActivityChart.rangeEnd(range, today)

        val days = mutableMapOf<Long, Long>()
        val cursor = Calendar.getInstance().apply { timeInMillis = start }
        while (cursor.timeInMillis <= end) {
            days[cursor.timeInMillis] = MS_PER_MINUTE
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return days
    }

    /**
     * A `get()` rather than a stored value, and not by preference: field initialisers run when the
     * test class is constructed, which is *before* `@Before` installs the fixed timezone. Storing it
     * captured a midnight in whatever zone the machine happened to be in, which then matched none of
     * the UTC day keys computed inside the tests — a fixture disagreeing with itself, in the exact
     * way pinning the timezone was supposed to prevent.
     */
    private val tuesday: Long get() = midnight(2026, 9, 15)

    // ------------------------------------------------------------------ total preservation

    @Test
    fun everyDayInTheWeekRangeLandsInABucket() {
        val recorded = aMinuteEveryDay(StatsRange.WEEK, tuesday)

        val buckets = ActivityChart.bucketsFor(StatsRange.WEEK, tuesday, recorded)

        assertEquals(recorded.size, buckets.sumOf { it.playtimeMinutes })
    }

    @Test
    fun everyDayInTheMonthRangeLandsInABucket() {
        val recorded = aMinuteEveryDay(StatsRange.MONTH, tuesday)

        val buckets = ActivityChart.bucketsFor(StatsRange.MONTH, tuesday, recorded)

        assertEquals(ActivityChart.DAYS_IN_MONTH_VIEW, recorded.size)
        assertEquals(recorded.size, buckets.sumOf { it.playtimeMinutes })
    }

    /**
     * The one most likely to be wrong, because months are 28 to 31 days long and the folding has to
     * hold across a year boundary and a February.
     */
    @Test
    fun everyDayInTheYearRangeLandsInExactlyOneMonth() {
        val recorded = aMinuteEveryDay(StatsRange.YEAR, tuesday)

        val buckets = ActivityChart.bucketsFor(StatsRange.YEAR, tuesday, recorded)

        assertEquals(ActivityChart.MONTHS_IN_YEAR_VIEW, buckets.size)
        assertEquals(recorded.size, buckets.sumOf { it.playtimeMinutes })
    }

    @Test
    fun theYearRangeHoldsAcrossAFebruaryAndAYearBoundary() {
        // March 2026 back to April 2025 — includes February and crosses the new year.
        val marchFirst = midnight(2026, 3, 1)
        val recorded = aMinuteEveryDay(StatsRange.YEAR, marchFirst)

        val buckets = ActivityChart.bucketsFor(StatsRange.YEAR, marchFirst, recorded)

        assertEquals(recorded.size, buckets.sumOf { it.playtimeMinutes })
        assertEquals(
            "February 2026 has 28 days and each carries a minute",
            28,
            buckets[buckets.size - 2].playtimeMinutes,
        )
    }

    /**
     * The 31st is where a naive month walk breaks: stepping back a month from 31 March lands on
     * 3 March in a 28-day February, and the buckets silently cover the wrong year.
     */
    @Test
    fun theYearRangeIsCorrectWhenTodayIsThe31st() {
        val lastOfMarch = midnight(2026, 3, 31)

        val months = AnalyticsDays.monthsEnding(lastOfMarch, ActivityChart.MONTHS_IN_YEAR_VIEW)

        assertEquals(midnight(2026, 3, 1), months.last())
        assertEquals(midnight(2025, 4, 1), months.first())
    }

    @Test
    fun aDayWithNoRecordedPlaytimeIsAnEmptyBucketRatherThanAMissingOne() {
        val buckets = ActivityChart.bucketsFor(StatsRange.WEEK, tuesday, emptyMap())

        assertEquals(AnalyticsDays.DAYS_PER_WEEK, buckets.size)
        assertTrue(buckets.all { it.playtimeMinutes == 0 })
    }

    // ------------------------------------------------------------------ ranges

    /**
     * The week is drawn whole, running to Sunday rather than stopping at today, so the days still
     * to come sit empty. Inherited from the original chart and kept: a week with days left in it
     * should not read as a week that collapsed.
     */
    @Test
    fun theWeekRangeRunsToSundayNotToToday() {
        assertEquals(midnight(2026, 9, 14), ActivityChart.rangeStart(StatsRange.WEEK, tuesday))
        assertEquals(midnight(2026, 9, 20), ActivityChart.rangeEnd(StatsRange.WEEK, tuesday))
    }

    /** The rolling ranges have no future half to draw, so they end today. */
    @Test
    fun theRollingRangesEndToday() {
        assertEquals(tuesday, ActivityChart.rangeEnd(StatsRange.MONTH, tuesday))
        assertEquals(tuesday, ActivityChart.rangeEnd(StatsRange.YEAR, tuesday))
    }

    @Test
    fun theMonthRangeIsThirtyDaysEndingToday() {
        assertEquals(midnight(2026, 8, 17), ActivityChart.rangeStart(StatsRange.MONTH, tuesday))
    }

    @Test
    fun theYearRangeStartsAtTheFirstOfTheMonthTwelveMonthsBack() {
        assertEquals(midnight(2025, 10, 1), ActivityChart.rangeStart(StatsRange.YEAR, tuesday))
    }

    // ------------------------------------------------------------------ the current bar

    @Test
    fun exactlyOneBucketIsMarkedCurrentInEveryRange() {
        StatsRange.entries.forEach { range ->
            val buckets = ActivityChart.bucketsFor(range, tuesday, emptyMap())

            assertEquals(range.name, 1, buckets.count { it.isCurrent })
        }
    }

    @Test
    fun theCurrentBucketIsTodayInTheDailyRanges() {
        val week = ActivityChart.bucketsFor(StatsRange.WEEK, tuesday, emptyMap())
        val month = ActivityChart.bucketsFor(StatsRange.MONTH, tuesday, emptyMap())

        assertEquals("Tuesday is the second bar of a Monday-first week", 1, week.indexOfFirst { it.isCurrent })
        assertEquals("today is the last of thirty rolling days", month.size - 1, month.indexOfFirst { it.isCurrent })
    }

    @Test
    fun theCurrentBucketIsThisMonthInTheYearRange() {
        val buckets = ActivityChart.bucketsFor(StatsRange.YEAR, tuesday, emptyMap())

        assertEquals(buckets.size - 1, buckets.indexOfFirst { it.isCurrent })
    }

    // ------------------------------------------------------------------ labels

    @Test
    fun theWeekRangeLabelsEveryBar() {
        val buckets = ActivityChart.bucketsFor(StatsRange.WEEK, tuesday, emptyMap())

        assertEquals(listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"), buckets.map { it.label })
    }

    /**
     * Thirty labels do not fit across a phone, so most bars carry none. Month starts are always
     * named, which is what lets a reader see where one month ends and the next begins without
     * counting bars.
     */
    @Test
    fun theMonthRangeNamesMonthStartsAndNumbersTheAnchorDays() {
        val buckets = ActivityChart.bucketsFor(StatsRange.MONTH, tuesday, emptyMap())
        val labelled = buckets.filter { it.label.isNotEmpty() }.map { it.label }

        // Aug 17 - Sep 15: Aug 20, then the month start, then Sep 10. Aug 10 falls before the
        // window and Sep 20 after it.
        assertEquals(listOf("20", "SEP", "10"), labelled)
    }

    /**
     * Every other month, counting back from the current one, so the month being measured is always
     * the one that is named.
     */
    @Test
    fun theYearRangeLabelsAlternateMonthsEndingWithTheCurrentOne() {
        val buckets = ActivityChart.bucketsFor(StatsRange.YEAR, tuesday, emptyMap())

        assertEquals("SEP", buckets.last().label)
        assertEquals("", buckets[buckets.size - 2].label)
        assertEquals(
            ActivityChart.MONTHS_IN_YEAR_VIEW / 2,
            buckets.count { it.label.isNotEmpty() },
        )
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
