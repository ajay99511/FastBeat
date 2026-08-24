package com.local.offlinemediaplayer.playback

import android.net.Uri
import com.local.offlinemediaplayer.data.db.PlaybackHistory
import com.local.offlinemediaplayer.data.db.QueueItemEntity
import com.local.offlinemediaplayer.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterization tests for the queue persistence rules, written to pin existing behaviour before
 * the rest of `PlaybackViewModel`'s queue state is touched (OQ-9, option (a)).
 *
 * These are not aspirational — every assertion describes what the app already does. If one of them
 * ever fails during the remaining P4-E work, the refactor changed behaviour and is wrong, which is
 * precisely the signal that was missing when P4-E.4 was first attempted.
 *
 * Robolectric is present only to supply `android.net.Uri` for `MediaFile`; the logic under test is
 * pure. See F-23.
 */
@RunWith(RobolectricTestRunner::class)
class QueuePolicyTest {
    private fun audio(id: Long) = media(id, isVideo = false)

    private fun video(id: Long) = media(id, isVideo = true)

    private fun media(
        id: Long,
        isVideo: Boolean,
    ) = MediaFile(
        id = id,
        uri = Uri.EMPTY,
        title = "t$id",
        duration = 1_000,
        isVideo = isVideo,
    )

    private fun history(
        position: Long,
        duration: Long,
    ) = PlaybackHistory(
        mediaId = 1,
        position = position,
        duration = duration,
        timestamp = 0,
        mediaType = "AUDIO",
    )

    private fun rows(vararg ids: Long) = ids.mapIndexed { i, id -> QueueItemEntity(id, i) }

    // ------------------------------------------------------------------ persistence guard

    @Test
    fun anAllAudioQueueIsPersistable() {
        assertTrue(QueuePolicy.isPersistable(listOf(audio(1), audio(2))))
    }

    @Test
    fun anEmptyQueueIsPersistable() {
        assertTrue("clearing the queue must be able to clear storage too", QueuePolicy.isPersistable(emptyList()))
    }

    @Test
    fun aQueueContainingAVideoIsNotPersistable() {
        assertFalse(QueuePolicy.isPersistable(listOf(video(1))))
    }

    /**
     * The guard is whole-queue, not per-item. A single video poisons the whole write — otherwise
     * playing a video would silently truncate the saved music session instead of leaving it alone.
     */
    @Test
    fun aMixedQueueIsRejectedEntirelyRatherThanPartiallySaved() {
        assertFalse(QueuePolicy.isPersistable(listOf(audio(1), video(2), audio(3))))
    }

    // ------------------------------------------------------------------ mapping to storage

    @Test
    fun listPositionBecomesSortOrder() {
        val entities = QueuePolicy.toEntities(listOf(audio(30), audio(10), audio(20)))

        assertEquals(listOf(30L, 10L, 20L), entities.map { it.mediaId })
        assertEquals(
            "sortOrder must record list position, not media id",
            listOf(0, 1, 2),
            entities.map { it.sortOrder },
        )
    }

    @Test
    fun anEmptyQueueMapsToNoRows() {
        assertTrue(QueuePolicy.toEntities(emptyList()).isEmpty())
    }

    // ------------------------------------------------------------------ restore

    @Test
    fun restorePreservesStoredOrder() {
        val restored = QueuePolicy.restore(rows(3, 1, 2), mapOf(1L to audio(1), 2L to audio(2), 3L to audio(3)), 0)!!

        assertEquals(listOf(3L, 1L, 2L), restored.queue.map { it.id })
    }

    @Test
    fun restoreDropsMediaThatNoLongerExists() {
        // Files get deleted off the device between sessions; a missing row is normal, not an error.
        val restored = QueuePolicy.restore(rows(1, 99, 2), mapOf(1L to audio(1), 2L to audio(2)), 0)!!

        assertEquals(listOf(1L, 2L), restored.queue.map { it.id })
    }

    @Test
    fun restoreFiltersOutVideosThatReachedStorage() {
        val restored = QueuePolicy.restore(rows(1, 2), mapOf(1L to audio(1), 2L to video(2)), 0)!!

        assertEquals(
            "the audio-only contract is defended on read as well as write",
            listOf(1L),
            restored.queue.map { it.id },
        )
    }

    @Test
    fun restoreKeepsAValidIndex() {
        val restored = QueuePolicy.restore(rows(1, 2, 3), mapOf(1L to audio(1), 2L to audio(2), 3L to audio(3)), 2)!!

        assertEquals(2, restored.index)
    }

    @Test
    fun restoreClampsAnIndexLeftOverFromALongerQueue() {
        // A stale "last_queue_index" must resume at the nearest valid track, never crash.
        val restored = QueuePolicy.restore(rows(1, 2), mapOf(1L to audio(1), 2L to audio(2)), 47)!!

        assertEquals(1, restored.index)
    }

    @Test
    fun restoreClampsANegativeIndex() {
        val restored = QueuePolicy.restore(rows(1, 2), mapOf(1L to audio(1), 2L to audio(2)), -5)!!

        assertEquals(0, restored.index)
    }

    @Test
    fun restoreClampsWhenFilteringShrankTheQueueBelowTheSavedIndex() {
        // Three rows saved, index 2; two of them are now gone, so index 2 no longer exists.
        val restored = QueuePolicy.restore(rows(1, 2, 3), mapOf(1L to audio(1)), 2)!!

        assertEquals(listOf(1L), restored.queue.map { it.id })
        assertEquals(0, restored.index)
    }

    @Test
    fun restoreReturnsNullWhenStorageIsEmpty() {
        assertNull(QueuePolicy.restore(emptyList(), mapOf(1L to audio(1)), 0))
    }

    @Test
    fun restoreReturnsNullWhenNothingResolves() {
        assertNull("caller must fall back to the last played track", QueuePolicy.restore(rows(7, 8), emptyMap(), 0))
    }

    @Test
    fun restoreReturnsNullWhenEveryRowWasAVideo() {
        assertNull(QueuePolicy.restore(rows(1), mapOf(1L to video(1)), 0))
    }

    // ------------------------------------------------------------------ resume position

    @Test
    fun aPartiallyPlayedTrackResumesWhereItStopped() {
        assertEquals(4_000L, QueuePolicy.resumePosition(history(position = 4_000, duration = 10_000)))
    }

    @Test
    fun aTrackPlayedPast99PercentRestartsFromTheBeginning() {
        // Otherwise resuming would drop the user one second from the end of a finished track.
        assertEquals(0L, QueuePolicy.resumePosition(history(position = 9_950, duration = 10_000)))
    }

    @Test
    fun theCompletionBoundaryItselfCountsAsFinished() {
        assertEquals(0L, QueuePolicy.resumePosition(history(position = 9_900, duration = 10_000)))
    }

    @Test
    fun justBelowTheBoundaryStillResumes() {
        assertEquals(9_899L, QueuePolicy.resumePosition(history(position = 9_899, duration = 10_000)))
    }

    @Test
    fun anUnknownDurationResumesRatherThanBeingTreatedAsFinished() {
        // duration == 0 means "not yet known", not "zero-length track".
        assertEquals(1_234L, QueuePolicy.resumePosition(history(position = 1_234, duration = 0)))
    }

    @Test
    fun noHistoryMeansStartFromTheBeginning() {
        assertEquals(0L, QueuePolicy.resumePosition(null))
    }
}
