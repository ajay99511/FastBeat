package com.local.offlinemediaplayer.domain

import com.local.offlinemediaplayer.data.db.DailyPlaytime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Personal bests.
 *
 * The rule worth pinning is the one that spans two classes: **the longest streak and the current
 * streak must agree about which runs are unbroken.** They read the same list with the same
 * consecutiveness rule, and a test asserts that directly rather than trusting the two
 * implementations to stay in step.
 *
 * UTC is pinned; the weekday grouping and the day arithmetic both depend on it.
 */
class CalculateRecordsUseCaseTest {
    private val calculate = CalculateRecordsUseCase()
    private val currentStreak = CalculateStreakUseCase()
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

    private fun day(
        dayKey: Long,
        minutes: Int,
    ) = DailyPlaytime(dayKey, minutes * MS_PER_MINUTE)

    // ------------------------------------------------------------------ empty

    @Test
    fun noHistoryMeansNoRecords() {
        val records = calculate(emptyList(), emptyList())

        assertEquals(0, records.longestStreakDays)
        assertNull(records.bestDay)
        assertNull(records.busiestWeekday)
    }

    /**
     * `initDailyPlaytime` seeds a zero row whenever a session starts, so rows with no playtime are
     * routine. A "best day" of zero minutes is not a record.
     */
    @Test
    fun daysWithNoPlaytimeAreNotRecords() {
        val seeded = listOf(day(midnight(2026, 9, 14), 0), day(midnight(2026, 9, 15), 0))

        val records = calculate(emptyList(), seeded)

        assertNull(records.bestDay)
        assertNull(records.busiestWeekday)
    }

    // ------------------------------------------------------------------ longest streak

    @Test
    fun theLongestStreakIsTheLongestRunAnywhereInHistory() {
        // A 4-day run in August, a 2-day run in September. Newest first, as the query returns.
        val activeDays =
            listOf(
                midnight(2026, 9, 15),
                midnight(2026, 9, 14),
                midnight(2026, 8, 13),
                midnight(2026, 8, 12),
                midnight(2026, 8, 11),
                midnight(2026, 8, 10),
            )

        assertEquals(4, calculate(activeDays, emptyList()).longestStreakDays)
    }

    /**
     * Unlike the current streak, a record does not have to reach today — that is the whole point of
     * a record.
     */
    @Test
    fun aLongFinishedRunStillCountsAsTheRecord() {
        val lastYear = (0..9).map { midnight(2025, 3, 20) - it * MS_PER_DAY }

        assertEquals(10, calculate(lastYear, emptyList()).longestStreakDays)
        assertEquals(
            "the same run is not a *current* streak",
            0,
            currentStreak(lastYear, midnight(2026, 9, 15)),
        )
    }

    @Test
    fun asingleActiveDayIsAOneDayRecord() {
        assertEquals(1, calculate(listOf(midnight(2026, 9, 15)), emptyList()).longestStreakDays)
    }

    /**
     * The cross-class invariant. Whatever the current streak counts, the record is at least that —
     * otherwise the screen would show "4 day streak" beside "best ever: 2 days", built from the
     * identical list.
     */
    @Test
    fun theRecordIsNeverShorterThanTheCurrentStreak() {
        val today = midnight(2026, 9, 15)
        val runs =
            listOf(
                listOf(today),
                listOf(today, today - MS_PER_DAY),
                (0..6).map { today - it * MS_PER_DAY },
                (0..2).map { today - it * MS_PER_DAY } + listOf(today - 5 * MS_PER_DAY),
            )

        runs.forEach { activeDays ->
            val current = currentStreak(activeDays, today)
            val record = calculate(activeDays, emptyList()).longestStreakDays

            assertEquals(
                "current $current must not exceed record $record for $activeDays",
                true,
                record >= current,
            )
        }
    }

    // ------------------------------------------------------------------ best day

    @Test
    fun theBestDayIsTheOneWithTheMostPlaytime() {
        val history =
            listOf(
                day(midnight(2026, 9, 13), 30),
                day(midnight(2026, 9, 14), 240),
                day(midnight(2026, 9, 15), 90),
            )

        val records = calculate(emptyList(), history)

        assertEquals(midnight(2026, 9, 14), records.bestDay)
        assertEquals(240, records.bestDayMinutes)
    }

    // ------------------------------------------------------------------ busiest weekday

    @Test
    fun theBusiestWeekdayIsSummedAcrossTheWholeHistory() {
        // Two Mondays of 60 minutes each beat one Saturday of 100.
        val history =
            listOf(
                day(midnight(2026, 9, 7), 60), // Monday
                day(midnight(2026, 9, 14), 60), // Monday
                day(midnight(2026, 9, 12), 100), // Saturday
            )

        val records = calculate(emptyList(), history)

        assertEquals(Calendar.MONDAY, records.busiestWeekday)
        assertEquals(120, records.busiestWeekdayMinutes)
    }

    /** Ties resolve to the earlier weekday rather than to whatever the map iterated first. */
    @Test
    fun tiedWeekdaysResolveDeterministically() {
        val sundayThenMonday =
            listOf(
                day(midnight(2026, 9, 13), 60), // Sunday — Calendar.SUNDAY is 1
                day(midnight(2026, 9, 14), 60), // Monday
            )

        assertEquals(Calendar.SUNDAY, calculate(emptyList(), sundayThenMonday).busiestWeekday)
        assertEquals(
            "input order must not change the answer",
            Calendar.SUNDAY,
            calculate(emptyList(), sundayThenMonday.reversed()).busiestWeekday,
        )
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val MS_PER_DAY = 86_400_000L
    }
}
