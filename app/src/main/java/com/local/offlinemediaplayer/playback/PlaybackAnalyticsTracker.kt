package com.local.offlinemediaplayer.playback

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.data.db.PlayEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Owns playback analytics: when a listen counts as a "play", when leaving a track counts as a
 * "skip", and how much time is credited to today's total.
 *
 * Extracted from `PlaybackViewModel` (P4-E.2). Behaviour is a faithful port — thresholds, flush
 * cadence and the fire-and-forget write strategy are unchanged. Two pre-existing quirks were
 * deliberately preserved rather than fixed here, because silently changing what the analytics
 * report inside a refactor commit would make the refactor unreviewable; both are logged as F-34
 * and F-30 for a separate task.
 *
 * THREADING — all public methods are safe to call from the main thread and never block. Database
 * writes are dispatched onto [trackerScope] so the caller's 500 ms position loop is never
 * suspended, matching the "fire and forget IO, DO NOT suspend the loop" contract the original code
 * relied on.
 */
@Singleton
class PlaybackAnalyticsTracker
    @Inject
    constructor(
        private val mediaDao: MediaDao,
    ) {
        private companion object {
            const val TAG = "PlaybackAnalyticsTracker"

            /** A track counts as played after 30 s, or half its length if it is shorter. */
            const val PLAY_COUNT_THRESHOLD_MS = 30_000L

            /** …but never sooner than 5 s, so very short clips cannot count instantly. */
            const val MIN_PLAY_THRESHOLD_MS = 5_000L

            /** Daily playtime is flushed to the database every 60 ticks (60 × 500 ms = 30 s). */
            const val FLUSH_EVERY_TICKS = 60

            const val DAY_IN_MILLIS = 86_400_000L
        }

        /**
         * Scope for the fire-and-forget database writes.
         *
         * Unlike `QueuePersistence.saveQueue`, these genuinely cannot be `suspend`: the caller is a
         * 500 ms position loop that must never block (see F-31 for the case where the opposite was
         * true). It is therefore overridable so tests can substitute a deterministic dispatcher
         * instead of racing an IO thread.
         */
        @VisibleForTesting
        internal var trackerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Milliseconds of the CURRENT track listened to so far, reset on every track change. */
        private var currentTrackPlaytimeMs = 0L

        /** Guards against counting the same track as a play twice. */
        private var hasLoggedCurrentTrack = false

        /** Playtime accumulated since the last database flush. */
        private var pendingDailyPlaytimeMs = 0L

        private var tickCount = 0

        /**
         * The day playtime is credited to, captured once when the session starts. A session that
         * runs past midnight rolls over at the next flush, so time is credited to the day it was
         * actually listened in (F-30). Rollover is checked at flush points rather than every tick,
         * which bounds misattribution at one flush interval — 30 s — instead of a whole session.
         */
        private var sessionDayKey = 0L

        /**
         * Clock source. Injectable so the midnight-rollover behaviour is testable; a bug that only
         * reproduces once a day at a specific hour is otherwise untestable in practice.
         */
        @VisibleForTesting
        internal var now: () -> Long = { System.currentTimeMillis() }

        /** Precomputed midnight after [sessionDayKey], so the per-tick check is a long compare. */
        private var nextDayBoundaryMs = Long.MAX_VALUE

        /** True while a track has been listened to but has not yet reached the play threshold. */
        val isCurrentTrackUnlogged: Boolean
            get() = !hasLoggedCurrentTrack

        /**
         * Begins a playback session. Ensures today's playtime row exists and resets the flush
         * cadence. Called when the position loop starts.
         */
        fun onSessionStarted() {
            sessionDayKey = normalizedToday()
            nextDayBoundaryMs = sessionDayKey + DAY_IN_MILLIS
            tickCount = 0
            pendingDailyPlaytimeMs = 0L
            val day = sessionDayKey
            trackerScope.launch {
                try {
                    mediaDao.initDailyPlaytime(day)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize daily playtime", e)
                }
            }
        }

        /**
         * Ends a playback session, persisting whatever has accrued since the last flush.
         *
         * Without this, up to one flush interval (30 s) of listening time was discarded every time
         * playback stopped, because the position loop is cancelled and the in-memory accumulator
         * went with it (F-34). Pausing and resuming repeatedly could lose most of a listening
         * session from the user's statistics.
         */
        fun onSessionStopped() {
            flushDailyPlaytime()
        }

        /**
         * Resets per-track state. Must be called on every track change — without it a long listen
         * to one track would immediately satisfy the threshold for the next one, counting a play
         * the user never made. This is the "no double-counting across track transitions" invariant.
         */
        fun onTrackChanged() {
            currentTrackPlaytimeMs = 0L
            hasLoggedCurrentTrack = false
        }

        /**
         * Advances analytics by one position-loop tick. Call only while playback is actually
         * playing; a paused tick must not accrue playtime.
         *
         * @param mediaId the currently playing track, or null when the player is on an item the
         *   library does not know about. Daily playtime still accrues in that case — matching the
         *   original loop, which credited time whenever playback was playing regardless of whether
         *   the track had been resolved — but no play can be recorded without an id.
         * @param durationMs its duration, or 0 when not yet known — the play threshold scales with
         *   it, which is why the card's two-argument signature could not be used as written
         * @param deltaMs elapsed time since the previous tick
         */
        fun onPositionUpdate(
            mediaId: Long?,
            durationMs: Long,
            deltaMs: Long,
        ) {
            rollOverIfDayChanged()
            pendingDailyPlaytimeMs += deltaMs

            if (mediaId != null && !hasLoggedCurrentTrack) {
                currentTrackPlaytimeMs += deltaMs
                if (currentTrackPlaytimeMs >= playThresholdFor(durationMs)) {
                    hasLoggedCurrentTrack = true
                    recordPlay(mediaId)
                }
            }

            // Cadence preserved exactly from the original loop: the check runs before the counter
            // is incremented, so the first tick of a session flushes immediately and every 60th
            // tick thereafter.
            if (tickCount % FLUSH_EVERY_TICKS == 0) {
                flushDailyPlaytime()
            }
            tickCount++
        }

        /**
         * Records that the user manually left [mediaId] before it counted as a play. This powers
         * the "Most Skipped" smart playlist. `initAnalytics` ensures the row exists first; both
         * statements are IGNORE/UPDATE, so a genuine play recorded later is never lost.
         */
        fun recordSkip(mediaId: Long) {
            trackerScope.launch {
                try {
                    mediaDao.initAnalytics(mediaId, System.currentTimeMillis())
                    mediaDao.incrementSkipCount(mediaId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record skip for $mediaId", e)
                }
            }
        }

        private fun recordPlay(mediaId: Long) {
            trackerScope.launch {
                try {
                    val now = System.currentTimeMillis()
                    // AnalyticsViewModel reacts to these writes automatically via Flow.
                    mediaDao.initAnalytics(mediaId, now)
                    mediaDao.incrementPlayCount(mediaId, now)
                    mediaDao.logPlayEvent(PlayEvent(mediaId = mediaId, timestamp = now))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record play for $mediaId", e)
                }
            }
        }

        private fun flushDailyPlaytime() {
            if (pendingDailyPlaytimeMs > 0) {
                val timeToSave = pendingDailyPlaytimeMs
                val day = sessionDayKey
                pendingDailyPlaytimeMs = 0L
                trackerScope.launch {
                    try {
                        mediaDao.addToDailyPlaytime(day, timeToSave)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save daily playtime", e)
                    }
                }
            }
        }

        /**
         * Advances accrual onto the new day once the clock passes midnight.
         *
         * Checked **before** the tick is accrued, and flushes first, so time listened before
         * midnight is credited to the old day and time after it to the new one. Doing this at flush
         * time instead would mis-date up to a whole flush interval — which is exactly what the
         * first attempt at this fix did, and what `timeListenedAfterMidnightIsCreditedToTheNewDay`
         * caught.
         *
         * The check is a long comparison against a precomputed boundary rather than a `Calendar`
         * allocation, because it runs on every 500 ms tick.
         */
        private fun rollOverIfDayChanged() {
            if (now() < nextDayBoundaryMs) return
            flushDailyPlaytime()
            sessionDayKey = normalizedToday()
            nextDayBoundaryMs = sessionDayKey + DAY_IN_MILLIS
            val day = sessionDayKey
            trackerScope.launch {
                try {
                    mediaDao.initDailyPlaytime(day)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize daily playtime after midnight rollover", e)
                }
            }
        }

        private fun playThresholdFor(durationMs: Long): Long {
            val scaled = if (durationMs > 0) min(PLAY_COUNT_THRESHOLD_MS, durationMs / 2) else PLAY_COUNT_THRESHOLD_MS
            return max(MIN_PLAY_THRESHOLD_MS, scaled)
        }

        private fun normalizedToday(): Long {
            val c = Calendar.getInstance()
            c.timeInMillis = now()
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
    }
