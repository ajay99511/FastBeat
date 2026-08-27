package com.local.offlinemediaplayer.playback

import android.net.Uri
import com.local.offlinemediaplayer.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Completes P4-G.2 — the half its card left open: "the four StateFlows, still untouched".
 *
 * `QueuePolicyTest` pins the *rules* (what may be persisted, how a saved queue is restored).
 * This pins the *state*: the relationship between the play order and the display order, which was
 * previously spread across sixteen assignment sites inside a 2 178-line ViewModel and could not be
 * asserted on at all.
 *
 * [QueueManager] itself has no coroutines, no Android framework types and no Room — that is the
 * point of having extracted it, and it is why every assertion below is a direct synchronous read.
 * Robolectric appears only to supply `android.net.Uri` for the `MediaFile` fixtures, exactly as in
 * `QueuePolicyTest` — see F-23.
 */
@RunWith(RobolectricTestRunner::class)
class QueueManagerTest {
    private val manager = QueueManager()

    private fun track(id: Long) = MediaFile(id = id, uri = Uri.EMPTY, title = "t$id", duration = 1_000, isVideo = false)

    private fun tracks(vararg ids: Long) = ids.map(::track)

    // ------------------------------------------------------------------ initial state

    @Test
    fun startsEmptyWithNoIndexAndNoContext() {
        assertTrue(manager.queue.isEmpty())
        assertTrue(manager.displayed.isEmpty())
        assertNull("nothing is loaded, so there is no position", manager.index)
        assertNull("an empty queue came from nowhere", manager.context)
    }

    // ------------------------------------------------------------------ play vs display order

    /**
     * The invariant that used to be implicit in seven pairs of adjacent assignments: outside
     * shuffle, what plays and what the queue sheet shows are the same list.
     */
    @Test
    fun setQueueMakesTheDisplayOrderMatchThePlayOrder() {
        manager.setQueue(tracks(3, 1, 2))

        assertEquals(listOf(3L, 1L, 2L), manager.queue.map { it.id })
        assertEquals(listOf(3L, 1L, 2L), manager.displayed.map { it.id })
    }

    /**
     * The counterpart invariant, and the reason [QueueManager.setPlayOrder] exists as a separate
     * operation. Under shuffle the display follows Media3's shuffled timeline, and the caller
     * recomputes it right after. If this method also wrote the display order, the queue sheet
     * would snap back to unshuffled order for a frame on every reorder and removal.
     */
    @Test
    fun setPlayOrderLeavesTheDisplayOrderAloneForTheCallerToRecompute() {
        manager.setQueue(tracks(1, 2, 3))
        manager.setDisplayOrder(tracks(3, 2, 1)) // as if shuffled

        manager.setPlayOrder(tracks(1, 2, 3, 4))

        assertEquals(listOf(1L, 2L, 3L, 4L), manager.queue.map { it.id })
        assertEquals(
            "the shuffled display order must survive a play-order change",
            listOf(3L, 2L, 1L),
            manager.displayed.map { it.id },
        )
    }

    @Test
    fun setDisplayOrderDoesNotDisturbThePlayOrder() {
        manager.setQueue(tracks(1, 2, 3))

        manager.setDisplayOrder(tracks(2, 3, 1))

        assertEquals(
            "the play order is authoritative and mirrors ExoPlayer",
            listOf(1L, 2L, 3L),
            manager.queue.map { it.id },
        )
        assertEquals(listOf(2L, 3L, 1L), manager.displayed.map { it.id })
    }

    /** A later [QueueManager.setQueue] re-synchronises the two, ending any shuffled divergence. */
    @Test
    fun setQueueResynchronisesAfterTheOrdersHaveDiverged() {
        manager.setQueue(tracks(1, 2, 3))
        manager.setDisplayOrder(tracks(3, 1, 2))

        manager.setQueue(tracks(7, 8))

        assertEquals(listOf(7L, 8L), manager.queue.map { it.id })
        assertEquals(listOf(7L, 8L), manager.displayed.map { it.id })
    }

    // ------------------------------------------------------------------ clearing

    /**
     * `clearQueue` empties the play order and deliberately leaves the index alone, because the
     * ViewModel clears the two independently and at different moments. Pinning it stops a future
     * "tidy-up" from folding the index reset in here and changing when the UI loses its position.
     */
    @Test
    fun clearQueueEmptiesThePlayOrderWithoutTouchingTheIndex() {
        manager.setQueue(tracks(1, 2))
        manager.setIndex(1)

        manager.clearQueue()

        assertTrue(manager.queue.isEmpty())
        assertEquals("the index is cleared separately, by its own call", 1, manager.index)
    }

    @Test
    fun clearQueueLeavesTheDisplayOrderForTheCallerToRefresh() {
        manager.setQueue(tracks(1, 2))

        manager.clearQueue()

        assertEquals(
            "clearing the play order does not itself repaint the queue sheet",
            listOf(1L, 2L),
            manager.displayed.map { it.id },
        )
    }

    // ------------------------------------------------------------------ index and context

    @Test
    fun indexRoundTripsIncludingBackToNull() {
        manager.setIndex(4)
        assertEquals(4, manager.index)

        manager.setIndex(null)
        assertNull("null means nothing is loaded, not position zero", manager.index)
    }

    @Test
    fun playlistContextRoundTripsIncludingBackToNull() {
        manager.setPlaylistContext("ALBUM_42")
        assertEquals("ALBUM_42", manager.context)

        manager.setPlaylistContext(null)
        assertNull("null is the Library case, which autoFill treats differently", manager.context)
    }

    // ------------------------------------------------------------------ flow exposure

    @Test
    fun theFlowsPublishTheSameValuesAsTheSnapshotAccessors() {
        manager.setQueue(tracks(5, 6))
        manager.setIndex(1)
        manager.setPlaylistContext("SMART_recent")

        assertEquals(manager.queue, manager.currentQueue.value)
        assertEquals(manager.displayed, manager.displayQueue.value)
        assertEquals(manager.index, manager.currentIndex.value)
        assertEquals(manager.context, manager.playlistContext.value)
    }

    /** Snapshots handed out must not alias later state — callers copy them into local variables. */
    @Test
    fun aPreviouslyReadSnapshotIsNotMutatedByALaterWrite() {
        manager.setQueue(tracks(1, 2))
        val snapshot = manager.queue

        manager.setQueue(tracks(9))

        assertEquals(listOf(1L, 2L), snapshot.map { it.id })
    }
}
