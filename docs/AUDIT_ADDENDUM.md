# Engineering Audit — Addendum A (Verification Pass)

> **Date:** 2026-08-21
> **Purpose:** This addendum records findings from a *verification pass* run against the working tree
> after [ENGINEERING_AUDIT.md](./ENGINEERING_AUDIT.md) was written. Every claim below was confirmed by
> a command, and the command is shown so it can be re-run.
>
> **Why this exists:** the original audit read the code. This pass read the code **and the repository
> state**. Three defects live in the gap between those two things — they are invisible to anyone
> reviewing source files alone, and two of them block the entire remediation plan.

---

## A.1 🔴 CRITICAL — `.gitignore` excludes the entire `app/` module; two source files are unversioned

**Severity:** Critical (permanent source loss + non-compiling committed tree)
**Status:** Not fixed — this is now the first task in the plan.

### Evidence

```console
$ git check-ignore -v app/src/main/java/com/local/offlinemediaplayer/NewFile.kt
.gitignore:7:/app       app/src/main/java/com/local/offlinemediaplayer/NewFile.kt

$ git check-ignore -v baseline-prof.txt
.gitignore:18:*.txt     baseline-prof.txt
```

`.gitignore` line 7 is `/app` and line 18 is `*.txt`. Line 7 was almost certainly intended as
`/app/build` or `app/release`; as written it ignores the **entire application module**.

Files already tracked stay tracked, which is why this has gone unnoticed — 164 files under `app/`
remain in the index from before the rule took effect. But **every new file created under `app/` is
silently ignored.**

### Consequence 1 — the equalizer feature is not in version control

```console
$ find app/src -name "*.kt" | while read f; do
    git ls-files --error-unmatch "$f" >/dev/null 2>&1 || echo "UNTRACKED: $f"; done
UNTRACKED: app/src/main/java/com/local/offlinemediaplayer/audio/AudioEffectsManager.kt
UNTRACKED: app/src/main/java/com/local/offlinemediaplayer/ui/components/EqualizerSheet.kt
```

These two files exist only on one machine's disk. `git clean -xfd`, a fresh clone, or a lost disk
destroys the equalizer feature permanently. `git status` does **not** warn about them, because
ignored files are not reported as untracked.

### Consequence 2 — the committed tree does not compile

Three **tracked** files reference the two **untracked** ones:

```console
$ git grep -n "AudioEffectsManager\|EqualizerSheet" HEAD -- app/src
HEAD:.../service/PlaybackService.kt:12:  import com.local.offlinemediaplayer.audio.AudioEffectsManager
HEAD:.../service/PlaybackService.kt:28:  @Inject lateinit var audioEffectsManager: AudioEffectsManager
HEAD:.../ui/screens/NowPlayingScreen.kt:56: import com.local.offlinemediaplayer.ui.components.EqualizerSheet
HEAD:.../viewmodel/PlaybackViewModel.kt:27: import com.local.offlinemediaplayer.audio.AudioEffectsManager
HEAD:.../viewmodel/PlaybackViewModel.kt:121:    val audioEffects: AudioEffectsManager
```

A clean clone of `feature--equilizer-integration` fails to compile with unresolved references. The
breakage is branch-local — `master` has no references to either symbol — so it was introduced by
commit `91681be` on this branch.

> [!CAUTION]
> **This re-orders the plan.** The original plan scheduled CI (P0-B) immediately after `.gitignore`
> tidying, and rated the `.gitignore` task "⬜ None risk — hygiene". In reality CI **cannot pass**
> until this is fixed, and the fix is a data-preservation task, not hygiene. Adding CI first would
> produce a red build whose cause looks like a CI misconfiguration rather than a missing file — an
> expensive false trail.

### Consequence 3 — several planned tasks were silently unachievable

| Planned artifact | Path | Blocked by |
|---|---|---|
| Room schema `6.json` (old P1-C.3) | `app/schemas/…` | `/app` rule |
| Migration test (old P1-D.1) | `app/src/androidTest/…` | `/app` rule |
| All new test files (old P2-A, P2-C, P2-E) | `app/src/test/…` | `/app` rule |
| Extracted classes (old P2-D) | `app/src/main/…` | `/app` rule |
| Baseline Profile output (old P3-E) | `baseline-prof.txt` | `*.txt` rule |

Every one of these would have been written, "committed", and silently lost.

---

## A.2 🟡 MAJOR — Migration tests cannot run with the current build configuration

**Status:** Not fixed — added as an explicit prerequisite task ahead of writing any migration test.

The original audit correctly noted "`room-testing` is already in `build.gradle.kts`". Verification
shows it is present in **only one** source set, and the supporting configuration is absent:

```console
$ grep -n "room.testing" app/build.gradle.kts
133:    testImplementation(libs.room.testing)     # src/test only — NOT androidTest

$ grep -n "sourceSets\|assets.srcDir" app/build.gradle.kts
(no output)
```

Two concrete gaps:

1. **`room-testing` is not on the `androidTest` classpath.** The original plan placed
   `MigrationTest.kt` in `app/src/androidTest/`, where the dependency does not exist.
2. **The exported schemas are not on any test classpath.** `MigrationTestHelper` must read
   `app/schemas/…/1.json` at test runtime. That requires either an
   `androidTest { assets.srcDirs += files("$projectDir/schemas") }` source-set entry, or — for a JVM
   test — the schema directory passed in as a system property. Neither exists.

A third, subtler issue: unit tests run under `useJUnitPlatform()` with Kotest, while
`MigrationTestHelper` is a **JUnit 4 `TestRule`**. It can be driven manually without the rule, but
that is a deliberate design choice the plan must state rather than leave the implementor to discover.

