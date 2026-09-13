# Plan: Decompose the large UI files

**Tier:** Standard per slice, Consequential in aggregate — no behaviour change is intended anywhere, but the work touches every screen the user sees.
**Author / date:** 2026-09-08
**Status:** Complete — all slices `UI-1`–`UI-7` done; follow-ups N-6 and N-10 also closed
**Companion:** `implementation_plan.md`. This is the work its §3 triage deferred as *"Rewriting the mega-composables … stays on the backlog until Phases 0–4 are done."* Phases 0–4 closed (40 of 43 tracker rows ✅), so it is now unblocked rather than jumping the queue. Task IDs use the `UI-*` namespace.

---

## 1. Problem and outcome

**Outcome:** a contributor can find the code for a thing they can see on screen, change it without reading a thousand lines of unrelated code, and render it in isolation in a test or a preview. Today the six largest files hold 60 % of the UI, and the largest single *function* is 1 220 lines.

**Why it is worth doing now, when the app works:** this is inert debt — it loses no data and crashes nothing — but it is compounding, and two concrete costs are already visible in the repository:

1. **It has already produced a wrong abstraction.** F-7 and P5-G.1 recorded that `MiniPlayer` could not be tested until it was split into a ViewModel-bound wrapper and a stateless `MiniPlayerContent`. Every mega-composable has the same property: it cannot be tested at all.
2. **It has corrupted the formatting.** At 10+ levels of nesting, ktlint wraps code into one- and two-token columns and breaks comments mid-sentence. `MeScreen` contained the comment `// If clicking "Last / Played" (not / active), we / need to play it.` split across six lines. That is not a style preference; it is unreadable code produced mechanically by depth.

**Invariants (the whole point of this work):**
1. **Zero behaviour change.** Every slice is a move or an extraction. If a slice changes what the user sees, it has failed, and the plan's guiding principle — *"Zero regressions. Every change independently verifiable and independently revertible"* — applies unchanged.
2. **Recomposition scope is preserved.** Where a block collects a flow, the collection stays at the same depth. Hoisting a `collectAsStateWithLifecycle` into the parent widens the recomposition scope and is a silent performance regression, not a refactor.
3. The compiler is the safety net for references; detekt, ktlint and the 249-test suite are the safety net for quality. Nothing merges without all four green.

**Non-goals:**
- **Changing any signature to "improve" architecture** — e.g. replacing a `PlaybackViewModel` parameter with callbacks. That is a real improvement (audit §1.4, F-7) and it is a *different task*, because it changes call sites rather than moving them. Doing both at once makes a behaviour bug indistinguishable from a move bug.
- **Rewriting the drawing code.** `ActivityBarChart` is 206 lines of Canvas work. It is long because it draws a chart, not because it is badly organised.
- **A multi-module split.** `implementation_plan.md` §3 already rejected this for want of evidence; nothing here changes that.
- **Externalising the hardcoded strings** found along the way. Real (N-2, N-3 in the data-portability plan) but a separate concern; mixing it in would hide moves inside string churn.

---

## 2. The bar, taken from the repo's own configuration

These are not aspirations — they are what `detekt.yml`, `app/build.gradle.kts` and the baselines already enforce. A new file that breaks any of them fails CI.

| Rule | Value | Source | Consequence for this work |
|---|---|---|---|
| `LongMethod` | **120 lines** | `detekt.yml` (raised from 60 *because* composables are declarative) | Every newly created composable must come in under 120 lines. This is the single number that decides how far a split has to go. |
| `WildcardImport` | **active**, baselined per file | `detekt-baseline.xml` | **New files must use explicit imports.** A wildcard in a new file is a new violation; the baseline only covers the files that already had one. |
| `MaxLineLength` | 120 | `detekt.yml` | Dedenting makes lines shorter, so moves help here rather than hurt. |
| `LongParameterList` | 10 (functions) | `detekt.yml`, deliberately raised | Many small parameters is the *wanted* shape. Do not pass a ViewModel to dodge this. |
| `MagicNumber` | excluded under `**/ui/**` | `detekt.yml` | dp/sp literals move as-is. |
| ktlint | baselined by **file + line** | `app/config/ktlint/baseline.xml`, F-38 | Editing above a baselined line breaks an untouched file. Fix the violation, never re-pin. |

