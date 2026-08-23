package com.local.offlinemediaplayer.playback

import com.local.offlinemediaplayer.data.db.BookmarkEntity
import com.local.offlinemediaplayer.data.db.MediaDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Timestamp bookmarks for the track currently playing.
 *
 * Extracted from `PlaybackViewModel` (P4-E.5). Straight delegation — every statement is the one
 * that was there before, only relocated.
 *
 * [bookmarksFor] deliberately takes the current-track flow as a parameter rather than holding a
 * reference to whatever is playing. That keeps this class free of playback state: it maps "which
 * track is current" to "that track's bookmarks" and owns nothing else, so it stays trivially
 * testable and cannot drift out of sync with the player.
 */
@Singleton
class BookmarkManager
    @Inject
    constructor(
        private val mediaDao: MediaDao,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Bookmarks belonging to whichever media id [currentMediaId] currently emits, switching
         * whenever it changes and emitting an empty list when nothing is playing.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun bookmarksFor(currentMediaId: Flow<Long?>): Flow<List<BookmarkEntity>> =
            currentMediaId.flatMapLatest { id ->
                if (id != null) mediaDao.getBookmarks(id) else flowOf(emptyList())
            }

        fun addBookmark(
            mediaId: Long,
            timestamp: Long,
            label: String,
        ) {
            scope.launch {
                mediaDao.addBookmark(
                    BookmarkEntity(mediaId = mediaId, timestamp = timestamp, label = label),
                )
            }
        }

        fun deleteBookmark(id: Long) {
            scope.launch { mediaDao.deleteBookmark(id) }
        }
    }
