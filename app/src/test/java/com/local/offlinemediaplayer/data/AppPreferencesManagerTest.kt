package com.local.offlinemediaplayer.data

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * P5-C.3 moved the whole `app_prefs` file from SharedPreferences to Preferences DataStore. Two
 * things have to hold: an existing install must not lose anything on upgrade, and the defaults an
 * absent key falls back to must be the ones the SharedPreferences build used.
 *
 * The second matters as much as the first. Every one of these values is read through a `?: default`
 * now, so a wrong default here is indistinguishable from a lost preference at the call site — the
 * app would simply open on the wrong theme, or resume with shuffle off, and nothing would look
 * broken.
 *
 * Each test builds its own [AppPreferencesManager] over its own temp file. That is deliberate: a
 * top-level `preferencesDataStore` delegate is a process-wide singleton whose migration runs exactly
 * once, so the P5-C.2 sort-preference migration could only ever be covered by a single test method
 * in a class no other test was allowed to touch. Making the store constructor-injectable removes
 * that constraint entirely — [AppPreferencesManager.createDataStore] is still the code under test,
 * only its destination is redirected.
 */
@RunWith(RobolectricTestRunner::class)
class AppPreferencesManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The real production wiring — migration included — writing to a file this test owns. */
    private fun manager(): AppPreferencesManager =
        AppPreferencesManager(
            AppPreferencesManager.createDataStore(context) {
                File(tempFolder.newFolder(), "app_prefs.preferences_pb")
            },
        )

    private fun seedLegacyPrefs(seed: android.content.SharedPreferences.Editor.() -> Unit) {
        context
            .getSharedPreferences(AppPreferencesManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply(seed)
            .commit()
    }

    // ------------------------------------------------------------------ upgrade path

    /**
     * The whole file migrates as one unit. `app_prefs` was written by four unrelated classes, so a
     * per-writer migration could leave the file half-moved: the queue would resume from DataStore
     * while the theme still read a SharedPreferences file that had already been deleted.
     */
    @Test
    fun everyKeyWrittenByTheSharedPreferencesBuildSurvivesTheUpgrade() =
        runBlocking {
            seedLegacyPrefs {
                // QueuePersistence
                putInt("last_queue_index", 7)
                putBoolean("last_shuffle_enabled", true)
                putInt("last_repeat_mode", Player.REPEAT_MODE_ALL)
                putString("last_playlist_context", "ALBUM_42")
                putString("saved_audio_session", "{\"queueIds\":[1]}")
                // ThemeViewModel
                putBoolean("is_dark_mode", false)
                putString("current_theme_id", "green")
                // PlaybackViewModel
                putFloat("video_brightness", 0.42f)
                // LibraryViewModel — sorts and layouts
                putInt("sort_audio_field", 3)
                putBoolean("sort_audio_asc", false)
                putInt("sort_albums_field", 1)
                putBoolean("view_video_grid", false)
                putBoolean("view_album_list", true)
            }

            val prefs = manager()

            assertEquals(7, prefs.getQueueIndex())
            assertTrue(prefs.getShuffleEnabled())
            assertEquals(Player.REPEAT_MODE_ALL, prefs.getRepeatMode())
            assertEquals("ALBUM_42", prefs.getPlaylistContext())
            assertEquals("{\"queueIds\":[1]}", prefs.getSavedAudioSession())
            assertFalse(prefs.getDarkTheme())
            assertEquals("green", prefs.getThemeId())
            assertEquals(0.42f, prefs.getVideoBrightness(), 0.0001f)
            assertEquals(StoredSort(3, ascending = false), prefs.getSort(LibrarySort.AUDIO))
            assertEquals(StoredSort(1, ascending = null), prefs.getSort(LibrarySort.ALBUMS))
            assertFalse(prefs.getLayout(LibraryLayout.VIDEO_GRID))
            assertTrue(prefs.getLayout(LibraryLayout.ALBUM_LIST))
        }

    /**
     * The legacy file is emptied once migrated, so the migration cannot run a second time and
     * resurrect stale values over newer ones.
     */
    @Test
    fun theLegacySharedPreferencesFileIsClearedOnceMigrated() =
        runBlocking {
            seedLegacyPrefs { putInt("last_queue_index", 5) }

            assertEquals(5, manager().getQueueIndex())

            val legacy = context.getSharedPreferences(AppPreferencesManager.PREFS_NAME, Context.MODE_PRIVATE)
            assertTrue("legacy prefs should be emptied once migrated, found: ${legacy.all}", legacy.all.isEmpty())
        }

    /**
     * The pre-split sort key. `sort_audio` held a combined `SortOption` ordinal before the
     * field/direction split; `LibraryViewModel` still converts it once when the new keys are
     * absent, so the migration must carry it across rather than drop it as unrecognised.
     */
    @Test
    fun thePreSplitCombinedSortOrdinalIsCarriedAcross() =
        runBlocking {
            seedLegacyPrefs { putInt("sort_video", 4) }

            val prefs = manager()
            assertNull("the new-style keys were never written", prefs.getSort(LibrarySort.VIDEO))
            assertEquals(4, prefs.getLegacySortOrdinal(LibrarySort.VIDEO))
        }

    // ------------------------------------------------------------------ defaults

    @Test
    fun aFreshInstallGetsTheSameDefaultsTheSharedPreferencesBuildUsed() =
        runBlocking {
            val prefs = manager()

            assertEquals(0, prefs.getQueueIndex())
            assertFalse(prefs.getShuffleEnabled())
            assertEquals(Player.REPEAT_MODE_OFF, prefs.getRepeatMode())
            assertNull(prefs.getPlaylistContext())
            assertNull(prefs.getSavedAudioSession())
            assertTrue("the app opens dark unless told otherwise", prefs.getDarkTheme())
            assertEquals("orange", prefs.getThemeId())
            assertEquals(
                "the brightness sentinel must mean follow-the-system, not full brightness",
                AppPreferencesManager.BRIGHTNESS_UNSET,
                prefs.getVideoBrightness(),
                0.0001f,
            )
            LibrarySort.entries.forEach { assertNull(prefs.getSort(it)) }
            LibrarySort.entries.forEach { assertNull(prefs.getLegacySortOrdinal(it)) }
            assertTrue(prefs.getLayout(LibraryLayout.VIDEO_GRID))
            assertTrue(prefs.getLayout(LibraryLayout.FOLDER_GRID))
            assertTrue(prefs.getLayout(LibraryLayout.MOVIE_GRID))
            assertFalse("albums default to grid, so the list flag is off", prefs.getLayout(LibraryLayout.ALBUM_LIST))
        }

    // ------------------------------------------------------------------ round trips

    @Test
    fun everyValueRoundTripsThroughDataStore() =
        runBlocking {
            val prefs = manager()

            prefs.setQueueIndex(3)
            prefs.setShuffleEnabled(true)
            prefs.setRepeatMode(Player.REPEAT_MODE_ONE)
            prefs.setPlaylistContext("SMART_recent")
            prefs.setSavedAudioSession("{}")
            prefs.setDarkTheme(false)
            prefs.setThemeId("blue")
            prefs.setVideoBrightness(0.75f)
            prefs.setSort(LibrarySort.MOVIES, fieldOrdinal = 2, ascending = true)
            prefs.setLayout(LibraryLayout.FOLDER_GRID, false)

            assertEquals(3, prefs.getQueueIndex())
            assertTrue(prefs.getShuffleEnabled())
            assertEquals(Player.REPEAT_MODE_ONE, prefs.getRepeatMode())
            assertEquals("SMART_recent", prefs.getPlaylistContext())
            assertEquals("{}", prefs.getSavedAudioSession())
            assertFalse(prefs.getDarkTheme())
            assertEquals("blue", prefs.getThemeId())
            assertEquals(0.75f, prefs.getVideoBrightness(), 0.0001f)
            assertEquals(StoredSort(2, ascending = true), prefs.getSort(LibrarySort.MOVIES))
            assertFalse(prefs.getLayout(LibraryLayout.FOLDER_GRID))
        }

    /** Nullable values are removed, not stored as a null that a later read would trip over. */
    @Test
    fun clearingANullableValueRemovesTheKey() =
        runBlocking {
            val prefs = manager()

            prefs.setPlaylistContext("ALBUM_1")
            prefs.setSavedAudioSession("{}")
            prefs.setPlaylistContext(null)
            prefs.setSavedAudioSession(null)

            assertNull(prefs.getPlaylistContext())
            assertNull(prefs.getSavedAudioSession())
        }

    /**
     * The four lists share one file and their key names are built from a common prefix. Writing one
     * must not touch the others — a bug here would silently re-sort a screen the user never opened.
     */
    @Test
    fun eachLibraryListKeepsItsOwnSortAndLayout() =
        runBlocking {
            val prefs = manager()

            prefs.setSort(LibrarySort.AUDIO, fieldOrdinal = 1, ascending = false)
            prefs.setSort(LibrarySort.VIDEO, fieldOrdinal = 2, ascending = true)
            prefs.setLayout(LibraryLayout.VIDEO_GRID, false)

            assertEquals(StoredSort(1, ascending = false), prefs.getSort(LibrarySort.AUDIO))
            assertEquals(StoredSort(2, ascending = true), prefs.getSort(LibrarySort.VIDEO))
            assertNull(prefs.getSort(LibrarySort.MOVIES))
            assertNull(prefs.getSort(LibrarySort.ALBUMS))
            assertFalse(prefs.getLayout(LibraryLayout.VIDEO_GRID))
            assertTrue("only the video layout was written", prefs.getLayout(LibraryLayout.MOVIE_GRID))
        }

    /**
     * `sort_audio` (the legacy combined ordinal) and `sort_audio_field` (the new one) coexist in the
     * same file, and the prefix of one is the whole of the other. Writing the new key must not be
     * mistaken for writing the legacy key, or the one-time conversion would re-run on every launch.
     */
    @Test
    fun theLegacySortKeyAndTheNewSortKeyDoNotCollide() =
        runBlocking {
            val prefs = manager()

            prefs.setSort(LibrarySort.AUDIO, fieldOrdinal = 5, ascending = true)

            assertEquals(StoredSort(5, ascending = true), prefs.getSort(LibrarySort.AUDIO))
            assertNull(
                "writing the new key must not create the legacy one",
                prefs.getLegacySortOrdinal(LibrarySort.AUDIO),
            )
        }
}