### The trap that will catch the next person

**detekt's baseline is keyed by `FileName.kt$signature`, not by path or line.** Move a baselined function to a new file and its suppression stops matching, so detekt reports pre-existing debt as a *new* violation and CI goes red on code you only relocated.

The fix is **not** `./gradlew detektBaseline` — regenerating absolves genuinely new problems at the same time. Edit the affected `<ID>` entries by hand, changing only the filename:

```
-  <ID>LongMethod:MeScreen.kt$@Composable private fun ActivityBarChart( … )</ID>
+  <ID>LongMethod:ActivityTrendsSection.kt$@Composable private fun ActivityBarChart( … )</ID>
```

Same debt, new address, and the diff says exactly that. Note that a file keeping its *name* (`MeScreen.kt` moving into `me/`) keeps its entries automatically, because the key has no path in it.

### A second trap, found the hard way in `UI-1`

Pruning imports by asking "does this symbol appear in the file?" gives **false positives from comments** — `ActivityTrendsSection.kt` retained `import …foundation.background` because the word appeared in `// Draw background pill for selected`. Strip comments before matching, and let `ktlintCheck` be the authority. Conversely, `runtime.getValue` / `runtime.setValue` are delegate operators that a naive search calls unused and the compiler requires: never remove them by hand.

---

## 3. Target structure

One folder per screen-level feature, holding the screen and only the composables that screen owns. Shared leaves stay in `ui/components/`.

```
ui/screens/
  me/                            ← UI-1, UI-2 ✅  (1 848 L in one file → 2 213 L across 9)
    MeScreen.kt              240   state + the vertical order of ten sections, nothing else
    ThemeSwitcherRow.kt      100   ThemeSwitcherRow, ThemeButton
    ContinueWatchingSection.kt 210 ContinueWatchingSection, ContinueWatchingCard
    ListeningActivitySection.kt 429 …Section, FavoritesCard, NowPlayingSummaryRow,
                                   FavoriteTrackRow, AnalyticsCard
    SuggestedTracksSection.kt  496 …Section, FeaturedTrackCard, PlayActionsRow,
                                   GradientDivider, SuggestedTrackRow, SuggestedTrackArtwork
    LibrarySummarySection.kt   181 LibraryStatsSection, StatCard
    ActivityTrendsSection.kt   343 ActivityTrendsSection, ActivityBarChart
    MeSettingsCards.kt         155 DarkModeToggleCard, AccessibilityGuideCard
    ShuffleAllButton.kt         59 ShuffleAllButton
  video/                         ← UI-3, UI-4 ✅  (3 616 L in 3 files → 3 966 L across 11)
    VideoListScreen.kt       698   the video library screen
    VideoListItems.kt        578   VideoListItem + VideoCardItem — the shared pair, see below
    VideoPlayerScreen.kt     564   the player: state, gestures, PiP, lifecycle
    VideoPlayerControls.kt   512   VideoPlayerControls, PlayerTopBar, PlayerBottomControls
    VideoPlayerDialogs.kt    374   BookmarksDialog, TrackSelectionDialog, TrackSelectionHeader, TrackItem
    VideoFolderScreen.kt     331   the folder/movies tab shell
    MoviesListContent.kt     267   the Movies tab content
    VideoPlayerOverlays.kt   254   CenterGesture, Buffering, Error, SpeedBoost
    FolderItems.kt           175   FolderItem, FolderListItem
    ContinueWatchingRow.kt   162   ContinueWatchingRow, ContinueWatchingTile
    PlayerSystemUi.kt         51   hideSystemBars, showSystemBars, calculatePipAspectRatio
  player/                        ← UI-5 ✅  (969 L in one file → 1 066 L across 4)
    NowPlayingScreen.kt      601   the screen: artwork, metadata, transport, sheets
    QueueSheet.kt            225   QueueSheetContent — the draggable play queue
    NowPlayingDialogs.kt     141   PlaybackSpeedDialog, SleepTimerDialog, formatSpeed
    PlaybackControls.kt       99   PlaybackControlsWithProgress — the seek bar
  audio/        7 files  3 533 L  ← UI-6 ✅  Album*, Artist*, AudioLibrary, AudioList, Decade*, SmartPlaylistDetail
  playlist/     3 files  1 954 L  ← UI-6 ✅  PlaylistList (serves BOTH tabs), PlaylistDetail, VideoPlaylistDetail
  onboarding/   2 files    689 L  ← UI-6 ✅  PermissionScreens, AccessibilityGuideScreen
  image/        1 file     342 L  ← UI-6 ✅  ImageListScreen
                                   ui/screens/ now has ZERO loose files.
```

