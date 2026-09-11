# Plan: Local data portability (export/import) and Material You

**Tier:** Consequential — the backup file is an external contract (a one-way door), and slice FA-0 changes a privacy-facing behaviour.
**Author / date:** 2026-09-08
**Status:** Draft — awaiting owner approval
**Companion:** `implementation_plan.md` (remediation, Phases 0–5). This plan is *additive feature work* and is kept separate so the remediation record stays uncontaminated. Task IDs use the `FA-*` / `FB-*` namespace, which does not collide with `P0`–`P5`. Progress is tracked in §12 of **this** file, following the same "update the status cell as the last step of the same commit" rule.

---

## 1. Problem and outcome

### Feature A — data portability

**Outcome:** a FastBeat user can put a copy of everything the app has learned about them somewhere they choose, and put it back later. Today that value — playlists and their ordering, bookmarks, resume positions, listening streaks, play counts — exists in exactly one SQLite file with no user-reachable escape hatch. Clearing app data, reinstalling, or moving to a new phone destroys all of it, silently and permanently.

**Why now:** it is the last remaining *irreversible* loss in the product, and it is the same class of problem Phase 0 (unversioned source) and Phase 3 (destructive migrations) existed to close. It also becomes urgent the moment FA-0 is decided, because turning cloud auto-backup off removes the only accidental safety net users currently have.

**Users / actors:** the device owner, acting deliberately. There is no background, automatic, or scheduled path in this plan — every export and every import is a thing the user starts and points at a destination.

**Domain context:** the seven Room tables in `AppDatabase` (v5) plus an explicit allowlist of `app_prefs` DataStore keys. Media *files themselves are never copied* — only the app's knowledge about them.

**Invariants:**
1. A backup file never contains audio, video, or image bytes.
2. Import is a **transaction**: it fully applies or it does not apply at all. There is no half-imported state.
3. Import is **idempotent**: importing the same file twice produces the same database state as importing it once. (One deliberate exception, `play_events` — see DR-A3.)
4. Import never deletes a row the user has that the file does not mention. The default mode is merge, not replace.
5. Export and import require no permission the app does not already hold, and open no network path. The `INTERNET` removal in `AndroidManifest.xml` stays untouched.
6. Rows are addressed in the file by a **device-independent content key**, never by a raw MediaStore `_ID`.

**Acceptance criteria:**
- [ ] From the Me tab, the user can write a `.json` backup to any location the system file picker offers, and the file is valid JSON with `"format": "fastbeat.backup"` and `"formatVersion": 1`.
- [ ] The backup contains no media bytes and no absolute file paths.
- [ ] Importing that file into a freshly installed app restores playlists (with membership and order), bookmarks, resume positions, per-media play/skip counts, daily playtime, and the allowlisted preferences — for every media file still present on the device.
- [ ] Rows whose media is *not* present on the device are reported as a count ("312 of 340 items matched") and skipped, not silently dropped and not imported as dangling rows.
- [ ] Importing the same file a second time changes nothing (`play_events` excepted).
- [ ] Importing a file that is not a FastBeat backup, is truncated, has `formatVersion` 2, or exceeds the size cap, fails with a specific user-facing message and leaves the database byte-identical.
- [ ] An import that throws partway through leaves the database byte-identical.
- [ ] A backup taken on device X and restored on device Y matches media by content, not by id.

**Non-goals:**
- **Automatic / scheduled / background backup.** No `WorkManager`, no silent writes. One dependency, one battery consideration, and one privacy question we do not need.
- **Exporting the playback queue or the interrupted audio session.** Transient state; restoring a queue pointing at absent media is worse than restoring nothing. `current_queue`, `last_queue_index`, `saved_audio_session`, `last_playlist_context` are excluded by allowlist.
- **Cross-app formats (M3U, XSPF).** A different feature for a different audience; adding it later does not require changing this format.
- **Encryption of the backup file.** The user chose the destination. A passphrase adds a key-loss failure mode with no threat model behind it.
- **Copying media files.** FastBeat is not a file manager.
- **Selective / partial import** (e.g. playlists only). Deferred with a seam — see §4.

### Feature B — Material You

**Outcome:** on Android 12+, users can have FastBeat take its accent from the system wallpaper palette instead of one of the three curated themes. An app whose stated proposition includes "Premium Theming" currently declares the capability and never uses it: `Theme.kt:56` takes `dynamicColor: Boolean = false` and no call site ever passes `true`.

**Acceptance criteria:**
- [ ] On API 31+, a control in the Me tab's theme row switches the app between the curated accent and the system dynamic accent, and the choice survives process death.
- [ ] On API 26–30 the control is not shown (not shown-and-disabled — there is nothing to explain).
- [ ] With the toggle off, the rendered colour scheme is identical to today's.
- [ ] Enabling the toggle changes the accent everywhere the curated accent is used, including all 28 `LocalAppTheme.current` read sites — no screen keeps the old accent.
- [ ] Switching the toggle does not cause the launch theme flash that `ThemeViewModel.isLoaded` exists to prevent.

**Non-goals:**
- A fourth entry in the curated theme list. Dynamic colour is a *modifier on* the selected theme, not a theme (DR-B1).
- Restyling the hand-tuned dark surface tokens. Dynamic mode adopts the system scheme wholesale.
- Per-screen or partial dynamic colour.

### Constraints (both features)

