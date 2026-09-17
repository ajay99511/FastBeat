package com.local.offlinemediaplayer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The week-over-week comparison.
 *
 * Plain JVM arithmetic, but the arithmetic is not the point — the boundary between "compared" and
 * "not comparable" is. Every shortcut for an empty baseline produces a number the user will read as
 * a fact: 0% reads as *unchanged*, a large percentage reads as a comparison that was never made.
 * These tests pin the refusal to produce one.
 */
class PeriodChangeTest {
    // ------------------------------------------------------------------ no baseline

    @Test
    fun aPeriodWithNoHistoryBehindItIsNotComparable() {
        assertEquals(PeriodChange.NoBaseline, PeriodChange.between(previous = 0, current = 5_000))
    }

    @Test
    fun twoEmptyPeriodsAreNotComparableEither() {
        assertEquals(PeriodChange.NoBaseline, PeriodChange.between(previous = 0, current = 0))
    }

    /**
     * No caller can supply a negative today — playtime and counts are non-negative. Inventing a
     * meaning for one would hide whichever bug produced it, so it is reported as not comparable
     * rather than silently sign-flipped into a plausible-looking percentage.
     */
    @Test
    fun negativeInputsAreNotComparable() {
        assertEquals(PeriodChange.NoBaseline, PeriodChange.between(previous = -10, current = 100))
        assertEquals(PeriodChange.NoBaseline, PeriodChange.between(previous = 100, current = -10))
    }

    // ------------------------------------------------------------------ comparison

    @Test
    fun anUnchangedPeriodIsZeroPercentAndNotAMissingComparison() {
        assertEquals(PeriodChange.Changed(0), PeriodChange.between(previous = 4_000, current = 4_000))
    }

    @Test
    fun aDoubledPeriodIsPlusOneHundredPercent() {
        assertEquals(PeriodChange.Changed(100), PeriodChange.between(previous = 4_000, current = 8_000))
    }

    @Test
    fun aHalvedPeriodIsMinusFiftyPercent() {
        assertEquals(PeriodChange.Changed(-50), PeriodChange.between(previous = 8_000, current = 4_000))
    }

    @Test
    fun aPeriodThatFellToNothingIsMinusOneHundredPercent() {
        assertEquals(PeriodChange.Changed(-100), PeriodChange.between(previous = 8_000, current = 0))
    }

    // ------------------------------------------------------------------ rounding

    /** Rounds to nearest rather than truncating, so the shown number is the closest true one. */
    @Test
    fun roundsToTheNearestPercent() {
        // +16.66…% must not display as +16%.
        assertEquals(PeriodChange.Changed(17), PeriodChange.between(previous = 6_000, current = 7_000))
    }

    @Test
    fun roundsDownwardsWhenThatIsNearer() {
        // +14.28…%
        assertEquals(PeriodChange.Changed(14), PeriodChange.between(previous = 7_000, current = 8_000))
    }

    /**
     * A change too small to round to a whole percent reports as unchanged, which is honest: the
     * alternative is a chip that flickers between "no change" and "+1%" on a few seconds of
     * listening.
     */
    @Test
    fun aChangeBelowHalfAPercentReportsAsUnchanged() {
        assertEquals(PeriodChange.Changed(0), PeriodChange.between(previous = 1_000_000, current = 1_004_000))
    }

    // ------------------------------------------------------------------ scale

    /**
     * Large true values are returned as they are. Clamping belongs to the display layer, which knows
     * how much room a chip has; a domain type that quietly caps its own output would make the number
     * unusable for anything else.
     */
    @Test
    fun anEnormousRiseIsReportedTruthfully() {
        assertEquals(PeriodChange.Changed(8_900), PeriodChange.between(previous = 2_000, current = 180_000))
    }

    /** Realistic magnitudes: a full week of playtime in milliseconds does not overflow. */
    @Test
    fun aWeekOfMillisecondsDoesNotOverflow() {
        val busyWeek = 7L * 24 * 60 * 60 * 1000
        val quietWeek = busyWeek / 4

        assertEquals(PeriodChange.Changed(300), PeriodChange.between(quietWeek, busyWeek))
    }
}
