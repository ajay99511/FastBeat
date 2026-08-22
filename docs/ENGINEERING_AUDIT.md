# FastBeat Engineering Standards Audit

> **Date:** 2026-08-21  
> **Auditor perspective:** Senior Android engineer (Google L5+/Staff-level lens)  
> **Reference standard:** [engineering-skills](file:///C:/Agents/AgentResearchs/plans/engineering-skills) + modern Kotlin/Android best practices (2024–2026)  
> **Tier classification:** **Consequential** — FastBeat is a shipped consumer product with persisted user data, background services, and a Play Store presence

---

> [!IMPORTANT]
> **Superseded in part by [Addendum A](./AUDIT_ADDENDUM.md) (verification pass, 2026-08-21).**
> A follow-up pass re-ran every claim below against the working tree and the *repository state*.
> It confirmed the findings here, corrected two (§2.3 SharedPreferences scope, §10.4 plugin name),
> and added **one new Critical finding** that outranks everything in this document: `.gitignore`
> excludes the entire `app/` module, leaving two equalizer source files unversioned and the
> committed tree non-compiling. Read the addendum before acting on this audit.

---

## Executive Summary

FastBeat is a well-featured offline media player with a genuinely impressive scope — video with PiP/gesture controls, audio with persistent queues and analytics, image gallery, adaptive layouts, theming, and an equalizer. The tech stack choices (Compose, Media3, Hilt, Room, Kotest) are modern and defensible.

However, when measured against the engineering-skills standards and current industry best practices for a **production-quality, maintainable Android app**, there are **systemic gaps** across architecture, testing, data safety, observability, and code health that would accumulate significant compound cost over time. The gaps fall into three severity tiers:

| Severity | Count | Summary |
|----------|-------|---------|
| 🔴 Critical | 6 | Data loss risk, god class, destructive migrations, swallowed exceptions, near-zero test coverage, no CI build/test |
| 🟡 Major | 9 | Missing domain layer, tight coupling, no use-case abstraction, hardcoded strings, Gson over kotlinx.serialization, no error taxonomy, missing accessibility tests, no lint/detekt, no structured logging |
| 🟢 Minor | 8 | Commented-out code, legacy enums retained, `.tmp` files committed, naming inconsistencies, ProGuard defaults, missing KDoc on public APIs, no Baseline Profile, stale README version numbers |

---

## 1. Architecture & Design Judgment

### 🔴 1.1 — God Object: `PlaybackViewModel` (2,297 lines, 65 public methods, 28 MutableStateFlows)

**Standard violated:** *"A function does one thing at one level of abstraction"* / *"A module has one reason to change and a name that describes a responsibility, not a category"* — [testing-quality.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/testing-quality.md)

**What's happening:** [PlaybackViewModel.kt](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/viewmodel/PlaybackViewModel.kt) is a **monolithic orchestrator** that owns:
- Audio playback control (play, pause, skip, seek, queue management, shuffle, repeat)
- Video playback control (PiP state, resize mode, brightness, track selection)
- Media controller lifecycle (MediaSession connection, Player.Listener)
- Analytics tracking (play counts, playtime accumulation, streak logic)
- Image/video deletion with scoped-storage consent flow
- Bookmark CRUD
- Favorite toggle
- Queue persistence (Room + SharedPreferences)
- Playback speed control
- Error state management
- Navigation state

**Why this matters (business perspective):**
- **Every feature change** touches this file — merge conflicts, accidental regressions, and slow code reviews are inevitable
- **Unit testing is nearly impossible** — the class has too many dependencies and too much interleaved state to isolate behaviors
- **Onboarding new developers** requires understanding 2,300 lines of intertwined state before contributing safely
- **Feature velocity will decline** as the file grows — each new feature (equalizer presets, Chromecast, lyrics) will add more surface area

**Recommended decomposition:**

| Extracted module | Responsibility | Lines (est.) |
|---|---|---|
| `AudioPlaybackManager` | Queue, shuffle, repeat, skip, seek for audio | ~500 |
| `VideoPlaybackManager` | PiP, resize, brightness, video-specific controls | ~300 |
| `MediaControllerBinder` | MediaSession connection lifecycle, Player.Listener | ~200 |
| `PlaybackAnalyticsTracker` | Play counts, playtime accumulation, streak | ~200 |
| `MediaDeletionHandler` | Scoped storage consent, image/track deletion | ~200 |
| `BookmarkManager` | Bookmark CRUD against DAO | ~50 |
| `PlaybackViewModel` (slimmed) | UI state coordination, delegating to above | ~400 |

---

### 🟡 1.2 — Missing Domain / Use-Case Layer

**Standard violated:** *"Push side effects to the edges: pure decision logic in the middle, I/O at the boundary"* — [testing-quality.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/testing-quality.md)

**Current architecture:**
```
UI (Compose) → ViewModel → Repository → Room/MediaStore
```

**What's missing:** There is no explicit **domain/use-case layer**. Business logic (e.g., "should this count as a play?", "is the queue auto-refill threshold reached?", "calculate streak from active days") lives directly in ViewModels, mixed with coroutine dispatching and UI state management.

**Impact:**
- Business rules can't be unit-tested without mocking Android framework classes
- The same logic gets duplicated across ViewModels (e.g., `LibraryViewModel` and `PlaybackViewModel` both deal with media deletion)
- Violates Google's own [recommended architecture](https://developer.android.com/topic/architecture) which explicitly includes a domain layer for non-trivial apps

**Recommendation:** Introduce a `domain/` package with pure Kotlin use cases:
```kotlin
class LogPlayEventUseCase @Inject constructor(
    private val mediaDao: MediaDao
) {
    suspend operator fun invoke(mediaId: Long, playDurationMs: Long) {
        // Pure business rule: only count as play if > 30s
        if (playDurationMs >= PLAY_COUNT_THRESHOLD_MS) {
            mediaDao.incrementPlayCount(mediaId, System.currentTimeMillis())
            mediaDao.logPlayEvent(PlayEvent(mediaId = mediaId))
        }
    }
}
```

---

### 🟡 1.3 — `MediaFile` as a Catch-All Data Bag

[MediaFile.kt](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/model/MediaFile.kt) represents audio, video, AND images with boolean flags (`isVideo`, `isImage`) and nullable fields that only apply to one type (`year` is audio-only, `resolution` is video-only, `album` is audio-only).

**Standard violated:** *"Entity — something with identity"* / *"Value object — something defined by its values"* — [terminology.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/terminology.md)

**Business risk:** As you add features like lyrics (audio-only), chapters (video-only), or EXIF data (image-only), this class becomes a junk drawer where every consumer must check `isVideo`/`isImage` before accessing fields. A sealed hierarchy would make illegal states unrepresentable:

```kotlin
sealed class MediaItem {
    abstract val id: Long
    abstract val uri: Uri
    abstract val title: String
    // ... shared fields
    
    data class Audio(..., val album: String?, val year: Int?) : MediaItem()
    data class Video(..., val resolution: String) : MediaItem()
    data class Image(...) : MediaItem()
}
```

---

### 🟡 1.4 — UI Screens as Mega-Composables

Multiple UI files exceed 500+ lines with business logic embedded directly:

| File | Lines | Concern |
|------|-------|---------|
| `MeScreen.kt` | 1,770 | Analytics dashboard — contains formatting, calculation, and layout in one file |
| `VideoPlayerScreen.kt` | 1,490 | Player controls, gestures, PiP setup, track selection |
| `VideoListScreen.kt` | 1,181 | Video library with folders, search, deletion |
| `NowPlayingScreen.kt` | 930 | Audio player with queue management |

**Standard violated:** *"Components: presentational components take data and callbacks; business rules live outside them"* — [stack-appendices.md §3 Frontend](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/stack-appendices.md)

**Recommendation:** Extract reusable composables into the `components/` package and move state derivation into ViewModels or StateHolders.

---

## 2. Data Integrity & Persistence

### 🔴 2.1 — Destructive Database Migrations

```kotlin
// DatabaseModule.kt, line 27
.fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)
```

**Standard violated:** *"Correctness of data over everything. Never ship a change that can corrupt, lose, or leak persisted data. Migrations are expand → backfill → contract"* — [SKILL.md Non-negotiables](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/SKILL.md)

**Business impact:** Any user upgrading from schema versions 1–4 will **lose all playlists, playback history, analytics, bookmarks, and queue state** without warning. For a media player where the value proposition includes "remembers your position" and "rich analytics," this is a direct contradiction of the product promise.

**What to do:**
1. Write proper `Migration(4, 5)`, `Migration(3, 5)`, etc. with `ALTER TABLE` statements
2. Room schema exports exist in `app/schemas/` — use them with `MigrationTestHelper` to verify migrations
3. The `room-testing` dependency is already in `build.gradle.kts` but there are **zero migration tests**

### 🟡 2.2 — Missing Schema Versions (2, 3, 4)

Only versions 1 and 5 exist in `app/schemas/`. Versions 2–4 were never exported, making it impossible to write or verify migration paths for users on those versions.

### 🟡 2.3 — SharedPreferences for Structured State

[PlaybackViewModel.kt](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/viewmodel/PlaybackViewModel.kt) and [LibraryViewModel.kt](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/viewmodel/LibraryViewModel.kt) use raw `SharedPreferences` for sort state, brightness, and queue index. Modern Android recommends **Jetpack DataStore** (Preferences or Proto) which is:
- Coroutine-safe (SharedPreferences `apply()` can lose writes on process death)
- Observable (returns `Flow<T>`)
- Type-safe with Proto DataStore

### 🟡 2.4 — Gson for JSON Persistence

`Gson` is a legacy library that:
- Cannot handle Kotlin default parameters without `@SerializedName` and explicit nullability
- Is no longer actively developed by Google
- Adds ~260KB to APK size

**Recommendation:** Migrate to `kotlinx.serialization` which is Kotlin-native, compile-time safe, and smaller.

---

## 3. Error Handling & Reliability

### 🔴 3.1 — Swallowed Exceptions

**Standard violated:** *"No silent failure. Errors are handled, surfaced, or propagated — never swallowed. An empty catch is a defect."* — [SKILL.md Non-negotiables](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/SKILL.md)

Found **2 completely silent catch blocks**:

1. **[PlaybackService.kt:107](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/service/PlaybackService.kt#L107)** — `catch (_: Exception) {}` in `onTaskRemoved()`
   - If the position-save fails, the user loses their resume position with **zero diagnostic signal**
   - This runs in a `runBlocking` context, so the exception could mask a Room corruption or disk-full condition

2. **[ThumbnailManager.kt:128](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/data/ThumbnailManager.kt#L128)** — `try { retriever.release() } catch (_: Exception) {}`
   - Resource leak silently ignored (minor, as the retriever is being released, but the pattern normalizes swallowing)

### 🟡 3.2 — `e.printStackTrace()` in Production Code

Found **3 instances** of `e.printStackTrace()` ([MainActivity.kt:114](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/MainActivity.kt#L114), [VideoPlayerScreen.kt:228](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/ui/screens/VideoPlayerScreen.kt#L228), [VideoPlayerScreen.kt:476](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/ui/screens/VideoPlayerScreen.kt#L476)).

These write to `System.err` which is:
- Not visible in Logcat on release builds without `adb logcat`
- Not filterable by tag
- A debug artifact that should never ship

**All should use `Log.e(TAG, "context", e)` or a structured logging wrapper.**

### 🟡 3.3 — No Error Taxonomy

There is no sealed class or enum defining the app's error states. Errors are communicated as raw `String?` messages via `MutableSharedFlow<String>` and `MutableStateFlow<String?>`. This means:
- The UI can't distinguish retryable from fatal errors
- Error messages are hardcoded in English across ViewModels (not localized)
- No way to track error frequency or type analytically

**Recommendation:**
```kotlin
sealed class AppError {
    data class MediaAccessDenied(val mediaId: Long) : AppError()
    data class PlaybackFailed(val cause: PlaybackException) : AppError()
    data class DeleteFailed(val mediaId: Long, val cause: Throwable) : AppError()
    // ...
}
```

---

## 4. Testing & Quality Assurance

### 🔴 4.1 — Near-Zero Meaningful Test Coverage

**Standard violated:** *"Evidence, not assertion. 'Tested', 'works', 'done' are claims that require a command run and its output."* — [SKILL.md Non-negotiables](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/SKILL.md)

**Current state:**

| Test source | Files | What they cover |
|-------------|-------|-----------------|
| `src/test/` | 8 files | 1 placeholder `ExampleUnitTest`, 7 adaptive layout tests |
| `src/androidTest/` | 1 file | 1 placeholder `ExampleInstrumentedTest` |

**What is NOT tested:**
- ❌ `PlaybackViewModel` — 2,297 lines, 65 public methods, **zero tests**
- ❌ `LibraryViewModel` — 686 lines, **zero tests**
- ❌ `MediaRepository` — MediaStore queries, scan logic, **zero tests**
- ❌ `PlaylistRepository` — CRUD, migration logic, **zero tests**
- ❌ `MediaDao` — 190 lines of SQL queries, **zero tests**
- ❌ Room migrations — **zero tests** (despite `room-testing` dependency present)
- ❌ `AudioEffectsManager` — native audio effect lifecycle, **zero tests**
- ❌ Any screen composable — **zero Compose UI tests**
- ❌ `PlaybackService` — background service behavior, **zero tests**

**The test infrastructure is excellent** — Kotest, MockK, Turbine, Robolectric, and Hilt testing are all properly configured. The investment was made in the build system but not in actual test authoring.

**Business impact:** Without tests, every refactoring (including the ViewModel decomposition above) is a high-risk endeavor. The codebase is **change-hostile** — any modification could break undiscovered invariants with no safety net.

**Priority test targets (by risk × frequency of change):**
1. `MediaDao` queries — integration test with in-memory Room
2. Room migrations — `MigrationTestHelper`
3. Play-count / streak logic — unit test pure functions
4. Queue persistence round-trip — unit test
5. Sorting logic — unit test (already pure, easy win)
6. Delete flows — mocked integration test

---

### 🔴 4.2 — No CI Build or Test Execution

**Standard violated:** *"CI on every commit: build, lint, typecheck, test, security scan, artifact build"* — [stack-appendices.md §5](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/stack-appendices.md)

The `.github/workflows/` directory contains:
- `codeql.yml` — security scanning (good)
- `codewiki_sync.yml` — documentation sync

**Missing:**
- ❌ No workflow that runs `./gradlew build` on PRs
- ❌ No workflow that runs `./gradlew test` on PRs
- ❌ No lint check (`./gradlew lint`)
- ❌ No artifact build verification
- ❌ No APK size tracking

This means broken code can be merged without any automated gate.

---

## 5. Security & Privacy

### 🟡 5.1 — No Content Provider Export Restriction

The [PlaybackService](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/AndroidManifest.xml#L47-L54) is declared `android:exported="true"` with only the MediaSessionService intent filter. While this is required for media session discovery, there is no explicit `android:permission` attribute restricting who can bind to the service.

### 🟢 5.2 — `allowBackup="true"` Without Encryption

`android:allowBackup="true"` in the manifest means the Room database (playlists, analytics, history) can be extracted via `adb backup` on debuggable builds. For an offline-only app this is low risk, but `dataExtractionRules` should explicitly exclude the database if backup is enabled, or disable backup entirely.

### ✅ 5.3 — Good: Scoped Storage Compliance

The permission model correctly handles Android 10, 11, and 13+ scoped storage transitions with `READ_MEDIA_VISUAL_USER_SELECTED` for Android 14+. The `RecoverableSecurityException` flow is properly implemented.

---

## 6. Performance & Efficiency

### 🟡 6.1 — O(n) Media Lookups in Hot Paths

While `MediaRepository.mediaById` provides O(1) lookup by ID, several ViewModel operations still scan lists linearly:

```kotlin
// PlaybackViewModel.kt — called on every track transition
audioFiles.find { it.id == history.mediaId }
```

These are fine for small libraries but will degrade for users with 10,000+ tracks.

### 🟡 6.2 — No Baseline Profile

For a Compose-heavy app, a [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles) is the single highest-impact performance optimization. It pre-compiles critical Compose rendering paths at install time, reducing startup time by 20–40%.

**Missing:** No `baselineprofile` module, no profile rules.

### 🟢 6.3 — Thumbnail Generation Strategy is Solid

The `ThumbnailManager` correctly:
- Caches to disk
- Generates in background
- Cleans stale thumbnails
- Uses `MediaMetadataRetriever` appropriately

This is above average for a personal project.

---

## 7. Code Quality & Hygiene

### 🟡 7.1 — No Static Analysis (Detekt / Ktlint)

**Standard violated:** *"Lint, typecheck, and build clean — no new warnings"* — [testing-quality.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/testing-quality.md)

Neither `detekt` nor `ktlint` is configured. This means:
- Code style varies between files (some use trailing commas, some don't)
- Long functions, high cyclomatic complexity, and deep nesting go undetected
- No automated enforcement of Kotlin idioms

### 🟡 7.2 — Hardcoded User-Facing Strings

Error messages and UI labels are hardcoded in English throughout ViewModels:
```kotlin
_userMessage.emit("Couldn't delete this file")
_userMessage.emit("Track removed from queue")
```

These should use string resources (`R.string.*`) for:
- Localization readiness (seam for i18n)
- Accessibility (screen readers use localized strings)
- Consistency (same message in multiple places)

### 🟢 7.3 — Commented-Out Code

[MainScreen.kt](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/ui/MainScreen.kt#L3) contains commented-out imports:
```kotlin
// import com.local.offlinemediaplayer.model.MediaFile
// import androidx.compose.ui.Alignment
// import androidx.lifecycle.compose.collectAsStateWithLifecycle
```

**Standard:** *"Delete commented-out code. Version control remembers it."* — [testing-quality.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/testing-quality.md)

### 🟢 7.4 — Stale Removal Comments

[PlaybackViewModel.kt](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/viewmodel/PlaybackViewModel.kt#L286-L289):
```kotlin
// Removed duplicate setPipMode
// Removed duplicate setPipMode
// Removed duplicate closeVideo due to ambiguity
```

These are archaeological artifacts that add noise. The version control history already captures what was removed.

### 🟢 7.5 — Legacy Enums Retained

`SortOption` and `AlbumSortOption` in [PlaybackViewModel.kt](file:///C:/Users/ajaye/My_Products/FastBeat/app/src/main/java/com/local/offlinemediaplayer/viewmodel/PlaybackViewModel.kt#L68-L82) are marked as *"Superseded by SortState"* and *"retained only so persisted preference ordinals can be migrated"*. This is fine short-term, but they should be deleted after one release cycle when all users have migrated.

### 🟢 7.6 — Build Artifacts Committed

`build_info_output.txt` (49KB) and `build_stacktrace.txt` (4KB) are committed to the repo. These should be in `.gitignore`.

### 🟢 7.7 — `build.gradle.kts.tmp` Committed

A temporary file `app/build.gradle.kts.tmp` is committed. This is noise.

---

## 8. Observability & Diagnostics

### 🟡 8.1 — No Crash Reporting

**Standard violated:** *"You can diagnose a novel production problem without shipping new code"* — [reliability-observability.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/reliability-observability.md)

No crash reporting SDK (Firebase Crashlytics, Sentry, etc.) is integrated. When users experience crashes in production, there is **zero visibility** into what happened.

For a 100% offline app, this is partially mitigated — but even local crash logs would be useful for debugging.

### 🟡 8.2 — `Log.e` with Inconsistent Tags

Logging uses `Log.e(TAG, ...)` with per-class TAGs, which is the Android convention. However:
- No structured context (correlation ID, media ID, operation name) in log messages
- No release-build log stripping — `Log.d`/`Log.v` calls (if added) would ship to production
- Consider using Timber for automatic tag management and release-tree stripping

---

## 9. Mobile-Specific Standards (Stack Appendix §4)

### ✅ 9.1 — Good: Lifecycle Awareness

- PiP mode transitions are correctly handled in `MainActivity`
- Audio continues in background while video pauses on `onStop`
- State restoration via `rememberSaveable` in Compose
- Service correctly persists position in `onTaskRemoved`

### ✅ 9.2 — Good: Permission Handling

- Permissions are requested with proper rationale screens
- Graceful degradation paths exist for denied permissions
- Android 14+ `READ_MEDIA_VISUAL_USER_SELECTED` is handled

### ✅ 9.3 — Good: Adaptive Layouts

- `WindowSizeClass` from Material3 Adaptive is used
- Foldable device postures are detected
- 9 dedicated adaptive layout files exist
- Tests exist for adaptive behavior (the only well-tested area)

### 🟡 9.4 — Missing: Process Death State Restoration

While Compose `rememberSaveable` handles configuration changes, full **process death** restoration (where Android kills the process and restores from `SavedStateHandle`) is not systematically addressed. Queue state is persisted to Room (good), but transient UI states (current tab, scroll position, dialog state) may be lost.

---

## 10. Build System & Dependencies

### ✅ 10.1 — Good: Version Catalog

`gradle/libs.versions.toml` is well-organized with clear sections and comments. All versions are centralized.

### ✅ 10.2 — Good: Modern Toolchain

- AGP 9.0, Kotlin 2.2.10, KSP (not KAPT) — cutting edge
- Compose BOM for consistent Compose versions
- Room schema exports enabled

### 🟢 10.3 — `compileSdk = 36` vs `targetSdk = 35`

This is valid (compile against latest, target the stable API level), but the README states "Android SDK 35 (Target API 35)" while actually compiling against 36. Minor documentation drift.

### 🟢 10.4 — Missing `kotlin-android` Plugin

The root `build.gradle.kts` declares `kotlin-compose` and `ksp` but the `kotlin-android` plugin from `libs.versions.toml` (line 107) is defined but not applied anywhere. The Compose plugin may implicitly include it, but the explicit declaration in the catalog is dead configuration.

---

## 11. Business-Side Evaluation

### Product-Market Fit Assessment

| Dimension | Rating | Notes |
|-----------|--------|-------|
| **Feature completeness** | ⭐⭐⭐⭐ | Impressive breadth for a solo project — video, audio, images, PiP, analytics, theming, equalizer, adaptive layouts |
| **Polish** | ⭐⭐⭐ | Good UI framework (Compose + Material3), but mega-composable screens likely have UX jank on low-end devices |
| **Reliability** | ⭐⭐ | Destructive migrations, swallowed exceptions, and no crash reporting significantly undermine trust |
| **Maintainability** | ⭐⭐ | God class ViewModel and zero test coverage make feature iteration risky |
| **Scalability (team)** | ⭐ | No contributor could safely work on `PlaybackViewModel` without understanding the entire file |
| **Release confidence** | ⭐ | No CI, no tests, no crash reporting = blind shipping |

### Competitive Moat Analysis

FastBeat's differentiators (offline-only, analytics dashboard, adaptive layouts) are genuine. However, competitors like VLC, Musicolet, and Poweramp have:
- Mature codebases with years of edge-case hardening
- Codec support (FFmpeg integration) that Media3/ExoPlayer alone doesn't cover
- Active crash reporting and A/B testing infrastructure

**To compete effectively, FastBeat needs to shift from "feature-first" to "reliability-first" development.** The next 3 features should be: proper migrations, test coverage, and CI — not new user-facing capabilities.

---

## 12. Prioritized Remediation Roadmap

### Phase 1 — Stop the Bleeding (1–2 weeks)

| # | Action | Effort | Impact |
|---|--------|--------|--------|
| 1 | Write proper Room migrations (4→5 at minimum) | 2–3h | 🔴 Prevents data loss on upgrade |
| 2 | Replace swallowed exceptions with `Log.e` | 30min | 🔴 Makes failures diagnosable |
| 3 | Replace `e.printStackTrace()` with `Log.e(TAG, ...)` | 30min | 🟡 Clean diagnostic output |
| 4 | Add CI workflow: `./gradlew build lint test` | 1h | 🔴 Prevents broken merges |
| 5 | Delete `build_info_output.txt`, `.tmp`, commented code | 30min | 🟢 Hygiene |
| 6 | Add `.gitignore` entries for build artifacts | 15min | 🟢 Prevent recurrence |

### Phase 2 — Foundation (2–4 weeks)

| # | Action | Effort | Impact |
|---|--------|--------|--------|
| 7 | Write Room DAO integration tests (in-memory DB) | 1–2 days | 🔴 Validates all SQL queries |
| 8 | Write Room migration tests | 1 day | 🔴 Validates data safety |
| 9 | Extract `PlaybackViewModel` into 5–6 focused classes | 3–5 days | 🔴 Enables testability |
| 10 | Add unit tests for extracted classes (Kotest + MockK) | 2–3 days | 🔴 Safety net for future changes |
| 11 | Integrate detekt + ktlint with CI | 2h | 🟡 Automated style enforcement |
| 12 | Replace hardcoded strings with `R.string.*` | 1 day | 🟡 i18n readiness |

### Phase 3 — Professional Grade (4–8 weeks)

| # | Action | Effort | Impact |
|---|--------|--------|--------|
| 13 | Introduce domain/use-case layer | 2–3 days | 🟡 Clean architecture |
| 14 | Model `MediaFile` as sealed hierarchy | 2 days | 🟡 Type safety |
| 15 | Replace SharedPreferences with DataStore | 1–2 days | 🟡 Coroutine safety |
| 16 | Replace Gson with kotlinx.serialization | 1 day | 🟡 Kotlin-native serialization |
| 17 | Add Baseline Profile | 1 day | 🟡 Performance optimization |
| 18 | Add Firebase Crashlytics (or Sentry) | 2h | 🟡 Production visibility |
| 19 | Compose UI tests for critical screens | 3–5 days | 🟡 UI regression safety |
| 20 | Introduce error taxonomy (sealed class) | 1 day | 🟡 Consistent error handling |

---

## Appendix A: What FastBeat Does Well

It would be dishonest not to acknowledge the strengths:

1. **Modern stack choices** — Kotlin 2.2, Compose, Media3, Hilt, KSP, Room — all current best-practice selections
2. **Gradle configuration** — Version catalog, KSP instead of KAPT, proper test dependency separation, archivesName, R8 enabled
3. **Adaptive layouts** — Proper `WindowSizeClass`, foldable support, and the only area with real test coverage
4. **Scoped storage handling** — Correctly implements the full Android 10/11/13/14 permission matrix
5. **Thumbnail caching** — Thoughtful background generation with stale cleanup
6. **Queue persistence** — Room-backed queue survives process death
7. **PiP implementation** — Handles both pre-S manual and S+ automatic enter modes
8. **Audio effects** — Well-documented safety contract for the equalizer bypass behavior
9. **Sort state migration** — Clean migration from legacy combined-enum to field+direction model
10. **Documentation** — README, CONTRIBUTING, SECURITY, BLOGS_GUIDE, release notes — above average for a personal project

## Appendix B: Engineering Standards Reference Mapping

| Engineering Standard Document | Relevant FastBeat Gaps |
|---|---|
| [SKILL.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/SKILL.md) (Non-negotiables) | Data correctness (migrations), No silent failure (swallowed exceptions), Evidence not assertion (no tests) |
| [design-judgment.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/design-judgment.md) | Essentialism triage not applied, Cargo-cult layering (repo→VM with no domain) |
| [testing-quality.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/testing-quality.md) | Near-zero coverage, No behavior tests, Test infrastructure unused |
| [security-privacy.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/security-privacy.md) | Exported service without permission, allowBackup without exclusion |
| [reliability-observability.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/reliability-observability.md) | No crash reporting, No structured logging, No production diagnostics |
| [performance-efficiency.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/performance-efficiency.md) | No Baseline Profile, Linear scans in hot paths |
| [stack-appendices.md §4 Mobile](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/stack-appendices.md) | Good lifecycle handling, Missing process death restoration |
| [lifecycle-gates.md](file:///C:/Agents/AgentResearchs/plans/engineering-skills/skills/engineering-standards/references/lifecycle-gates.md) | No CI gate, No self-review checklist enforcement |
