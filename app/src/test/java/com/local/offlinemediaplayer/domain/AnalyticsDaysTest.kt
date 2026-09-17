package com.local.offlinemediaplayer.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.SimpleTimeZone
import java.util.TimeZone

/**
 * Day-key arithmetic for the stats windows.
 *
 * Plain JVM — no Robolectric, no Room. The timezone is pinned rather than inherited: every
 * assertion here depends on where midnight falls, so a suite that passed in UTC and failed on a
 * developer machine in Berlin would be worse than no suite at all.
 *
 * Two zones are used deliberately. The ordinary cases run in a fixed UTC zone, where the fixtures
 * read exactly as written. The DST cases run in a **synthetic** zone whose transition is placed on
 * a Wednesday. Real zones almost all shift on a Sunday — the last day of a Monday-based week, where
 * the mis-stepping this guards against happens to fall outside the keys — so pinning the behaviour
 * against a real zone would produce a test that passes for the wrong reason. The synthetic zone also
 * cannot be invalidated by a JDK tzdata update, which a test naming a real zone's 2026 rules could.
 */
class AnalyticsDaysTest {
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

    /**
     * UTC+0 with DST from 11 March to 4 November, both at 02:00 local.
     *
     * The `0` day-of-week arguments select "exact day of month" mode. 11 March 2026 is a Wednesday,
     * so the short day lands mid-week where a Monday-based week actually contains it.
     */
    private fun midWeekDstZone(): TimeZone =
        SimpleTimeZone(
            0,
            "TEST-MIDWEEK-DST",
            Calendar.MARCH,
            11,
            0,
            2 * MS_PER_HOUR.toInt(),
            Calendar.NOVEMBER,
            4,
            0,
            2 * MS_PER_HOUR.toInt(),
            MS_PER_HOUR.toInt(),
        )

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

    private fun fieldOf(
        timestamp: Long,
        field: Int,
    ): Int = Calendar.getInstance().apply { timeInMillis = timestamp }.get(field)

    // ------------------------------------------------------------------ shape

    @Test
    fun aWeekIsSevenDays() {
        assertEquals(AnalyticsDays.DAYS_PER_WEEK, AnalyticsDays.weekOf(midnight(2026, 9, 15)).size)
    }

    @Test
    fun everyKeyIsAMidnight() {
        AnalyticsDays.weekOf(midnight(2026, 9, 15)).forEach { key ->
            assertEquals(0, fieldOf(key, Calendar.HOUR_OF_DAY))
            assertEquals(0, fieldOf(key, Calendar.MINUTE))
            assertEquals(0, fieldOf(key, Calendar.SECOND))
            assertEquals(0, fieldOf(key, Calendar.MILLISECOND))
        }
    }

    @Test
    fun keysAreInAscendingOrder() {
        val week = AnalyticsDays.weekOf(midnight(2026, 9, 15))

        assertEquals(week.sorted(), week)
    }

    // ------------------------------------------------------------------ Monday first

    /**
     * Monday-first is a deliberate choice, not the locale's. The chart labels are a hardcoded
     * MON…SUN, so a Sunday-first or Saturday-first week would rotate the data under fixed labels
     * and mislabel every bar.
     */
    @Test
    fun theWeekStartsOnMonday() {
        // 2026-09-15 is a Tuesday.
        val week = AnalyticsDays.weekOf(midnight(2026, 9, 15))

        assertEquals(Calendar.MONDAY, fieldOf(week.first(), Calendar.DAY_OF_WEEK))
        assertEquals(midnight(2026, 9, 14), week.first())
    }

    @Test
    fun theWeekEndsOnSunday() {
        val week = AnalyticsDays.weekOf(midnight(2026, 9, 15))

        assertEquals(Calendar.SUNDAY, fieldOf(week.last(), Calendar.DAY_OF_WEEK))
        assertEquals(midnight(2026, 9, 20), week.last())
    }

    /**
     * Sunday is the *end* of its week, not the start of the next one — the case a naive
     * `DAY_OF_WEEK - MONDAY` gets wrong, because `Calendar.SUNDAY` is 1 and would push the cursor
     * forward a day instead of back six.
     */
    @Test
    fun sundayBelongsToTheWeekThatPrecedesIt() {
        val week = AnalyticsDays.weekOf(midnight(2026, 9, 20))

        assertEquals(midnight(2026, 9, 14), week.first())
        assertEquals(midnight(2026, 9, 20), week.last())
    }

    @Test
    fun aMondayIsItsOwnWeekStart() {
        val monday = midnight(2026, 9, 14)

        assertEquals(monday, AnalyticsDays.weekOf(monday).first())
    }

    // ------------------------------------------------------------------ DST

