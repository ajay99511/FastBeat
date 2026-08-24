package com.local.offlinemediaplayer.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.local.offlinemediaplayer.data.db.AppDatabase
import com.local.offlinemediaplayer.data.db.MediaDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/**
 * Tests for [PlaybackAnalyticsTracker] against a real in-memory Room database.
 *
 * These matter because the tracker decides what the user's listening statistics say, and it is
 * driven by a 500 ms loop that is impossible to reason about by hand: a play is recorded after a
 * threshold that itself varies with track length, and the accumulator has to be reset at exactly
 * the right moment or listening to one track silently credits the next one.
 *
 * DETERMINISM — the tracker writes fire-and-forget by design (its caller must never block), so both
 * the coroutine dispatcher and Room's executors are made synchronous here. Without that, every
 * assertion would race the write it is checking.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackAnalyticsTrackerTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaDao
    private lateinit var tracker: PlaybackAnalyticsTracker

    private companion object {
        const val TICK = 500L
        const val LONG_TRACK = 300_000L // 5 minutes: threshold is the flat 30 s
        const val PLAY_THRESHOLD = 30_000L
        const val DAY_MS = 86_400_000L
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
        dao = db.mediaDao()
        tracker = PlaybackAnalyticsTracker(dao)
        tracker.trackerScope = CoroutineScope(Dispatchers.Unconfined)
        tracker.onSessionStarted()
    }

    @After
    fun tearDown() = db.close()

    /** Feeds [totalMs] of playback to the tracker in 500 ms ticks, as the position loop does. */
    private fun play(
        mediaId: Long?,
        durationMs: Long,
        totalMs: Long,
    ) {
        repeat((totalMs / TICK).toInt()) {
            tracker.onPositionUpdate(mediaId, durationMs, TICK)
        }
    }

    private fun playCount(mediaId: Long): Int = runBlocking { dao.getAnalytics(mediaId)?.playCount ?: 0 }

    private fun skipCount(mediaId: Long): Int = runBlocking { dao.getAnalytics(mediaId)?.skipCount ?: 0 }

    private fun today(): Long =
        Calendar
            .getInstance()
            .apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

    private fun playtimeToday(): Long = runBlocking { dao.getPlaytimeForDay(today()).first() ?: 0L }

    // ------------------------------------------------------------------ the play threshold

    @Test
    fun aTrackIsNotCountedBeforeTheThreshold() {
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD - TICK)

        assertEquals(0, playCount(1))
    }

    @Test
    fun aTrackIsCountedOnceItReachesTheThreshold() {
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD)

        assertEquals(1, playCount(1))
    }

    @Test
    fun aShortTrackUsesHalfItsLengthAsTheThreshold() {
        // 20 s track -> min(30 s, 10 s) = 10 s, which is above the 5 s floor.
        play(mediaId = 1, durationMs = 20_000, totalMs = 9_500)
        assertEquals("still below half the track", 0, playCount(1))

        play(mediaId = 1, durationMs = 20_000, totalMs = TICK)
        assertEquals(1, playCount(1))
    }

    @Test
    fun aVeryShortClipStillNeedsFiveSeconds() {
        // 4 s clip -> min(30 s, 2 s) = 2 s, floored to 5 s so a clip cannot count almost instantly.
        play(mediaId = 1, durationMs = 4_000, totalMs = 4_500)
        assertEquals("the 5 s floor must beat half of a 4 s clip", 0, playCount(1))

        play(mediaId = 1, durationMs = 4_000, totalMs = TICK)
        assertEquals(1, playCount(1))
    }

    @Test
    fun anUnknownDurationFallsBackToTheFlatThreshold() {
        // duration 0 means "not yet known" — it must not be read as a zero-length track.
        play(mediaId = 1, durationMs = 0, totalMs = PLAY_THRESHOLD - TICK)
        assertEquals(0, playCount(1))

        play(mediaId = 1, durationMs = 0, totalMs = TICK)
        assertEquals(1, playCount(1))
    }

    @Test
    fun aTrackIsCountedOnlyOnceNoMatterHowLongItKeepsPlaying() {
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD * 4)

        assertEquals(1, playCount(1))
    }

    // ------------------------------------------------------------------ THE key invariant

    /**
     * The card's stated invariant: no double-counting across track transitions. Without the reset,
     * 25 s already banked against track 1 would carry over and credit track 2 a play after only 5 s
     * that the user never gave it.
     */
    @Test
    fun listeningTimeDoesNotCarryOverToTheNextTrack() {
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = 25_000)
        assertEquals("track 1 never reached the threshold", 0, playCount(1))

        tracker.onTrackChanged()
        play(mediaId = 2, durationMs = LONG_TRACK, totalMs = 10_000)

        assertEquals("track 2 must start from zero, not inherit track 1's 25 s", 0, playCount(2))
    }

    @Test
    fun theNextTrackStillCountsOnceItEarnsTheThresholdItself() {
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = 25_000)
        tracker.onTrackChanged()

        play(mediaId = 2, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD)

        assertEquals(1, playCount(2))
        assertEquals("track 1 must remain uncounted", 0, playCount(1))
    }

    @Test
    fun aTrackPlayedTwiceInOneSessionCountsTwice() {
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD)
        tracker.onTrackChanged()
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD)

        assertEquals(2, playCount(1))
    }

    @Test
    fun isCurrentTrackUnloggedTracksTheThreshold() {
        assertTrue("nothing logged yet", tracker.isCurrentTrackUnlogged)

        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD)
        assertFalse("logged once the threshold is met", tracker.isCurrentTrackUnlogged)

        tracker.onTrackChanged()
        assertTrue("a new track starts unlogged again", tracker.isCurrentTrackUnlogged)
    }

    // ------------------------------------------------------------------ what a play writes

    @Test
    fun recordingAPlayAlsoLogsAPlayEventForRecentFavourites() {
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD)

        val mostPlayed = runBlocking { dao.getMostPlayedMediaIdSinceFlow(0).first() }
        assertEquals(1L, mostPlayed)
    }

    // ------------------------------------------------------------------ skips

    @Test
    fun recordingASkipIncrementsTheSkipCount() {
        tracker.recordSkip(1)

        assertEquals(1, skipCount(1))
    }

    @Test
    fun aSkipCreatesTheAnalyticsRowIfTheTrackWasNeverPlayed() {
        assertNull(runBlocking { dao.getAnalytics(1) })

        tracker.recordSkip(1)

        assertEquals(1, skipCount(1))
        assertEquals("a skip is not a play", 0, playCount(1))
    }

    @Test
    fun aSkipDoesNotDiscardAnEarlierPlay() {
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD)

        tracker.recordSkip(1)

        assertEquals(1, playCount(1))
        assertEquals(1, skipCount(1))
    }

    // ------------------------------------------------------------------ daily playtime

    @Test
    fun theSessionCreatesTodaysPlaytimeRow() {
        assertEquals(0L, playtimeToday())
    }

    @Test
    fun playtimeAccruesAndIsFlushed() {
        // The first tick of a session flushes immediately, then every 60th tick (30 s).
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD * 2)

        assertTrue("expected accrued playtime, got ${playtimeToday()}", playtimeToday() > 0)
    }

    @Test
    fun playtimeAccruesEvenWhenTheTrackIsUnknown() {
        // The original loop credited time whenever playback was playing, resolved track or not.
        play(mediaId = null, durationMs = 0, totalMs = PLAY_THRESHOLD * 2)

        assertTrue(playtimeToday() > 0)
    }

    @Test
    fun noPlayIsRecordedWithoutAMediaId() {
        play(mediaId = null, durationMs = LONG_TRACK, totalMs = PLAY_THRESHOLD * 2)

        assertEquals("nothing to attribute a play to", 0, playCount(0))
    }

    // ------------------------------------------------------------------ F-34: flush on stop

    /**
     * Up to one flush interval (30 s) of listening time used to be discarded every time playback
     * stopped: the accumulator lives in memory and the position loop that owned it was cancelled.
     * A user pausing every minute lost most of their recorded listening time.
     */
    @Test
    fun stoppingASessionPersistsTimeAccruedSinceTheLastFlush() {
        // 10 s is well short of the 30 s flush cadence, so before the fix this was simply lost.
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = 10_000)
        val beforeStop = playtimeToday()

        tracker.onSessionStopped()

        val afterStop = playtimeToday()
        assertTrue(
            "expected accrued time to be persisted on stop, before=$beforeStop after=$afterStop",
            afterStop > beforeStop,
        )
    }

    @Test
    fun stoppingTwiceDoesNotDoubleCountTheSameTime() {
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = 10_000)

        tracker.onSessionStopped()
        val afterFirst = playtimeToday()
        tracker.onSessionStopped()

        assertEquals("a second stop has nothing left to flush", afterFirst, playtimeToday())
    }

    @Test
    fun stoppingWithNothingAccruedIsHarmless() {
        tracker.onSessionStopped()

        assertEquals(0L, playtimeToday())
    }

    // ------------------------------------------------------------------ F-30: midnight rollover

    /**
     * Playtime used to be credited to whichever day the session *started* on, so an evening session
     * running past midnight booked all of its time to the previous day. Time is now attributed to
     * the day it was actually listened in, checked at flush points.
     */
    @Test
    fun timeListenedAfterMidnightIsCreditedToTheNewDay() {
        val startOfToday = today()
        val justBeforeMidnight = startOfToday + DAY_MS - 60_000L
        tracker.now = { justBeforeMidnight }
        tracker.onSessionStarted()

        // Listen for a while on the old day.
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = 10_000)
        tracker.onSessionStopped()
        val oldDayTotal = runBlocking { dao.getPlaytimeForDay(startOfToday).first() ?: 0L }
        assertTrue("time before midnight belongs to the old day", oldDayTotal > 0)

        // Clock crosses midnight; keep listening.
        val afterMidnight = startOfToday + DAY_MS + 60_000L
        tracker.now = { afterMidnight }
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = 10_000)
        tracker.onSessionStopped()

        val newDayTotal = runBlocking { dao.getPlaytimeForDay(startOfToday + DAY_MS).first() ?: 0L }
        assertTrue(
            "time after midnight must be credited to the new day, got $newDayTotal",
            newDayTotal > 0,
        )
        assertEquals(
            "the old day must not gain any further time",
            oldDayTotal,
            runBlocking { dao.getPlaytimeForDay(startOfToday).first() ?: 0L },
        )
    }

    @Test
    fun aSessionThatDoesNotCrossMidnightKeepsCreditingOneDay() {
        play(mediaId = 1, durationMs = LONG_TRACK, totalMs = 10_000)
        tracker.onSessionStopped()

        assertEquals(
            "no rollover should occur, so tomorrow stays empty",
            0L,
            runBlocking { dao.getPlaytimeForDay(today() + DAY_MS).first() ?: 0L },
        )
    }
}