**Naming rules**
- A file is named for **what it contains**, not for where it came from: `LibrarySummarySection.kt`, not `MeScreenPart2.kt`.
- A file holding one public composable plus its private leaves is named after the public one.
- Section composables end in `Section`; tappable units end in `Card` / `Button` / `Row`.

**Visibility rules** — Kotlin's `private` is *file*-private, so splitting a file breaks every `private` reference that crosses the new boundary. That is the one mechanical hazard in a pure move.
- `internal` — called from a sibling file in the same package (most section composables).
- `private` — leaf used only within its own file (`ThemeButton`, `StatCard`, `ActivityBarChart`).
- `public` — only for something genuinely used from another package. `UI-1` found `ThemeButton`, `AnalyticsCard`, `BookmarksDialog`, `TrackSelectionDialog`, `QueueSheetContent` and `PlaybackControlsWithProgress` were all public and all used in exactly one file: public by accident, not by design. Narrow on the way past.

**Parameter rules** — follow the shape the codebase already uses, which `LibraryStatsSection` and `ActivityTrendsSection` established: pass **data + the accent colour + callbacks**, not a ViewModel and not a whole screen state object. `LongParameterList` is set to 10 precisely so this is allowed.

---

## 4. Slices

Ordered by size of prize against risk. One per session.

| ID | Target | Before | Risk | Status |
|---|---|---|---|---|
| UI-1 | `MeScreen.kt` → `ui/screens/me/`, moving the already-standalone composables and extracting the two settings cards | 1 848 L | 🟩 | ✅ |
| UI-2 | Extract `MeScreen`'s three remaining inline blocks — continue-watching (144 L), listening-activity (411 L), suggested-tracks (417 L) | 1 145 L | 🟨 | ✅ |
| UI-3 | `VideoPlayerScreen.kt` → `ui/screens/video/` | 1 580 L | 🟨 | ✅ |
| UI-4 | `VideoListScreen.kt` + `VideoFolderScreen.kt` → `ui/screens/video/` | 1 215 / 821 L | 🟨 | ✅ |
| UI-5 | `NowPlayingScreen.kt` → `ui/screens/player/` | 969 L | 🟨 | ✅ |
| UI-6 | Group the remaining flat screens into `audio/`, `playlist/`, `image/`, `onboarding/` | 13 files | 🟩 | ✅ |
| UI-7 | Retire the wildcard imports and their five baseline entries in `MeScreen.kt` once it is small | — | 🟩 | ⬜ |

### UI-2 preparation (already measured, so the next session does not have to)

`MeScreen`'s inline blocks close over exactly these, and nothing else:

| Block | Lines | Closes over |
|---|---|---|
| Continue watching | 144 | `continueWatchingList`, `onPlayMedia`, `theme` |
| Listening activity | 411 | `analytics`, `onPlayMedia`, `theme`, `viewModel.currentTrack`, `viewModel.lastPlayedAudio` |
| Suggested tracks | 417 | `audioList`, `displayedSongs`, `randomSongs`, `searchQuery`, `isExpanded`, `onPlayMedia`, `theme`, `viewModel.setQueue` |

Notes that decide the signatures:
- All three exceed 120 lines, so each needs a second level of extraction (the LazyRow item, the favourites card, the track row) — a move alone will not pass detekt.
- **Pass `currentTrack` and `lastPlayedAudio` as `StateFlow`s, not as collected values.** They are collected four levels deep inside the favourites card; collecting them in `MeScreen` instead would recompose the whole screen on every track change. This is invariant 2.
- `isExpanded` is only ever set to `true` (one "show more" affordance), so hoist it as `isExpanded: Boolean, onExpand: () -> Unit`.
- `theme` is `LocalAppTheme.current` — an ambient. Existing sections take `primaryColor: Color` explicitly; match that.

