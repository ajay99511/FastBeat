package com.local.offlinemediaplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The stored sort record for one library list, exactly as it exists on disk. */
data class StoredSort(
    val fieldOrdinal: Int,
    /** `null` when the direction was never written — the caller applies the field's own default. */
    val ascending: Boolean?,
)

/**
 * The four library lists that persist a sort. `prefix` is also the *legacy* key: before the
 * field/direction split, each list stored a single combined-enum ordinal under this exact name.
 */
enum class LibrarySort(
    internal val prefix: String,
) {
    AUDIO("sort_audio"),
    VIDEO("sort_video"),
    MOVIES("sort_movies"),
    ALBUMS("sort_albums"),
}

/** The four library lists that persist a grid-vs-list layout choice. */
enum class LibraryLayout(
    internal val key: String,
    internal val default: Boolean,
) {
    VIDEO_GRID("view_video_grid", default = true),
    FOLDER_GRID("view_folder_grid", default = true),
    MOVIE_GRID("view_movie_grid", default = true),

    /** Inverted sense: this one records *list* view, and so defaults to off. */
    ALBUM_LIST("view_album_list", default = false),
}

/**
 * Everything stored in the `app_prefs` file: the persisted audio session, the theme, the video
 * brightness, and the library's sort and layout choices.
 *
 * **Why one class for four unrelated concerns.** `app_prefs` was written directly by
 * `QueuePersistence`, `ThemeViewModel`, `PlaybackViewModel` and `LibraryViewModel` with no shared
 * key registry — nothing stopped two of them colliding on a key, and nobody could answer "what is
 * in this file?" without grepping. DataStore forces the issue: one file admits exactly one
 * [DataStore] instance per process, so migrating those writers independently was never possible.
 * P5-C.3 migrates the file as one unit, and this class is the key registry that was missing. Keys,
 * types and defaults are declared here and nowhere else; callers name *what they want*, never a
 * key string.
 *
 * `AudioEffectsManager` uses a separate prefs file and is deliberately untouched.
 *
 * Reads are `suspend` because DataStore has no synchronous read and `runBlocking` on it risks an
 * ANR. Every caller could already hydrate from a coroutine, or was made able to — see
 * `ThemeViewModel.isLoaded` for the one case where the delay is user-visible.
 *
 * **Key names and value encodings are byte-identical to the SharedPreferences build**, so every
 * migrated entry decodes to exactly what the user had chosen.
 *
 * detekt's TooManyFunctions targets classes doing too many *things*. This does one thing — it is
 * the key registry for one file — and its size is simply two accessors per stored key. Splitting it
 * would mean several classes over one DataStore, which is the arrangement this task exists to end.
 */
