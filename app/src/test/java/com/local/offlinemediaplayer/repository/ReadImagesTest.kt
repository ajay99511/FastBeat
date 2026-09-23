package com.local.offlinemediaplayer.repository

import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cursor-to-model mapping for images.
 *
 * Worth testing for the reason `MediaDaoTest` gives about SQL literals: this is string column keys
 * and index arithmetic, which the compiler cannot check and which fails *silently* — a mis-wired
 * column yields a plausible number in the wrong field, not a crash. The bug this replaces was of
 * exactly that shape: every image carried `size = 0` because the column was never selected, and the
 * Me tab's storage total quietly under-reported for as long as that lasted.
 *
 * Robolectric only so `MatrixCursor`, `Uri` and `ContentUris` are real.
 */
@RunWith(RobolectricTestRunner::class)
class ReadImagesTest {
    private val collection: Uri = Uri.parse("content://media/external/images/media")

    private val fullProjection =
        arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )

    private fun cursorOf(
        projection: Array<String>,
        vararg rows: Array<Any?>,
    ) = MatrixCursor(projection).apply { rows.forEach { addRow(it) } }

    /** `dateModified` is fixed rather than a parameter: no test varies it, and detekt caps the list. */
    private fun fullRow(
        id: Long = 42L,
        name: String? = "beach.jpg",
        size: Any? = 2_048L,
        dateAdded: Any? = 1_700_000_000L,
        width: Any? = 4032,
        height: Any? = 3024,
        mime: Any? = "image/jpeg",
        bucketId: Any? = "1234",
        bucketName: Any? = "Camera",
    ): Array<Any?> = arrayOf(id, name, size, dateAdded, DATE_MODIFIED, width, height, mime, bucketId, bucketName)

    // ------------------------------------------------------------------ mapping

    @Test
    fun everyColumnLandsInItsOwnField() {
        val image = readImages(cursorOf(fullProjection, fullRow()), collection).single()

        assertEquals(42L, image.id)
        assertEquals("beach.jpg", image.title)
        assertEquals("beach.jpg", image.displayName)
        assertEquals(2_048L, image.size)
        assertEquals(1_700_000_000L, image.dateAdded)
        assertEquals(DATE_MODIFIED, image.dateModified)
        assertEquals(4032, image.width)
        assertEquals(3024, image.height)
        assertEquals("image/jpeg", image.mimeType)
        assertEquals("1234", image.bucketId)
        assertEquals("Camera", image.bucketName)
    }

    /** Images are images. Everything downstream branches on these two. */
    @Test
    fun theResultIsFlaggedAsAnImageAndNotAVideo() {
        val image = readImages(cursorOf(fullProjection, fullRow()), collection).single()

        assertTrue(image.isImage)
        assertTrue(!image.isVideo)
    }

    @Test
    fun theUriIsTheCollectionWithTheIdAppended() {
        val image = readImages(cursorOf(fullProjection, fullRow(id = 7)), collection).single()

        assertEquals("content://media/external/images/media/7", image.uri.toString())
    }

    @Test
    fun rowOrderIsPreserved() {
        val cursor =
            cursorOf(
                fullProjection,
                fullRow(id = 1, name = "first.jpg"),
                fullRow(id = 2, name = "second.jpg"),
                fullRow(id = 3, name = "third.jpg"),
            )

        assertEquals(listOf(1L, 2L, 3L), readImages(cursor, collection).map { it.id })
    }

    @Test
    fun anEmptyCursorIsAnEmptyList() {
        assertTrue(readImages(cursorOf(fullProjection), collection).isEmpty())
    }

    // ------------------------------------------------------------------ missing data

    /**
     * MediaStore does not guarantee every optional column on every OEM build or volume. A missing
     * one must cost that field, not the whole library — reading through `getColumnIndexOrThrow`
     * would take the entire Images tab down on a device that omits, say, `BUCKET_ID`.
     */
    @Test
    fun anAbsentOptionalColumnCostsOnlyThatField() {
        val minimal = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)

        val image = readImages(cursorOf(minimal, arrayOf(9L, "lonely.jpg")), collection).single()

        assertEquals(9L, image.id)
        assertEquals("lonely.jpg", image.title)
        assertEquals(0L, image.size)
        assertEquals(0, image.width)
        assertEquals("", image.mimeType)
        assertEquals("Unknown", image.bucketName)
    }

    /** A present-but-NULL column is the same situation and must not throw either. */
    @Test
    fun aNullColumnValueIsTreatedAsAbsent() {
        val row = fullRow(size = null, width = null, height = null, mime = null, bucketId = null)

        val image = readImages(cursorOf(fullProjection, row), collection).single()

        assertEquals(0L, image.size)
        assertEquals(0, image.width)
        assertEquals("", image.mimeType)
        assertEquals("", image.bucketId)
    }

    @Test
    fun aMissingDisplayNameFallsBackRatherThanProducingABlankRow() {
        val image = readImages(cursorOf(fullProjection, fullRow(name = null)), collection).single()

        assertEquals("Unknown Image", image.title)
    }

    /**
     * Files at the volume root carry no bucket name. "Unknown" is what the video query already
     * substitutes, so both media types group under the same heading rather than one showing a blank.
     */
    @Test
    fun anEmptyBucketNameBecomesUnknown() {
        val image = readImages(cursorOf(fullProjection, fullRow(bucketName = "")), collection).single()

        assertEquals("Unknown", image.bucketName)
    }

    private companion object {
        const val DATE_MODIFIED = 1_700_000_500L
    }
}
