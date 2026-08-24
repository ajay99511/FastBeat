package com.local.offlinemediaplayer.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.media3.common.PlaybackException
import com.local.offlinemediaplayer.R

/**
 * A failure the user needs to be told about.
 *
 * Introduced by P4-F.1. The point is not ceremony around exceptions — it is that today every
 * user-facing failure is a hardcoded English string built at the point it happens, scattered across
 * `PlaybackViewModel` and `LibraryViewModel`. That makes the app untranslatable (F-6), makes the
 * same failure phrased differently in different places, and puts presentation inside the ViewModel.
 *
 * An [AppError] carries *what went wrong*; [userMessage] decides how to say it, once, from string
 * resources. P4-F.2 migrates the existing call sites onto this.
 */
sealed interface AppError {
    /** The message to show the user, resolved from string resources. */
    fun userMessage(context: Context): String

    /** The app was refused access to media it needs — typically a revoked storage permission. */
    data object MediaAccessDenied : AppError {
        override fun userMessage(context: Context): String = context.getString(R.string.error_media_access_denied)
    }

    /** A delete could not be completed. Deliberately not split by cause: the user can only retry. */
    data object DeleteFailed : AppError {
        override fun userMessage(context: Context): String = context.getString(R.string.error_delete_failed)
    }

    /**
     * Playback stopped because of [reason].
     *
     * The distinction matters to the user in a way most error detail does not: a missing file is
     * something they can fix by restoring it, an unsupported format never will be, and a permission
     * problem is a settings change. Collapsing these into one message would remove the only part of
     * a playback failure the user can act on.
     */
    data class PlaybackFailed(
        val reason: Reason,
    ) : AppError {
        override fun userMessage(context: Context): String = context.getString(reason.messageRes)

        enum class Reason(
            @param:StringRes val messageRes: Int,
        ) {
            FILE_NOT_FOUND(R.string.error_playback_file_not_found),
            PERMISSION_DENIED(R.string.error_playback_permission_denied),
            UNSUPPORTED_FORMAT(R.string.error_playback_unsupported_format),
            AUDIO_INIT_FAILED(R.string.error_playback_audio_init_failed),
            UNKNOWN(R.string.error_playback_unknown),
        }

        companion object {
            /**
             * Maps a Media3 error code onto a [Reason]. Any code not listed becomes [Reason.UNKNOWN]
             * rather than leaking a raw code into the UI — Media3 adds codes over time, and an
             * unrecognised one must still produce a sentence a person can read.
             */
            fun fromErrorCode(errorCode: Int): PlaybackFailed =
                PlaybackFailed(
                    when (errorCode) {
                        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> Reason.FILE_NOT_FOUND
                        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> Reason.PERMISSION_DENIED
                        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                        -> Reason.UNSUPPORTED_FORMAT
                        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> Reason.AUDIO_INIT_FAILED
                        else -> Reason.UNKNOWN
                    },
                )
        }
    }

    /**
     * Anything without its own case yet, named by the string it should show.
     *
     * This exists so migrating a call site is never blocked on inventing a new case, but it is the
     * weakest member of the hierarchy: a `GenericError` tells calling code nothing it can branch on.
     * Prefer adding a real case when a failure turns out to be worth distinguishing.
     */
    data class GenericError(
        @param:StringRes val messageRes: Int = R.string.error_generic,
    ) : AppError {
        override fun userMessage(context: Context): String = context.getString(messageRes)
    }
}
