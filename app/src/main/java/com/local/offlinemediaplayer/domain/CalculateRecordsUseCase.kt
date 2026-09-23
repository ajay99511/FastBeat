package com.local.offlinemediaplayer.domain

import com.local.offlinemediaplayer.data.db.DailyPlaytime
import java.util.Calendar
import javax.inject.Inject

/**
 * Personal bests.
 *
 * Nulls mean "no record yet" and are rendered as such. A best day of "1 Jan 1970, 0m" is the shape
 * a zero default would produce, and it is worse than an empty state because it looks like data.
 */
data class ListeningRecords(
    val longestStreakDays: Int = 0,
    val bestDay: Long? = null,
    val bestDayMinutes: Int = 0,
    /** A [Calendar.DAY_OF_WEEK] constant, or null before enough has been recorded. */
    val busiestWeekday: Int? = null,
    val busiestWeekdayMinutes: Int = 0,
)

/**
 * Derives the records from history the app already keeps.
 *
 * Takes already-collected lists rather than a DAO, for the reason [CalculateStreakUseCase] records:
 * the screen needs these to recompute as playtime is written, so the flows have to stay upstream.
 * Reading the DAO here with `first()` would turn a reactive screen into a stale one.
 */
class CalculateRecordsUseCase
    @Inject
    constructor() {
        /**
         * @param activeDays day keys with meaningful playtime, newest first — the same list the
         *   current streak is built from, so the two agree on which days count.
         * @param dailyPlaytimes every recorded day, in any order.
         */
        operator fun invoke(
            activeDays: List<Long>,
            dailyPlaytimes: List<DailyPlaytime>,
        ): ListeningRecords {
            val best = dailyPlaytimes.filter { it.totalPlaytimeMs > 0 }.maxByOrNull { it.totalPlaytimeMs }
            val busiest = busiestWeekday(dailyPlaytimes)

            return ListeningRecords(
                longestStreakDays = longestStreak(activeDays),
                bestDay = best?.date,
                bestDayMinutes = ((best?.totalPlaytimeMs ?: 0L) / MS_PER_MINUTE).toInt(),
                busiestWeekday = busiest?.first,
                busiestWeekdayMinutes = ((busiest?.second ?: 0L) / MS_PER_MINUTE).toInt(),
            )
        }

        /**
         * The longest unbroken run in the whole history, current or not.
         *
         * Unlike the current streak, this one does not care whether the run reaches today — that is
         * the entire point of a record. It walks the same newest-first list with the same
         * [AnalyticsDays.isDayBefore] rule, so a run the current streak counts can never be one this
         * refuses to.
         */
        private fun longestStreak(activeDays: List<Long>): Int {
            if (activeDays.isEmpty()) return 0

            var longest = 1
            var run = 1
            for (index in 1 until activeDays.size) {
                if (AnalyticsDays.isDayBefore(activeDays[index], activeDays[index - 1])) {
                    run++
                    if (run > longest) longest = run
                } else {
                    run = 1
                }
            }
            return longest
        }

        /**
         * Which weekday carries the most playtime across all history, and how much.
         *
         * Grouped in Kotlin rather than SQL because the day keys are *local* midnights stored as
         * epoch milliseconds; SQLite's `strftime('%w', …)` would read them as UTC and mis-assign
         * every day for any user east or west of it.
         *
         * Ties break towards the earlier weekday so the answer is stable rather than dependent on
         * map iteration order.
         */
        private fun busiestWeekday(dailyPlaytimes: List<DailyPlaytime>): Pair<Int, Long>? {
            val totals = HashMap<Int, Long>(DAYS_PER_WEEK)
            dailyPlaytimes.forEach { day ->
                if (day.totalPlaytimeMs <= 0) return@forEach
                val weekday = Calendar.getInstance().apply { timeInMillis = day.date }.get(Calendar.DAY_OF_WEEK)
                totals[weekday] = (totals[weekday] ?: 0L) + day.totalPlaytimeMs
            }

            return totals.entries
                .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
                .firstOrNull()
                ?.let { it.key to it.value }
        }

        private companion object {
            const val MS_PER_MINUTE = 60_000L
            const val DAYS_PER_WEEK = 7
        }
    }
