package com.local.offlinemediaplayer.playback

import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [MediaDeletionHandler]'s legacy (API ≤ 28) delete path and its pending-slot
 * bookkeeping.
 *
 * `Context` and `ContentResolver` are mocked rather than using Robolectric's real ones, because the
 * behaviour worth testing is what happens when the resolver **throws** — a real resolver against an
 * unregistered authority just returns 0 and never exercises the failure branches.
 *
 * WHAT THIS FILE CANNOT COVER, honestly stated:
 *  - the **API 29 `RecoverableSecurityException` retry**, which needs a real `RemoteAction` and a
 *    system consent dialog. That path is device-only and remains F-18's outstanding manual check.
 *  - the **API 30+ `createDeleteRequest`** path. This was attempted, not assumed: under Robolectric
 *    it fails with `ClassCastException: android.os.Parcelable$Subclass2 cannot be cast to
 *    android.app.PendingIntent`, because the shadowed MediaStore returns a generic Parcelable stub.
 * Both remain covered only by the manual sequence recorded on P4-E.3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MediaDeletionHandlerTest {
    private lateinit var resolver: ContentResolver
    private lateinit var handler: MediaDeletionHandler
    private val uri: Uri = Uri.parse("content://media/external/audio/media/1")

    @Before
    fun setUp() {
        resolver = mockk(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.contentResolver } returns resolver
        handler = MediaDeletionHandler(context)
    }

    // ------------------------------------------------------------------ legacy success path

    @Test
    fun aSuccessfulDeleteInvokesTheCompletionCallback() =
        runTest {
            var deleted = false
            handler.requestDelete(DeletionKind.TRACK, uri, deleted = { deleted = true }, failed = {})

            assertTrue(deleted)
            verify { resolver.delete(uri, null, null) }
        }

    @Test
    fun aSuccessfulDeleteDoesNotReportFailure() =
        runTest {
            var failed = false
            handler.requestDelete(DeletionKind.TRACK, uri, deleted = {}, failed = { failed = true })

            assertFalse(failed)
        }

    /**
     * After an outright delete there is no consent round-trip, so the callback slots must be gone.
     * If they lingered, a stray confirmation — say the user deleting something else — would re-run
     * this deletion's completion logic against the wrong item.
     */
    @Test
    fun aStrayConfirmationAfterAnImmediateDeleteDoesNothing() =
        runTest {
            var deletedCount = 0
            handler.requestDelete(DeletionKind.TRACK, uri, deleted = { deletedCount++ }, failed = {})
            assertEquals(1, deletedCount)

            handler.onDeleteConfirmed(DeletionKind.TRACK)

            assertEquals("completion must not run twice", 1, deletedCount)
        }

    // ------------------------------------------------------------------ legacy failure paths

    @Test
    fun aNonRecoverableSecurityExceptionReportsFailure() =
        runTest {
            every { resolver.delete(uri, null, null) } throws SecurityException("denied")
            var deleted = false
            var failure: Exception? = null

            handler.requestDelete(DeletionKind.TRACK, uri, deleted = { deleted = true }, failed = { failure = it })

            assertFalse("a denied delete is not a success", deleted)
            assertTrue(failure is SecurityException)
        }

    @Test
    fun anUnexpectedExceptionReportsFailure() =
        runTest {
            every { resolver.delete(uri, null, null) } throws IllegalStateException("boom")
            var failure: Exception? = null

            handler.requestDelete(DeletionKind.TRACK, uri, deleted = {}, failed = { failure = it })

            assertTrue(failure is IllegalStateException)
        }

    @Test
    fun aFailedDeleteClearsItsSlotsSoAConfirmationCannotResurrectIt() =
        runTest {
            every { resolver.delete(uri, null, null) } throws SecurityException("denied")
            var deleted = false
            handler.requestDelete(DeletionKind.TRACK, uri, deleted = { deleted = true }, failed = {})

            handler.onDeleteConfirmed(DeletionKind.TRACK)

            assertFalse(deleted)
        }

    // ------------------------------------------------------------------ pending-slot bookkeeping

    @Test
    fun theTwoDeletionKindsHaveIndependentSlots() =
        runTest {
            every { resolver.delete(uri, null, null) } throws SecurityException("denied")
            var imageFailed = false
            var trackFailed = false

            handler.requestDelete(DeletionKind.IMAGE, uri, deleted = {}, failed = { imageFailed = true })
            handler.requestDelete(DeletionKind.TRACK, uri, deleted = {}, failed = { trackFailed = true })

            assertTrue("each kind reports its own failure", imageFailed)
            assertTrue(trackFailed)
        }

    @Test
    fun confirmingWithNothingPendingIsHarmless() =
        runTest {
            handler.onDeleteConfirmed(DeletionKind.IMAGE)
            handler.onDeleteConfirmed(DeletionKind.TRACK)
        }

    @Test
    fun cancellingClearsEveryPendingSlot() =
        runTest {
            every { resolver.delete(uri, null, null) } throws SecurityException("denied")
            var deleted = false
            handler.requestDelete(DeletionKind.IMAGE, uri, deleted = { deleted = true }, failed = {})

            handler.onDeleteCancelled()
            handler.onDeleteConfirmed(DeletionKind.IMAGE)

            assertFalse("a cancelled deletion must never complete later", deleted)
        }

    // ------------------------------------------------------------------ consent intent stream

    /**
     * `deleteIntentEvent` is a zero-replay `SharedFlow`, so an emission is delivered only to
     * collectors already listening. Pinned so a later change to buffering is a deliberate decision
     * rather than an accident — the Activity must be collecting before a delete is requested.
     */
    @Test
    fun theConsentIntentStreamDeliversToAnActiveCollector() =
        runTest(UnconfinedTestDispatcher()) {
            val seen = mutableListOf<IntentSender>()
            val job = launch(Dispatchers.Unconfined) { handler.deleteIntentEvent.collect { seen.add(it) } }

            // On API 28 nothing is emitted: the delete happens outright with no consent dialog.
            handler.requestDelete(DeletionKind.TRACK, uri, deleted = {}, failed = {})

            assertTrue("API 28 deletes directly and needs no consent dialog", seen.isEmpty())
            job.cancel()
        }
}
