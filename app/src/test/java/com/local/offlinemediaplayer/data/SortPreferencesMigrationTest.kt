package com.local.offlinemediaplayer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.local.offlinemediaplayer.ui.screens.AudioSortOption
import com.local.offlinemediaplayer.ui.screens.VideoSortOption
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The upgrade path for P5-C.2: a user who chose sorts on the SharedPreferences build must still see
 * them after updating to the DataStore build.
 *
 * WHY THIS IS ONE TEST METHOD IN ITS OWN CLASS — and it must stay that way.
 *
 * `preferencesDataStore` keeps a **single process-wide instance**, created on first access, and the
 * `SharedPreferencesMigration` attached to it runs exactly once at that moment. A second test method
 * that seeded `shared_prefs/sort_playlists.xml` afterwards would seed a file migration has already
 * walked past, and would fail for reasons that have nothing to do with the code under test. That is
 * not hypothetical: the first draft of these tests was split across several methods and three failed
 * precisely this way.
 *
 * Splitting it across classes does NOT help, and that was measured rather than assumed: Robolectric
 * shares one sandbox classloader between test classes on the same config, so the singleton — and the
 * spent migration — is shared too. The migration test passed alone and failed in the full suite.
 *
 * The workable arrangement is therefore this one: a single class, a single method, and no other test
 * anywhere that touches this DataStore. Everything the migration must guarantee is asserted here in
 * sequence against one seeded legacy file. If you add a second test that reads sort preferences, it
 * will consume the migration and this test will start failing for reasons unrelated to the code.
 *
 * The card's stated verification — install the old APK, set a sort, upgrade — remains the on-device
 * confirmation. This makes it a confirmation rather than the only evidence.
 */
@RunWith(RobolectricTestRunner::class)
class SortPreferencesMigrationTest {
    @Test
    fun sortsChosenOnTheSharedPreferencesBuildSurviveTheUpgrade() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()

            // Exactly what the pre-DataStore build wrote: per-playlist keys, enum ordinals, and a
            // separate boolean for direction. Several playlists, both media types, because the key
            // space is generated per playlist and the migration must carry all of it.
            context
                .getSharedPreferences("sort_playlists", Context.MODE_PRIVATE)
                .edit()
                .putInt("audio_sort_p1", AudioSortOption.ARTIST.ordinal)
                .putBoolean("audio_sort_asc_p1", false)
                .putInt("audio_sort_p2", AudioSortOption.SIZE.ordinal)
                .putInt("video_sort_v9", VideoSortOption.DURATION.ordinal)
                .putBoolean("video_sort_asc_v9", false)
                .putInt("audio_sort_stale", 9_999)
                .commit()

            val manager = SortPreferencesManager(context)

            // 1. The field AND the direction both survive — losing the boolean would silently flip
            //    every descending list back to ascending.
            assertEquals(
                AudioSortOption.ARTIST to false,
                manager.getAudioPlaylistSort("p1"),
            )

            // 2. Every playlist's entry is carried, not merely the first key encountered.
            assertEquals(AudioSortOption.SIZE, manager.getAudioPlaylistSort("p2").first)
            assertEquals(VideoSortOption.DURATION to false, manager.getVideoPlaylistSort("v9"))

            // 3. A playlist that never had a sort still gets the default rather than a neighbour's.
            assertEquals(
                AudioSortOption.DEFAULT to true,
                manager.getAudioPlaylistSort("never-set"),
            )

            // 4. An ordinal with no matching enum constant — possible if a constant is ever removed
            //    or reordered — degrades to the default instead of crashing on data already on disk.
            assertEquals(AudioSortOption.DEFAULT, manager.getAudioPlaylistSort("stale").first)

            // 5. The legacy file is cleaned up, so the migration cannot run a second time and
            //    resurrect stale values over newer ones.
            val legacy = context.getSharedPreferences("sort_playlists", Context.MODE_PRIVATE)
            assertTrue(
                "legacy prefs should be emptied once migrated, found: ${legacy.all}",
                legacy.all.isEmpty(),
            )

            // 6. Writes after the migration land in DataStore and read back correctly.
            manager.saveAudioPlaylistSort("p1", AudioSortOption.TITLE, ascending = true)
            assertEquals(
                AudioSortOption.TITLE to true,
                manager.getAudioPlaylistSort("p1"),
            )

            // 7. A write against one playlist does not bleed into another.
            assertEquals(
                "an unrelated playlist keeps its own default",
                AudioSortOption.DEFAULT to true,
                manager.getAudioPlaylistSort("unrelated"),
            )

            // 8. A second manager observes the same store. DataStore throws if two instances are
            //    created for one file, which is why the store is a top-level delegate.
            assertEquals(
                AudioSortOption.TITLE to true,
                SortPreferencesManager(context).getAudioPlaylistSort("p1"),
            )

            // 9. Video defaults are independent of audio ones.
            assertEquals(
                VideoSortOption.DEFAULT to true,
                manager.getVideoPlaylistSort("never-set"),
            )
        }
}
