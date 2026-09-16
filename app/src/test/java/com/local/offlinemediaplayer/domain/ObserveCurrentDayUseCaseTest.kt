package com.local.offlinemediaplayer.domain

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The day ticker that every stats window is measured against.
 *
 * Virtual time is wired to the fake clock rather than run alongside it: `now` is defined as
 * `base + currentTime`, so advancing the test scheduler *is* advancing the wall clock. Without that
 * the two drift apart and the test would assert on a midnight the code never sees — the same class
 * of bug the production code had, reproduced in the test that was supposed to catch it.
 *
 * Plain JVM, UTC pinned, no Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCurrentDayUseCaseTest {
    private lateinit var originalZone: TimeZone
    private val useCase = ObserveCurrentDayUseCase()

    @Before
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Long {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute)
        return calendar.timeInMillis
    }

    /**
     * Advances virtual time — and therefore the fake clock — to just past [instant].
     *
     * The `+ 1` is not cosmetic: `advanceTimeBy` runs tasks scheduled *strictly before* the new
     * time, so advancing to exactly the wake-up instant leaves the ticker asleep and every
     * positive assertion here would fail for a reason that has nothing to do with the code.
     */
    private fun TestScope.advancePast(
        instant: Long,
        base: Long,
    ) = advanceTimeBy(instant - base - currentTime + 1)

    // ------------------------------------------------------------------ startOfToday

    @Test
    fun startOfTodayDropsTheTimeOfDay() {
        useCase.now = { at(2026, 9, 15, hour = 13, minute = 47) }

        assertEquals(at(2026, 9, 15), useCase.startOfToday())
    }

    @Test
    fun startOfTodayIsIdempotentOnAMidnight() {
        useCase.now = { at(2026, 9, 15) }

        assertEquals(at(2026, 9, 15), useCase.startOfToday())
    }

    /** One millisecond before midnight is still the old day — the off-by-one that decides a streak. */
    @Test
    fun theLastMillisecondOfADayBelongsToThatDay() {
        useCase.now = { at(2026, 9, 16) - 1 }

        assertEquals(at(2026, 9, 15), useCase.startOfToday())
    }

    // ------------------------------------------------------------------ the ticker

    @Test
    fun emitsTodayImmediately() =
        runTest {
            val base = at(2026, 9, 15, hour = 13, minute = 47)
            useCase.now = { base + currentTime }

            useCase().test {
                assertEquals(at(2026, 9, 15), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The regression this task exists for. A screen that stays subscribed across midnight must be
     * told, or it keeps crediting today's minutes to yesterday's bucket.
     */
    @Test
    fun emitsTheNewDayOnceMidnightPasses() =
        runTest {
            val base = at(2026, 9, 15, hour = 13, minute = 47)
            useCase.now = { base + currentTime }

            useCase().test {
                assertEquals(at(2026, 9, 15), awaitItem())

                advancePast(at(2026, 9, 16), base)

                assertEquals(at(2026, 9, 16), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The other half of that guarantee, and the one a coarse "just poll every hour" fix would fail:
     * nothing re-emits while the day has not changed, so no downstream query re-runs for nothing.
     */
    @Test
    fun doesNotEmitAgainBeforeMidnight() =
        runTest {
            val base = at(2026, 9, 15, hour = 13, minute = 47)
            useCase.now = { base + currentTime }
            val untilMidnight = at(2026, 9, 16) - base

            useCase().test {
                assertEquals(at(2026, 9, 15), awaitItem())

                advanceTimeBy(untilMidnight - 1)

                // Virtual time is now one millisecond short of the boundary, so the ticker is
                // still asleep because the day genuinely has not turned over — not because the
                // scheduler happened not to run it.
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun keepsTickingDayAfterDay() =
        runTest {
            val base = at(2026, 9, 15, hour = 23, minute = 59)
            useCase.now = { base + currentTime }

            useCase().test {
                assertEquals(at(2026, 9, 15), awaitItem())

                advancePast(at(2026, 9, 16), base)
                assertEquals(at(2026, 9, 16), awaitItem())

                advancePast(at(2026, 9, 17), base)
                assertEquals(at(2026, 9, 17), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * A subscriber that arrives exactly at midnight gets the new day, not the one that just ended.
     * Guards the boundary condition in both directions at once: `startOfToday` must be inclusive of
     * its own first millisecond.
     */
    @Test
    fun aSubscriptionOpenedAtMidnightSeesTheNewDay() =
        runTest {
            val base = at(2026, 9, 16)
            useCase.now = { base + currentTime }

            useCase().test {
                assertEquals(at(2026, 9, 16), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
