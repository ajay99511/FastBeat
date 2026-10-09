package com.local.offlinemediaplayer.viewmodel

import android.net.Uri
import com.local.offlinemediaplayer.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ordering for the image grid.
 *
 * Robolectric only so `Uri` is real; the comparators are plain Kotlin.
 *
 * Worth testing because a sort that silently orders by the wrong field looks like a working feature.
 * The default directions are part of that: a camera roll opened oldest-first, or the smallest photo
 * shown as the "top" result, is not a bug anyone reports — they just conclude the sort is broken.
 */
@RunWith(RobolectricTestRunner::class)
class ImageSortTest {
    private fun image(
        id: Long,
        title: String,
        size: Long = 0,
        dateAdded: Long = 0,
    ) = MediaFile(
        id = id,
        uri = Uri.EMPTY,
        title = title,
        duration = 0,
        isVideo = false,
        isImage = true,
        size = size,
        dateAdded = dateAdded,
    )

    private val photos =
        listOf(
            image(1, "beach.jpg", size = 3_000, dateAdded = 200),
            image(2, "Apple.png", size = 9_000, dateAdded = 100),
            image(3, "mountain.jpg", size = 1_000, dateAdded = 300),
        )

    private fun titlesSortedBy(
        field: ImageSortField,
        ascending: Boolean = field.defaultAscending,
    ) = photos.applyImageSort(SortState(field, ascending)).map { it.title }

    // ------------------------------------------------------------------ defaults

    /** A camera roll opens newest-first. Anything else reads as a broken gallery. */
    @Test
    fun dateAddedDefaultsToNewestFirst() {
        assertTrue(!ImageSortField.DATE_ADDED.defaultAscending)
        assertEquals(listOf("mountain.jpg", "beach.jpg", "Apple.png"), titlesSortedBy(ImageSortField.DATE_ADDED))
    }

    @Test
    fun sizeDefaultsToLargestFirst() {
        assertTrue(!ImageSortField.SIZE.defaultAscending)
        assertEquals(listOf("Apple.png", "beach.jpg", "mountain.jpg"), titlesSortedBy(ImageSortField.SIZE))
    }

    @Test
    fun nameDefaultsToAToZ() {
        assertTrue(ImageSortField.NAME.defaultAscending)
        assertEquals(listOf("Apple.png", "beach.jpg", "mountain.jpg"), titlesSortedBy(ImageSortField.NAME))
    }

    /**
     * Case-insensitively, or `Apple.png` sorts before every lowercase name regardless of letter —
     * the classic ASCII ordering bug that makes an alphabetical list look arbitrary.
     */
    @Test
    fun nameIgnoresCase() {
        // The fixture has to be one where the two orderings actually differ. 'Z' is 90 and 'a' is
        // 97, so case-sensitively "Zebra" sorts first — an earlier version of this test used
        // Apple/Mango/zebra, which happens to order identically either way and therefore passed
        // against a case-sensitive comparator.
        val mixed = listOf(image(1, "Zebra.jpg"), image(2, "apple.png"), image(3, "Mango.gif"))

        val sorted = mixed.applyImageSort(SortState(ImageSortField.NAME)).map { it.title }

        assertEquals(listOf("apple.png", "Mango.gif", "Zebra.jpg"), sorted)
    }

    // ------------------------------------------------------------------ direction

    @Test
    fun everyFieldCanBeReversed() {
        assertEquals(listOf("Apple.png", "beach.jpg", "mountain.jpg"), titlesSortedBy(ImageSortField.DATE_ADDED, true))
        assertEquals(listOf("mountain.jpg", "beach.jpg", "Apple.png"), titlesSortedBy(ImageSortField.SIZE, true))
        assertEquals(listOf("mountain.jpg", "beach.jpg", "Apple.png"), titlesSortedBy(ImageSortField.NAME, false))
    }

    /** Re-selecting the active field flips it; picking another applies that field's own default. */
    @Test
    fun theSortStateFlipsOnReselectionAndResetsOnChange() {
        val newestFirst = SortState(ImageSortField.DATE_ADDED)

        val flipped = newestFirst.select(ImageSortField.DATE_ADDED)
        assertTrue(flipped.ascending)

        val switched = flipped.select(ImageSortField.NAME)
        assertEquals(ImageSortField.NAME, switched.field)
        assertTrue("a new field starts at its own default", switched.ascending)
    }

    // ------------------------------------------------------------------ edges

    @Test
    fun anEmptyLibrarySortsToNothing() {
        assertTrue(emptyList<MediaFile>().applyImageSort(SortState(ImageSortField.NAME)).isEmpty())
    }

    /**
     * MediaStore may report neither a date nor a size. Those photos must stay in the list rather
     * than being dropped or crashing the comparator.
     */
    @Test
    fun photosWithNoDateOrSizeAreStillListed() {
        val partial = photos + image(4, "unknown.jpg")

        ImageSortField.entries.forEach { field ->
            assertEquals(field.name, 4, partial.applyImageSort(SortState(field)).size)
        }
    }

    /**
     * The sort fields are deliberately not `SortField`, which offers Runtime and Play Count — a
     * photo has neither, and a sort that cannot order anything is worse than one fewer option.
     */
    @Test
    fun onlyFieldsAPhotoActuallyHasAreOffered() {
        assertEquals(
            listOf("Date Added", "Name", "Size"),
            ImageSortField.entries.map { it.label },
        )
    }
}
