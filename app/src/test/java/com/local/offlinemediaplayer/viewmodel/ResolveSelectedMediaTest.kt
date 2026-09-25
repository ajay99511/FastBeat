package com.local.offlinemediaplayer.viewmodel

import android.net.Uri
import com.local.offlinemediaplayer.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Turning selected ids back into the files they name.
 *
 * This exists because the mutation that matters slipped past a full Compose suite. The screen tests
 * can prove the grid asks for a deletion; they cannot see that the ViewModel then searched two of
 * the three media lists and found nothing. That bug is **silent** — an empty URI list is a valid
 * delete request, so the selection clears, no error appears, and the photos are still there.
 *
 * Robolectric only so `Uri` is real.
 */
@RunWith(RobolectricTestRunner::class)
class ResolveSelectedMediaTest {
    private fun media(
        id: Long,
        isVideo: Boolean = false,
        isImage: Boolean = false,
    ) = MediaFile(
        id = id,
        uri = Uri.EMPTY,
        title = "media-$id",
        duration = 0,
        isVideo = isVideo,
        isImage = isImage,
    )

    private val audio = listOf(media(1), media(2))
    private val videos = listOf(media(10, isVideo = true))
    private val images = listOf(media(20, isImage = true), media(21, isImage = true))

    /** The regression. Images were not searched, so selecting them deleted nothing. */
    @Test
    fun selectedImagesAreFound() {
        val resolved = resolveSelectedMedia(setOf(20L, 21L), audio, videos, images)

        assertEquals(listOf(20L, 21L), resolved.map { it.id })
    }

    @Test
    fun selectedAudioAndVideoAreStillFound() {
        assertEquals(listOf(1L), resolveSelectedMedia(setOf(1L), audio, videos, images).map { it.id })
        assertEquals(listOf(10L), resolveSelectedMedia(setOf(10L), audio, videos, images).map { it.id })
    }

    /**
     * Selection state is shared across tabs, so a mixed set is reachable in principle. It must
     * resolve rather than silently dropping whichever type the search forgot.
     */
    @Test
    fun aMixedSelectionResolvesCompletely() {
        val resolved = resolveSelectedMedia(setOf(1L, 10L, 20L), audio, videos, images)

        assertEquals(setOf(1L, 10L, 20L), resolved.map { it.id }.toSet())
    }

    @Test
    fun idsThatMatchNothingAreDroppedRatherThanFailing() {
        val resolved = resolveSelectedMedia(setOf(1L, 999L), audio, videos, images)

        assertEquals(listOf(1L), resolved.map { it.id })
    }

    @Test
    fun anEmptySelectionResolvesToNothing() {
        assertTrue(resolveSelectedMedia(emptySet(), audio, videos, images).isEmpty())
    }

    @Test
    fun unselectedMediaIsNotIncluded() {
        val resolved = resolveSelectedMedia(setOf(20L), audio, videos, images)

        assertEquals(1, resolved.size)
        assertEquals(20L, resolved.single().id)
    }
}
