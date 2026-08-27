package com.local.offlinemediaplayer.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.local.offlinemediaplayer.data.db.AppDatabase
import com.local.offlinemediaplayer.data.db.MediaDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [LogPlayEventUseCase] against a real in-memory Room database, because the thing worth checking is
 * what actually lands in the tables — three writes that have to agree with each other.
 */
@RunWith(RobolectricTestRunner::class)
class LogPlayEventUseCaseTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaDao
    private lateinit var logPlayEvent: LogPlayEventUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.mediaDao()
        logPlayEvent = LogPlayEventUseCase(dao)
    }

    @After
    fun tearDown() = db.close()

    /**
     * `play_events` has no read query in [MediaDao] — production only ever aggregates it — so the
     * test reads the table directly rather than adding a production method that nothing ships uses.
     */
    private fun playEvents(mediaId: Long): List<Long> =
        db.openHelper.readableDatabase
            .query("SELECT timestamp FROM play_events WHERE mediaId = $mediaId ORDER BY timestamp")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(0))
                }
            }

    @Test
    fun recordsTheAggregateAndTheIndividualEvent() =
        runBlocking {
            logPlayEvent(mediaId = 7, now = NOW)

            val analytics = dao.getAnalytics(7)!!
            assertEquals(1, analytics.playCount)
            assertEquals(NOW, analytics.lastPlayed)
            assertEquals(1, playEvents(7).size)
        }

    /**
     * The reason [LogPlayEventUseCase] takes `now` instead of calling the clock three times. Smart
     * playlists sort on `lastPlayed` while the activity chart aggregates `PlayEvent.timestamp`; if
     * those two were read separately either side of midnight, a play would be filed under one day
     * and reported as most-recent on another.
     */
    @Test
    fun theAggregateAndTheEventShareOneTimestamp() =
        runBlocking {
            logPlayEvent(mediaId = 7, now = NOW)

            val analytics = dao.getAnalytics(7)!!
            assertEquals(analytics.lastPlayed, playEvents(7).single())
        }

    @Test
    fun repeatedPlaysAccumulateTheCountAndAdvanceLastPlayed() =
        runBlocking {
            logPlayEvent(mediaId = 7, now = NOW)
            logPlayEvent(mediaId = 7, now = NOW + 60_000)
            logPlayEvent(mediaId = 7, now = NOW + 120_000)

            val analytics = dao.getAnalytics(7)!!
            assertEquals(3, analytics.playCount)
            assertEquals(NOW + 120_000, analytics.lastPlayed)
        }

    /** Every play is its own event — the chart counts them, so they must not be collapsed. */
    @Test
    fun everyPlayIsRecordedAsASeparateEvent() =
        runBlocking {
            repeat(3) { logPlayEvent(mediaId = 7, now = NOW + it * 1_000L) }

            assertEquals(3, playEvents(7).size)
        }

    @Test
    fun tracksAreCountedIndependently() =
        runBlocking {
            logPlayEvent(mediaId = 7, now = NOW)
            logPlayEvent(mediaId = 8, now = NOW)
            logPlayEvent(mediaId = 7, now = NOW + 1_000)

            assertEquals(2, dao.getAnalytics(7)!!.playCount)
            assertEquals(1, dao.getAnalytics(8)!!.playCount)
        }

    /**
     * `initAnalytics` must not wipe an existing row. If it did, playing a track a second time would
     * reset its play count to one and the all-time favourite would never rise above it.
     */
    @Test
    fun anExistingAnalyticsRowIsNotResetByALaterPlay() =
        runBlocking {
            logPlayEvent(mediaId = 7, now = NOW)
            val firstPlayCount = dao.getAnalytics(7)!!.playCount

            logPlayEvent(mediaId = 7, now = NOW + 5_000)

            assertEquals(1, firstPlayCount)
            assertEquals(2, dao.getAnalytics(7)!!.playCount)
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