| Constraint | Source |
|---|---|
| No new runtime dependency | project dependency posture; `libs.versions.toml` is deliberately low-touch |
| No network, ever | `AndroidManifest.xml` removes `INTERNET` via `tools:node="remove"` |
| minSdk 26, targetSdk 35, compileSdk 36 | `app/build.gradle.kts` |
| CI has **no emulator** — `androidTest` is not a gate | `.github/workflows/build.yml` runs `assembleDebug lint testDebugUnitTest assembleRelease detekt ktlintCheck` |
| No surgery in `PlaybackViewModel.kt` (2 173 L) or `MeScreen.kt` (1 848 L) | plan constraint; both are known-hazardous |
| One task per turn, status updated in the same commit | project working protocol |
| detekt + ktlint are baselined and gate on **new** violations | `app/build.gradle.kts` |

### Assumptions (load-bearing ones marked ⚠)

- ⚠ **MediaStore `_ID` is not stable across devices, and not guaranteed stable across a rescan on one device.** Everything in Feature A's file format follows from this. Not verified experimentally — FA-1 exists to make the system correct whether or not an id happens to survive.
- ⚠ **`MediaFile` already carries every field the content key needs** (`displayName`, `size`, `duration`, `mimeType`, `path`) — verified by reading `model/MediaFile.kt`. No new MediaStore projection and no new query are required.
- `MediaRepository.mediaById: StateFlow<Map<Long, MediaFile>>` (`MediaRepository.kt:59`) is populated before the user can reach the Me tab. If it is empty at import time the import must report "library not scanned yet", not match zero items.
- Realistic backup size is under a few hundred KB (a few thousand rows at ~100 B). The 32 MB import cap exists for hostile input, not for real users.

---

## 2. Current state

**Verified by reading the repository on 2026-09-08:**

| Fact | Location |
|---|---|
| Room `AppDatabase` version **5**, 7 entities | `data/db/AppDatabase.kt:17`, `data/db/Entities.kt` |
| DAO has **no bulk read** for `playback_history`, `media_analytics`, `daily_playtime`, `play_events`, `bookmarks` (only per-id or `Flow` variants) | `data/db/MediaDao.kt` |
| `getAllPlaylistsWithRefs()` exists but returns `Flow` | `MediaDao.kt:136` |
| `getPlaylistIdByName(name, isVideo)` already exists — the import's playlist-matching primitive | `MediaDao.kt:161` |
| `room-ktx` is on the classpath, so `db.withTransaction { }` is available | `app/build.gradle.kts` |
| kotlinx.serialization is already a dependency with an established lenient `Json { }` config | `repository/PlaylistRepository.kt:41` |
| DataStore key registry with a documented "callers name what they want, never a key string" rule | `data/AppPreferencesManager.kt` |
| `AppPreferencesManager` has an `internal constructor` taking a `DataStore` for tests | `AppPreferencesManager.kt` + `AppPreferencesManagerTest.kt` |
| `Theme.kt` declares `dynamicColor: Boolean = false`; **no call site passes it** | `ui/theme/Theme.kt:56`, `MainActivity.kt:78` |
| `LocalAppTheme.current` is read at **28 sites across 17 files** | `grep -rn "LocalAppTheme.current"` |
| Theme picker is a self-contained ~25-line `Row` of three `ThemeButton`s | `ui/screens/MeScreen.kt:128-153` |
| `ThemeViewModel.isLoaded` gates first composition to prevent a theme flash | `viewmodel/ThemeViewModel.kt:40-54` |
| **Precedent for adding a screen off the Me tab without touching navigation:** `onNavigateToAccessibilityGuide: () -> Unit` threaded MeScreen → AdaptiveMeScreen → MainScreen, with a `showAccessibilityGuide` boolean | `MeScreen.kt:71`, `AdaptiveMeScreen.kt:25`, `MainScreen.kt:233,300,356` |
| ⚠ `showAccessibilityGuide` is reset to `false` at **six** separate sites | `MainScreen.kt:302,443,481,519,557,581` |
| `android:allowBackup="true"` with **stock, entirely commented-out** backup rules | `AndroidManifest.xml`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` |
| JVM test conventions: Kotest specs, JUnit4+Robolectric where a `Context` is needed, both on the JUnit Platform | `app/src/test/...`, OQ-8 in `implementation_plan.md` |

**Existing patterns to follow (copy these, do not invent):**
- Serialization + tolerant decode: `PlaylistRepository.kt:34-46`.
- A `@Singleton` class with an `internal` test constructor: `AppPreferencesManager`.
- Single-purpose domain use case: `domain/GetContinueWatchingUseCase.kt`.
- User-facing text via `@StringRes` in `model/UserMessage.kt` / `model/AppError.kt`, never a literal.
- New screen reached by a callback + boolean from `MainScreen`: the accessibility guide.

**Must not break:**
- The `INTERNET` tripwire and the three user-facing statements of the offline promise (README, SECURITY.md, in-app Accessibility Guide).
- The migration story: **Feature A introduces no schema change and no new migration.** Every table it reads and writes already exists at v5.
- `ThemeViewModel.isLoaded`'s no-flash guarantee.

**Prior art:** `implementation_plan.md` DR-4 / OQ-7 establish that this project prefers a loud failure over a silent data wipe. Import's merge-by-default and all-or-nothing transaction follow that precedent directly.

---

## 3. Design

### Approach

**Feature A.** A new `data/backup/` package holds a pure serialization core and a thin Android shell. The core is a set of `@Serializable` DTOs, a `MediaKey` value type, and two pure functions — build a snapshot from database rows, and reconcile a snapshot against the device's library — none of which touch Android, a file, or a dispatcher. The shell is a Hilt `@Singleton` repository that reads the DAO, hands rows to the core, and streams bytes through a SAF `Uri`, plus one new Compose screen reached from the Me tab by the accessibility-guide callback pattern. Media are addressed in the file by content key with an index-based reference from every row, so the file contains no device-local ids at all.

**Feature B.** One new DataStore boolean, one new `StateFlow` on `ThemeViewModel` hydrated inside the existing `init` block *before* `isLoaded` flips, and the colour-scheme branch lifted out of the `@Composable` into a plain internal function so it can be unit-tested without Robolectric. In dynamic mode the selected `AppThemeConfig` is `copy(primaryColor = scheme.primary)`, which keeps all 28 `LocalAppTheme` sites correct with no edits.

### Contract — backup file format v1

The file is UTF-8 JSON. **This is the one-way door in this plan**: once a user has written a file, its shape is a contract.

```jsonc
{
  "format": "fastbeat.backup",     // magic; reject anything else
  "formatVersion": 1,              // reject > 1 with a specific message
  "createdAt": 1757280000000,      // epoch ms, informational
  "app": { "versionName": "1.0.1", "versionCode": 42 },
  "dbSchemaVersion": 5,            // informational; NOT a gate

  // Every media-referencing row below points at an INDEX in this array.
  // No MediaStore _ID appears anywhere in the file.
  "media": [
    { "displayName": "track.mp3", "size": 5242880, "durationMs": 214000, "mimeType": "audio/mpeg" }
  ],

  "history":       [ { "m": 0, "positionMs": 1000, "durationMs": 214000, "timestamp": 1757,
                       "mediaType": "AUDIO", "audioTrackIndex": -1, "subtitleTrackIndex": -1 } ],
  "analytics":     [ { "m": 0, "playCount": 12, "skipCount": 1, "lastPlayed": 1757 } ],
  "dailyPlaytime": [ { "date": 20260908, "totalPlaytimeMs": 3600000 } ],
  "playEvents":    [ { "m": 0, "timestamp": 1757 } ],
  "playlists":     [ { "name": "Favorites", "isVideo": false, "createdAt": 1757,
                       "items": [ { "m": 0, "addedAt": 1757 } ] } ],
  "bookmarks":     [ { "m": 0, "timestampMs": 65000, "label": "chorus", "createdAt": 1757 } ],
  "preferences":   { "is_dark_mode": true, "current_theme_id": "orange", "use_dynamic_color": false,
                     "video_brightness": 0.5, "sort_audio_field": 2, "sort_audio_asc": true }
}
```

Part of the contract, not commentary:
- `"m"` is an index into `media`. An out-of-range index makes the file **invalid**, not partially importable.
- `playlists[].id` is deliberately **absent**. Playlist identity across devices is `(name, isVideo)` — see DR-A4.
- `preferences` is an **allowlist**, keyed by the same key strings `AppPreferencesManager` already writes, so the file is readable without knowing Kotlin. Session keys (`last_queue_index`, `last_shuffle_enabled`, `last_repeat_mode`, `last_playlist_context`, `saved_audio_session`) are excluded.
- Decoder config: `ignoreUnknownKeys = true` (forward compatibility with a future v1.x that adds a field), `isLenient = false` (this is our own format; leniency would only hide bugs), `coerceInputValues = false`.
- Encoder: `prettyPrint = false`, `encodeDefaults = true`.

### Contract — new Kotlin surface

```kotlin
// data/backup/MediaKey.kt — pure, no Android imports
@JvmInline value class MediaKey(val value: String)