@Suppress("TooManyFunctions")
@Singleton
class AppPreferencesManager
    internal constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * The production constructor. The store is built here rather than injected so that no Hilt
         * module is required; `@Singleton` is what upholds the one-instance-per-file rule DataStore
         * enforces. Tests use the internal constructor with a store over a temp file — which is
         * also why this is not a top-level `preferencesDataStore` delegate. A delegate is a
         * process-wide singleton whose migration can only ever run once, and that makes the
         * upgrade path untestable alongside any other test touching the same file.
         */
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(createDataStore(context))

        // ------------------------------------------------------------------ audio session

        suspend fun getQueueIndex(): Int = read(KEY_QUEUE_INDEX, 0)

        suspend fun setQueueIndex(value: Int) = write(KEY_QUEUE_INDEX, value)

        suspend fun getShuffleEnabled(): Boolean = read(KEY_SHUFFLE, false)

        suspend fun setShuffleEnabled(value: Boolean) = write(KEY_SHUFFLE, value)

        suspend fun getRepeatMode(): Int = read(KEY_REPEAT, Player.REPEAT_MODE_OFF)

        suspend fun setRepeatMode(value: Int) = write(KEY_REPEAT, value)

        suspend fun getPlaylistContext(): String? = readOrNull(KEY_CONTEXT)

        suspend fun setPlaylistContext(value: String?) = writeOrRemove(KEY_CONTEXT, value)

        /** The interrupted-session JSON snapshot, or null when there is none. */
        suspend fun getSavedAudioSession(): String? = readOrNull(KEY_SESSION)

        suspend fun setSavedAudioSession(json: String?) = writeOrRemove(KEY_SESSION, json)

        // ------------------------------------------------------------------ theme

        suspend fun getDarkTheme(): Boolean = read(KEY_DARK_MODE, true)

        suspend fun setDarkTheme(value: Boolean) = write(KEY_DARK_MODE, value)

        suspend fun getThemeId(): String = read(KEY_THEME_ID, DEFAULT_THEME_ID)

        suspend fun setThemeId(value: String) = write(KEY_THEME_ID, value)

        // ------------------------------------------------------------------ video brightness

        /** [BRIGHTNESS_UNSET] means "never set — follow the system brightness". */
        suspend fun getVideoBrightness(): Float = read(KEY_BRIGHTNESS, BRIGHTNESS_UNSET)

        suspend fun setVideoBrightness(value: Float) = write(KEY_BRIGHTNESS, value)

        // ------------------------------------------------------------------ library sort

        /** The stored sort for [sort], or null when the field/direction keys were never written. */
        suspend fun getSort(sort: LibrarySort): StoredSort? {
            val prefs = dataStore.data.first()
            val field = prefs[intPreferencesKey(sort.fieldKey)] ?: return null
            return StoredSort(field, prefs[booleanPreferencesKey(sort.ascendingKey)])
        }

        /**
         * The pre-split combined-enum ordinal for [sort], or null if that build never ran here.
         * Read once per list, to seed [setSort] the first time the field/direction keys are absent.
         */
        suspend fun getLegacySortOrdinal(sort: LibrarySort): Int? = readOrNull(intPreferencesKey(sort.prefix))

        suspend fun setSort(
            sort: LibrarySort,
            fieldOrdinal: Int,
            ascending: Boolean,
        ) {
            dataStore.edit { prefs ->
                prefs[intPreferencesKey(sort.fieldKey)] = fieldOrdinal
                prefs[booleanPreferencesKey(sort.ascendingKey)] = ascending
            }
        }

        // ------------------------------------------------------------------ library layout

        suspend fun getLayout(layout: LibraryLayout): Boolean = read(booleanPreferencesKey(layout.key), layout.default)

        suspend fun setLayout(
            layout: LibraryLayout,
            value: Boolean,
        ) = write(booleanPreferencesKey(layout.key), value)

        // ------------------------------------------------------------------ plumbing

        private suspend fun <T> read(
            key: Preferences.Key<T>,
            default: T,
        ): T = dataStore.data.first()[key] ?: default

        private suspend fun <T> readOrNull(key: Preferences.Key<T>): T? = dataStore.data.first()[key]

        private suspend fun <T> write(
            key: Preferences.Key<T>,
            value: T,
        ) {
            dataStore.edit { it[key] = value }
        }

        private suspend fun <T> writeOrRemove(
            key: Preferences.Key<T>,
            value: T?,
        ) {
            dataStore.edit { if (value != null) it[key] = value else it.remove(key) }
        }

        companion object {
            const val PREFS_NAME = "app_prefs"
            const val DEFAULT_THEME_ID = "orange"
            const val BRIGHTNESS_UNSET = -1f

            private val KEY_QUEUE_INDEX = intPreferencesKey("last_queue_index")
            private val KEY_SHUFFLE = booleanPreferencesKey("last_shuffle_enabled")
            private val KEY_REPEAT = intPreferencesKey("last_repeat_mode")
            private val KEY_CONTEXT = stringPreferencesKey("last_playlist_context")
            private val KEY_SESSION = stringPreferencesKey("saved_audio_session")
            private val KEY_DARK_MODE = booleanPreferencesKey("is_dark_mode")
            private val KEY_THEME_ID = stringPreferencesKey("current_theme_id")
            private val KEY_BRIGHTNESS = floatPreferencesKey("video_brightness")

            private val LibrarySort.fieldKey get() = prefix + "_field"

            private val LibrarySort.ascendingKey get() = prefix + "_asc"

            /**
             * [SharedPreferencesMigration] performs the one-time read-old to write-new to
             * delete-old handover for the whole file. Using the platform migration rather than
             * hand-rolling it matters: it runs inside DataStore's own transaction, it deletes
             * `shared_prefs/app_prefs.xml` only once the new file is durably written, and it
             * therefore cannot half-apply if the process dies partway through. Passing no key list
             * migrates *every* key, which is exactly the point — see the class doc.
             *
             * [produceFile] is overridable so the migration test can exercise *this* wiring — the
             * real migration, on a real legacy file — over a temp destination it controls, rather
             * than re-declaring the migration and testing a copy of it.
             */
            internal fun createDataStore(
                context: Context,
                produceFile: () -> File = { context.preferencesDataStoreFile(PREFS_NAME) },
            ): DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    migrations = listOf(SharedPreferencesMigration(context, PREFS_NAME)),
                    produceFile = produceFile,
                )
        }
    }
