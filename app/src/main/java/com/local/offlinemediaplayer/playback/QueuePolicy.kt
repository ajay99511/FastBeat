package com.local.offlinemediaplayer.playback

import com.local.offlinemediaplayer.data.db.PlaybackHistory
import com.local.offlinemediaplayer.data.db.QueueItemEntity
import com.local.offlinemediaplayer.model.MediaFile

/** The queue and position to resume, as decided by [QueuePolicy.restore]. */
data class QueueRestore(
    val queue: List<MediaFile>,
    val index: Int,
)

/**
 * The pure rules governing what gets written to the persisted audio session and what comes back
 * out of it. No state, no Android, no coroutines — just the decisions.
 *
 * Extracted from `PlaybackViewModel` (P4-E.4, step 1) so that the invariant *Room and ExoPlayer
 * must never disagree on queue content or order* has something to be tested against. Every rule
 * here was previously inline in `persistQueue` and the restore path, and each is a rule you would
 * only discover by reading carefully — which is exactly why they are now pinned by tests rather
 * than by comments.
 *
 * The four queue StateFlows deliberately remain in the ViewModel; see OQ-9.
 */
object QueuePolicy {
    /** A track is treated as finished, and so not resumed, past this fraction of its duration. */
    private const val COMPLETION_FRACTION = 0.99

    /**
     * Whether [queue] may be written to the persisted session.
     *
     * The persisted `current_queue` is the **audio** session only. A queue containing any video is
     * rejected outright so that watching a video cannot wipe the music queue on the next cold
     * start. This is a whole-queue guard, not a per-item filter: a mixed queue is not partially
     * saved, it is not saved at all.
     */
    fun isPersistable(queue: List<MediaFile>): Boolean = queue.none { it.isVideo }

    /** Maps a queue to storage rows, where list position becomes `sortOrder`. */
    fun toEntities(queue: List<MediaFile>): List<QueueItemEntity> =
        queue.mapIndexed { index, media -> QueueItemEntity(media.id, index) }

    /**
     * Rebuilds a queue from storage.
     *
     * - rows whose media no longer exists in [mediaById] are dropped, not treated as errors: files
     *   get deleted off the device between sessions
     * - any video that somehow reached storage is filtered out, defending the audio-only contract
     *   from the read side as well as the write side
     * - [savedIndex] is clamped into range, so a stale index left by a longer previous queue
     *   resumes at the nearest valid track instead of crashing
     *
     * Returns null when nothing restorable remains, which is the caller's signal to fall back to
     * the last played audio track.
     */
    fun restore(
        saved: List<QueueItemEntity>,
        mediaById: Map<Long, MediaFile>,
        savedIndex: Int,
    ): QueueRestore? {
        val queue =
            saved
                .mapNotNull { mediaById[it.mediaId] }
                .filter { !it.isVideo }
        if (queue.isEmpty()) return null
        return QueueRestore(queue, savedIndex.coerceIn(0, queue.size - 1))
    }

    /**
     * The position to resume [history] at, or 0 to start from the beginning.
     *
     * A track watched or listened to past [COMPLETION_FRACTION] is considered finished and restarts
     * rather than resuming one second from the end. A duration of 0 means "not yet known", which
     * must resume rather than be mistaken for a completed track.
     */
    fun resumePosition(history: PlaybackHistory?): Long {
        if (history == null) return 0L
        val unfinished = history.duration == 0L || history.position < history.duration * COMPLETION_FRACTION
        return if (unfinished) history.position else 0L
    }
}
