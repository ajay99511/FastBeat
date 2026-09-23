package com.local.offlinemediaplayer.domain

import kotlin.math.roundToInt

/**
 * How one period compares with the period before it.
 *
 * A bare total tells the user very little — four hours this week is good or bad only against last
 * week. The comparison is modelled as a type rather than returned as a percentage because the
 * interesting case is the one a number cannot express: when the earlier period recorded nothing,
 * there is no percentage, and every shortcut for that case lies. Zero implies "no change", a dash
 * implies missing data, and a large positive number implies a comparison that was never made.
 * [NoBaseline] says the only true thing, and the UI can then decline to draw anything.
 */
sealed interface PeriodChange {
    /** The earlier period recorded nothing, so there is nothing to compare against. */
    data object NoBaseline : PeriodChange

    /**
     * A signed percentage change against the earlier period. Zero means genuinely unchanged, which
     * is information — it is not the same as [NoBaseline].
     */
    data class Changed(
        val percent: Int,
    ) : PeriodChange

    companion object {
        /**
         * Compares two totals in the same unit.
         *
         * Rounds to nearest rather than truncating: a rise from 100 to 199 is +99%, and reporting
         * it as +99% rather than +99 after truncation matters less than the principle that the
         * displayed number should be the closest true one.
         *
         * Negative inputs are treated as no baseline rather than producing a sign-flipped
         * percentage; no caller can produce them today — playtime and counts are non-negative — and
         * inventing a meaning for one would only hide the bug that supplied it.
         */
        fun between(
            previous: Long,
            current: Long,
        ): PeriodChange {
            if (previous <= 0L || current < 0L) return NoBaseline

            val ratio = (current - previous).toDouble() / previous.toDouble()
            return Changed((ratio * PERCENT).roundToInt())
        }

        private const val PERCENT = 100
    }
}
