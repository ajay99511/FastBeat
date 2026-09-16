package com.local.offlinemediaplayer.domain

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.util.Calendar
import javax.inject.Inject

/**
 * The analytics day — midnight-normalised — re-emitted whenever the clock crosses into a new one.
 *
 * Every window on the stats screen is measured relative to "today": today's playtime, the rolling
 * seven- and thirty-day totals, the streak, and the week the bar chart covers. Before this existed,
 * that value was read once by an inline `Calendar` call inside `AnalyticsViewModel`'s
 * `flatMapLatest`, driven by a trigger that only ever emitted at construction. The screen therefore
 * measured the day it was *subscribed* on, not the day it was *showing*: sit on the Me tab past
 * midnight and today's minutes kept accruing into yesterday's bucket on screen, and the chart went
 * on highlighting yesterday as "today".
 *
 * Emitting the day as a flow rather than reading it as a value is what makes the fix structural:
 * the windows are derived from an input that changes, so they recompute on their own rather than
 * needing somebody to remember to refresh them. It also retires `refreshAnalytics()`, which was
 * public, uncalled from anywhere in the app, and would not have fixed this anyway — the day was
 * recomputed on resubscription already, so the one case it could not repair is precisely the one
 * where the screen stays subscribed across midnight.
 *
 * **On the duplicated day definition.** `PlaybackAnalyticsTracker.normalizedToday()` computes the
 * same midnight, because it is the *writer* of `daily_playtime` and this is the *reader*; if the two
 * ever disagreed, playtime would be banked in a bucket the screen does not query. They are
 * deliberately left as two copies rather than merged: each carries its own injectable clock, and a
 * shared implementation would give a test that moves one clock a second, hidden clock it does not
 * control — which is how the analytics tests would start lying. Second occurrence, noted, not yet
 * extracted. If a third appears, extract then.
 *
 * Known limitation, shared with the rest of the analytics stack: the day is whatever the device's
 * current default timezone says it is, read fresh on each computation. A timezone change is not
 * observed, so it takes effect at the next boundary rather than immediately.
 */
class ObserveCurrentDayUseCase
    @Inject
    constructor() {
        /**
         * Clock source. Injectable so midnight crossing is testable; a bug that only reproduces
         * once a day at one specific instant is otherwise untestable in practice. Mirrors the hook
         * [com.local.offlinemediaplayer.playback.PlaybackAnalyticsTracker] uses for the same reason.
         */
        @VisibleForTesting
        internal var now: () -> Long = { System.currentTimeMillis() }

        /**
         * Midnight of the day [now] falls in, then midnight of each following day as it arrives.
         *
         * Conflated deliberately: a wake-up that finds the day unchanged — a backward clock
         * adjustment, or an early wake on a DST day — re-sleeps instead of forcing every downstream
         * query to re-run for a value that did not change.
         */
        operator fun invoke(): Flow<Long> =
            flow {
                while (true) {
                    emit(startOfToday())
                    delay((startOfNextDay() - now()).coerceAtLeast(MIN_SLEEP_MS))
                }
            }.distinctUntilChanged()

        /** Midnight of the day [now] falls in. */
        fun startOfToday(): Long = startOfDayCalendar().timeInMillis

        /**
         * Midnight of the following day.
         *
         * Computed by adding a calendar day rather than 86 400 000 ms, so the sleep is right on the
         * two days a year a local day is 23 or 25 hours long. Getting this wrong is not dramatic —
         * the flow re-sleeps or wakes an hour late once a year — but the calendar-correct version is
         * no harder to write than the arithmetic one.
         */
        private fun startOfNextDay(): Long =
            startOfDayCalendar()
                .apply { add(Calendar.DAY_OF_YEAR, 1) }
                .timeInMillis

        private fun startOfDayCalendar(): Calendar =
            Calendar.getInstance().apply {
                timeInMillis = now()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        private companion object {
            /** Never sleep for zero: a non-advancing clock would turn the loop into a spin. */
            const val MIN_SLEEP_MS = 1L
        }
    }
