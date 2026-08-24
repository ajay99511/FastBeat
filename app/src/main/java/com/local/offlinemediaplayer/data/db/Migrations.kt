package com.local.offlinemediaplayer.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migrates the database from schema v1 to v5, preserving all existing user data.
 *
 * Before this existed, v1 users were silently wiped by
 * `fallbackToDestructiveMigrationFrom(1, 2, 3, 4)`: watch history, resume positions and
 * analytics all disappeared on upgrade.
 *
 * PROVENANCE: every statement below is copied verbatim from `docs/migration_v1_to_v5.sql`,
 * which is itself generated from the `createSql` fields of
 * `app/schemas/com.local.offlinemediaplayer.data.db.AppDatabase/5.json`. Nothing here was
 * written by hand from the @Entity classes. `tools/verify_migration_sql.py` asserts that this
 * file and the .sql document still agree with the exported schema — run it after any change.
 *
 * v2, v3 and v4 are deliberately NOT handled: those schemas were never exported, so a
 * migration for them could be written but never verified. See DR-4 in implementation_plan.md.
 *
 * Overrides `migrate(SQLiteConnection)` rather than `migrate(SupportSQLiteDatabase)`. Both work
 * today, but the connection overload is the one Room calls in *both* modes — if a SQLiteDriver
 * is ever configured, the SupportSQLiteDatabase overload throws NotImplementedError.
 */
val MIGRATION_1_5 =
    object : Migration(1, 5) {
        override fun migrate(connection: SQLiteConnection) {
            // 1. playback_history — add 3 columns. Existing rows are preserved.
            // SQLite requires a DEFAULT when adding a NOT NULL column; v5 declares none, so these
            // seeds come from the Kotlin property defaults in Entities.kt. See DR-3.
            connection.execSQL(
                "ALTER TABLE `playback_history` ADD COLUMN `duration` INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL(
                "ALTER TABLE `playback_history` ADD COLUMN `audioTrackIndex` INTEGER NOT NULL DEFAULT -1",
            )
            connection.execSQL(
                "ALTER TABLE `playback_history` ADD COLUMN `subtitleTrackIndex` INTEGER NOT NULL DEFAULT -1",
            )

            // 2. 6 new tables. Created before their indices, and `playlists`
            // before `playlist_media_cross_ref`, which has a FK onto it.
            // AUTOINCREMENT is load-bearing on `bookmarks.id` and `play_events.id` — without it
            // Room's TableInfo comparison fails.
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isVideo` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist_media_cross_ref` (`playlistId` TEXT NOT NULL, `mediaId` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `mediaId`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `label` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `current_queue` (`mediaId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_playtime` (`date` INTEGER NOT NULL, `totalPlaytimeMs` INTEGER NOT NULL, PRIMARY KEY(`date`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `play_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)",
            )

            // 3. 4 new indices.
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playlist_media_cross_ref_playlistId` ON `playlist_media_cross_ref` (`playlistId`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playlist_media_cross_ref_mediaId` ON `playlist_media_cross_ref` (`mediaId`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_play_events_mediaId` ON `play_events` (`mediaId`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_play_events_timestamp` ON `play_events` (`timestamp`)",
            )

            // No PRAGMA foreign_keys here: Room manages that around the migration transaction.
        }
    }
