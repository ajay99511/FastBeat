package com.local.offlinemediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    private val _analyticsUpdateTrigger = MutableStateFlow(System.currentTimeMillis())

    fun refreshAnalytics() {
        _analyticsUpdateTrigger.value = System.currentTimeMillis()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val realtimeAnalytics = combine(
        _analyticsUpdateTrigger,
        mediaRepository.audioList,
        mediaRepository.videoList
    ) { _, audio, videos ->
        audio + videos
    }.flatMapLatest { allMedia ->
        val today = getNormalizedToday()
        val weekStart = today - (6L * 24 * 60 * 60 * 1000)
        val monthStart = today - (29L * 24 * 60 * 60 * 1000)

        // Using vararg combine for > 5 flows
        combine(
            mediaDao.getPlaytimeForDay(today),
            mediaDao.getPlaytimeRange(weekStart, today),
            mediaDao.getPlaytimeRange(monthStart, today),
            mediaDao.getActiveDays(),
            mediaDao.getOverallFavoriteMediaIdFlow(),
            mediaDao.getMostPlayedMediaIdSinceFlow(monthStart)
        ) { args ->
            val todayMs = args[0] as Long? ?: 0L
            val weekMs = args[1] as Long? ?: 0L
            val monthMs = args[2] as Long? ?: 0L
            @Suppress("UNCHECKED_CAST")
            val activeDays = args[3] as List<Long>
            val overallFavId = args[4] as Long?
            val recentFavId = args[5] as Long?

            val avgDailyMs = monthMs / 30

            var currentStreak = 0
            if (activeDays.isNotEmpty()) {
                val lastActive = activeDays.first()
                if (lastActive == today || lastActive == (today - 86400000L)) {
                    currentStreak = 1
                    var checkDate = lastActive
                    for (i in 1 until activeDays.size) {
                        val prevDate = activeDays[i]
                        if (checkDate - prevDate == 86400000L) {
                            currentStreak++
                            checkDate = prevDate
                        } else break
                    }
                }
            }

            val overallFav = allMedia.find { it.id == overallFavId }
            val recentFav = allMedia.find { it.id == recentFavId }

            // Fetch play counts (suspend call inside flow map is fine as it's on a background thread)
            // But let's avoid blocking. For simplicity now, we use a small DB fetch.
            val currentFavPlayCount = recentFavId?.let { id ->
                // Using a block to avoid let inference issues
                mediaDao.getAnalytics(id)?.playCount
            } ?: 0
            val allTimeFavPlayCount = overallFavId?.let { id ->
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
                allTimeFavoritePlayCount = allTimeFavPlayCount
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RealtimeAnalytics())

    val continueWatchingList = combine(
        mediaRepository.videoList,
        mediaDao.getContinueWatching()
    ) { videos, historyItems ->
        historyItems.mapNotNull { history ->
            val video = videos.find { it.id == history.mediaId }
            video?.let { it to history }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val weeklyActivity = _analyticsUpdateTrigger.flatMapLatest {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY

        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val mondayMs = cal.timeInMillis
        val sundayMs = mondayMs + (6L * 24 * 60 * 60 * 1000)

        mediaDao.getWeekDailyPlaytimes(mondayMs, sundayMs).map { records ->
            val labels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
            val todayMs = getNormalizedToday()
            val playtimeMap = records.associate { it.date to it.totalPlaytimeMs }

            labels.mapIndexed { index, label ->
                val dayMs = mondayMs + (index.toLong() * 24 * 60 * 60 * 1000)
                DailyActivity(
                    dayLabel = label,
                    playtimeMinutes = ((playtimeMap[dayMs] ?: 0L) / 60000).toInt(),
                    isToday = dayMs == todayMs
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), generateEmptyWeek())

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
}