internal fun MediaFile.backupKey(): MediaKey            // "name|size", lowercased name
internal fun BackupMedia.backupKey(): MediaKey

// data/backup/MediaResolver.kt — pure; THE risk-bearing unit
internal object MediaResolver {
    data class Resolution(val byIndex: Map<Int, Long>, val matched: Int, val unmatched: Int)
    fun resolve(exported: List<BackupMedia>, library: Map<Long, MediaFile>): Resolution
}

// data/backup/BackupSnapshot.kt — @Serializable DTOs mirroring the JSON above

// data/backup/BackupCodec.kt — pure
internal object BackupCodec {
    fun encode(snapshot: BackupSnapshot): String
    fun decode(text: String): Result<BackupSnapshot>   // Failure carries a BackupError
}

// data/backup/BackupRepository.kt — @Singleton, the only Android-aware piece
class BackupRepository @Inject constructor(dao, db, prefs, mediaRepository, @ApplicationContext ctx) {
    suspend fun export(target: Uri): Result<ExportSummary>
    suspend fun import(source: Uri): Result<ImportSummary>   // one db.withTransaction
}

// model/BackupError.kt — @StringRes-carrying, alongside AppError/UserMessage
```

`ExportSummary(mediaCount, rowCount, bytes)` and `ImportSummary(matched, unmatched, playlists, bookmarks, historyRows, playEventsSkipped)` are what the UI reports. Reporting counts rather than "Done" is what makes an import that silently matched nothing visible.

### Merge semantics (import — default and only mode in v1)

| Table | Rule | Why |
|---|---|---|
| `playback_history` | keep the row with the **greater `timestamp`** | last-write-wins per media; idempotent |
| `media_analytics` | `max(playCount)`, `max(skipCount)`, `max(lastPlayed)` | **not** sum — summing makes re-import non-idempotent |
| `daily_playtime` | `max(totalPlaytimeMs)` per date | same reason |
| `playlists` | match on `(name, isVideo)` via existing `getPlaylistIdByName`; union the membership, preserving relative order by `addedAt` | DR-A4 |
| `bookmarks` | dedupe on `(mediaId, timestampMs, label)` | no natural key exists; the triple is what a user means by "the same bookmark" |
| `play_events` | imported **only when the local table is empty**; otherwise skipped and reported | DR-A3 |
| preferences | overwrite each allowlisted key present in the file | the user asked to restore their settings |

### Feature B contract

```kotlin
// AppPreferencesManager — following the existing accessor-pair pattern
suspend fun getDynamicColor(): Boolean   // KEY_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color"), default false
suspend fun setDynamicColor(value: Boolean)

