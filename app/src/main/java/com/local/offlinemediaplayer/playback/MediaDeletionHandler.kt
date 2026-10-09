package com.local.offlinemediaplayer.playback

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Which pending deletion a callback refers to. Each kind has an independent pending slot. */
enum class DeletionKind {
    IMAGE,
    TRACK,
}

/**
 * Whether a delete on this device can be a recoverable move to the system trash.
 *
 * `createTrashRequest` arrived in API 30. Below it there is no trash at all, so a delete is
 * permanent and the confirmation copy has to say so — which is why this is one function rather
 * than a version check written out at each of the places that needs the answer. The UI asks it to
 * choose its wording and the handler asks it to choose its request; a second copy of the check is
 * a second thing that can disagree.
 */
internal fun supportsTrash(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.R

/**
 * Owns the scoped-storage delete round-trip: asking the system for permission to delete a file,
 * surfacing the consent dialog, and retrying where the platform requires it.
 *
 * Extracted from `PlaybackViewModel` (P4-E.3), where this ~35-line dance existed **twice** —
 * once for images and once for the current track — differing only in which pending field it wrote.
 * What deletion *means* to the app (pruning the image list, cleaning repositories, repairing the
 * queue) stays in the ViewModel and is supplied as callbacks; only the platform mechanism lives
 * here.
 *
 * THE THREE PLATFORM PATHS, all preserved verbatim:
 *  - **API 30+ (R)** — [MediaStore.createDeleteRequest] returns a [PendingIntent]; the system
 *    performs the delete itself once the user consents, so [onDeleteConfirmed] must NOT re-attempt
 *    it.
 *  - **API 29 (Q)** — a direct delete throws [RecoverableSecurityException]. Consent only grants
 *    write access, so the app **must** re-attempt the delete afterwards. That is why
 *    [pendingLegacyUris] exists; dropping it would make deletion silently no-op on Android 10.
 *  - **API 26–28** — a direct delete simply works.
 */
@Singleton
class MediaDeletionHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private companion object {
            const val TAG = "MediaDeletionHandler"
        }

        private val _deleteIntentEvent = MutableSharedFlow<IntentSender>()

        /** Consent dialogs the Activity must launch. */
        val deleteIntentEvent: SharedFlow<IntentSender> = _deleteIntentEvent.asSharedFlow()

        /** URIs awaiting a post-consent retry on API 29. Empty on every other API level. */
        private val pendingLegacyUris = mutableMapOf<DeletionKind, Uri>()

        private val onDeleted = mutableMapOf<DeletionKind, suspend () -> Unit>()
        private val onFailed = mutableMapOf<DeletionKind, suspend (Exception) -> Unit>()

        /**
         * Attempts to delete [uri]. Either completes immediately (invoking [deleted]) or emits a
         * consent intent, in which case the caller must route the Activity result back through
         * [onDeleteConfirmed] or [onDeleteCancelled].
         *
         * Must be called from a coroutine on an IO dispatcher, as it touches the ContentResolver.
         *
         * `InstanceOfCheckForException` is suppressed rather than baselined: `RecoverableSecurityException`
         * only exists from API 29, so it cannot be caught directly on a minSdk 26 app. Catching
         * `SecurityException` and narrowing behind a version check is the idiom Android documents for
         * exactly this case — deliberate, not debt.
         */
        @Suppress("InstanceOfCheckForException")
        suspend fun requestDelete(
            kind: DeletionKind,
            uri: Uri,
            deleted: suspend () -> Unit,
            failed: suspend (Exception) -> Unit,
            moveToTrash: Boolean = false,
        ) {
            onDeleted[kind] = deleted
            onFailed[kind] = failed

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Both return a PendingIntent and both are completed by the system once the user
                // consents, so the post-consent path below is identical either way. Trashed items
                // are excluded from MediaStore queries by default, so a rescan drops them exactly
                // as a deletion would.
                val pendingIntent: PendingIntent =
                    if (moveToTrash) {
                        MediaStore.createTrashRequest(context.contentResolver, listOf(uri), true)
                    } else {
                        MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                    }
                _deleteIntentEvent.emit(pendingIntent.intentSender)
                return
            }

            try {
                context.contentResolver.delete(uri, null, null)
                // Deleted outright on API <= 28: no consent round-trip, so drop the slots now
                // rather than leaving stale callbacks a later confirmation could re-invoke.
                onDeleted.remove(kind)
                onFailed.remove(kind)
                deleted()
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    pendingLegacyUris[kind] = uri
                    _deleteIntentEvent.emit(e.userAction.actionIntent.intentSender)
                } else {
                    report(kind, e)
                }
            } catch (e: Exception) {
                report(kind, e)
            }
        }

        /**
         * The user granted consent. On API 29 this re-runs the delete, because consent there only
         * grants write access. On API 30+ the system already deleted the file, so this only runs
         * the completion callback.
         */
        suspend fun onDeleteConfirmed(kind: DeletionKind) {
            val retryUri = pendingLegacyUris.remove(kind)
            if (retryUri != null) {
                try {
                    context.contentResolver.delete(retryUri, null, null)
                } catch (e: Exception) {
                    report(kind, e)
                    return
                }
            }
            onDeleted.remove(kind)?.invoke()
            onFailed.remove(kind)
        }

        /** The user dismissed the system dialog. Clears every pending slot, as before. */
        fun onDeleteCancelled() {
            pendingLegacyUris.clear()
            onDeleted.clear()
            onFailed.clear()
        }

        private suspend fun report(
            kind: DeletionKind,
            e: Exception,
        ) {
            Log.e(TAG, "Failed to delete ${kind.name.lowercase()}", e)
            pendingLegacyUris.remove(kind)
            onDeleted.remove(kind)
            onFailed.remove(kind)?.invoke(e)
        }
    }
