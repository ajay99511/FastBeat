package com.local.offlinemediaplayer.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.SimpleTimeZone
import java.util.TimeZone

/**
 * The streak arithmetic, which had no coverage before P5-A.1 because it lived inside a `combine`
 * inside a `stateIn` inside `AnalyticsViewModel`.
 *
 * Plain JVM: no Robolectric, no Android, no Room — the point of having pulled it out.
 *
 * The timezone is pinned to UTC. Consecutiveness is a calendar question as of S-6.6, and the fixed
 * `today - n * DAY` fixtures below are only equivalent to calendar days in a zone without DST — in
 * one with it, two fixtures can normalise onto the same day and the arithmetic stops describing
 * what the test says it does. [aRunAcrossADstShiftIsNotBroken] covers the DST case explicitly,
 * with dates instead of arithmetic.
 */
class CalculateStreakUseCaseTest {
    private val calculate = CalculateStreakUseCase()
    private lateinit var originalZone: TimeZone

    private val today = 1_700_000_000_000L

    @Before
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    private fun daysAgo(n: Int) = today - n * DAY

    // ------------------------------------------------------------------ nothing to count

    @Test
    fun noActivityIsNoStreak() {
        assertEquals(0, calculate(emptyList(), today))
    }

    /**
     * The streak must be *current*. A long run that ended last week is over, however impressive —
     * showing it would tell the user they are on a 30-day streak they actually broke.
     */
    @Test
    fun aRunThatEndedBeforeYesterdayIsNotAStreak() {
        val staleRun = listOf(daysAgo(2), daysAgo(3), daysAgo(4), daysAgo(5))

        assertEquals(0, calculate(staleRun, today))
    }

    // ------------------------------------------------------------------ counting

    @Test
    fun listeningOnlyTodayIsAOneDayStreak() {
        assertEquals(1, calculate(listOf(today), today))
    }

    /**
     * Today being absent is not a break — the day is still in progress, and a user who has not
     * played anything yet this morning has not lost yesterday's streak.
     */
    @Test
    fun aStreakEndingYesterdayStillCounts() {
        assertEquals(2, calculate(listOf(daysAgo(1), daysAgo(2)), today))
    }

    @Test
    fun countsEveryConsecutiveDay() {
        val run = listOf(today, daysAgo(1), daysAgo(2), daysAgo(3), daysAgo(4))

        assertEquals(5, calculate(run, today))
    }

    /** The first gap ends the count; older activity beyond it is a different, finished streak. */
    @Test
    fun stopsAtTheFirstGapAndIgnoresEarlierActivity() {
        val run = listOf(today, daysAgo(1), daysAgo(3), daysAgo(4), daysAgo(5))

        assertEquals("days 3-5 are a separate, broken run", 2, calculate(run, today))
    }

    @Test
    fun aSingleDayAfterALongGapIsAStreakOfOne() {
        assertEquals(1, calculate(listOf(today, daysAgo(9)), today))
    }

    // ------------------------------------------------------------------ input shape

    /**
     * The query hands these over `ORDER BY date DESC`, and the walk depends on it. If that ordering
     * were ever dropped from the DAO the count would silently collapse to 1 — this pins the
     * assumption so the failure lands here rather than as a wrong number on the stats screen.
     */
    @Test
    fun assumesNewestFirstOrdering() {
        val oldestFirst = listOf(daysAgo(2), daysAgo(1), today)

        assertEquals(
            "ascending input cannot be counted and must not be silently treated as a streak",
            0,
            calculate(oldestFirst, today),
        )
    }

    /** Duplicate rows for one day are not a consecutive pair and must not inflate the count. */
    @Test
    fun repeatedDaysDoNotExtendTheStreak() {
        assertEquals(1, calculate(listOf(today, today), today))
    }

    private companion object {
        const val DAY = 86_400_000L
    }

    /**
     * The limitation this use case used to document as permanent.
     *
     * A local day is 23 or 25 hours long twice a year, so comparing day keys with
     * `earlier - later == 86_400_000` breaks a streak the user did not break — silently, on one day
     * a year, in a zone the developer probably does not live in. Consecutiveness now comes from
     * [AnalyticsDays.isDayBefore], which asks the calendar.
     *
     * The zone is synthetic, with its transition on 11 March, so the fixture cannot be invalidated
     * by a JDK tzdata update.
     */
    @Test
    fun aRunAcrossADstShiftIsNotBroken() {
        val dstZone =
            SimpleTimeZone(
                0,
                "TEST-MIDWEEK-DST",
                Calendar.MARCH,
                11,
                0,
                2 * 60 * 60 * 1000,
                Calendar.NOVEMBER,
                4,
                0,
                2 * 60 * 60 * 1000,
                60 * 60 * 1000,
            )
        TimeZone.setDefault(dstZone)

        fun midnight(
            year: Int,
            month: Int,
            dayOfMonth: Int,
        ): Long {
            val calendar = Calendar.getInstance()
            calendar.clear()
            calendar.set(year, month - 1, dayOfMonth)
            return calendar.timeInMillis
        }

        // 11 March is 23 hours long, so 12 March midnight is 23 h after 11 March midnight.
        val run = listOf(midnight(2026, 3, 12), midnight(2026, 3, 11), midnight(2026, 3, 10))

        assertEquals(
            "the 23-hour day is still one day",
            3,
            calculate(run, midnight(2026, 3, 12)),
        )
    }
}