// ui/theme/Theme.kt — the branch lifted OUT of the composable so it is unit-testable
internal fun resolveAccent(
    activeTheme: AppThemeConfig,
    dynamicScheme: ColorScheme?,   // null when unavailable or disabled
): AppThemeConfig = if (dynamicScheme == null) activeTheme
                    else activeTheme.copy(primaryColor = dynamicScheme.primary)
```

`OfflineMediaPlayerTheme` keeps its signature; `MainActivity.kt:78` starts passing `dynamicColor = useDynamicColor`. Inside, when `dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`, the scheme is `dynamicDarkColorScheme(context)` / `dynamicLightColorScheme(context)` used **wholesale** (not merged with the custom `DarkSurface*` tokens), and `LocalAppTheme` is provided `resolveAccent(activeTheme, scheme)`.

### Cross-cutting

- **Authz:** N/A — single-user on-device app, no accounts.
- **Validation:** at the import boundary only. Magic string, `formatVersion`, byte cap (32 MB), `"m"` index range, non-negative durations/counts. Anything failing rejects the whole file.
- **Security / privacy:** the import file is **untrusted input the user picked**. It is parsed by kotlinx.serialization (no reflection, no polymorphic deserialization enabled), written only through parameterized Room inserts, and never used to construct a filesystem path — so neither path traversal nor injection has a route. The export contains the user's media *file names*; SECURITY.md must say so plainly (FA-5). No new permission; SAF grants access per document. `INTERNET` remains removed.
- **Observability:** structured `Log.i` with counts at the start and end of each operation, `Log.e` with the `BackupError` on failure. No telemetry — there is no network. The user-visible counts *are* the observability.
- **Performance:** whole file in memory. Budget: an export of 5 000 rows completes under 2 s on a mid-range device; measured in FA-2's device check, not assumed. Both operations run on `Dispatchers.IO`; neither blocks the main thread.
- **Accessibility / i18n:** every new string goes in `strings.xml` (the project is post-F-33; a literal in a new file is a regression). The new screen needs `contentDescription` on its icons and must be operable with TalkBack. The Material You control needs a state description, not just a visual.

---

## 4. Design judgment

**Must be right now** (irreversible or expensive to retrofit):
- The file format, including the decision that rows reference media by content-key index rather than `_ID`. Files written under a wrong format are worthless after a fix.
- Merge semantics being idempotent. A summing merge that ships will have already corrupted counts by the time anyone notices.
- The all-or-nothing transaction.
- The `allowBackup` decision — it governs whether user data is currently leaving the device.

**At ship:**
- Counts reported to the user (an import that matched nothing must look different from one that worked).
- Unit tests on the resolver and the codec; they are where the risk lives and they run in CI.
- Strings externalised.

**Deferred, with a seam:**
- **Replace-mode import.** Seam: `BackupRepository.import(source)` gains a `mode` parameter; the merge rules already live in one function per table.
- **Selective import.** Seam: `BackupSnapshot` is already per-table, so a filter is a field selection, not a re-parse.
- **M3U / other formats.** Seam: `BackupCodec` is the only place that knows JSON.
- **Streaming encode/decode** for very large libraries. Seam: `BackupCodec` takes and returns text today; swapping to `encodeToStream` is local to it.

**Explicitly not doing:**
- Scheduled/automatic backup — a dependency and a battery/privacy question with no demand behind it.
- Encryption — no threat model; adds irreversible key loss.
- Exporting queue/session — restoring it is worse than not.
- A `favorites` table or any schema change — this plan is deliberately migration-free.

**Abstraction decisions:**

| Tempting generalization | Evidence of variation | Decision | Gate that failed |
|---|---|---|---|
| A pluggable `BackupFormat` interface (JSON now, M3U later) | one format exists; M3U is a different feature for a different audience | Stay concrete: `BackupCodec` object | Gate 1 — no second case |
| A generic `Mergeable<T>` strategy over the seven tables | seven tables, seven *different* rules (max, last-write-wins, union, dedupe-triple) | Seven explicit functions in one file | Gate 2 — they look alike, they change for different reasons |
| A generic media-identity service for the whole app | one consumer (import) | `MediaResolver`, scoped to `data/backup/` | Gate 4 — no complexity reduction across call sites |
| A `SettingsScreen` framework to host backup + theme + future settings | two settings surfaces today, both already placed | One `BackupScreen`; theme stays in its existing row | Gate 1 — a framework for two items |
| A fourth curated theme representing "dynamic" | one dynamic source | `AppThemeConfig.copy(primaryColor = …)` | Gate 3 — the volatile axis is the accent, not the theme identity |

**One-way doors:**
1. **The v1 file format.** Mitigated by the `format` + `formatVersion` envelope: a v2 reader can accept v1, and a v1 reader rejects v2 loudly rather than mis-reading it. This is why FA-1 (identity) is sequenced before anything writes a file.
2. **`allowBackup=false`, if chosen.** Users who currently have a Google cloud backup lose that safety net on the next release. This is precisely why FA-0 lands *with* the export path, not before it in a release of its own.

**Decision records:**

> **DR-A1 — Backup posture: turn Android Auto Backup off and replace it with an explicit local export.**
> **Context:** `allowBackup="true"` with stock, fully commented-out `backup_rules.xml` / `data_extraction_rules.xml` means the Room DB and DataStore are uploaded to the user's Google Drive on API 31+. README, SECURITY.md and the in-app guide all state that data never leaves the device. The claim currently has a hole of exactly the kind the `INTERNET` tripwire was written to close elsewhere.
> **Alternatives:** *(a)* keep auto-backup and reword the three claims — loses the product's defining promise to save a config line. *(b)* keep auto-backup but exclude the DB via `data_extraction_rules` — half-measure; the DataStore still goes, and the promise still needs qualifying. *(c)* leave it and document — the promise stays false.
> **Consequences:** the offline claim becomes true and testable from the manifest alone. Users lose an implicit cloud restore they were never told they had, which makes the export path load-bearing rather than a nicety — hence the sequencing. Reversal is a one-line manifest change.
> **Owner decision required** — see OQ-A1.

> **DR-A2 — Media identity is `displayName + size`, with duration as a tiebreaker only.**
> **Context:** MediaStore `_ID` is meaningless on another device and not guaranteed across a rescan. Every restored row must find its media by content.
> **Alternatives:** *(a)* absolute `path` — breaks on a different storage root, a different device, or a moved file; also puts the user's directory layout in the file for no gain. *(b)* content hash — correct, but requires reading every file's bytes: minutes of I/O for a feature that must feel instant. *(c)* `name + size + duration` as the key — duration is `0` for images and can be absent, so it would fail to match rows that are otherwise unambiguous.
> **Consequences:** two files with the same name *and* the same exact byte size collide; the consequence is a mis-attributed resume position, not corruption or data loss. When a key matches more than one library entry, the resolver prefers the candidate whose `durationMs` matches and otherwise treats it as unmatched — better a reported skip than a wrong attribution. Reversal: a v2 format may add a `contentHash` field; v1 files keep working.

> **DR-A3 — `play_events` is imported only into an empty table.**
> **Context:** `play_events` is an append-only log with `autoGenerate` ids and no natural key. Re-importing duplicates rows, which would inflate `getMostPlayedMediaIdSinceFlow`.
> **Alternatives:** *(a)* a unique index on `(mediaId, timestamp)` — correct, but a schema migration, and this plan is deliberately migration-free. *(b)* a per-row existence check — N queries against the table with the most rows. *(c)* never import them — loses "most played in range" fidelity on a genuine restore, which is the main use case.
> **Consequences:** the fresh-install restore (the case that matters) gets full fidelity; a merge into a used database skips them and *says so* via `ImportSummary.playEventsSkipped`. This is the one deliberate exception to invariant 3, recorded here so it is a decision rather than a bug. Reversal: add the unique index at a future schema bump and drop the special case.

> **DR-A4 — Playlist identity across devices is `(name, isVideo)`, not the stored UUID.**
> **Context:** `PlaylistEntity.id` is a locally generated String. Importing it verbatim risks colliding with an unrelated local playlist, and preserving it gives the user nothing they can see.
> **Alternatives:** *(a)* import the id verbatim — collision risk, and two playlists the user calls "Workout" would stay separate. *(b)* always generate a new id — re-import creates duplicate playlists, breaking idempotence.
> **Consequences:** merging into a playlist the user happens to have named the same is possible and, we judge, what they would want. `getPlaylistIdByName` already exists for this. Renaming a playlist between export and import produces two playlists — acceptable and explicable.

> **DR-B1 — Dynamic colour is a modifier on the selected theme, not a fourth theme.**
> **Context:** `AppThemeConfig` carries `subtitle` and `curatedTitle` — user-facing copy — and its `primaryColor` is read at 28 sites.
> **Alternatives:** *(a)* a fourth `AppThemeConfig` in `ThemeViewModel.themes` — requires inventing copy for it, makes "dynamic" mutually exclusive with the curated identity, and leaves `themes` keyed by an id with no fixed colour. *(b)* provide the dynamic scheme only through `MaterialTheme.colorScheme` and leave `LocalAppTheme` curated — the 28 accent sites would keep the *old* colour and the app would look half-converted.
> **Consequences:** every accent site follows with zero edits; the curated copy keeps working. Reversal is deleting one `copy(...)`.

---

## 5. Risks and failure modes

| Risk | Likelihood | Impact | Mitigation / early signal |
|---|---|---|---|
| Content key mismatches most of the library on restore | Medium | Feature looks broken | FA-1 lands first, pure and unit-tested including property tests; the UI reports matched/unmatched counts, so a bad match rate is visible immediately rather than silent |
| Import half-applies and corrupts state | Low | Severe — data loss inside a data-protection feature | Single `db.withTransaction`; a test that throws mid-import and asserts the DB is unchanged |
| Non-idempotent merge inflates counts | Medium if unguarded | Silent analytics corruption | `max`-based rules; an explicit "import twice, assert identical" test |
| Hostile/corrupt file causes OOM or crash | Low | Crash on a user-chosen file | 32 MB cap checked before parse; `Result`-returning codec; no polymorphic deserialization |
| `allowBackup=false` removes a safety net users relied on | Certain, if DR-A1 is accepted | Data loss for someone mid-migration | Ship FA-0 in the same release as the export path; call it out in release notes |
| Dynamic scheme wrecks contrast on hand-tuned dark surfaces | Medium | Ugly, not broken | Off by default, one tap to revert; on-device check across ≥3 wallpapers in FB-2 |
| Missing one of the six `showAccessibilityGuide = false` reset sites | **High** — the concrete trap in `MainScreen.kt` | Backup screen gets stuck open | FA-3 names all six line numbers; verify by navigating away via each path |
| ktlint baseline breaks on untouched code | Medium | Red CI on unrelated lines | Known (F-38): the baseline is line-keyed. Do not re-pin; fix or move the code |
| New strings hardcoded in the new screen | Medium | i18n regression against F-33 | `grep` for string literals in `data/backup/` and the new screen as part of FA-3's verify step |

**Blast radius:** Feature A is additive — a new package, additive DAO queries, one new screen, three small edits to thread a callback. Nothing existing changes behaviour except `AndroidManifest.xml` in FA-0. Feature B touches four files and is a no-op while the pref is false.

**Worst realistic failure:** a user on a build with `allowBackup=false`, who has never exported, clears app data and loses everything — the same loss as today, but now without the accidental cloud copy. Detection is a bug report, which is why the export path must ship in the same release as FA-0, not later.

---

## 6. Implementation slices

Ordered by risk. One slice per turn. Update §12 in the same commit.

### FA-0 — Settle the backup posture
- **Intent:** the offline promise becomes either true or honestly worded, enforced by config rather than by luck.
- **Changes:** `app/src/main/AndroidManifest.xml` (`android:allowBackup`), `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` (replace the stock comment blocks with a real, commented decision), `SECURITY.md` + `README.md` if the wording must follow.
- **Blocked on:** OQ-A1. **Do not guess this one.**
- **Acceptance:** the merged manifest shows the chosen value; the xml files state *why* in a comment, in the style of the existing `INTERNET` block.
- **Verify:** `./gradlew :app:assembleDebug`, then inspect `app/build/intermediates/merged_manifests/debug/AndroidManifest.xml` for the `allowBackup` attribute. Quote the line as evidence.
- **Rollback:** revert one attribute.
- **Risk retired:** the privacy claim stops being partly false, and the release sequencing for FA-2/FA-3 is settled.

### FA-1 — Media identity: `MediaKey` + `MediaResolver` (pure)
- **Intent:** the hardest question — "is this the same file on a different device?" — is answered and tested before any file format reaches a disk.
- **Changes:** new `data/backup/MediaKey.kt`, `data/backup/MediaResolver.kt`; new `app/src/test/java/.../data/backup/MediaResolverTest.kt`.
- **Constraint:** **zero Android imports** in both production files. That is what keeps the test JVM-only.
- **Acceptance:** exact match; case-insensitive name match; size mismatch → unmatched; duplicate key resolved by duration tiebreaker; ambiguous duplicate with no tiebreaker → unmatched (never guessed); empty library → all unmatched, with a correct count.
- **Verify:** `./gradlew :app:testDebugUnitTest --tests "*MediaResolverTest*"` — all green, and the ambiguous case asserts *unmatched*, not a lucky pick.
- **Rollback:** delete the package; nothing references it yet.
- **Risk retired:** the single assumption the whole feature rests on. This slice is verified by test only, not by a user journey — deliberately, because learning here is cheap and learning after the format ships is not.

### FA-2 — Export end-to-end
- **Intent:** the user can write a real backup file and open it in a text editor.
- **Changes:** `data/db/MediaDao.kt` — add suspend bulk reads (`getAllHistory`, `getAllAnalytics`, `getAllDailyPlaytime`, `getAllPlayEvents`, `getAllBookmarks`, `getAllPlaylistsWithRefsOnce`, `countPlayEvents`). New `data/backup/BackupSnapshot.kt`, `BackupCodec.kt`, `BackupRepository.kt` (export half). `AppPreferencesManager` — an allowlisted-export accessor, following the existing key-registry rule (callers must not name key strings). New `model/BackupError.kt`. New strings.
- **Not in this slice:** the import half. The entry point may be temporary if FA-3 is taken separately; folding FA-3's entry point in here is acceptable if the implementor judges the screen small enough to land whole.
- **Acceptance:** a written file parses as JSON, carries the magic + version, contains no `mediaId` field anywhere, and round-trips through `BackupCodec.decode` to an equal snapshot.
- **Verify:** `./gradlew :app:testDebugUnitTest --tests "*Backup*"`; plus on device — export, `adb pull`, and confirm by eye that a known playlist and a known bookmark are present and that no absolute path appears.
- **Rollback:** delete the package; the DAO additions are inert.
- **Risk retired:** whether the DAO reads and the DTO shape actually cover the data.

### FA-3 — The Backup screen and its entry point
- **Intent:** the feature is reachable and reports what it did.
- **Changes:** new `ui/screens/BackupScreen.kt` (all new UI lives here); `ui/screens/MeScreen.kt` — add one `onNavigateToBackup: () -> Unit` parameter and one row (**no other edits to this file**); `ui/adaptive/AdaptiveMeScreen.kt` — pass through; `ui/MainScreen.kt` — a `showBackup` boolean mirroring `showAccessibilityGuide`, **including a reset at every one of the six sites** (`MainScreen.kt:302, 443, 481, 519, 557, 581`). SAF via `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"))`, suggested name `fastbeat-backup-<yyyyMMdd-HHmm>.json`.
- **Acceptance:** reachable from Me; export writes to a user-chosen location; the result line shows counts; every navigation path away leaves no stuck screen; TalkBack reads every control.
- **Verify:** on device — reach the screen, export to Downloads, then leave via each of the six paths. `./gradlew :app:lint detekt ktlintCheck`. Grep the new screen for user-facing literals; expect none.
- **Rollback:** revert four files.
- **Risk retired:** the `MainScreen` boolean trap, the most likely place for a silent bug in this plan.

