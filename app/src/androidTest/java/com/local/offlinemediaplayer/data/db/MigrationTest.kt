package com.local.offlinemediaplayer.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that [MIGRATION_1_5] carries a real v1 database forward to v5 without losing user data.
 *
 * This test is deliberately written against *behaviour*, not against a particular migration
 * strategy. If `runMigrationsAndValidate` rejects the `ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT`
 * form on a default-value mismatch, the fix is to switch [MIGRATION_1_5] to the table-rebuild
 * pattern — **this file does not change**. See DR-3 in implementation_plan.md.
 *
 * Requires a connected device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private companion object {
        const val DB_NAME = "migration-test-db"

        // Fixture values chosen to be recognisable in a failure message and impossible to
        // confuse with the seeded defaults (0 / -1) that the migration writes.
        const val MEDIA_ID_VIDEO = 101L
        const val MEDIA_ID_AUDIO = 202L
        const val POSITION_VIDEO = 45_678L
        const val POSITION_AUDIO = 9_012L
        const val TIMESTAMP_VIDEO = 1_700_000_000_000L
        const val TIMESTAMP_AUDIO = 1_700_000_999_000L
        const val PLAY_COUNT = 7
        const val SKIP_COUNT = 3
        const val LAST_PLAYED = 1_699_999_000_000L

        val NEW_TABLES =
            listOf(
                "playlists",
                "playlist_media_cross_ref",
                "bookmarks",
                "current_queue",
                "daily_playtime",
                "play_events",
            )
    }

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    /** Creates a v1 database containing known rows in both v1 tables. */
    private fun seedV1(): SupportSQLiteDatabase =
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO playback_history (mediaId, position, timestamp, mediaType) " +
                    "VALUES ($MEDIA_ID_VIDEO, $POSITION_VIDEO, $TIMESTAMP_VIDEO, 'video')",
            )
            execSQL(
                "INSERT INTO playback_history (mediaId, position, timestamp, mediaType) " +
                    "VALUES ($MEDIA_ID_AUDIO, $POSITION_AUDIO, $TIMESTAMP_AUDIO, 'audio')",
            )
            execSQL(
                "INSERT INTO media_analytics (mediaId, playCount, skipCount, lastPlayed) " +
                    "VALUES ($MEDIA_ID_VIDEO, $PLAY_COUNT, $SKIP_COUNT, $LAST_PLAYED)",
            )
            close()
        }

    private fun migrate(): SupportSQLiteDatabase = helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_1_5)

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use {
            it.moveToFirst()
            it.getInt(0)
        }

    /**
     * The headline guarantee: a v1 user upgrading to v5 keeps every row, with every original
     * column value intact. Before [MIGRATION_1_5] existed these rows were dropped outright.
     */
    @Test
    fun migrate1To5_preservesExistingPlaybackHistory() {
        seedV1()
        val db = migrate()

        assertEquals("playback_history row count", 2, db.count("playback_history"))

        db
            .query(
                "SELECT mediaId, position, duration, timestamp, mediaType, " +
                    "audioTrackIndex, subtitleTrackIndex FROM playback_history ORDER BY mediaId",
            ).use { c ->
                assertTrue("expected a first row", c.moveToFirst())
                assertEquals("mediaId", MEDIA_ID_VIDEO, c.getLong(0))
                assertEquals("position must survive the migration", POSITION_VIDEO, c.getLong(1))
                assertEquals("timestamp must survive the migration", TIMESTAMP_VIDEO, c.getLong(3))
                assertEquals("mediaType must survive the migration", "video", c.getString(4))

                assertTrue("expected a second row", c.moveToNext())
                assertEquals("mediaId", MEDIA_ID_AUDIO, c.getLong(0))
                assertEquals("position must survive the migration", POSITION_AUDIO, c.getLong(1))
                assertEquals("timestamp must survive the migration", TIMESTAMP_AUDIO, c.getLong(3))
                assertEquals("mediaType must survive the migration", "audio", c.getString(4))
            }
    }

    /**
     * The three added columns are NOT NULL with no declared default, so pre-existing rows must be
     * backfilled with the same values the Kotlin entity defaults use — otherwise old rows would
     * carry values the app never intends. This is the assertion DR-3 is really about.
     */
    @Test
    fun migrate1To5_seedsNewColumnsOnExistingRows() {
        seedV1()
        val db = migrate()

        db
            .query(
                "SELECT duration, audioTrackIndex, subtitleTrackIndex " +
                    "FROM playback_history ORDER BY mediaId",
            ).use { c ->
                var rows = 0
                while (c.moveToNext()) {
                    rows++
                    assertEquals("duration seed", 0L, c.getLong(0))
                    assertEquals("audioTrackIndex seed", -1, c.getInt(1))
                    assertEquals("subtitleTrackIndex seed", -1, c.getInt(2))
                }
                assertEquals("expected both rows to be checked", 2, rows)
            }
    }

    /** media_analytics is unchanged between v1 and v5; its data must be untouched. */
    @Test
    fun migrate1To5_preservesMediaAnalytics() {
        seedV1()
        val db = migrate()

        db
            .query(
                "SELECT mediaId, playCount, skipCount, lastPlayed FROM media_analytics",
            ).use { c ->
                assertTrue("media_analytics row must survive", c.moveToFirst())
                assertEquals("mediaId", MEDIA_ID_VIDEO, c.getLong(0))
                assertEquals("playCount", PLAY_COUNT, c.getInt(1))
                assertEquals("skipCount", SKIP_COUNT, c.getInt(2))
                assertEquals("lastPlayed", LAST_PLAYED, c.getLong(3))
                assertEquals("no extra rows", 1, c.count)
            }
    }

    /** All six v5 tables must exist after the migration, and start empty. */
    @Test
    fun migrate1To5_createsSixNewEmptyTables() {
        seedV1()
        val db = migrate()

        NEW_TABLES.forEach { table ->
            db
                .query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf<Any>(table),
                ).use { c ->
                    assertTrue("table `$table` must exist after migration", c.moveToFirst())
                }
            assertEquals("new table `$table` must start empty", 0, db.count(table))
        }
    }

    /**
     * The one foreign key in v5 must actually be enforced, not merely declared: deleting a
     * playlist has to take its cross-ref rows with it, or orphaned rows accumulate silently.
     */
    @Test
    fun migrate1To5_playlistForeignKeyCascadesOnDelete() {
        seedV1()
        val db = migrate()
        // Room enables this per-connection at runtime; MigrationTestHelper does not, so the
        // constraint is turned on explicitly for the assertion to mean anything.
        db.execSQL("PRAGMA foreign_keys=ON")

        db.execSQL(
            "INSERT INTO playlists (id, name, createdAt, isVideo) VALUES ('p1', 'Road trip', 1, 0)",
        )
        db.execSQL(
            "INSERT INTO playlist_media_cross_ref (playlistId, mediaId, addedAt) " +
                "VALUES ('p1', $MEDIA_ID_AUDIO, 2)",
        )
        assertEquals("cross-ref row should exist", 1, db.count("playlist_media_cross_ref"))

        db.execSQL("DELETE FROM playlists WHERE id = 'p1'")

        assertEquals(
            "deleting the playlist must cascade to playlist_media_cross_ref",
            0,
            db.count("playlist_media_cross_ref"),
        )
    }

    /** The four v5 indices must be present, or query plans silently degrade. */
    @Test
    fun migrate1To5_createsExpectedIndices() {
        seedV1()
        val db = migrate()

        val expected =
            listOf(
                "index_playlist_media_cross_ref_playlistId",
                "index_playlist_media_cross_ref_mediaId",
                "index_play_events_mediaId",
                "index_play_events_timestamp",
            )
        expected.forEach { index ->
            db
                .query(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
                    arrayOf<Any>(index),
                ).use { c ->
                    assertTrue("index `$index` must exist after migration", c.moveToFirst())
                }
        }
    }
}