---

## 5. Verification

Per slice, all four, with real output pasted into the tracker:

```
./gradlew :app:compileDebugKotlin      # references and visibility
./gradlew :app:detekt                  # LongMethod / WildcardImport on the new files
./gradlew :app:ktlintCheck             # unused imports, indentation, import order
./gradlew :app:testDebugUnitTest       # 249 tests must stay at 249 passing
```

Plus, because none of the above can see the screen: **open the affected tab on a device and confirm it looks the same.** A pure move has no unit test that would fail if it were wrong in a way the compiler allows.

**What none of this catches** — the honest gap. There is no screenshot test and no instrumented gate in CI (OQ-5). A refactor that reorders two sections, drops a `Spacer`, or changes a padding compiles, lints and tests green. The only defences are that the moved code is byte-identical where possible, and a human looking at the screen.

---

## 6. Stop-and-ask triggers

- A move cannot be done without changing a signature or a call site's arguments → that is not a move; card it separately.
- A composable cannot be brought under 120 lines without restructuring its logic → stop, because that is a redesign.
- `detektBaseline` looks like the answer → it is not; hand-edit the entries (§2).
- A `private` member is needed from two files and `internal` feels wrong → the split boundary is in the wrong place.
- The screen looks different afterwards, in any respect → revert the slice, do not patch forward.

---

## 7. Progress tracker

| ID | Task | Risk | Status | Evidence |
|---|---|---|---|---|
| UI-1 | `MeScreen.kt` → `ui/screens/me/` (7 files) | 🟩 | ✅ | 1 848 L → 1 145 L entry + 6 files, largest new file 344 L. `compileDebugKotlin` BUILD SUCCESSFUL; `detekt` + `ktlintCheck` + `testDebugUnitTest` BUILD SUCCESSFUL, **249 tests, 0 failures, 0 errors, 0 skipped**. 3 detekt baseline entries relocated `MeScreen.kt` → `ActivityTrendsSection.kt` (not regenerated). `ThemeButton` narrowed `public`→`private`; `AnalyticsCard`, `ShuffleAllButton`, `LibraryStatsSection`, `ActivityTrendsSection` → `internal`. One dead commented-out line (`// onCheckedChange = { viewModel.toggleThemeMode() }`) deleted. Single importer `AdaptiveMeScreen.kt:12` repointed. |
| UI-2 | Extract `MeScreen`'s three inline blocks | 🟨 | ✅ | 1 145 L → **240 L**; `MeScreen` itself is now 100 lines of state plus the vertical order of ten sections. 3 new files, 23 composables in the package, **every one under the 120-line gate**. `assembleDebug` + `lint` + `detekt` + `ktlintCheck` BUILD SUCCESSFUL; **249 tests, 0 failures, 0 errors, 0 skipped**. See the note below on what this removed from the baseline. |
| UI-3 | `VideoPlayerScreen.kt` → `video/` | 🟨 | ✅ | 1 580 L → 5 files, largest 564 L. `assembleDebug` + `lint` + `detekt` + `ktlintCheck` BUILD SUCCESSFUL; **249 tests, 0 failures, 0 errors, 0 skipped**. 6 composables narrowed from `public`/`private` to `internal`, 3 kept `private` (same-file callers only). `GestureMode` had to go `private`→`internal`: `CenterGestureOverlay` takes it as a parameter, which the compiler caught immediately. Baseline: 5 entries deleted, 3 relocated — see below. |
| UI-4 | `VideoListScreen` + `VideoFolderScreen` → `video/` | 🟨 | ✅ | 2 036 L in 2 files → 6 files (the `video/` package is now 11 files, largest 698 L). `assembleDebug` + `lint` + `detekt` + `ktlintCheck` BUILD SUCCESSFUL; **249 tests, 0 failures, 0 errors, 0 skipped**. detekt baseline: 6 relocated, 2 **fixed**. ktlint baseline: 3 stale entries pruned. Two new traps found — see below. |
| UI-5 | `NowPlayingScreen.kt` → `player/` | 🟨 | ✅ | 969 L → 4 files, largest 601 L. `assembleDebug` + `lint` + `detekt` + `ktlintCheck` BUILD SUCCESSFUL; **249 tests, 0 failures, 0 errors, 0 skipped**. detekt 257 → 254 (2 relocated, 2 `MaxLineLength` fixed, 1 stale removed); ktlint 21 → 19. Trap #2 did not bite — this screen referenced no siblings in `ui.screens`. A **new** gotcha did: the file began with a blank line, so `package` sat on line 2 and a line-1 substitution silently did nothing. See below. |
| UI-6 | Group the remaining flat screens | 🟩 | ✅ | 13 files into 4 packages; `ui/screens/` has **no loose files left**. `assembleDebug` + `lint` + `detekt` + `ktlintCheck` BUILD SUCCESSFUL; **249 tests, 0 failures, 0 errors, 0 skipped**. detekt 254 → 243, ktlint 19 → 9, **zero baseline relocations needed**. Four pre-move checks earned their keep — see below. |
| UI-7 | Retire wildcard imports | 🟩 | ✅ | Done as part of UI-2 — once `MeScreen.kt` was 240 lines its 5 wildcard imports were trivially expandable, so the 5 `WildcardImport` baseline entries were **deleted, not relocated**. |