### FA-4 — Import end-to-end
- **Intent:** a backup can actually be restored, safely.
- **Changes:** `BackupRepository.import` inside a single `db.withTransaction`; the seven merge functions; validation at the boundary; `ImportSummary` surfaced in `BackupScreen`; `ActivityResultContracts.OpenDocument`.
- **Acceptance:** all of §1's import criteria, especially: import-twice-is-a-no-op; a throwing import leaves the DB byte-identical; a non-FastBeat file gives a specific message; unmatched media are counted and skipped.
- **Verify:** `./gradlew :app:testDebugUnitTest --tests "*Backup*"`, including an in-memory-Room test that imports twice and diffs every table, and one that injects a failure mid-transaction. On device: export → clear app data → reinstall → import → confirm playlists, bookmarks and resume positions.
- **Rollback:** the import entry point is one screen section; removing it leaves export intact.
- **Risk retired:** transactional integrity and idempotence — the two ways this feature could hurt someone.

### FA-5 — Documentation
- **Changes:** `README.md` (feature list), `SECURITY.md` (what a backup file contains, and that the user chooses where it goes), `docs/FEATURES.md`, `release_notes.md`, and the FA-0 decision if not already recorded there.
- **Verify:** a reader who has never seen the code can tell from SECURITY.md what leaves the device and when.