---

## A.3 🟡 MAJOR — `playback_history` migration carries a schema-validation risk

**Status:** Open — resolution deferred to the migration test, by design (see plan Decision Record DR-3).

Schema v1 → v5 requires three new columns on `playback_history`:

```
v1: mediaId, position, timestamp, mediaType
v5: mediaId, position, duration, timestamp, mediaType, audioTrackIndex, subtitleTrackIndex
```

All three new columns are `NOT NULL` **with no SQL default**, because the entity declares Kotlin
defaults rather than `@ColumnInfo(defaultValue = …)`:

```kotlin
val duration: Long = 0,            // Kotlin default — not a SQL default
val audioTrackIndex: Int = -1,
val subtitleTrackIndex: Int = -1
```

The exported v5 `createSql` confirms no `DEFAULT` clause is emitted:

```sql
CREATE TABLE IF NOT EXISTS `playback_history` (`mediaId` INTEGER NOT NULL, `position` INTEGER NOT NULL,
  `duration` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `mediaType` TEXT NOT NULL,
  `audioTrackIndex` INTEGER NOT NULL, `subtitleTrackIndex` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))
```

**The tension:** SQLite requires a `DEFAULT` when adding a `NOT NULL` column to a populated table, so
the migration must write `ADD COLUMN duration INTEGER NOT NULL DEFAULT 0`. The resulting column then
*has* a SQL default that the exported schema does not declare.

Whether `runMigrationsAndValidate()` rejects that mismatch depends on Room's internal
`TableInfo.Column` comparison rules, which are version-specific (Room 2.7.2 here). **This addendum
does not guess the answer** — the plan resolves it empirically by writing the test first and keeping a
documented fallback (table rebuild) ready. See plan Decision Record DR-3.

---

## A.4 Corrections and confirmations to the original audit

Everything else in the original audit was re-verified. Results:

| Original claim | Verdict | Evidence |
|---|---|---|
| `PlaybackViewModel` is 2,297 lines | ✅ Confirmed (2,296) | `wc -l` |
| `MeScreen` 1,770 / `VideoPlayerScreen` 1,490 / `VideoListScreen` 1,181 / `NowPlaying` 930 | ✅ Confirmed (1769/1489/1180/929) | `wc -l` |
| 2 swallowed `catch (_: Exception) {}` | ✅ Confirmed, exact lines | `grep -rn "catch (_"` |
| 3 × `e.printStackTrace()` | ✅ Confirmed, exact lines | `grep -rn printStackTrace` |
| `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)` | ✅ Confirmed | `DatabaseModule.kt:27` |
| Only schemas 1 and 5 exported | ✅ Confirmed | `ls app/schemas/…` |
| v1 = 2 tables, v5 = 8 tables | ✅ Confirmed | schema JSON |
| No build/test CI workflow | ✅ Confirmed — only `codeql.yml`, `codewiki_sync.yml` | `ls .github/workflows` |
| 8 unit test files (7 real + 1 placeholder), 1 androidTest placeholder | ✅ Confirmed | `find app/src/test` |
| Build artifacts committed | ✅ Confirmed — and *still tracked* despite the `*.txt` ignore | `git ls-files` |
| No detekt / ktlint | ✅ Confirmed | root `build.gradle.kts` |
| Gson used in `PlaylistRepository` only | ✅ Confirmed — single consumer, 2 call sites | `grep -rn Gson` |
| SharedPreferences in 2 ViewModels | ⚠️ **Revised** — actually **5 sites** | see below |
| §10.4 "`kotlin-android` plugin defined but not applied" | ⚠️ **Revised** — the real gap is the **Hilt** plugin | see below |

### Revision to §2.3 — SharedPreferences has five consumers, not two

```console
app/src/main/java/…/audio/AudioEffectsManager.kt:57    getSharedPreferences(PREFS, MODE_PRIVATE)
app/src/main/java/…/data/SortPreferencesManager.kt:17  getSharedPreferences("sort_playlists", …)
app/src/main/java/…/viewmodel/LibraryViewModel.kt:52   getSharedPreferences("app_prefs", …)
app/src/main/java/…/viewmodel/PlaybackViewModel.kt:149 getSharedPreferences("app_prefs", …)
app/src/main/java/…/viewmodel/ThemeViewModel.kt:25     getSharedPreferences("app_prefs", …)
```

Three of them share the **same `app_prefs` file** from three different classes with no shared key
registry — so a key collision between `LibraryViewModel`, `PlaybackViewModel`, and `ThemeViewModel`
would be a silent data-corruption bug. This raises the priority of the DataStore work and means it
must be done as **one coordinated migration**, not three independent ones.

### Revision to §10.4 — the unapplied plugin is Hilt, not `kotlin-android`

The root `build.gradle.kts` declares `android.application`, `kotlin.compose`, and `ksp` with
`apply false`, but **not** `hilt.android` — which `app/build.gradle.kts` nonetheless applies. This
currently resolves, but it is inconsistent with how the other three plugins are declared and should
be normalized when the root build file is next touched.

---

## A.5 Net effect on severity counts

| Severity | Original | Revised | Delta |
|---|---|---|---|
| 🔴 Critical | 6 | **7** | +1 (`.gitignore` / unversioned sources) |
| 🟡 Major | 9 | **11** | +2 (migration test wiring, migration default-value risk) |
| 🟢 Minor | 8 | 8 | — |

The top-priority action changes from *"write Room migrations"* to *"restore version-control
integrity"*, because the latter is a precondition for every other task producing a durable artifact.
