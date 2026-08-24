package com.local.offlinemediaplayer.model

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.test.core.app.ApplicationProvider
import com.local.offlinemediaplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [AppError].
 *
 * A sealed hierarchy that nothing uses yet is easy to ship broken — a missing string resource or a
 * mis-mapped error code would not surface until P4-F.2 wires it in. These resolve every message
 * against real resources so the type is known to work before anything depends on it.
 */
@RunWith(RobolectricTestRunner::class)
class AppErrorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyErrorResolvesToANonBlankMessage() {
        val errors =
            listOf(
                AppError.MediaAccessDenied,
                AppError.DeleteFailed,
                AppError.GenericError(),
            ) +
                AppError.PlaybackFailed.Reason.entries
                    .map { AppError.PlaybackFailed(it) }

        errors.forEach { error ->
            val message = error.userMessage(context)
            assertTrue("$error produced a blank message", message.isNotBlank())
        }
    }

    @Test
    fun messagesMatchTheStringsTheyReplace() {
        // Wording is preserved verbatim from the hardcoded originals; P4-F.2 must not change what
        // the user reads, only where it comes from.
        assertEquals("Couldn't delete this file", AppError.DeleteFailed.userMessage(context))
        assertEquals(
            "File not found or has been moved",
            AppError.PlaybackFailed(AppError.PlaybackFailed.Reason.FILE_NOT_FOUND).userMessage(context),
        )
        assertEquals(
            "Playback error occurred",
            AppError.PlaybackFailed(AppError.PlaybackFailed.Reason.UNKNOWN).userMessage(context),
        )
    }

    // ------------------------------------------------------------------ error-code mapping

    @Test
    fun aMissingFileMapsToFileNotFound() {
        assertEquals(
            AppError.PlaybackFailed.Reason.FILE_NOT_FOUND,
            AppError.PlaybackFailed.fromErrorCode(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND).reason,
        )
    }

    @Test
    fun aPermissionProblemMapsToPermissionDenied() {
        assertEquals(
            AppError.PlaybackFailed.Reason.PERMISSION_DENIED,
            AppError.PlaybackFailed.fromErrorCode(PlaybackException.ERROR_CODE_IO_NO_PERMISSION).reason,
        )
    }

    @Test
    fun bothDecoderFailuresMapToUnsupportedFormat() {
        // Decoder-init and decoding failures are different codes but the same story to the user.
        assertEquals(
            AppError.PlaybackFailed.Reason.UNSUPPORTED_FORMAT,
            AppError.PlaybackFailed.fromErrorCode(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED).reason,
        )
        assertEquals(
            AppError.PlaybackFailed.Reason.UNSUPPORTED_FORMAT,
            AppError.PlaybackFailed.fromErrorCode(PlaybackException.ERROR_CODE_DECODING_FAILED).reason,
        )
    }

    @Test
    fun anAudioTrackFailureMapsToAudioInitFailed() {
        assertEquals(
            AppError.PlaybackFailed.Reason.AUDIO_INIT_FAILED,
            AppError.PlaybackFailed.fromErrorCode(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED).reason,
        )
    }

    /**
     * Media3 adds error codes over time. An unrecognised one must still produce a readable
     * sentence rather than leaking a number into the UI or throwing.
     */
    @Test
    fun anUnrecognisedCodeFallsBackToUnknown() {
        assertEquals(
            AppError.PlaybackFailed.Reason.UNKNOWN,
            AppError.PlaybackFailed.fromErrorCode(PlaybackException.ERROR_CODE_TIMEOUT).reason,
        )
        assertEquals(
            AppError.PlaybackFailed.Reason.UNKNOWN,
            AppError.PlaybackFailed.fromErrorCode(-12345).reason,
        )
    }

    // ------------------------------------------------------------------ generic

    @Test
    fun genericErrorDefaultsToTheCatchAllMessage() {
        assertEquals(
            context.getString(R.string.error_generic),
            AppError.GenericError().userMessage(context),
        )
    }

    @Test
    fun genericErrorCanCarryASpecificString() {
        assertEquals(
            context.getString(R.string.error_delete_failed),
            AppError.GenericError(R.string.error_delete_failed).userMessage(context),
        )
    }

    @Test
    fun errorsOfTheSameKindCompareEqual() {
        // Value semantics matter: these end up in flows where duplicate emissions get conflated.
        assertEquals(AppError.DeleteFailed, AppError.DeleteFailed)
        assertEquals(
            AppError.PlaybackFailed(AppError.PlaybackFailed.Reason.UNKNOWN),
            AppError.PlaybackFailed(AppError.PlaybackFailed.Reason.UNKNOWN),
        )
    }
}
