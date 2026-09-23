package com.local.offlinemediaplayer.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One bar. [label] is blank for bars that carry no axis label — see [ActivityChart.bucketsFor]. */
data class ActivityBucket(
    val label: String = "",
    val playtimeMinutes: Int = 0,
    val isCurrent: Boolean = false,
)

/**
 * Turns recorded daily playtime into the bars the activity chart draws, for whichever
 * [StatsRange] is selected.
 *
 * **The invariant is that bucketing is total-preserving:** every day inside the queried range lands
 * in exactly one bucket, and no day lands in two. A chart that drops or double-counts a day is
 * worse than no chart, because it is wrong quietly and in a way that looks like behaviour — "I
 * listened less in March" rather than "March is missing a day". The window this queries and the
 * buckets it fills are both derived from [rangeStart]/[rangeEnd] and [AnalyticsDays], so the two
 * cannot disagree.
 *
 * Pure: the day to measure from is passed in, never read from the clock. That belongs to
 * [ObserveCurrentDayUseCase].
 */
object ActivityChart {
    /** Number of daily bars in the [StatsRange.MONTH] view. */
    const val DAYS_IN_MONTH_VIEW = 30

    /** Number of monthly bars in the [StatsRange.YEAR] view. */
    const val MONTHS_IN_YEAR_VIEW = 12

    /** First day key the range covers. */
    fun rangeStart(
        range: StatsRange,
        today: Long,
    ): Long =
        when (range) {
            StatsRange.WEEK -> AnalyticsDays.weekOf(today).first()
            StatsRange.MONTH -> AnalyticsDays.daysBefore(today, DAYS_IN_MONTH_VIEW - 1)
            StatsRange.YEAR -> AnalyticsDays.monthsEnding(today, MONTHS_IN_YEAR_VIEW).first()
        }

    /**
     * Last day key the range covers.
     *
     * [StatsRange.WEEK] runs to Sunday rather than to today, so the week is drawn whole with the
     * days still to come sitting empty. That was the original chart's behaviour and it is worth
     * keeping: a week that visibly has days left in it does not read as a week that collapsed.
     * The rolling ranges end today, because "the last 30 days" has no future half to draw.
     */
    fun rangeEnd(
        range: StatsRange,
        today: Long,
    ): Long =
        when (range) {
            StatsRange.WEEK -> AnalyticsDays.weekOf(today).last()
            StatsRange.MONTH, StatsRange.YEAR -> today
        }

    /**
     * Builds the bars.
     *
     * @param playtimeByDay milliseconds keyed by day key, as stored. Days with no row are absent
     *   rather than zero, and are drawn as an empty bar — which is the truth: no playback was
     *   recorded, and that is different from unknown only in ways this chart cannot show.
     *
     * Axis labels are sparse by necessity. Thirty labels do not fit across a phone, so only anchor
     * days carry one and the rest are blank; the bars stay evenly spaced either way, so a blank
     * label costs nothing but the reader's ability to name that exact bar — which a tap already
     * answers.
     */
    fun bucketsFor(
        range: StatsRange,
        today: Long,
        playtimeByDay: Map<Long, Long>,
    ): List<ActivityBucket> =
        when (range) {
            StatsRange.WEEK -> weekBuckets(today, playtimeByDay)
            StatsRange.MONTH -> monthBuckets(today, playtimeByDay)
            StatsRange.YEAR -> yearBuckets(today, playtimeByDay)
        }

    private fun weekBuckets(
        today: Long,
        playtimeByDay: Map<Long, Long>,
    ): List<ActivityBucket> =
        AnalyticsDays.weekOf(today).mapIndexed { index, dayKey ->
            ActivityBucket(
                label = WEEKDAY_LABELS[index],
                playtimeMinutes = minutesOn(dayKey, playtimeByDay),
                isCurrent = dayKey == today,
            )
        }

    private fun monthBuckets(
        today: Long,
        playtimeByDay: Map<Long, Long>,
    ): List<ActivityBucket> =
        AnalyticsDays.daysEnding(today, DAYS_IN_MONTH_VIEW).map { dayKey ->
            ActivityBucket(
                label = monthViewLabelFor(dayKey),
                playtimeMinutes = minutesOn(dayKey, playtimeByDay),
                isCurrent = dayKey == today,
            )
        }

    /**
     * Days are folded into months by asking each day which month it belongs to, rather than by
     * generating every day of every month and looking it up. Same result, but it cannot fall out of
     * step with the calendar — a month's length never enters the calculation.
     */
    private fun yearBuckets(
        today: Long,
        playtimeByDay: Map<Long, Long>,
    ): List<ActivityBucket> {
        val months = AnalyticsDays.monthsEnding(today, MONTHS_IN_YEAR_VIEW)
        val totals = HashMap<Long, Long>(months.size)
        playtimeByDay.forEach { (dayKey, playtimeMs) ->
            val month = AnalyticsDays.startOfMonth(dayKey)
            totals[month] = (totals[month] ?: 0L) + playtimeMs
        }

        val currentMonth = AnalyticsDays.startOfMonth(today)
        return months.mapIndexed { index, monthKey ->
            ActivityBucket(
                // Every other month, counting back from the current one, so the month being
                // measured is always the one that is named.
                label = if ((months.size - 1 - index) % 2 == 0) monthAbbreviation(monthKey) else "",
                playtimeMinutes = ((totals[monthKey] ?: 0L) / MS_PER_MINUTE).toInt(),
                isCurrent = monthKey == currentMonth,
            )
        }
    }

    /**
     * The first of a month is named; the 10th and 20th carry their number. That gives three or four
     * anchors across thirty days and, because month starts are always labelled, a reader can tell
     * where one month ends and the next begins without counting bars.
     */
    private fun monthViewLabelFor(dayKey: Long): String {
        val dayOfMonth = Calendar.getInstance().apply { timeInMillis = dayKey }.get(Calendar.DAY_OF_MONTH)
        return when (dayOfMonth) {
            FIRST_OF_MONTH -> monthAbbreviation(dayKey)
            in MONTH_VIEW_ANCHOR_DAYS -> dayOfMonth.toString()
            else -> ""
        }
    }

    private fun minutesOn(
        dayKey: Long,
        playtimeByDay: Map<Long, Long>,
    ): Int = ((playtimeByDay[dayKey] ?: 0L) / MS_PER_MINUTE).toInt()

    /**
     * Localised, unlike the weekday labels above, which are a hardcoded MON…SUN inherited from the
     * original chart. The inconsistency is real and is left alone here rather than fixed in passing:
     * localising the weekday row means deriving the first day of the week from the locale too, and
     * that changes which day each bar represents. A separate change, not a side effect of this one.
     */
    private fun monthAbbreviation(dayKey: Long): String =
        SimpleDateFormat("MMM", Locale.getDefault())
            .format(Date(dayKey))
            .uppercase(Locale.getDefault())

    private val WEEKDAY_LABELS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    private const val FIRST_OF_MONTH = 1

    /**
     * Days inside a month that carry a numeric label in the 30-day view.
     *
     * Two of them, plus the always-named month start, gives three or four anchors across thirty
     * bars — enough to locate a bar, few enough to stay legible on a phone.
     */
    private const val MID_MONTH_ANCHOR_DAY = 10
    private const val LATE_MONTH_ANCHOR_DAY = 20
    private val MONTH_VIEW_ANCHOR_DAYS = setOf(MID_MONTH_ANCHOR_DAY, LATE_MONTH_ANCHOR_DAY)

    private const val MS_PER_MINUTE = 60_000L
}
