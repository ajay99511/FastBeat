package com.local.offlinemediaplayer.playback

import com.local.offlinemediaplayer.model.MediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The in-memory queue state: what is queued, what the UI shows, where playback is, and which
 * playlist/album/artist the queue came from.
 *
 * Final step of P4-E.4, taken only once OQ-9's (a) and (b) were in place — [QueuePolicy] with its
 * characterization tests, then [QueuePersistence] for the disk side. This class is the third piece:
 * the four StateFlows that were the ViewModel's spine, with 81 references spread across it.
 *
 * **Play order vs display order.** [currentQueue] is the authoritative play order and mirrors
 * ExoPlayer's media items. [displayQueue] is what the queue sheet renders. They are equal *except*
 * under shuffle, where the display follows Media3's shuffled timeline instead. That relationship
 * was previously implicit — expressed as two adjacent `.value =` assignments in seven places and as
 * a lone assignment in nine others — which made "may these two ever disagree, and when?"
 * unanswerable without reading all sixteen. It is now the difference between [setQueue] (they
 * match) and [setPlayOrder] (the caller recomputes the display separately, because shuffle is on).
 *
 * **Deliberately not a `@Singleton`.** Every other extraction in P4-E is one, but those hold
 * services; this holds *state that used to die with the ViewModel*. Making it process-scoped would
 * silently outlive `PlaybackViewModel` and resurrect a stale queue into a fresh one. Constructed by
 * the ViewModel, so the lifetime is exactly what it was before this change.
 *
 * No coroutines and no Android types: every operation is a synchronous state assignment, so the
 * tests are ordinary JVM tests.
 */
class QueueManager {
    private val _currentQueue = MutableStateFlow<List<MediaFile>>(emptyList())

    /** The authoritative play order — the same order and content as ExoPlayer's media items. */
    val currentQueue: StateFlow<List<MediaFile>> = _currentQueue.asStateFlow()

    private val _displayQueue = MutableStateFlow<List<MediaFile>>(emptyList())

    /** What the queue sheet shows: [currentQueue], or Media3's shuffled order when shuffling. */
    val displayQueue: StateFlow<List<MediaFile>> = _displayQueue.asStateFlow()

    private val _currentIndex = MutableStateFlow<Int?>(null)

    /** Position within [currentQueue]; null when nothing is loaded. */
    val currentIndex: StateFlow<Int?> = _currentIndex.asStateFlow()

    private val _playlistContext = MutableStateFlow<String?>(null)

    /** Where the queue came from — a playlist id, `ALBUM_x`, `ARTIST_x`, `SMART_x`, or null. */
    val playlistContext: StateFlow<String?> = _playlistContext.asStateFlow()

    // Snapshot accessors. The call sites read these far more often than they collect, and
    // `queueManager.queue` reads better than `queueManager.currentQueue.value`.

    val queue: List<MediaFile> get() = _currentQueue.value

    val displayed: List<MediaFile> get() = _displayQueue.value

    val index: Int? get() = _currentIndex.value

    val context: String? get() = _playlistContext.value

    /**
     * Replaces the queue, with the display order matching it.
     *
     * This is the normal path: outside shuffle the two are always equal. Use [setPlayOrder] when
     * shuffle may be active and the display order is about to be recomputed from the timeline.
     */
    fun setQueue(items: List<MediaFile>) {
        _currentQueue.value = items
        _displayQueue.value = items
    }

    /**
     * Replaces the play order **only**, leaving the display order stale on purpose.
     *
     * Every caller follows this with a display refresh derived from Media3's timeline; assigning
     * the play order to the display here would overwrite the shuffled order with the linear one and
     * make the queue sheet jump back to unshuffled for a frame.
     */
    fun setPlayOrder(items: List<MediaFile>) {
        _currentQueue.value = items
    }

    /** Sets the display order alone — the shuffled projection of [currentQueue]. */
    fun setDisplayOrder(items: List<MediaFile>) {
        _displayQueue.value = items
    }

    fun setIndex(value: Int?) {
        _currentIndex.value = value
    }

    fun setPlaylistContext(value: String?) {
        _playlistContext.value = value
    }

    /** Empties the play order. Leaves the index alone — callers clear it separately, as before. */
    fun clearQueue() {
        _currentQueue.value = emptyList()
    }
}