### What UI-2 removed from `detekt-baseline.xml`

Seven entries, all deleted rather than moved, each verified by removing it and re-running detekt:

| Entry | Why it is gone |
|---|---|
| `LongMethod:MeScreen.kt$@Composable fun MeScreen(…)` | 1 220 lines → 100. Under the 120 gate with no suppression. |
| `CyclomaticComplexMethod:MeScreen.kt$@Composable fun MeScreen(…)` | The branching moved into the sections that own it. |
| `WildcardImport:MeScreen.kt$…` × 5 | Explicit imports throughout the package. |

Three entries were **relocated** in UI-1 (`ComplexCondition`, `CyclomaticComplexMethod`, `LongMethod` on `ActivityBarChart` → `ActivityTrendsSection.kt`). `MeScreen.kt` now has **zero** baseline entries.

Getting `MeScreen` under the gate took one genuine extraction beyond the three carded blocks: at 127 lines it was 7 over, so the suggestion-pool derivation became `rememberSuggestions()` returning a `SuggestionState(pool, visible)`. The `remember(audioList)` key is unchanged, so the sample is still drawn once per library rather than re-shuffling on every keystroke.

### What UI-3 did to `detekt-baseline.xml`

Five entries deleted, three relocated, net -2:

| Entry | Outcome |
|---|---|
| `TooManyFunctions:VideoPlayerScreen.kt$…VideoPlayerScreen.kt` | **Deleted** — 14 top-level functions became 3–4 per file. |
| `LongMethod:…$TrackSelectionDialog(…)` | **Deleted** — it was 122 lines, 2 over. Extracting its 25-line icon-and-title header as `TrackSelectionHeader` brought it under the gate, so the debt is gone rather than moved. |
| `LongMethod:…$PlayerBottomControls(…)` | **Relocated** to `VideoPlayerControls.kt` — 183 lines, genuinely long. |
| `LongParameterList:…$( isPlaying: …)` (PlayerBottomControls, 16 params) | **Relocated** |
| `LongParameterList:…$( title: …)` (PlayerTopBar, 11 params) | **Relocated** |

`PlayerTopBar` and `PlayerBottomControls` were left alone deliberately. Fixing 11 and 16 parameters means a parameter-object redesign, which changes call sites rather than moving them — the non-goal stated in §1. Card it separately.

### How the three relocated IDs were obtained, and the check that made it safe

Hand-editing the filename inside an `<ID>` is not always enough: `TrackSelectionDialog` also gained an `internal` modifier in the move, and the modifier is **part of the baseline signature**. So the exact new IDs were captured by backing up the baseline, running `./gradlew :app:detektBaseline`, copying out only the three IDs needed, then **restoring the backup** and applying those three by hand.

