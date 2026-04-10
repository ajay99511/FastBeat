package com.local.offlinemediaplayer.data

import android.content.Context
import androidx.core.content.edit
import com.local.offlinemediaplayer.ui.screens.AudioSortOption
import com.local.offlinemediaplayer.ui.screens.VideoSortOption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Manages per-playlist sort preference persistence using SharedPreferences.
 * Each playlist stores its sort option and ascending/descending direction independently.
 */
class SortPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("sort_playlists", Context.MODE_PRIVATE)

    // --- Audio Playlist Sort ---

    fun getAudioPlaylistSort(playlistId: String): Pair<AudioSortOption, Boolean> {
        val optionOrdinal = prefs.getInt("audio_sort_$playlistId", AudioSortOption.DEFAULT.ordinal)
        val ascending = prefs.getBoolean("audio_sort_asc_$playlistId", true)
        val option = AudioSortOption.entries.getOrElse(optionOrdinal) { AudioSortOption.DEFAULT }
        return option to ascending
    }

    fun saveAudioPlaylistSort(playlistId: String, option: AudioSortOption, ascending: Boolean) {
        prefs.edit {
            putInt("audio_sort_$playlistId", option.ordinal)
            putBoolean("audio_sort_asc_$playlistId", ascending)
        }
    }

    // --- Video Playlist Sort ---

    fun getVideoPlaylistSort(playlistId: String): Pair<VideoSortOption, Boolean> {
        val optionOrdinal = prefs.getInt("video_sort_$playlistId", VideoSortOption.DEFAULT.ordinal)
        val ascending = prefs.getBoolean("video_sort_asc_$playlistId", true)
        val option = VideoSortOption.entries.getOrElse(optionOrdinal) { VideoSortOption.DEFAULT }
        return option to ascending
    }

    fun saveVideoPlaylistSort(playlistId: String, option: VideoSortOption, ascending: Boolean) {
        prefs.edit {
            putInt("video_sort_$playlistId", option.ordinal)
            putBoolean("video_sort_asc_$playlistId", ascending)
        }
    }
}
