package com.local.offlinemediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.domain.AnalyticsDays
import com.local.offlinemediaplayer.domain.CalculateStreakUseCase
import com.local.offlinemediaplayer.domain.GetContinueWatchingUseCase
import com.local.offlinemediaplayer.domain.ObserveCurrentDayUseCase
import com.local.offlinemediaplayer.domain.PeriodChange
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// --- Data classes for UI consumption ---

/**
 * The counts-and-storage summary at the top of the stats surface.
 *
 * [totalStorageBytes] is derived rather than stored, so the headline figure cannot drift away from
 * the breakdown printed underneath it — the previous shape accepted a total as a constructor
 * argument, and the one it was given left images out entirely while the card called itself
 * "Total Storage Used".
 */
data class LibraryStats(
    val songCount: Int = 0,
    val videoCount: Int = 0,
    val imageCount: Int = 0,
    val playlistCount: Int = 0,
    val audioStorageBytes: Long = 0,
    val videoStorageBytes: Long = 0,
    val imageStorageBytes: Long = 0,
) {
    val totalStorageBytes: Long
        get() = audioStorageBytes + videoStorageBytes + imageStorageBytes
}

data class DailyActivity(
    val dayLabel: String,
    val playtimeMinutes: Int = 0,
    val isToday: Boolean = false,
)

/**
 * The long-window numbers: everything ever recorded, and how the current week compares with the one
 * before it.
 *
 * Lifetime and momentum share one flow because they share one question — *is this going anywhere?*
 * — and because computing them together is what keeps [weekOverWeek] consistent with the week total
 * it is drawn beside. Splitting them would leave the percentage free to describe a week the screen
 * is no longer showing.
 *
 * [firstActiveDay] is null until something has been played. That is a real state with a real
 * rendering, not a missing value to paper over with a zero.
 */
data class ListeningTotals(
    val lifetimeMinutes: Int = 0,
    val lifetimePlays: Int = 0,
    val firstActiveDay: Long? = null,
    val thisWeekMinutes: Int = 0,
    val previousWeekMinutes: Int = 0,
    val weekOverWeek: PeriodChange = PeriodChange.NoBaseline,
)

/**
 * Folds the indexed media lists and the playlist count into [LibraryStats].
 *
 * Top-level and `internal` rather than a lambda inside the `combine` so that the one rule this has
 * actually got wrong — *every* indexed media type is counted — is pinned by a test rather than by
 * reading a flow declaration. The bug it replaces was invisible for exactly that reason: the fold
 * summed two of the three lists and nothing said so out loud.
 */
internal fun libraryStatsOf(
    audio: List<MediaFile>,
    videos: List<MediaFile>,
    images: List<MediaFile>,
    playlistCount: Int,
): LibraryStats =
    LibraryStats(
        songCount = audio.size,
        videoCount = videos.size,
        imageCount = images.size,
        playlistCount = playlistCount,
        audioStorageBytes = audio.sumOf { it.size },
        videoStorageBytes = videos.sumOf { it.size },
        imageStorageBytes = images.sumOf { it.size },
    )