The regenerated file was diffed against the backup before anything was accepted, and that diff paid for itself. It showed 3 additions (the expected relocations) and **18 removals**, only 5 of which were mine. The other 13 were stale entries for `AccessibilityGuideScreen.kt`, `AlbumDetailScreen.kt`, `AudioListScreen.kt`, `NowPlayingScreen.kt` and `AnalyticsViewModel.kt` — debt fixed by earlier work that nobody ever pruned. Verified stale directly (that file now has 0 wildcard imports and 0 lines over 120), so regenerating would have been *accurate* here. It was still rejected, because bundling an unrelated 13-entry cleanup into a refactor slice makes the slice harder to review. Those 13 are recorded as N-6 below.

**The rule stands: use `detektBaseline` as a lookup tool, never as the commit.** Diff it before you trust it.

### Two more traps, found in UI-4

**1. The ktlint baseline is keyed by PATH, not just filename — so a package move invalidates every entry for that file.** §2 said "file + line"; that was understated. `app/config/ktlint/baseline.xml` stores `name="src/main/java/.../ui/screens/VideoListScreen.kt"`, so moving the file into `video/` orphaned its two `max-line-length` suppressions and ktlint went red on lines nobody touched. This is distinct from the detekt trap, which keys on the bare filename and therefore survives a package move.

Both were fixed rather than re-pinned — they were two long ternaries that wrap cleanly — which cleared a detekt entry *and* a ktlint entry with one edit.

Worth knowing: `MeScreen.kt` had an `import-ordering` entry that UI-2 silently resolved (the explicit-import rewrite fixed it), and `VideoListScreen.kt` had the two above. Three stale ktlint entries pruned, 24 -> 21.

**2. Moving a file into a subpackage breaks unqualified references to siblings left behind in the parent package.** `VideoFolderScreen` called `PlaylistListScreen` with no import, because they shared `ui.screens`. After the move it needed `import com.local.offlinemediaplayer.ui.screens.PlaylistListScreen`. The compiler catches this immediately and it is a one-line fix, but expect one per screen that reaches across tabs — and expect more of them in UI-6, which moves the remaining 14 flat screens all at once.

### The pre-move checklist, distilled from UI-6

UI-6 moved 13 files at once and every trap from the earlier slices fired at least once. Run these
**four checks before moving anything**; together they take a minute and each one caught a real
breakage here:

