package com.local.offlinemediaplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.local.offlinemediaplayer.ui.screens.AudioSortOption
import com.local.offlinemediaplayer.ui.screens.VideoSortOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val SORT_PREFS_NAME = "sort_playlists"

/**
 * The DataStore backing the per-playlist sort preferences.
 *
 * Declared as a top-level delegate rather than built inside the class deliberately: DataStore throws
 * if two instances are ever created for the same file, and this class was not a `@Singleton` before
 * this change. The delegate guarantees exactly one instance per process however many times the class
 * is constructed — belt and braces alongside the `@Singleton` added below.
 *
 * [SharedPreferencesMigration] performs the one-time read-old → write-new → delete-old handover.
 * Using the platform migration rather than hand-rolling it matters: it runs inside DataStore's own
 * transaction, it only deletes `shared_prefs/sort_playlists.xml` once the new file has been durably
 * written, and it therefore cannot half-apply if the process dies partway through.
 *
 * The two files share a name without colliding — SharedPreferences lives under `shared_prefs/`,
 * DataStore under `datastore/`.
 */
private val Context.sortDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SORT_PREFS_NAME,
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, SORT_PREFS_NAME)) },
)

/**
 * Per-playlist sort preferences: which field a list is sorted by, and in which direction.
 *
 * Migrated from SharedPreferences to Preferences DataStore in P5-C.2. Preferences rather than Proto
 * because the keys are generated at runtime from playlist ids — an unbounded key space Proto cannot
 * express without a schema redesign. See OQ-3.
 *
 * Reads are `suspend` because DataStore has no synchronous read, and `runBlocking` on it risks an
 * ANR. This costs nothing at the call sites: each screen already hydrated its sort inside a
 * `LaunchedEffect`, so the suspend read drops into a coroutine that was there anyway.
 *
 * **Key names and value encodings are unchanged** — the enum `ordinal` is still what is stored — so
 * a migrated entry decodes to exactly the sort the user had chosen.
 */
@Singleton
class SortPreferencesManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dataStore get() = context.sortDataStore

        // --- Audio Playlist Sort ---

        suspend fun getAudioPlaylistSort(playlistId: String): Pair<AudioSortOption, Boolean> {
            val prefs = dataStore.data.first()
            val ordinal = prefs[audioSortKey(playlistId)] ?: AudioSortOption.DEFAULT.ordinal
            val ascending = prefs[audioAscKey(playlistId)] ?: true
            // getOrElse guards an ordinal written by a build whose enum had more entries: removing
            // or reordering a constant must not crash on data already on disk.
            return AudioSortOption.entries.getOrElse(ordinal) { AudioSortOption.DEFAULT } to ascending
        }

        suspend fun saveAudioPlaylistSort(
            playlistId: String,
            option: AudioSortOption,
            ascending: Boolean,
        ) {
            dataStore.edit { prefs ->
                prefs[audioSortKey(playlistId)] = option.ordinal
                prefs[audioAscKey(playlistId)] = ascending
            }
        }

        // --- Video Playlist Sort ---

        suspend fun getVideoPlaylistSort(playlistId: String): Pair<VideoSortOption, Boolean> {
            val prefs = dataStore.data.first()
            val ordinal = prefs[videoSortKey(playlistId)] ?: VideoSortOption.DEFAULT.ordinal
            val ascending = prefs[videoAscKey(playlistId)] ?: true
            return VideoSortOption.entries.getOrElse(ordinal) { VideoSortOption.DEFAULT } to ascending
        }

        suspend fun saveVideoPlaylistSort(
            playlistId: String,
            option: VideoSortOption,
            ascending: Boolean,
        ) {
            dataStore.edit { prefs ->
                prefs[videoSortKey(playlistId)] = option.ordinal
                prefs[videoAscKey(playlistId)] = ascending
            }
        }

        private companion object {
            // Key strings are byte-identical to the SharedPreferences originals, so migrated
            // entries are found by these same lookups.
            fun audioSortKey(id: String) = intPreferencesKey("audio_sort_$id")

            fun audioAscKey(id: String) = booleanPreferencesKey("audio_sort_asc_$id")

            fun videoSortKey(id: String) = intPreferencesKey("video_sort_$id")

            fun videoAscKey(id: String) = booleanPreferencesKey("video_sort_asc_$id")
        }
    }
