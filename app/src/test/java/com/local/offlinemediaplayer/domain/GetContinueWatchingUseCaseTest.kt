package com.local.offlinemediaplayer.domain

import android.net.Uri
import com.local.offlinemediaplayer.data.db.PlaybackHistory
import com.local.offlinemediaplayer.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The history-to-video join that P5-A.1 de-duplicated. Two screens were doing it by hand and
 * disagreeing about the duration fallback; these tests pin the single version they now share.
 *
 * Robolectric only supplies `android.net.Uri` for the `MediaFile` fixtures — see F-23.
 */
@RunWith(RobolectricTestRunner::class)
class GetContinueWatchingUseCaseTest {
    private val getContinueWatching = GetContinueWatchingUseCase()

    private fun video(
        id: Long,
        duration: Long = 100_000,
    ) = MediaFile(id = id, uri = Uri.EMPTY, title = "v$id", duration = duration, isVideo = true)

    private fun history(
        id: Long,
        position: Long = 10_000,
        duration: Long = 100_000,
    ) = PlaybackHistory(
        mediaId = id,
        position = position,
        duration = duration,
        timestamp = id,
        mediaType = "VIDEO",
    )

    @Test
    fun pairsHistoryWithItsVideo() {
        val result = getContinueWatching(listOf(video(1)), listOf(history(1, position = 25_000)))

        val item = result.single()
        assertEquals(1L, item.media.id)
        assertEquals(25_000L, item.position)
        assertEquals(100_000L, item.duration)
    }

    /**
     * Videos get deleted between sessions. A resume entry pointing at a file that is gone must
     * disappear rather than surface as a row that crashes or shows a blank tile when tapped.
     */
    @Test
    fun dropsHistoryWhoseVideoNoLongerExists() {
        val result = getContinueWatching(listOf(video(1)), listOf(history(1), history(404)))

        assertEquals(listOf(1L), result.map { it.media.id })
    }

    @Test
    fun returnsNothingWhenEveryVideoIsGone() {
        assertTrue(getContinueWatching(emptyList(), listOf(history(1))).isEmpty())
    }

    /**
     * The duration fallback — the behaviour the two hand-written copies disagreed on. The query
     * deliberately admits rows with `duration = 0`, so without this a half-watched film reports 0 %
     * and reads as "not started".
     */
    @Test
    fun fallsBackToTheMediaDurationWhenHistoryRecordedNone() {
        val result =
            getContinueWatching(
                listOf(video(1, duration = 80_000)),
                listOf(history(1, position = 40_000, duration = 0)),
            )

        val item = result.single()
        assertEquals(80_000L, item.duration)
        assertEquals("half way through, not zero", 0.5f, item.progress, 0.001f)
    }

    /** Most-recently-watched first is the query's ordering and the order the row is meant to show. */
    @Test
    fun preservesTheOrderHistoryArrivesIn() {
        val result =
            getContinueWatching(
                listOf(video(1), video(2), video(3)),
                listOf(history(3), history(1), history(2)),
            )

        assertEquals(listOf(3L, 1L, 2L), result.map { it.media.id })
    }

    // ------------------------------------------------------------------ progress

    @Test
    fun progressIsClampedRatherThanExceedingOne() {
        val result =
            getContinueWatching(
                listOf(video(1, duration = 100)),
                listOf(history(1, position = 500, duration = 100)),
            )

        assertEquals(1f, result.single().progress, 0.001f)
    }

    /** Both durations unknown: report no progress rather than dividing by zero. */
    @Test
    fun progressIsZeroWhenNoDurationIsKnownAtAll() {
        val result =
            getContinueWatching(
                listOf(video(1, duration = 0)),
                listOf(history(1, position = 5_000, duration = 0)),
            )

        assertEquals(0f, result.single().progress, 0.001f)
    }
}
