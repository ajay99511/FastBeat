# Plan: Decompose the large UI files

**Tier:** Standard per slice, Consequential in aggregate — no behaviour change is intended anywhere, but the work touches every screen the user sees.
**Author / date:** 2026-09-08
**Status:** In progress — `UI-1` complete
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
  me/                       ← UI-1 ✅
    MeScreen.kt             the entry point: state, and the vertical order of sections
    ThemeSwitcherRow.kt     ThemeSwitcherRow + ThemeButton
    ListeningActivitySection.kt
    ContinueWatchingSection.kt
    SuggestedTracksSection.kt
    LibrarySummarySection.kt   LibraryStatsSection + StatCard
    ActivityTrendsSection.kt   ActivityTrendsSection + ActivityBarChart
    MeSettingsCards.kt         DarkModeToggleCard + AccessibilityGuideCard
    ShuffleAllButton.kt
  video/                    ← UI-3, UI-4
  player/                   ← UI-5
  audio/  playlist/  image/  onboarding/    ← UI-6, once the large files are done
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
| UI-2 | Extract `MeScreen`'s three remaining inline blocks — continue-watching (144 L), listening-activity (411 L), suggested-tracks (417 L) | 1 145 L | 🟨 | ⬜ |
| UI-3 | `VideoPlayerScreen.kt` → `ui/screens/video/`; overlays, controls and dialogs are already separate functions | 1 580 L | 🟨 | ⬜ |
| UI-4 | `VideoListScreen.kt` + `VideoFolderScreen.kt` → `ui/screens/video/`; `VideoListItem` / `VideoCardItem` are shared by both and belong in one file | 1 215 / 821 L | 🟨 | ⬜ |
| UI-5 | `NowPlayingScreen.kt` → `ui/screens/player/` | 969 L | 🟨 | ⬜ |
| UI-6 | Group the remaining flat screens into `audio/`, `playlist/`, `image/`, `onboarding/` | — | 🟩 | ⬜ |
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
| UI-2 | Extract `MeScreen`'s three inline blocks | 🟨 | ⬜ | Dependency surface measured — see §4 |
| UI-3 | `VideoPlayerScreen.kt` → `video/` | 🟨 | ⬜ | |
| UI-4 | `VideoListScreen` + `VideoFolderScreen` → `video/` | 🟨 | ⬜ | |
| UI-5 | `NowPlayingScreen.kt` → `player/` | 🟨 | ⬜ | |
| UI-6 | Group the remaining flat screens | 🟩 | ⬜ | |
| UI-7 | Retire `MeScreen.kt`'s wildcard imports | 🟩 | ⬜ | |