### FB-1 — Dynamic-colour plumbing (invisible)
- **Intent:** the capability exists and is off; rendering is provably unchanged.
- **Changes:** `data/AppPreferencesManager.kt` (+`KEY_DYNAMIC_COLOR` and its accessor pair); `viewmodel/ThemeViewModel.kt` (a `useDynamicColor` StateFlow hydrated **inside the existing `init` block, before `_isLoaded.value = true`**); `ui/theme/Theme.kt` (`resolveAccent` + the API-31-guarded scheme branch); `MainActivity.kt:78` (pass the value).
- **Acceptance:** with the pref false, the colour scheme and `LocalAppTheme` are identical to before; no new frame of theme flash at launch.
- **Verify:** `./gradlew :app:testDebugUnitTest --tests "*AppPreferencesManagerTest*" --tests "*resolveAccent*"`; on device, cold-launch and confirm no flash.
- **Rollback:** revert four small files.
- **Risk retired:** the launch-flash regression — the only way this feature can damage something that works today.

### FB-2 — The toggle
- **Changes:** `ui/screens/MeScreen.kt` — one control in the existing theme `Row` at lines 128–153, shown only when `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`; new strings; `ThemeViewModel.toggleDynamicColor()`.
- **Acceptance:** toggling changes the accent app-wide and survives process death; the control is absent below API 31.
- **Verify:** on device (an API 31+ AVD is available per OQ-4): toggle, then check Now Playing, the Mini Player and Me for the new accent; kill and relaunch; repeat against at least three wallpapers; confirm the API 26–30 AVD shows no control.
- **Rollback:** revert; the pref becomes inert.

