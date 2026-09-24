package com.local.offlinemediaplayer.ui.screens.image

import android.content.Intent
import android.net.Uri
import com.local.offlinemediaplayer.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the viewer's info button shows, and what the share button hands to another app.
 *
 * Robolectric so `Intent` and `Uri` are real; the formatting itself is plain Kotlin.
 *
 * The rule under test throughout is that **absent data is absent, not zero**. MediaStore does not
 * guarantee these columns, and "0 × 0" or a date in 1970 reads as a fact rather than as a gap —
 * which is the failure this phase has now hit three times in different disguises.
 */
@RunWith(RobolectricTestRunner::class)
class ImageDetailsTest {
    private fun image(
        title: String = "beach.jpg",
        size: Long = 2_048L,
        dateAdded: Long = 1_700_000_000L,
        width: Int = 4032,
        height: Int = 3024,
        mimeType: String = "image/jpeg",
        bucketName: String = "Camera",
    ) = MediaFile(
        id = 1,
        uri = Uri.parse("content://media/external/images/media/1"),
        title = title,
        duration = 0,
        isVideo = false,
        isImage = true,
        size = size,
        dateAdded = dateAdded,
        width = width,
        height = height,
        mimeType = mimeType,
        bucketName = bucketName,
    )

    private fun labels(file: MediaFile) = imageDetails(file).map { it.label }

    private fun valueOf(
        file: MediaFile,
        label: String,
    ) = imageDetails(file).first { it.label == label }.value

    // ------------------------------------------------------------------ details

    @Test
    fun aFullyDescribedPhotoShowsEveryRow() {
        assertEquals(listOf("Name", "Dimensions", "Size", "Added", "Folder", "Type"), labels(image()))
    }

    @Test
    fun theNameIsAlwaysShownEvenWhenNothingElseIs() {
        val bare = image(size = 0, dateAdded = 0, width = 0, height = 0, mimeType = "", bucketName = "")

        assertEquals(listOf("Name"), labels(bare))
        assertEquals("beach.jpg", valueOf(bare, "Name"))
    }

    @Test
    fun anUnknownSizeIsOmittedRatherThanShownAsZero() {
        assertTrue("Size" !in labels(image(size = 0)))
    }

    @Test
    fun anUnknownDateIsOmittedRatherThanShownAsNineteenSeventy() {
        assertTrue("Added" !in labels(image(dateAdded = 0)))
    }

    @Test
    fun anUnknownFolderIsOmitted() {
        assertTrue("Folder" !in labels(image(bucketName = "")))
    }

    // ------------------------------------------------------------------ dimensions

    @Test
    fun dimensionsCarryTheMegapixelCountThatMakesThemMeaningful() {
        assertEquals("4032 × 3024 (12.2 MP)", dimensionsOf(image()))
    }

    /** Both or neither: one dimension without the other is not a fact worth printing. */
    @Test
    fun aHalfKnownSizeIsNotReported() {
        assertNull(dimensionsOf(image(width = 4032, height = 0)))
        assertNull(dimensionsOf(image(width = 0, height = 3024)))
    }

    /** A 100 MP image overflows `Int` when multiplied out, which is why the maths is in `Long`. */
    @Test
    fun aVeryLargePhotoDoesNotOverflow() {
        assertEquals("12000 × 9000 (108.0 MP)", dimensionsOf(image(width = 12_000, height = 9_000)))
    }

    // ------------------------------------------------------------------ sharing

    @Test
    fun shareSendsTheImageItself() {
        val intent = shareIntentFor(image())

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals(image().uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
    }

    /**
     * Without this flag the receiving app gets a URI into a media store it has no rights to, and
     * the share silently produces a broken image on the other side.
     */
    @Test
    fun shareGrantsTheReceiverPermissionToReadIt() {
        val intent = shareIntentFor(image())

        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    /** A receiver that only handles PNG should not be offered a JPEG, so the real type is passed. */
    @Test
    fun theStoredTypeIsUsedRatherThanAWildcard() {
        assertEquals("image/png", shareIntentFor(image(mimeType = "image/png")).type)
    }

    @Test
    fun anUnknownTypeFallsBackToSomethingEveryAppAccepts() {
        assertEquals(FALLBACK_MIME_TYPE, shareIntentFor(image(mimeType = "")).type)
    }
}