1. **`grep -n '^package ' FILE` on every file.** Five of the 13 had `package` on line 2. Substitute
   by pattern, never by line number (trap #3).
2. **Enumerate every top-level declaration in the files you are moving, not just the composables.**
   The composable-only scan used in UI-4 missed `AudioSortOption` and `VideoSortOption`, which are
   `enum class`es declared inside `PlaylistDetailScreen.kt` / `VideoPlaylistDetailScreen.kt` and
   referenced unqualified from `DecadeScreens.kt`.
3. **Grep for wildcard imports of the package you are draining.** `AudioNavigationHost.kt` had
   `import com.local.offlinemediaplayer.ui.screens.*`, which silently covered six screens. Replacing
   it with six explicit imports also retired its `WildcardImport` baseline entry.
4. **Search `src/test` too.** `compileDebugKotlin` does not compile tests, so a broken test source
   passes the main compile and only fails later. `PermissionScreensTest.kt` sat in package
   `ui.screens` and called the three permission screens unqualified. Test layout should mirror
   production: it moved to `ui/screens/onboarding/` as well.

### What UI-6 confirmed about the two baselines

**detekt needed zero relocations.** Its IDs are keyed by *bare filename* plus signature, and this
slice changed neither — only the package. That is the precise difference from ktlint, whose IDs are
keyed by *path* and which therefore broke for all five moved files that had entries.

The ktlint fallout was fixed rather than re-pinned: 10 of the 12 orphaned violations were the same
`name = if (cond) A else B,` shape wrapped in every earlier slice, so they were wrapped here too and
their entries deleted. The remaining two are long user-facing string literals in
`PermissionScreens.kt`; those want `strings.xml` (N-3), not a line break, so only their path was
repointed.

**Totals across UI-1..UI-6: detekt 261 → 243, ktlint 24 → 9.** Every one of those 33 removals was
either a violation genuinely fixed or an entry verified stale — none were regenerated away. The
follow-up pass (N-10 then N-6) took detekt to **226**: 261 → 226 overall, a 13 % reduction in
suppressed debt achieved without once accepting a blind regeneration.

### A third trap, found in UI-5

**Do not assume `package` is on line 1.** `NowPlayingScreen.kt` opened with a blank line, so the
`sed '1s|^package ...|...|'` used in every earlier slice matched nothing and the moved file kept its
old package while sitting in the new directory. The failure is loud but misleading: the compiler
reports *"Unresolved reference"* for the file's own siblings and for the screen itself from its
importer, which reads like a missing import rather than a wrong package declaration.

Substitute on the `package` line by pattern, not by line number:

```
sed -i 's|^package com.local.offlinemediaplayer.ui.screens$|package ...ui.screens.player|' FILE
```

Check `head -1 FILE` after every move. This is the cheapest possible verification and it would have
caught this before the compile.

**Running tally of the stale-baseline group (N-6):** UI-5's diff showed it at ~15 detekt entries, not
the 13 first estimated, because `ImplicitDefaultLocale:NowPlayingScreen.kt$String.format(...)` is in
it too — `String.format` does not appear in that file at all. That one was removed here, since the
slice was editing that file's entries anyway.

### The shared-component question UI-4 existed to answer

`VideoListItem` and `VideoCardItem` are called from both `VideoListScreen` and `MoviesListContent`, so neither screen can own them. They now live in `VideoListItems.kt` as `internal`. That is the whole reason this slice was carded separately.

The slice also turned up a **name collision that is not duplication**: `ContinueWatchingCard` existed in both `ui/screens/me/` and `VideoFolderScreen.kt`, taking the same `ContinueWatchingItem`. Diffing them showed materially different designs — the `me` one is a 180x130 card with the title overlaid on the artwork, the video one a 16:9 tile with the title beneath. Gate 2 of the Abstraction Decision Procedure fails: they look alike but change for different reasons. **They were deliberately not merged.** The video one was renamed `ContinueWatchingTile` (file-private, one call site) so a grep for either name returns one thing.

### Deduplication found during UI-2

The "Current Obsession" and "All Time #1" rows in the listening-activity card were **two 91-line blocks whose diff was 13 lines, all of them data** — track, icon, accent colour, label, play count. They are now one `FavoriteTrackRow` with five parameters.

This was checked against the Abstraction Decision Procedure before merging, not assumed: Gate 1 two existing cases; Gate 2 they vary for the same reason (both are "a favourite track row" — a layout change should hit both); Gate 3 the volatile axis is the data, not the structure; Gate 4 182 lines become ~70 plus two 8-line call sites; Gate 5 a third case ("forgotten gem") fits with no boolean flag. **The diff was produced and read before the merge** — had the rows differed in behaviour anywhere, this would have been two composables.

---

## 8. Out-of-scope follow-ups

| # | Item | Severity |
|---|---|---|
| N-6 | ~~**~15 stale entries in `detekt-baseline.xml`** for `AccessibilityGuideScreen.kt` (6), `AlbumDetailScreen.kt` (2), `AudioListScreen.kt` (2), `NowPlayingScreen.kt` (1) and `AnalyticsViewModel.kt` (1) — `WildcardImport`, `MaxLineLength`, `ImplicitDefaultLocale`, `MagicNumber`. The violations were fixed by earlier work and the entries were never pruned. Verified stale during UI-3 (`AccessibilityGuideScreen.kt` now has 0 wildcard imports and 0 lines over 120). Harmless, but they make the baseline read as worse debt than there is. Prune deliberately, in a commit of their own.~~ ✅ **Closed** — 17 pruned, see §9. | 🟢 Minor |
| N-10 | ~~Sort enums declared in UI, imported by `data/`.~~ ✅ **Closed** — see §9. | 🟡 Major |
| N-12 | `viewmodel/ThemeViewModel.kt` imports `ui.theme.AppThemeConfig`. The last UI import from a non-UI package. Defensible (a ViewModel holding a theme choice) but worth a deliberate decision. | 🟢 Minor |
| N-11 | `AudioPlaylistItemCard` lives inside `playlist/PlaylistDetailScreen.kt` but is used by `DecadeScreens.kt` and `SmartPlaylistDetailScreen.kt` in `audio/`. It is a shared leaf importing from another screen's file; it wants its own file, the way `VideoListItems.kt` was handled in UI-4. | 🟢 Minor |
| N-7 | `PlayerTopBar` (11 params) and `PlayerBottomControls` (16 params) are the only two `LongParameterList` suppressions left in the video package. Fixing them means grouping the transport callbacks into a parameter object — a signature redesign, out of scope for a decomposition slice. | 🟢 Minor |
| N-8 | `VideoPlayerScreen.kt` (564 L), `VideoListScreen.kt` (698 L) and `VideoFolderScreen.kt` (331 L) still carry wildcard imports and their baseline entries. Each is a UI-7-style cleanup once its screen function is decomposed further. | 🟢 Minor |
| N-9 | `VideoListItem` (211 L) and `VideoCardItem` (259 L) are the two longest composables left in the package, both with 10+ parameters. They are item renderers with a lot of conditional chrome (selection mode, overflow menu, progress); splitting them is a real task, not a move. | 🟡 Major |

---

## 9. Follow-ups closed after the plan

### N-10 — the layering inversion (Major) ✅

`AudioSortOption` and `VideoSortOption` were `enum class`es declared inside Compose screen files and
imported by `data/SortPreferencesManager.kt` and `viewmodel/PlaylistViewModel.kt`. A persistence
class had to import from `ui` to name the ordinal it was storing — the dependency pointed the wrong
way, and UI-6 had made it *more* visible by re-pointing those imports at `ui.screens.playlist.*`.

Both enums moved to `viewmodel/Sorting.kt`, which already hosts `SortableField`, `SortState`,
`SortField`, `AlbumSortField` and `applySort`. They deliberately do **not** implement
`SortableField`: they carry no default direction, and giving them one would be a behaviour change
rather than a move.

**Acceptance, checked directly:** no file under `data/`, `repository/`, `domain/`, `playback/`,
`service/` or `model/` imports from `ui` any more. One import of that shape survives —
`viewmodel/ThemeViewModel.kt` → `ui.theme.AppThemeConfig` — and is left alone: a ViewModel holding a
theme selection is a defensible UI-adjacent dependency, unlike a DataStore manager reaching into a
screen file. Recorded as N-12 rather than fixed by reflex.

Two `MatchingDeclarationName` suppressions disappeared as a side effect: an `enum class
AudioSortOption` living in `PlaylistDetailScreen.kt` had tripped that rule, and moving it to a file
whose name matches its contents resolved it.

### N-6 — the stale baseline entries (Minor) ✅

Pruned deliberately, in their own pass, exactly as promised after being declined four times inside
refactor slices. 17 entries removed, detekt 243 → 226, **zero added**.

The regenerated baseline was accepted here — the only time in this whole effort — because the diff
showed no additions at all, and because each claim was then verified against the source by hand
rather than taken on trust:

| Claimed stale | Checked | Result |
|---|---|---|
| `MagicNumber:AnalyticsViewModel.kt$86400000L` | `grep -c '86400000L'` | 0 |
| `ImplicitDefaultLocale:AudioListScreen.kt$String.format(...)` | `grep -c 'String.format'` | 0 |
| `ImplicitDefaultLocale:AlbumDetailScreen.kt$String.format(...)` | `grep -c 'String.format'` | 0 |
| `MaxLineLength:AlbumDetailScreen.kt$?:` | `awk length>120` | 0 |
| `MaxLineLength:AccessibilityGuideScreen.kt` x6 | `awk length>120` | 0 |
| `WildcardImport:AccessibilityGuideScreen.kt` x4 | `grep -c 'import .*\*$'` | 0 |
| `MatchingDeclarationName:PlaylistDetailScreen.kt$AudioSortOption` | `grep -c 'enum class'` | 0 |

That is the standard for accepting a regeneration: **no additions, and every removal independently
confirmed.** Anything less, hand-edit instead.

### Still open

N-7 (parameter objects for `PlayerTopBar` / `PlayerBottomControls`), N-8 (wildcard imports in three
screen files), N-9 (`VideoListItem` / `VideoCardItem` are 211 and 259 lines), N-11
(`AudioPlaylistItemCard` wants its own file), N-12 (`ThemeViewModel` → `ui.theme.AppThemeConfig`).
N-9 is the only Major one left and is a real task, not a move.