    /**
     * The reason this file exists.
     *
     * `daily_playtime` is keyed by calendar midnight, so a week stepped as `start + n * 86_400_000`
     * produces keys that match nothing once a shift has happened mid-week — and a key that matches
     * nothing renders as a zero-height bar, silently reporting a day of listening as a day of
     * silence.
     */
    @Test
    fun aWeekContainingAMidWeekSpringForwardStillProducesRealMidnights() {
        TimeZone.setDefault(midWeekDstZone())

        val week = AnalyticsDays.weekOf(midnight(2026, 3, 9))

        // Proves the fixture actually straddles the transition, so the test cannot pass vacuously.
        assertEquals(23 * MS_PER_HOUR, week.zipWithNext { day, next -> next - day }.min())

        week.forEach { key -> assertEquals(0, fieldOf(key, Calendar.HOUR_OF_DAY)) }
        assertEquals(midnight(2026, 3, 9), week.first())
        assertEquals(midnight(2026, 3, 15), week.last())
    }

    /** The mirror case: the 4 November fall-back makes that day 25 hours long. */
    @Test
    fun aWeekContainingAMidWeekFallBackStillProducesRealMidnights() {
        TimeZone.setDefault(midWeekDstZone())

        val week = AnalyticsDays.weekOf(midnight(2026, 11, 2))

        assertEquals(25 * MS_PER_HOUR, week.zipWithNext { day, next -> next - day }.max())

        week.forEach { key -> assertEquals(0, fieldOf(key, Calendar.HOUR_OF_DAY)) }
        assertEquals(midnight(2026, 11, 2), week.first())
        assertEquals(midnight(2026, 11, 8), week.last())
    }

    /**
     * Pins the difference from the arithmetic the chart used to do, so a future "simplification"
     * back to `start + n * DAY_MS` fails here rather than in production.
     */
    @Test
    fun steppingByAFixedDayWouldHaveProducedTheWrongKeys() {
        TimeZone.setDefault(midWeekDstZone())

        val week = AnalyticsDays.weekOf(midnight(2026, 3, 9))
        val naive = List(AnalyticsDays.DAYS_PER_WEEK) { week.first() + it * MS_PER_DAY }

        // Identical up to and including the short day itself; divergent from the day after it.
        assertEquals(week.take(TRANSITION_INDEX + 1), naive.take(TRANSITION_INDEX + 1))
        assertEquals(MS_PER_HOUR, naive[TRANSITION_INDEX + 1] - week[TRANSITION_INDEX + 1])
        assertNotEquals(week.last(), naive.last())
    }

    // ------------------------------------------------------------------ daysBefore

    @Test
    fun daysBeforeZeroIsTheDayItself() {
        val day = midnight(2026, 9, 15)

        assertEquals(day, AnalyticsDays.daysBefore(day, 0))
    }

    @Test
    fun daysBeforeWalksBackwardsByCalendarDays() {
        assertEquals(midnight(2026, 9, 9), AnalyticsDays.daysBefore(midnight(2026, 9, 15), 6))
    }

    @Test
    fun daysBeforeCrossesMonthAndYearBoundaries() {
        assertEquals(midnight(2026, 8, 31), AnalyticsDays.daysBefore(midnight(2026, 9, 1), 1))
        assertEquals(midnight(2025, 12, 31), AnalyticsDays.daysBefore(midnight(2026, 1, 1), 1))
    }

    /**
     * The window-start defect this fixes, in the direction that actually loses data.
     *
     * Range queries compare with `date >= :start`. Walking back across a *fall-back* — the 25-hour
     * day — leaves `today - n * 86_400_000` an hour **after** the midnight it meant to land on, so
     * that day's own key fails the predicate and "last 7 days" quietly becomes six.
     *
     * (Walking back across a spring-forward drifts the other way and is harmless, which is exactly
     * why this was never noticed: the bug only bites in one direction, once a year.)
     */
    @Test
    fun daysBeforeSurvivesADstShiftThatFixedArithmeticWouldNot() {
        TimeZone.setDefault(midWeekDstZone())

        val today = midnight(2026, 11, 6)
        val windowStart = AnalyticsDays.daysBefore(today, 6)
        val naive = today - 6 * MS_PER_DAY

        assertEquals(midnight(2026, 10, 31), windowStart)
        assertEquals(0, fieldOf(windowStart, Calendar.HOUR_OF_DAY))

        assertEquals(MS_PER_HOUR, naive - windowStart)
        assertTrue(
            "the naive bound sits past the first day's key, so `date >= :start` drops that day",
            naive > windowStart,
        )
    }

    // ------------------------------------------------------------------ robustness

    /** A caller that forgets to normalise must not be able to produce keys that match no row. */
    @Test
    fun aTimestampWithATimeOfDayIsNormalisedFirst() {
        val tuesdayAfternoon = midnight(2026, 9, 15) + 13 * MS_PER_HOUR + 47 * MS_PER_MINUTE

        assertEquals(AnalyticsDays.weekOf(midnight(2026, 9, 15)), AnalyticsDays.weekOf(tuesdayAfternoon))
    }

    @Test
    fun theEpochIsNotASpecialCase() {
        val week = AnalyticsDays.weekOf(0L)

        assertEquals(AnalyticsDays.DAYS_PER_WEEK, week.size)
        assertTrue(week.first() <= 0L)
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val MS_PER_HOUR = 60 * MS_PER_MINUTE
        const val MS_PER_DAY = 24 * MS_PER_HOUR

        /** Index of Wednesday 11 March within the Monday-based week starting 9 March 2026. */
        const val TRANSITION_INDEX = 2
    }
}