---

## 7. Verification

| What | Level | Why this level |
|---|---|---|
| `MediaResolver` matching rules | Unit (Kotest, property-based for the ambiguity cases) | pure logic, and the highest-consequence logic in the plan |
| `BackupCodec` round-trip and rejection of bad input | Unit | pure; covers the untrusted-input boundary |
| Merge rules, idempotence, transaction rollback | Integration (in-memory Room on the JVM — the `MediaDaoTest` pattern) | real SQLite behaviour *is* the risk |
| Preferences allowlist | Unit (`AppPreferencesManagerTest` pattern, DataStore over a temp file) | established pattern, real store |
| `resolveAccent` | Unit | the branch was lifted out of the composable precisely so this needs no Robolectric |
| Backup screen navigation, SAF, dynamic-colour rendering | **Manual on device** | SAF and wallpaper-derived colour cannot run on a CI runner; `androidTest` is not a CI gate here |

**Edge cases to cover:** empty database; empty library at import time; media matched 0 %; duplicate content keys; playlist name collision; bookmark exact duplicate; `formatVersion` 2; truncated JSON; a 40 MB file; a picked file that is a PNG; `"m"` out of range; negative counts; an import interrupted by a thrown exception.

**Non-functional checks:** export of ~5 000 rows under 2 s on device; no main-thread I/O (StrictMode or inspection); `detekt` and `ktlintCheck` clean without touching either baseline; `libs.versions.toml` unchanged at the end of both features.

**Do not** add a dependency to make a test easier. If a test seems to need one, that is a stop-and-ask.

---

## 8. Rollout

- **Migration order:** none — no schema change, no data migration. FA-0 is a config change that takes effect when the next release is installed.
- **Feature flag:** none for A (it is user-initiated and additive). Feature B is self-flagging: the pref defaults to `false`.
- **Sequencing requirement:** if DR-A1 is accepted, **FA-0 and FA-2/FA-3 must ship in the same release.** Removing the implicit cloud backup in a release with no export path is a strict regression in data safety.
- **Rollback:** every slice is a revert of additive files. No data change makes a code rollback unsafe — a v1 backup file stays readable by any later version, and the app writes nothing to the database that an older build cannot read.
- **Comms:** `release_notes.md` must state plainly that cloud auto-backup is off and that export is how you keep a copy.

---

## 9. Stop-and-ask triggers

