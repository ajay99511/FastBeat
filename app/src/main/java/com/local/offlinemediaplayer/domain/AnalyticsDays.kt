package com.local.offlinemediaplayer.domain

import java.util.Calendar

/**
 * Calendar arithmetic over *day keys* — the midnight-normalised timestamps that `daily_playtime`
 * is keyed by.
 *
 * The one rule this exists to enforce: a key produced here must be byte-identical to the key
 * `PlaybackAnalyticsTracker` writes under, or the day's playtime is banked in a bucket nothing
 * reads. That rules out the obvious shortcut of stepping a week with `start + n * 86_400_000`,
 * which the bar chart used to do.
 *
 * That shortcut is *usually* fine, which is exactly what makes it worth pinning down rather than
 * arguing about: most timezones shift on a Sunday, the last day of a Monday-based week, so the
 * mis-stepped days fall outside the keys being generated. In a zone that shifts on any other
 * weekday the days after the shift are each an hour out, match no stored key, and render as
 * zero-height bars — the failure reports "you listened to nothing on Thursday" instead of crashing,
 * which is the kind that survives to production. Stepping with [Calendar.add] is right in every
 * zone and is no harder to write, so there is nothing to trade off.
 *
 * Pure and stateless: every function takes the day key it should work from, so nothing here reads
 * the clock. Reading the clock is [ObserveCurrentDayUseCase]'s job, and keeping the two apart is
 * what makes this testable without a fake clock.
 */
object AnalyticsDays {
    /**
     * The seven day keys of the Monday-to-Sunday week containing [dayKey], in order.
     *
     * **Monday-first is deliberate, not locale-derived.** The chart labels it renders are a
     * hardcoded MON…SUN, so deriving the first day from [Calendar.getFirstDayOfWeek] — which is
     * Sunday in the US locale and Saturday in several others — would rotate the data under fixed
     * labels and mislabel every bar. If week-start ever becomes a user preference, it belongs here
     * as a parameter, with the labels derived from the same choice.
     */
    fun weekOf(dayKey: Long): List<Long> {
        val cursor = normalizedCalendar(dayKey)
        val dayOfWeek = cursor.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) DAYS_PER_WEEK - 1 else dayOfWeek - Calendar.MONDAY
        cursor.add(Calendar.DAY_OF_YEAR, -daysFromMonday)

        val week = ArrayList<Long>(DAYS_PER_WEEK)
        repeat(DAYS_PER_WEEK) {
            week += cursor.timeInMillis
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return week
    }

    /**
     * The day key [days] calendar days before [dayKey].
     *
     * Window starts were previously written inline as `today - 6L * 24 * 60 * 60 * 1000`. The range
     * queries compare with `date >= :start`, and walking back across a 25-hour day leaves that
     * expression an hour *past* the midnight it meant to land on. The intended first day's own key
     * then fails the predicate, and "Last 7 Days" quietly becomes six.
     *
     * Walking back across a 23-hour day drifts the other way and is harmless, which is why this
     * survived: it loses data in one direction only, once a year.
     */
    fun daysBefore(
        dayKey: Long,
        days: Int,
    ): Long =
        normalizedCalendar(dayKey)
            .apply { add(Calendar.DAY_OF_YEAR, -days) }
            .timeInMillis

    /**
     * Tolerates a timestamp that has not been normalised yet, so a caller cannot produce keys with
     * a time-of-day component that will match nothing in the table.
     */
    private fun normalizedCalendar(timestamp: Long): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    const val DAYS_PER_WEEK = 7
}
