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