Stop and ask rather than deciding alone if:
- **OQ-A1 is unanswered** — FA-0 must not be guessed; it changes a privacy-facing behaviour.
- A schema change or a new Room migration appears necessary. Nothing here needs one; if you think you need one, the design has drifted.
- A new dependency seems necessary, including a test-only one.
- `MediaResolver`'s match rate on a real device library is below ~95 % for media that is genuinely present — the key definition is wrong and DR-A2 must be revisited *before* any file format ships.
- The merge rule for a table cannot be made idempotent — that is a format problem, not an implementation detail.
- Adding the Backup screen turns out to need more than one parameter and one row in `MeScreen.kt` — the plan's minimal-touch premise has failed and needs rethinking.
- The dynamic scheme makes any screen unreadable rather than merely different.
- An acceptance criterion turns out to be untestable as written.
- Anything in §2 turns out not to match the repository.

---

## 10. Open questions

| # | Question | Owner | Blocks | Default if unanswered |
|---|---|---|---|---|
| **OQ-A1** | 🔴 `allowBackup`: turn Android Auto Backup **off** (DR-A1), exclude the DB/DataStore selectively, or keep it and reword the three offline claims? | Owner | **FA-0, and the release sequencing of FA-2/FA-3** | ⛔ None — do not guess. Recommendation is DR-A1 (off), paired with export in the same release |
| **OQ-A2** | Should import offer a **replace** mode in v1, or merge only? | Owner | FA-4 scope | Merge only. Replace can destroy data the file does not mention, and the seam for adding it later already exists in the design |
| **OQ-A3** | Should the export include `thumbnailPath` cache references? | Eng | FA-2 | No — regenerable, device-local, pure bloat in the file |
| **OQ-A4** | Where should the Me-tab entry point sit — next to the accessibility-guide row, or in a new "Data" section? | Owner | FA-3 | Next to the accessibility-guide row: it reuses an established pattern and adds one row to a 1 848-line file rather than a section |
| **OQ-B1** | On API 31+, should dynamic colour default **on** for new installs? | Owner | FB-1 | **Off.** The three curated themes are the product's stated identity; opting in is the honest default |

---

## 11. Out-of-scope follow-ups (record, do not action here)

| # | Item | Severity |
|---|---|---|
| N-1 | `DefaultTheme` in `Theme.kt:26` carries different user-facing copy ("HIDDEN LEAF MEDIA SCROLL" / "Hokage Selections") from `ThemeViewModel`'s `"orange"` entry ("AMBER HORIZON" / "Jump Back In"). The fallback renders different text from the theme it is meant to fall back to. | 🟡 Major |
| N-2 | `AppThemeConfig.subtitle` and `curatedTitle` are hardcoded English inside a Kotlin data class, outside `strings.xml` — a gap F-33 did not reach. | 🟢 Minor |
| N-3 | `MeScreen.kt` still contains hardcoded user-facing literals (e.g. `"Search in suggested..."`). F-33 closed the ViewModels, not the composables. | 🟢 Minor |
| N-4 | `Theme.kt` sets `window.statusBarColor` / `navigationBarColor`, both deprecated and no-ops under the edge-to-edge enforcement that `targetSdk = 35` opts into on Android 15. | 🟡 Major |
| N-5 | A unique index on `play_events(mediaId, timestamp)` would remove DR-A3's exception at the next schema bump. | 🟢 Minor |

---

## 12. Progress tracker

Update the status cell as the **last step** of each task, in the same commit.

| ID | Task | Risk | Depends on | Status | Evidence / note |
|---|---|---|---|---|---|
| FA-0 | Settle backup posture (`allowBackup`, extraction rules, docs) | 🟥 | OQ-A1 | ⬜ | Blocked on owner decision |
| FA-1 | `MediaKey` + `MediaResolver`, pure, unit-tested | 🟨 | — | ⬜ | |
| FA-2 | Export end-to-end (DAO bulk reads, DTOs, codec, repository) | 🟨 | FA-1 | ⬜ | |
| FA-3 | `BackupScreen` + Me-tab entry point (six reset sites) | 🟨 | FA-2 | ⬜ | |
| FA-4 | Import end-to-end (merge rules, transaction, validation) | 🟥 | FA-3 | ⬜ | |
| FA-5 | Docs: README, SECURITY.md, FEATURES.md, release notes | 🟩 | FA-4 | ⬜ | |
| FB-1 | Dynamic-colour plumbing (pref, `resolveAccent`, wiring) | 🟩 | — | ⬜ | No visible change while the pref is false |
| FB-2 | Material You toggle in the theme row + device verification | 🟩 | FB-1 | ⬜ | |

---

## 13. Residual risk (honest assessment)

- **The content key is a judgement, not a proof.** `displayName + size` will match the overwhelming majority of real libraries, and it will occasionally collide. FA-1's tests can prove the resolver behaves as specified; they cannot prove the specification is right for every library. The mitigation that actually matters is the visible matched/unmatched count — a user who sees "312 of 340" can tell something is off, where a silent restore could not.
- **The most valuable path — restore onto a new device — is only ever exercised manually.** CI has no emulator, and SAF cannot be driven from a JVM test. The pure core is well covered; the seam between it and Android is covered by one person doing it once.
- **DR-A3 knowingly breaks idempotence for one table.** It is bounded, reported to the user, and has a stated exit, but it is a real exception to a stated invariant.
- **Feature B is cosmetic and reversible; its only real risk is the launch-flash regression**, which is why FB-1 is a separate slice whose acceptance criterion is "nothing changed".
- **Nothing here reduces the risk that CI green means less than it looks** (no instrumented gate; OQ-5 branch protection still open upstream). Both features are designed so their risky parts are JVM-testable specifically because of that.