@HiltViewModel
class AnalyticsViewModel
    @Inject
    constructor(
        private val mediaDao: MediaDao,
        private val mediaRepository: MediaRepository,
        private val calculateStreak: CalculateStreakUseCase,
        private val getContinueWatching: GetContinueWatchingUseCase,
        observeCurrentDay: ObserveCurrentDayUseCase,
    ) : ViewModel() {
        /**
         * The day every window below is measured in, re-emitted at each midnight.
         *
         * This replaces a `MutableStateFlow` seeded once at construction plus a public
         * `refreshAnalytics()` that nothing ever called. The trigger's only real job was to make the
         * day-dependent windows recompute, and it could not do it: resubscribing already recomputed
         * them, so the sole case it was needed for — the screen staying subscribed across midnight —
         * was the one case it never covered. See [ObserveCurrentDayUseCase].
         */
        private val currentDay = observeCurrentDay()

        @OptIn(ExperimentalCoroutinesApi::class)
        val realtimeAnalytics =
            combine(
                currentDay,
                mediaRepository.audioList,
                mediaRepository.videoList,
            ) { today, audio, videos ->
                today to (audio + videos)
            }.flatMapLatest { (today, allMedia) ->
                // Calendar days, not millisecond arithmetic. `date >= :start` against a bound that
                // has drifted an hour excludes the day it was meant to include, so the rolling
                // "last 7 days" silently becomes six. See AnalyticsDays.daysBefore.
                val weekStart = AnalyticsDays.daysBefore(today, DAYS_IN_WINDOW - 1)
                val monthStart = AnalyticsDays.daysBefore(today, DAYS_IN_MONTH_WINDOW - 1)

                // Using vararg combine for > 5 flows
                combine(
                    mediaDao.getPlaytimeForDay(today),
                    mediaDao.getPlaytimeRange(weekStart, today),
                    mediaDao.getPlaytimeRange(monthStart, today),
                    mediaDao.getActiveDays(),
                    mediaDao.getOverallFavoriteMediaIdFlow(),
                    mediaDao.getMostPlayedMediaIdSinceFlow(monthStart),
                ) { args ->
                    val todayMs = args[0] as Long? ?: 0L
                    val weekMs = args[1] as Long? ?: 0L
                    val monthMs = args[2] as Long? ?: 0L

                    @Suppress("UNCHECKED_CAST")
                    val activeDays = args[3] as List<Long>
                    val overallFavId = args[4] as Long?
                    val recentFavId = args[5] as Long?

                    val avgDailyMs = monthMs / DAYS_IN_MONTH_WINDOW

                    val currentStreak = calculateStreak(activeDays, today)

                    val overallFav = allMedia.find { it.id == overallFavId }
                    val recentFav = allMedia.find { it.id == recentFavId }

                    // Fetch play counts (suspend call inside flow map is fine as it's on a background thread)
                    // But let's avoid blocking. For simplicity now, we use a small DB fetch.
                    val currentFavPlayCount =
                        recentFavId?.let { id ->
                            // Using a block to avoid let inference issues
                            mediaDao.getAnalytics(id)?.playCount
                        } ?: 0
                    val allTimeFavPlayCount =
                        overallFavId?.let { id ->
                            mediaDao.getAnalytics(id)?.playCount
                        } ?: 0

                    RealtimeAnalytics(
                        todayPlaytimeMinutes = (todayMs / 60000).toInt(),
                        weekPlaytimeMinutes = (weekMs / 60000).toInt(),
                        avgDailyMinutes = (avgDailyMs / 60000).toInt(),
                        streakDays = currentStreak,
                        currentFavorite = recentFav,
                        allTimeFavorite = overallFav,
                        currentFavoritePlayCount = currentFavPlayCount,
                        allTimeFavoritePlayCount = allTimeFavPlayCount,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RealtimeAnalytics())

        val continueWatchingList =
            combine(
                mediaRepository.videoList,
                mediaDao.getContinueWatching(),
                getContinueWatching::invoke,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val libraryStats =
            combine(
                mediaRepository.audioList,
                mediaRepository.videoList,
                mediaRepository.imageList,
                mediaDao.getPlaylistCountFlow(),
                ::libraryStatsOf,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())

        /**
         * Lifetime totals and week-over-week momentum.
         *
         * Five flows, so this is the *typed* `combine` rather than the vararg one — no `args[i] as
         * Long?` casting, which is the only part of `realtimeAnalytics` the compiler cannot check.
         *
         * The window boundaries are calendar days from [AnalyticsDays], not millisecond arithmetic:
         * the range predicate is `date >= :start`, so a start that lands an hour off excludes the
         * day it was meant to include and silently shortens the window.
         *
         * Both week totals are read here rather than reusing the one in `realtimeAnalytics`. It is
         * the same query against the same table, and paying for it twice is what guarantees the
         * percentage and the two totals it was computed from can never describe different weeks.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        val listeningTotals =
            currentDay
                .flatMapLatest { today ->
                    val thisWeekStart = AnalyticsDays.daysBefore(today, DAYS_IN_WINDOW - 1)
                    val previousWeekEnd = AnalyticsDays.daysBefore(today, DAYS_IN_WINDOW)
                    val previousWeekStart = AnalyticsDays.daysBefore(today, DAYS_IN_WINDOW * 2 - 1)

                    combine(
                        mediaDao.getTotalPlaytimeFlow(),
                        mediaDao.getTotalPlayCountFlow(),
                        mediaDao.getFirstActiveDayFlow(),
                        mediaDao.getPlaytimeRange(thisWeekStart, today),
                        mediaDao.getPlaytimeRange(previousWeekStart, previousWeekEnd),
                    ) { lifetimeMs, plays, firstDay, thisWeekMs, previousWeekMs ->
                        val thisWeek = thisWeekMs ?: 0L
                        val previousWeek = previousWeekMs ?: 0L

                        ListeningTotals(
                            lifetimeMinutes = ((lifetimeMs ?: 0L) / MS_PER_MINUTE).toInt(),
                            lifetimePlays = plays,
                            firstActiveDay = firstDay,
                            thisWeekMinutes = (thisWeek / MS_PER_MINUTE).toInt(),
                            previousWeekMinutes = (previousWeek / MS_PER_MINUTE).toInt(),
                            // Compared in milliseconds, before the truncation to minutes, so the
                            // percentage describes what was actually listened to rather than what
                            // survived rounding.
                            weekOverWeek = PeriodChange.between(previousWeek, thisWeek),
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListeningTotals())

        @OptIn(ExperimentalCoroutinesApi::class)
        val weeklyActivity =
            currentDay
                .flatMapLatest { today ->
                    // Day keys, not an arithmetic span: the week is stepped a calendar day at a
                    // time so the keys still match the table's across a DST shift. See AnalyticsDays.
                    val week = AnalyticsDays.weekOf(today)

                    mediaDao.getWeekDailyPlaytimes(week.first(), week.last()).map { records ->
                        val playtimeByDay = records.associate { it.date to it.totalPlaytimeMs }

                        week.mapIndexed { index, dayKey ->
                            DailyActivity(
                                dayLabel = DAY_LABELS[index],
                                playtimeMinutes = ((playtimeByDay[dayKey] ?: 0L) / MS_PER_MINUTE).toInt(),
                                isToday = dayKey == today,
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EMPTY_WEEK)

        private companion object {
            val DAY_LABELS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

            const val MS_PER_MINUTE = 60_000L

            /**
             * Length of the rolling "last 7 days" window, and of the week it is compared against.
             *
             * Named because the two windows must agree: comparing a 7-day total against a 6-day one
             * would produce a percentage that looks like a behaviour change and is an arithmetic
             * artefact. Both are derived from this one constant so they cannot drift apart.
             */
            const val DAYS_IN_WINDOW = 7

            /** Length of the rolling window behind the daily average. */
            const val DAYS_IN_MONTH_WINDOW = 30

            /**
             * Placeholder shown until [currentDay] emits, which it does on first collection.
             *
             * No day is marked as today on purpose: the alternative is a second, independent read
             * of the clock whose only job is to be replaced microseconds later, and a placeholder
             * that highlights the wrong bar is worse than one that highlights none.
             */
            val EMPTY_WEEK = DAY_LABELS.map { DailyActivity(dayLabel = it) }
        }
    }
