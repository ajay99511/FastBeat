package com.local.offlinemediaplayer.domain

import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.data.db.PlayEvent
import javax.inject.Inject

/**
 * Records that a track was played: the aggregate counters and the individual event.
 *
 * Extracted from `PlaybackAnalyticsTracker.recordPlay` in P5-A.1. This one *is* DAO-injected as the
 * card describes, because unlike the other two it genuinely writes.
 *
 * **The invariant is the shared timestamp.** All three writes take the same [now]. `lastPlayed` on
 * the analytics row and the `PlayEvent` timestamp must agree, because the smart playlists sort by
 * one and the activity chart aggregates the other — three separate `System.currentTimeMillis()`
 * calls would drift across a day boundary and put a play in one day's chart while the "last played"
 * says another. [now] is a parameter rather than read inside for the same reason it is testable:
 * the caller owns the clock.
 *
 * Failure handling stays at the call site. This deliberately does not catch: a use case that
 * swallows its own errors cannot tell a caller that analytics are silently not being recorded, and
 * `PlaybackAnalyticsTracker` already logs and continues, which is the right behaviour for something
 * that must never interrupt playback.
 */
class LogPlayEventUseCase
    @Inject
    constructor(
        private val mediaDao: MediaDao,
    ) {
        suspend operator fun invoke(
            mediaId: Long,
            now: Long,
        ) {
            mediaDao.initAnalytics(mediaId, now)
            mediaDao.incrementPlayCount(mediaId, now)
            mediaDao.logPlayEvent(PlayEvent(mediaId = mediaId, timestamp = now))
        }
    }
