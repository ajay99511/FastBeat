-- =====================================================================================
-- FastBeat — schema migration v1 → v5
--
-- GENERATED, NOT HAND-WRITTEN. Every CREATE statement below is copied verbatim from the
-- `createSql` fields of
--     app/schemas/com.local.offlinemediaplayer.data.db.AppDatabase/5.json
-- with Room's `${TABLE_NAME}` placeholder substituted for the literal table name. Nothing
-- here was transcribed from the @Entity classes: the exported JSON is what Room validates a
-- migrated database against, so it is the only authoritative source. A hand-written DDL that
-- merely looks right is how you ship a data-loss bug.
--
-- Regenerate + verify:  python tools/verify_migration_sql.py
--
-- v1 identityHash: 1611bfe6293a7ef1cfc7b90ecbfae404
-- v5 identityHash: 4b79b86decba99bca676628f5dee5c17
-- Room compares database STRUCTURE, not these hashes, after a migration runs. Never hand-edit
-- an identityHash anywhere — see DR-3 in implementation_plan.md.
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 1. playback_history — 3 columns added. Existing rows are PRESERVED.
-- -------------------------------------------------------------------------------------
--
-- ⚠️  READ THIS BEFORE CHANGING THE DEFAULT CLAUSES.
--
-- v5 declares all three columns `INTEGER NOT NULL` with **no** defaultValue:
--     duration            affinity=INTEGER  notNull=True  defaultValue=<absent>
--     audioTrackIndex     affinity=INTEGER  notNull=True  defaultValue=<absent>
--     subtitleTrackIndex  affinity=INTEGER  notNull=True  defaultValue=<absent>
--
-- But SQLite REQUIRES a DEFAULT on any NOT NULL column added via ALTER TABLE — without one
-- the statement is rejected outright. So the migration must supply one, which means the
-- migrated database will carry a default the declared schema does not have.
--
-- That asymmetry is the whole of DR-3. It is expected to be benign: Room's
-- TableInfo.Column equality only compares defaultValue when the ENTITY side declares one,
-- and here it declares none — so the DB-side default should be ignored during validation.
-- This reasoning is NOT the verification. P3-D proves it against a real emulator, and P3-D
-- is what decides DR-3. If validateMigration fails on these columns, fall back to the
-- table-rebuild pattern (CREATE new → INSERT SELECT → DROP old → ALTER RENAME).
--
-- Seed values below come from the Kotlin property defaults in Entities.kt, the only place
-- they are expressed. They must match, or existing rows get values the app never intends:
--     duration = 0
--     audioTrackIndex = -1
--     subtitleTrackIndex = -1

ALTER TABLE `playback_history` ADD COLUMN `duration` INTEGER NOT NULL DEFAULT 0;
ALTER TABLE `playback_history` ADD COLUMN `audioTrackIndex` INTEGER NOT NULL DEFAULT -1;
ALTER TABLE `playback_history` ADD COLUMN `subtitleTrackIndex` INTEGER NOT NULL DEFAULT -1;


-- -------------------------------------------------------------------------------------
-- 2. media_analytics — UNCHANGED between v1 and v5. No statement. Verified byte-identical
--    createSql in both schema files; do not touch this table.
-- -------------------------------------------------------------------------------------


-- -------------------------------------------------------------------------------------
-- 3. 6 new tables
-- -------------------------------------------------------------------------------------

-- playlists
CREATE TABLE IF NOT EXISTS `playlists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isVideo` INTEGER NOT NULL, PRIMARY KEY(`id`));

-- playlist_media_cross_ref
CREATE TABLE IF NOT EXISTS `playlist_media_cross_ref` (`playlistId` TEXT NOT NULL, `mediaId` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `mediaId`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

-- bookmarks
CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `label` TEXT NOT NULL, `createdAt` INTEGER NOT NULL);

-- current_queue
CREATE TABLE IF NOT EXISTS `current_queue` (`mediaId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`mediaId`));

-- daily_playtime
CREATE TABLE IF NOT EXISTS `daily_playtime` (`date` INTEGER NOT NULL, `totalPlaytimeMs` INTEGER NOT NULL, PRIMARY KEY(`date`));

-- play_events
CREATE TABLE IF NOT EXISTS `play_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL);


-- -------------------------------------------------------------------------------------
-- 4. 4 new indices
-- -------------------------------------------------------------------------------------

-- on playlist_media_cross_ref
CREATE INDEX IF NOT EXISTS `index_playlist_media_cross_ref_playlistId` ON `playlist_media_cross_ref` (`playlistId`);
-- on playlist_media_cross_ref
CREATE INDEX IF NOT EXISTS `index_playlist_media_cross_ref_mediaId` ON `playlist_media_cross_ref` (`mediaId`);
-- on play_events
CREATE INDEX IF NOT EXISTS `index_play_events_mediaId` ON `play_events` (`mediaId`);
-- on play_events
CREATE INDEX IF NOT EXISTS `index_play_events_timestamp` ON `play_events` (`timestamp`);


-- -------------------------------------------------------------------------------------
-- 5. Foreign keys
-- -------------------------------------------------------------------------------------
-- playlist_media_cross_ref.playlistId → playlists.id  ON UPDATE NO ACTION  ON DELETE CASCADE
-- Declared inline in the CREATE TABLE above — no separate statement. Note that SQLite only
-- enforces foreign keys when `PRAGMA foreign_keys=ON`; Room sets this per-connection.
-- Create `playlists` BEFORE `playlist_media_cross_ref` (the order above already does).
