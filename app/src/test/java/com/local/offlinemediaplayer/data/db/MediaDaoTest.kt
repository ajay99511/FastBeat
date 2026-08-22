package com.local.offlinemediaplayer.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for [MediaDao] against a real (in-memory) Room database.
 *
 * These run on the JVM under Robolectric rather than on a device, so they execute as part of
 * `testDebugUnitTest` and therefore inside CI. They are written as JUnit 4 rather than Kotest
 * because Robolectric is a JUnit 4 runner and `kotest-extensions-robolectric` cannot drive it
 * under Kotest 5 — see the comment in app/build.gradle.kts.
 *
 * The point of testing the DAO rather than mocking it: most of the behaviour here lives in SQL
 * string literals, which the Kotlin compiler cannot check. A typo in a WHERE clause is invisible
 * until it silently returns the wrong rows in production.
 */
@RunWith(RobolectricTestRunner::class)
class MediaDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaDao

    private companion object {
        const val DAY_MS = 86_400_000L

        /** `getActiveDays` counts a day as "active" only above this many ms. */
        const val ACTIVE_DAY_THRESHOLD_MS = 60_000L
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.mediaDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun history(
        mediaId: Long,
        position: Long = 1_000,
        duration: Long = 10_000,
        timestamp: Long = 1_000,
        mediaType: String = "VIDEO",
        audioTrackIndex: Int = -1,
        subtitleTrackIndex: Int = -1,
    ) = PlaybackHistory(
        mediaId,
        position,
        duration,
        timestamp,
        mediaType,
        audioTrackIndex,
        subtitleTrackIndex,
    )

    // ---------------------------------------------------------------- playback_history

    @Test
    fun saveHistory_roundTripsEveryField() =
        runBlocking {
            dao.saveHistory(
                history(
                    mediaId = 1,
                    position = 4_200,
                    duration = 60_000,
                    timestamp = 999,
                    mediaType = "AUDIO",
                    audioTrackIndex = 2,
                    subtitleTrackIndex = 3,
                ),
            )

            val row = dao.getHistory(1)!!
            assertEquals(1L, row.mediaId)
            assertEquals(4_200L, row.position)
            assertEquals(60_000L, row.duration)
            assertEquals(999L, row.timestamp)
            assertEquals("AUDIO", row.mediaType)
            assertEquals(2, row.audioTrackIndex)
            assertEquals(3, row.subtitleTrackIndex)
        }

    @Test
    fun saveHistory_replacesRowWithSameMediaId() =
        runBlocking {
            dao.saveHistory(history(mediaId = 1, position = 100))
            dao.saveHistory(history(mediaId = 1, position = 500))

            assertEquals(500L, dao.getHistory(1)!!.position)
        }

    @Test
    fun getHistory_returnsNullForUnknownMediaId() =
        runBlocking {
            assertNull(dao.getHistory(404))
        }

    /**
     * `updateHistoryPosition` deliberately writes only three columns. If it ever became a full
     * upsert it would wipe the user's saved audio/subtitle track selections on every position
     * save — which is exactly the guarantee `PlaybackService.onTaskRemoved` relies on.
     */
    @Test
    fun updateHistoryPosition_leavesMediaTypeAndTrackSelectionsIntact() =
        runBlocking {
            dao.saveHistory(
                history(
                    mediaId = 1,
                    position = 10,
                    duration = 20,
                    timestamp = 1,
                    mediaType = "VIDEO",
                    audioTrackIndex = 5,
                    subtitleTrackIndex = 7,
                ),
            )

            dao.updateHistoryPosition(mediaId = 1, position = 900, duration = 1_800, timestamp = 42)

            val row = dao.getHistory(1)!!
            assertEquals(900L, row.position)
            assertEquals(1_800L, row.duration)
            assertEquals(42L, row.timestamp)
            assertEquals("VIDEO", row.mediaType)
            assertEquals("audio track selection must survive a position update", 5, row.audioTrackIndex)
            assertEquals("subtitle selection must survive a position update", 7, row.subtitleTrackIndex)
        }

    @Test
    fun getLastPlayed_returnsTheMostRecentRowByTimestamp() =
        runBlocking {
            dao.saveHistory(history(mediaId = 1, timestamp = 100))
            dao.saveHistory(history(mediaId = 2, timestamp = 300))
            dao.saveHistory(history(mediaId = 3, timestamp = 200))

            assertEquals(2L, dao.getLastPlayed()!!.mediaId)
        }

    @Test
    fun getLastPlayedAudio_ignoresVideoRows() =
        runBlocking {
            dao.saveHistory(history(mediaId = 1, timestamp = 500, mediaType = "VIDEO"))
            dao.saveHistory(history(mediaId = 2, timestamp = 100, mediaType = "AUDIO"))

            assertEquals(2L, dao.getLastPlayedAudio()!!.mediaId)
        }

    // ------------------------------------------------------ getContinueWatching predicate
    // The query is: mediaType='VIDEO' AND position>0 AND (duration=0 OR position<duration*0.95)
    // Each clause gets its own test so a failure names the broken condition.

    @Test
    fun getContinueWatching_excludesAudio() =
        runBlocking {
            dao.saveHistory(history(mediaId = 1, position = 100, duration = 10_000, mediaType = "AUDIO"))

            assertTrue(dao.getContinueWatching().first().isEmpty())
        }

    @Test
    fun getContinueWatching_excludesUnstartedVideo() =
        runBlocking {
            dao.saveHistory(history(mediaId = 1, position = 0, duration = 10_000))

            assertTrue(dao.getContinueWatching().first().isEmpty())
        }

    @Test
    fun getContinueWatching_excludesVideoWatchedPast95Percent() =
        runBlocking {
            dao.saveHistory(history(mediaId = 1, position = 9_500, duration = 10_000))

            assertTrue(
                "a video at exactly 95% is finished, not resumable",
                dao.getContinueWatching().first().isEmpty(),
            )
        }

    @Test
    fun getContinueWatching_includesPartiallyWatchedVideo() =
        runBlocking {
            dao.saveHistory(history(mediaId = 1, position = 5_000, duration = 10_000))

            assertEquals(listOf(1L), dao.getContinueWatching().first().map { it.mediaId })
        }

    @Test
    fun getContinueWatching_includesVideoWithUnknownDuration() =
        runBlocking {
            dao.saveHistory(history(mediaId = 1, position = 5_000, duration = 0))

            assertEquals(
                "duration=0 means 'not yet known', which must not exclude the row",
                listOf(1L),
                dao.getContinueWatching().first().map { it.mediaId },
            )
        }

    @Test
    fun getContinueWatching_ordersMostRecentFirst() =
        runBlocking {
            dao.saveHistory(history(mediaId = 1, position = 100, timestamp = 100))
            dao.saveHistory(history(mediaId = 2, position = 100, timestamp = 300))
            dao.saveHistory(history(mediaId = 3, position = 100, timestamp = 200))

            assertEquals(listOf(2L, 3L, 1L), dao.getContinueWatching().first().map { it.mediaId })
        }

    // ---------------------------------------------------------------- media_analytics

    @Test
    fun initAnalytics_doesNotOverwriteAnExistingRow() =
        runBlocking {
            dao.initAnalytics(mediaId = 1, timestamp = 10)
            dao.incrementPlayCount(mediaId = 1, timestamp = 20)

            dao.initAnalytics(mediaId = 1, timestamp = 30)

            assertEquals(
                "INSERT OR IGNORE must not reset an accumulated play count",
                1,
                dao.getAnalytics(1)!!.playCount,
            )
        }

    @Test
    fun incrementPlayCount_accumulatesAndRecordsLastPlayed() =
        runBlocking {
            dao.initAnalytics(mediaId = 1, timestamp = 10)
            dao.incrementPlayCount(mediaId = 1, timestamp = 20)
            dao.incrementPlayCount(mediaId = 1, timestamp = 30)

            val analytics = dao.getAnalytics(1)!!
            assertEquals(2, analytics.playCount)
            assertEquals(30L, analytics.lastPlayed)
            assertEquals("play and skip counts are independent", 0, analytics.skipCount)
        }

    @Test
    fun incrementSkipCount_doesNotTouchPlayCount() =
        runBlocking {
            dao.initAnalytics(mediaId = 1, timestamp = 10)
            dao.incrementPlayCount(mediaId = 1, timestamp = 20)

            dao.incrementSkipCount(mediaId = 1)

            val analytics = dao.getAnalytics(1)!!
            assertEquals(1, analytics.playCount)
            assertEquals(1, analytics.skipCount)
        }

    // ---------------------------------------------------------------- queue + playlists

    @Test
    fun replaceQueue_swapsTheWholeQueueRatherThanMerging() =
        runBlocking {
            dao.replaceQueue(listOf(QueueItemEntity(1, 0), QueueItemEntity(2, 1)))
            dao.replaceQueue(listOf(QueueItemEntity(9, 0)))

            assertEquals(listOf(9L), dao.getSavedQueue().map { it.mediaId })
        }

    @Test
    fun replaceQueue_preservesSortOrder() =
        runBlocking {
            dao.replaceQueue(
                listOf(QueueItemEntity(30, 2), QueueItemEntity(10, 0), QueueItemEntity(20, 1)),
            )

            assertEquals(listOf(10L, 20L, 30L), dao.getSavedQueue().map { it.mediaId })
        }

    @Test
    fun replacePlaylistMedia_onlyAffectsTheTargetPlaylist() =
        runBlocking {
            dao.insertPlaylist(PlaylistEntity("a", "A", createdAt = 1, isVideo = false))
            dao.insertPlaylist(PlaylistEntity("b", "B", createdAt = 2, isVideo = false))
            dao.replacePlaylistMedia("a", listOf(PlaylistMediaCrossRef("a", 1, 1)))
            dao.replacePlaylistMedia("b", listOf(PlaylistMediaCrossRef("b", 2, 1)))

            dao.replacePlaylistMedia("a", listOf(PlaylistMediaCrossRef("a", 7, 1)))

            assertEquals(listOf(7L), dao.getMediaIdsForPlaylist("a"))
            assertEquals("playlist b must be untouched", listOf(2L), dao.getMediaIdsForPlaylist("b"))
        }

    @Test
    fun getMediaIdsForPlaylist_ordersByAddedAt() =
        runBlocking {
            dao.insertPlaylist(PlaylistEntity("a", "A", createdAt = 1, isVideo = false))
            dao.replacePlaylistMedia(
                "a",
                listOf(
                    PlaylistMediaCrossRef("a", 3, addedAt = 300),
                    PlaylistMediaCrossRef("a", 1, addedAt = 100),
                    PlaylistMediaCrossRef("a", 2, addedAt = 200),
                ),
            )

            assertEquals(listOf(1L, 2L, 3L), dao.getMediaIdsForPlaylist("a"))
        }

    @Test
    fun deletingPlaylist_cascadesToItsCrossRefs() =
        runBlocking {
            dao.insertPlaylist(PlaylistEntity("a", "A", createdAt = 1, isVideo = false))
            dao.addMediaToPlaylist(PlaylistMediaCrossRef("a", 1, 1))

            dao.deletePlaylist("a")

            assertTrue(
                "cross-refs must not outlive their playlist",
                dao.getMediaIdsForPlaylist("a").isEmpty(),
            )
        }

    // ---------------------------------------------------------------- daily_playtime

    @Test
    fun addToDailyPlaytime_accumulatesWithinTheSameDay() =
        runBlocking {
            dao.initDailyPlaytime(DAY_MS)
            dao.addToDailyPlaytime(DAY_MS, 5_000)
            dao.addToDailyPlaytime(DAY_MS, 7_000)

            assertEquals(12_000L, dao.getPlaytimeForDay(DAY_MS).first())
        }

    @Test
    fun getActiveDays_dropsDaysUnderThresholdAndOrdersNewestFirst() =
        runBlocking {
            // Just under the threshold — must be excluded.
            dao.initDailyPlaytime(1 * DAY_MS)
            dao.addToDailyPlaytime(1 * DAY_MS, ACTIVE_DAY_THRESHOLD_MS)
            // Comfortably over — must be included.
            dao.initDailyPlaytime(2 * DAY_MS)
            dao.addToDailyPlaytime(2 * DAY_MS, ACTIVE_DAY_THRESHOLD_MS + 1)
            dao.initDailyPlaytime(3 * DAY_MS)
            dao.addToDailyPlaytime(3 * DAY_MS, 10 * ACTIVE_DAY_THRESHOLD_MS)

            assertEquals(
                "exactly 60s is not active (query uses > not >=), and days come back newest first",
                listOf(3 * DAY_MS, 2 * DAY_MS),
                dao.getActiveDays().first(),
            )
        }

    // ---------------------------------------------------------------- deletion fan-out

    /**
     * `PlaylistRepository.cleanupDeletedMedia` calls these six DAO deletes together when a file
     * disappears from the device. If any one of them misses, the app keeps referencing media that
     * no longer exists. Covered here at the DAO level; the composition itself belongs to P4-B.
     */
    @Test
    fun deletionQueries_removeTheMediaFromEveryTableThatReferencesIt() =
        runBlocking {
            val gone = 1L
            val kept = 2L

            dao.insertPlaylist(PlaylistEntity("a", "A", createdAt = 1, isVideo = false))
            listOf(gone, kept).forEach { id ->
                dao.saveHistory(history(mediaId = id))
                dao.initAnalytics(id, timestamp = 1)
                dao.addMediaToPlaylist(PlaylistMediaCrossRef("a", id, 1))
                dao.addBookmark(BookmarkEntity(mediaId = id, timestamp = 1, label = "b"))
                dao.logPlayEvent(PlayEvent(mediaId = id, timestamp = 1))
            }
            dao.replaceQueue(listOf(QueueItemEntity(gone, 0), QueueItemEntity(kept, 1)))

            val ids = listOf(gone)
            dao.removeMediaFromAllPlaylists(ids)
            dao.removeMediaFromQueue(ids)
            dao.deleteAnalytics(ids)
            dao.deleteHistory(ids)
            dao.deleteBookmarksForMedia(ids)
            dao.deletePlayEvents(ids)

            assertNull("history", dao.getHistory(gone))
            assertNull("analytics", dao.getAnalytics(gone))
            assertEquals("playlist refs", listOf(kept), dao.getMediaIdsForPlaylist("a"))
            assertEquals("queue", listOf(kept), dao.getSavedQueue().map { it.mediaId })
            assertTrue("bookmarks", dao.getBookmarks(gone).first().isEmpty())

            // The surviving media must be entirely untouched.
            assertEquals(kept, dao.getHistory(kept)!!.mediaId)
            assertEquals(1, dao.getBookmarks(kept).first().size)
        }
}
