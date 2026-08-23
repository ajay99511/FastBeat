package com.local.offlinemediaplayer.playback

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.media3.common.Player
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.model.AudioPlayerState
import com.local.offlinemediaplayer.model.MediaFile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every write to, and read from, the persisted audio session — the Room `current_queue` rows, the
 * four scalar preferences, and the JSON snapshot that survives process death.
 *
 * Extracted from `PlaybackViewModel` (P4-E.4, step 2 / OQ-9 option b). The point is the invariant
 * *Room and ExoPlayer must never disagree on queue content or order*: previously the saved session
 * could be written from several places scattered through a 2 250-line file, so "what can change
 * the queue on disk?" had no single answer. Now it does.
 *
 * The decision rules live in [QueuePolicy] and are pinned by `QueuePolicyTest`; this class is the
 * plumbing around them. The queue StateFlows themselves deliberately remain in the ViewModel — see
 * OQ-9.
 */
@Singleton
class QueuePersistence
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val mediaDao: MediaDao,
    ) {
        private companion object {
            const val TAG = "QueuePersistence"
            const val PREFS = "app_prefs"
            const val KEY_QUEUE_INDEX = "last_queue_index"
            const val KEY_SHUFFLE = "last_shuffle_enabled"
            const val KEY_REPEAT = "last_repeat_mode"
            const val KEY_CONTEXT = "last_playlist_context"
            const val KEY_SESSION = "saved_audio_session"
        }

        private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // ------------------------------------------------------------------ queue rows

        /**
         * Persists [queue] if [QueuePolicy] allows it.
         *
         * Deliberately `suspend` rather than fire-and-forget: the caller keeps ownership of the
         * dispatcher (the ViewModel still launches this on IO exactly as before), which leaves this
         * class deterministic and therefore testable. An internal scope here made a save followed by
         * a load race each other — a real defect the round-trip test caught immediately.
         */
        suspend fun saveQueue(queue: List<MediaFile>) {
            if (!QueuePolicy.isPersistable(queue)) return
            try {
                mediaDao.replaceQueue(QueuePolicy.toEntities(queue))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist queue", e)
            }
        }

        /** Rebuilds the saved queue, or null when nothing restorable remains. */
        suspend fun loadQueue(mediaById: Map<Long, MediaFile>): QueueRestore? =
            QueuePolicy.restore(mediaDao.getSavedQueue(), mediaById, queueIndex)

        /** Where the given track should resume, applying the completion rule. */
        suspend fun resumePositionFor(mediaId: Long): Long = QueuePolicy.resumePosition(mediaDao.getHistory(mediaId))

        // ------------------------------------------------------------------ scalars

        var queueIndex: Int
            get() = prefs.getInt(KEY_QUEUE_INDEX, 0)
            set(value) = prefs.edit { putInt(KEY_QUEUE_INDEX, value) }

        var shuffleEnabled: Boolean
            get() = prefs.getBoolean(KEY_SHUFFLE, false)
            set(value) = prefs.edit { putBoolean(KEY_SHUFFLE, value) }

        var repeatMode: Int
            get() = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)
            set(value) = prefs.edit { putInt(KEY_REPEAT, value) }

        var playlistContext: String?
            get() = prefs.getString(KEY_CONTEXT, null)
            set(value) =
                prefs.edit {
                    if (value != null) putString(KEY_CONTEXT, value) else remove(KEY_CONTEXT)
                }

        // ------------------------------------------------------------------ interrupted session

        /**
         * Snapshots an audio session interrupted by video, so that a process kill while the video
         * is open does not lose the music session. Stored as JSON rather than in the queue tables
         * because it must not disturb `current_queue`, which may since have been rewritten.
         */
        fun saveAudioSession(state: AudioPlayerState) {
            try {
                val ids = JSONArray()
                state.queue.forEach { ids.put(it.id) }
                val json =
                    JSONObject().apply {
                        put("queueIds", ids)
                        put("index", state.currentIndex)
                        put("currentId", state.queue.getOrNull(state.currentIndex)?.id ?: -1L)
                        put("position", state.position)
                        put("shuffle", state.isShuffleEnabled)
                        put("repeat", state.repeatMode)
                    }
                prefs.edit { putString(KEY_SESSION, json.toString()) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist saved audio session", e)
            }
        }

        fun clearAudioSession() {
            prefs.edit { remove(KEY_SESSION) }
        }

        /**
         * Reconstructs an interrupted session, resolving ids against the freshly scanned library
         * and dropping any that no longer exist. Returns null when there is no saved session or
         * none of its tracks survive.
         *
         * The restored session is **always paused** — a cold start must never begin blasting audio.
         * The current track is located by **id first**, falling back to the stored index only if
         * that id is gone: tracks deleted from the library shift every later index, so trusting the
         * index alone would silently resume the wrong song.
         */
        fun loadAudioSession(mediaById: Map<Long, MediaFile>): AudioPlayerState? {
            val raw = prefs.getString(KEY_SESSION, null) ?: return null
            return try {
                val json = JSONObject(raw)
                val idsArray = json.getJSONArray("queueIds")
                val queue = mutableListOf<MediaFile>()
                for (i in 0 until idsArray.length()) {
                    val track = mediaById[idsArray.getLong(i)]
                    if (track != null && !track.isVideo) queue.add(track)
                }
                if (queue.isEmpty()) return null

                val currentId = json.optLong("currentId", -1L)
                val index =
                    queue.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }
                        ?: json.getInt("index").coerceIn(0, queue.size - 1)

                AudioPlayerState(
                    queue = queue,
                    currentIndex = index,
                    position = json.getLong("position"),
                    isPlaying = false,
                    isShuffleEnabled = json.getBoolean("shuffle"),
                    repeatMode = json.getInt("repeat"),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load saved audio session", e)
                null
            }
        }
    }
