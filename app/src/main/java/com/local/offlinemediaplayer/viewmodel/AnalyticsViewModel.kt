package com.local.offlinemediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.data.db.PlayEvent
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- Data classes for UI consumption ---

data class LibraryStats(
    val songCount: Int = 0,
    val videoCount: Int = 0,
    val playlistCount: Int = 0,
    val totalStorageBytes: Long = 0
)

data class DailyActivity(
    val dayLabel: String,
    val playtimeMinutes: Int = 0,
    val isToday: Boolean = false
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _analyticsUpdateTrigger = MutableStateFlow(0L)

    init {
        // Trigger initial calculate
        refreshAnalytics()
    }

    fun refreshAnalytics() {
        _analyticsUpdateTrigger.value = System.currentTimeMillis()
    }

    val realtimeAnalytics = combine(
        _analyticsUpdateTrigger,
        mediaRepository.audioList,
        mediaRepository.videoList
    ) { _, audio, videos ->
        calculateAnalytics(audio + videos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RealtimeAnalytics())

    val continueWatchingList = combine(
        mediaRepository.videoList,
        mediaDao.getContinueWatching()
    ) { videos, historyItems ->
        historyItems.mapNotNull { history ->
            val video = videos.find { it.id == history.mediaId }
            if (video != null) {
                video to history
            } else null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Library Stats Flow ---
    val libraryStats = combine(
        mediaRepository.audioList,
        mediaRepository.videoList,
        mediaDao.getPlaylistCountFlow()
    ) { audio, videos, playlistCount ->
        val totalStorage = audio.sumOf { it.size } + videos.sumOf { it.size }
        LibraryStats(
            songCount = audio.size,
            videoCount = videos.size,
            playlistCount = playlistCount,
            totalStorageBytes = totalStorage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())

    // --- Weekly Activity Trends Flow ---
    val weeklyActivity = combine(
        _analyticsUpdateTrigger,
        getWeekBoundsFlow()
    ) { _, bounds ->
        calculateWeeklyActivity(bounds.first, bounds.second)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        generateEmptyWeek()
    )

    /** Returns a flow that emits the (mondayMidnight, sundayMidnight) for the current week. */
    private fun getWeekBoundsFlow() = _analyticsUpdateTrigger.combine(
        MutableStateFlow(Unit)
    ) { _, _ ->
        val cal = Calendar.getInstance()
        // Get current day of week (Calendar.MONDAY=2 .. Calendar.SUNDAY=1)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Calculate offset to Monday (Monday=0, Tuesday=1, ..., Sunday=6)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY

        // Move to Monday midnight
        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val mondayMs = cal.timeInMillis

        // Sunday is Monday + 6 days
        val sundayMs = mondayMs + (6L * 24 * 60 * 60 * 1000)
        Pair(mondayMs, sundayMs)
    }

    private suspend fun calculateWeeklyActivity(
        mondayMs: Long,
        sundayMs: Long
    ): List<DailyActivity> = withContext(Dispatchers.IO) {
        val labels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        val todayMs = getNormalizedToday()
        val records = mediaDao.getWeekDailyPlaytimes(mondayMs, sundayMs).firstOrNull() ?: emptyList()
        val playtimeMap = records.associate { it.date to it.totalPlaytimeMs }

        labels.mapIndexed { index, label ->
            val dayMs = mondayMs + (index.toLong() * 24 * 60 * 60 * 1000)
            val minutesPlayed = ((playtimeMap[dayMs] ?: 0L) / 60000).toInt()
            DailyActivity(
                dayLabel = label,
                playtimeMinutes = minutesPlayed,
                isToday = dayMs == todayMs
            )
        }
    }

    private fun generateEmptyWeek(): List<DailyActivity> {
        val labels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val todayIndex = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        return labels.mapIndexed { index, label ->
            DailyActivity(dayLabel = label, isToday = index == todayIndex)
        }
    }

    private fun getNormalizedToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private suspend fun calculateAnalytics(allMedia: List<MediaFile>): RealtimeAnalytics {
        return withContext(Dispatchers.IO) {
            val today = getNormalizedToday()
            val weekStart = today - (6 * 24 * 60 * 60 * 1000)
            val monthStart = today - (29 * 24 * 60 * 60 * 1000)

            val todayMs = mediaDao.getPlaytimeForDay(today).firstOrNull() ?: 0L
            val weekMs = mediaDao.getPlaytimeRange(weekStart, today).firstOrNull() ?: 0L
            val monthMs = mediaDao.getPlaytimeRange(monthStart, today).firstOrNull() ?: 0L

            val avgDailyMs = monthMs / 30

            val activeDays = mediaDao.getActiveDays().firstOrNull() ?: emptyList()
            var currentStreak = 0
            if (activeDays.isNotEmpty()) {
                val todayCheck = activeDays.first()
                if (todayCheck == today || todayCheck == (today - 86400000)) {
                    currentStreak = 1
                    var checkDate = todayCheck
                    for (i in 1 until activeDays.size) {
                        val prevDate = activeDays[i]
                        if (checkDate - prevDate == 86400000L) {
                            currentStreak++
                            checkDate = prevDate
                        } else {
                            break
                        }
                    }
                }
            }

            val overallFavId = mediaDao.getOverallFavoriteMediaId()
            val recentFavId = mediaDao.getMostPlayedMediaIdSince(monthStart)

            val overallFav = allMedia.find { it.id == overallFavId }
            val recentFav = allMedia.find { it.id == recentFavId }

            val currentFavPlayCount = if (recentFavId != null) mediaDao.getAnalytics(recentFavId)?.playCount ?: 0 else 0
            val allTimeFavPlayCount = if (overallFavId != null) mediaDao.getAnalytics(overallFavId)?.playCount ?: 0 else 0

            RealtimeAnalytics(
                    todayPlaytimeMinutes = (todayMs / 60000).toInt(),
                    weekPlaytimeMinutes = (weekMs / 60000).toInt(),
                    avgDailyMinutes = (avgDailyMs / 60000).toInt(),
                    streakDays = currentStreak,
                    currentFavorite = recentFav,
                    allTimeFavorite = overallFav,
                    currentFavoritePlayCount = currentFavPlayCount,
                    allTimeFavoritePlayCount = allTimeFavPlayCount
            )
        }
    }

    fun recordPlay(mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            mediaDao.initAnalytics(mediaId, now)
            mediaDao.incrementPlayCount(mediaId, now)
            mediaDao.logPlayEvent(PlayEvent(mediaId = mediaId, timestamp = now))
            refreshAnalytics()
        }
    }
}
