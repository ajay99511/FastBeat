package com.local.offlinemediaplayer.viewmodel

import android.net.Uri
import com.local.offlinemediaplayer.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The library summary the stats surface renders.
 *
 * Robolectric is here only so [Uri] is a real class — [MediaFile] carries one, and nothing in these
 * tests ever calls a method on it. No database, no ViewModel, no dispatcher.
 *
 * What is worth pinning is not the arithmetic but the *coverage*: a fold that quietly omits one of
 * the three media lists produces a plausible number under a headline that claims to be a total, and
 * no reader of the flow declaration notices. That is exactly what shipped.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryStatsTest {
    private fun mediaOf(
        id: Long,
        size: Long,
        isVideo: Boolean = false,
        isImage: Boolean = false,
    ) = MediaFile(
        id = id,
        uri = Uri.EMPTY,
        title = "media-$id",
        duration = 0,
        isVideo = isVideo,
        isImage = isImage,
        size = size,
    )

    private val audio = listOf(mediaOf(1, size = 3_000), mediaOf(2, size = 5_000))
    private val videos = listOf(mediaOf(3, size = 900_000, isVideo = true))
    private val images = listOf(mediaOf(4, size = 40_000, isImage = true), mediaOf(5, size = 60_000, isImage = true))

    // ------------------------------------------------------------------ counts

    @Test
    fun everyListIsCounted() {
        val stats = libraryStatsOf(audio, videos, images, playlistCount = 7)

        assertEquals(2, stats.songCount)
        assertEquals(1, stats.videoCount)
        assertEquals(2, stats.imageCount)
        assertEquals(7, stats.playlistCount)
    }

    @Test
    fun anEmptyLibraryIsAllZeroes() {
        val stats = libraryStatsOf(emptyList(), emptyList(), emptyList(), playlistCount = 0)

        assertEquals(LibraryStats(), stats)
        assertEquals(0L, stats.totalStorageBytes)
    }

    // ------------------------------------------------------------------ storage

    @Test
    fun eachMediaTypeReportsItsOwnStorage() {
        val stats = libraryStatsOf(audio, videos, images, playlistCount = 0)

        assertEquals(8_000L, stats.audioStorageBytes)
        assertEquals(900_000L, stats.videoStorageBytes)
        assertEquals(100_000L, stats.imageStorageBytes)
    }

    /**
     * The regression. The fold summed audio and video only, under a card reading "Total Storage
     * Used", so a library with photos in it reported a total that matched nothing the user could
     * verify.
     */
    @Test
    fun imagesCountTowardsTheStorageTotal() {
        val withoutImages = libraryStatsOf(audio, videos, emptyList(), playlistCount = 0)
        val withImages = libraryStatsOf(audio, videos, images, playlistCount = 0)

        assertEquals(100_000L, withImages.totalStorageBytes - withoutImages.totalStorageBytes)
    }

    /**
     * The total is derived, not stored, so it cannot disagree with the breakdown printed beneath
     * it — which is the failure mode a second constructor argument would reintroduce.
     */
    @Test
    fun theTotalIsExactlyTheSumOfItsParts() {
        val stats = libraryStatsOf(audio, videos, images, playlistCount = 0)

        assertEquals(
            stats.audioStorageBytes + stats.videoStorageBytes + stats.imageStorageBytes,
            stats.totalStorageBytes,
        )
        assertEquals(1_008_000L, stats.totalStorageBytes)
    }

    /**
     * MediaStore can report a size of zero for a file it cannot stat. That must contribute nothing
     * rather than being treated as missing data, because the alternative — dropping the row — would
     * make the count and the storage figure describe different libraries.
     */
    @Test
    fun aZeroSizedFileStillCounts() {
        val stats = libraryStatsOf(audio + mediaOf(9, size = 0), videos, images, playlistCount = 0)

        assertEquals(3, stats.songCount)
        assertEquals(8_000L, stats.audioStorageBytes)
    }
}
