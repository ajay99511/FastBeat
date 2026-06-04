# Implementation Plan: Offline Media Player UI Responsiveness

## Overview

Add full responsive/adaptive layout support to FastBeat for Medium (600–839 dp) and Expanded
(≥ 840 dp) window size classes while preserving all existing Compact (< 600 dp) behavior
exactly as-is. The implementation is purely additive: new files live under `ui/adaptive/`,
existing compact composables are untouched, and `WindowSizeClass`-driven branching is introduced
only at the entry point (`MediaPlayerAppContent` / `MainActivity`).

---

## Tasks

- [ ] 1. Add dependencies and configure build
  - [ ] 1.1 Add adaptive and window dependencies to `app/build.gradle.kts`
    - Add `androidx.window:window:1.3.0`
    - Add `androidx.compose.material3.adaptive:adaptive:1.0.0`
    - Add `androidx.compose.material3.adaptive:adaptive-layout:1.0.0`
    - Add `androidx.compose.material3.adaptive:adaptive-navigation:1.0.0`
    - Add `testImplementation("io.kotest:kotest-runner-junit5:5.9.1")`
    - Add `testImplementation("io.kotest:kotest-property:5.9.1")`
    - Add `tasks.withType<Test> { useJUnitPlatform() }` block
    - Update Compose BOM to at least `2024.09.00` if currently older
    - _Requirements: 1.1_

- [ ] 2. Create `ui/adaptive/` foundation — data models and pure helpers
  - [ ] 2.1 Create `ui/adaptive/WindowSizeExt.kt`
    - Define `enum class AppWidthClass { Compact, Medium, Expanded }`
    - Implement `fun WindowWidthSizeClass.toAppWidthClass(): AppWidthClass`
      (`< 600 dp → Compact`, `600–839 dp → Medium`, `≥ 840 dp → Expanded`)
    - Implement `fun appWidthClassFromDp(widthDp: Float): AppWidthClass` (pure, testable)
    - Define `object AdaptiveLayoutConstants { val MAX_CONTENT_WIDTH = 840.dp; const val MEDIUM_WIDTH_DP = 600; const val EXPANDED_WIDTH_DP = 840 }`
    - _Requirements: 1.3_

  - [ ]* 2.2 Write property test for width classification (`AppWidthClassTest.kt`)
    - **Property 1: Width Classification Covers All Values**
    - Test `appWidthClassFromDp` for 200 random non-negative floats using `checkAll<Float>(PropTestConfig(iterations = 200))`
    - Assert `< 600f → Compact`, `600f–839f → Medium`, `≥ 840f → Expanded`
    - **Validates: Requirements 1.3**

  - [ ] 2.3 Create `ui/adaptive/DevicePosture.kt`
    - Define `sealed class DevicePosture { object Normal; data class TableTop(val hingePosition: Rect) }`
    - Implement `fun FoldingFeature.toDevicePosture(): DevicePosture`
      (`HALF_OPENED + HORIZONTAL → TableTop(bounds)`, all other → `Normal`)
    - _Requirements: 10.1, 10.5_

  - [ ]* 2.4 Write property test for device posture classification (`DevicePostureTest.kt`)
    - **Property 9: Device Posture Classification is Exhaustive**
    - Use `forAll` table with all four `(State × Orientation)` combinations
    - Assert only `(HALF_OPENED, HORIZONTAL)` → `TableTop`; all others → `Normal`
    - **Property 10: Table-Top Posture Round-Trip**
    - For all `AppWidthClass` values, verify `nowPlayingLayoutType(wc, Normal)` after `TableTop` equals direct `Normal` call
    - **Validates: Requirements 10.1, 10.4, 10.5**

  - [ ] 2.5 Create `ui/adaptive/LocalProviders.kt`
    - Define `val LocalWindowSizeClass = staticCompositionLocalOf { AppWidthClass.Compact }`
    - Define `val LocalDevicePosture = staticCompositionLocalOf<DevicePosture> { DevicePosture.Normal }`
    - _Requirements: 1.1_

- [ ] 3. Checkpoint — compile and run unit tests
  - Ensure all new files compile, existing tests still pass, and `AppWidthClassTest` + `DevicePostureTest` pass.
  - Ask the user if questions arise.

- [ ] 4. Implement adaptive grid helpers
  - [ ] 4.1 Create `ui/adaptive/AdaptiveGrid.kt`
    - Implement `fun adaptiveGridColumns(widthClass: AppWidthClass): Int`
      (`Compact → 2`, `Medium → 3`, `Expanded → 4`)
    - Implement `fun adaptiveImageCellSize(widthClass: AppWidthClass): Dp`
      (`Compact → 100.dp`, `Medium → 130.dp`, `Expanded → 160.dp`)
    - _Requirements: 3.1, 3.2, 3.3, 4.1, 4.2, 4.3_

  - [ ]* 4.2 Write property tests for grid helpers (`AdaptiveGridTest.kt`)
    - **Property 4: Adaptive Grid Column Count is Monotone and Correct**
    - Use `checkAll(Arb.enum<AppWidthClass>())` to assert exact column counts and monotonicity
    - **Property 5: Adaptive Image Cell Size is Monotone and Correct**
    - Use `checkAll(Arb.enum<AppWidthClass>())` to assert exact cell sizes and monotonicity
    - **Validates: Requirements 3.1, 3.2, 3.3, 4.1, 4.2, 4.3**

- [ ] 5. Implement adaptive navigation composables
  - [ ] 5.1 Create `ui/adaptive/AdaptiveNavigation.kt`
    - Define `data class AppNavigationDestination(tabIndex, label, selectedIcon, unselectedIcon, contentDescription)`
    - Define `val APP_DESTINATIONS: List<AppNavigationDestination>` (Videos, Music, Images, Stats)
    - Implement `fun navigationComponentFor(widthClass: AppWidthClass, isFullscreen: Boolean): NavigationComponentType`
      (`Compact+false → BottomBar`, `Medium+false → Rail`, `Expanded+false → Drawer`, `*+true → Hidden`)
    - Implement `@Composable fun FastBeatNavigationRail(selectedTab, onTabSelected, themeColor)`
      using `NavigationRail` and `APP_DESTINATIONS`
    - Implement `@Composable fun FastBeatNavigationDrawer(selectedTab, onTabSelected, themeColor)`
      using permanent `ModalNavigationDrawer` + `NavigationDrawerItem` and `APP_DESTINATIONS`
    - Tab-selection callbacks must mirror existing `NavigationBar` behavior (set `selectedTab`, reset search)
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

  - [ ]* 5.2 Write property tests for navigation selection (`AdaptiveNavigationTest.kt`)
    - **Property 2: Navigation Component Selection is Total and Consistent**
    - Use `checkAll<Boolean>` × `AppWidthClass.values()` to verify `navigationComponentFor` covers all cases
    - **Property 3: Tab Selection Equivalence Across Navigation Components**
    - Use `checkAll(Arb.int(0, 3))` to assert `simulateNavBarSelect(i) == simulateNavRailSelect(i) == simulateNavDrawerSelect(i)`
    - **Property 13: Header Visibility is Determined by Navigation Context**
    - Assert `showFastBeatHeader` returns `false` for Medium/Expanded with adaptive nav, `true` for Compact
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.6, 2.7, 9.2**

- [ ] 6. Implement `AdaptiveMainLayout.kt` and `AdaptiveMeScreen.kt`
  - [ ] 6.1 Create `ui/adaptive/AdaptiveMainLayout.kt`
    - Implement `@Composable fun AdaptiveMainLayout(widthClass, selectedTab, onTabSelected, isVideoPlayingFullscreen, content)`
    - Compose a `Row`: `FastBeatNavigationRail` (Medium) or `FastBeatNavigationDrawer` (Expanded) on start edge
    - Hide navigation component (zero width) when `isVideoPlayingFullscreen == true`
    - Apply `Modifier.widthIn(max = MAX_CONTENT_WIDTH).wrapContentWidth(Alignment.CenterHorizontally)` to `content` when `widthClass == Expanded`
    - _Requirements: 2.2, 2.3, 2.7, 7.2, 8.1, 8.2, 9.2_

  - [ ] 6.2 Create `ui/adaptive/AdaptiveMeScreen.kt`
    - Wrap existing `MeScreen` to apply `widthIn(max = MAX_CONTENT_WIDTH)` + `Alignment.CenterHorizontally` when `Expanded`
    - When `DevicePosture.TableTop`: split content at hinge — analytics summary cards in upper half, scrollable details in lower half
    - When not `TableTop`: delegate to standard `MeScreen` with width modifier
    - _Requirements: 8.1, 10.3_

  - [ ]* 6.3 Write property test for max content width modifier (`ContentWidthModifierTest.kt`)
    - **Property 8: Max Content Width Applied Only on Expanded**
    - Use `checkAll(Arb.enum<AppWidthClass>())` to assert `contentWidthConstraintApplied(wc)` is `true` only for `Expanded`
    - **Validates: Requirements 7.3, 8.1, 8.2, 8.3**

- [ ] 7. Checkpoint — compile and run unit tests
  - Ensure all unit tests (Properties 1–5, 8, 9, 10, 13) pass before continuing to UI composables.
  - Ask the user if questions arise.

- [ ] 8. Implement `AdaptiveNowPlayingScreen.kt`
  - [ ] 8.1 Create `ui/adaptive/AdaptiveNowPlayingScreen.kt`
    - Add pure selector `fun nowPlayingLayoutType(widthClass: AppWidthClass, posture: DevicePosture): NowPlayingLayoutType`
      returning `Vertical` (Compact+Normal), `TwoColumn` (Medium/Expanded+Normal), `TableTopSplit` (any+TableTop)
    - Add pure helper `fun computeAlbumArtMaxSize(availableHeightDp: Float): Float`
      returning `min(availableHeightDp * 0.85f, 400f)`
    - Implement `@Composable fun TwoColumnNowPlayingLayout(viewModel, onBack)`:
      `Row` with album art (leading, `min(availableHeight * 0.85f, 400.dp)`) and controls (trailing, vertically centered)
    - Implement `@Composable fun TableTopNowPlayingLayout(viewModel, posture, onBack)`:
      split at hinge — album art above, playback controls below
    - Implement `@Composable fun AdaptiveNowPlayingScreen(viewModel, onBack)`:
      read `LocalWindowSizeClass` + `LocalDevicePosture`, delegate to the appropriate layout type
    - Add overflow fallback: if controls clip, set `fallbackToVertical = true` and render original `NowPlayingScreen`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 10.2_

  - [ ]* 8.2 Write property tests for NowPlaying layout (`NowPlayingLayoutTest.kt`)
    - **Property 6: NowPlaying Layout Type Matches Width Class**
    - Use `checkAll(Arb.enum<AppWidthClass>())` — assert `Normal` posture returns `Vertical`/`TwoColumn`, `TableTop` always returns `TableTopSplit`
    - **Property 7: Album Art Size Constraint**
    - Use `checkAll(Arb.float(1f, 2000f))` to assert `computeAlbumArtMaxSize(h) <= 400f` and `<= h * 0.85f`
    - **Validates: Requirements 5.1, 5.2, 5.3, 10.2**

- [ ] 9. Implement `TwoPaneVideoHost.kt`
  - [ ] 9.1 Create `ui/adaptive/TwoPaneVideoHost.kt`
    - Add pure selector `fun videoNavigationLayout(widthClass: AppWidthClass, route: String): VideoNavigationLayout`
      (`Expanded + "video_folders" → TwoPane`, all other → SinglePane`)
    - Define `data class TwoPaneVideoState(val selectedFolderId: String? = null)`
    - Add extension `fun TwoPaneVideoState.selectFolder(folderId: String): TwoPaneVideoState`
    - Implement `@Composable fun TwoPaneVideoNavigationHost(viewModel, libraryViewModel, onVideoClick, isSearchVisible)`
      using `ListDetailPaneScaffold`
    - List pane: renders existing `VideoFolderScreen`; folder clicks update `selectedFolderId` state (no `navController` push in Expanded mode)
    - Detail pane: renders `VideoListScreen` for `selectedFolderId` or `EmptyDetailPane` ("Select a folder") when `null`
    - `onVideoClick` delegates to existing `viewModel.playVideoFromList` mechanism
    - _Requirements: 12.1, 12.2, 12.3, 12.4_

  - [ ]* 9.2 Write property tests for video navigation layout (`VideoNavigationLayoutTest.kt`)
    - **Property 11: Video Navigation Layout Selection is Total**
    - Use `checkAll(Arb.enum<AppWidthClass>(), Arb.string())` to assert `TwoPane` only for `(Expanded, "video_folders")`, `SinglePane` for all others
    - **Property 12: Two-Pane Folder Selection Drives Detail Content**
    - Use `checkAll(Arb.string(1, 20))` to assert `TwoPaneVideoState().selectFolder(id).selectedFolderId == id`
    - **Validates: Requirements 12.1, 12.2, 12.3**

- [ ] 10. Checkpoint — compile and run all unit PBT tests
  - All 13 properties (Properties 1–13) must pass. Ask the user if questions arise.

- [ ] 11. Wire `WindowSizeClass` and `DevicePosture` into `MainActivity` and `MediaPlayerAppContent`
  - [ ] 11.1 Modify `MainActivity.kt`
    - Call `calculateWindowSizeClass(this)` and map to `AppWidthClass` via `toAppWidthClass()`
    - Collect `WindowInfoTracker.getOrCreate(this).windowLayoutInfo` as a `StateFlow`, map to `DevicePosture`
    - Wrap collection in `.catch { emit(WindowLayoutInfo(emptyList())) }` for fallback
    - Catch any exception from `calculateWindowSizeClass` and default to `AppWidthClass.Compact`
    - Pass `widthClass: AppWidthClass` and `devicePosture: DevicePosture` as parameters to `MainScreen` / `MediaPlayerAppContent`
    - _Requirements: 1.1, 1.2, 10.5_

  - [ ] 11.2 Modify `MediaPlayerAppContent` in `MainScreen.kt`
    - Add `widthClass: AppWidthClass` and `devicePosture: DevicePosture` parameters (default `Compact`/`Normal` for backward compat)
    - Wrap content in `CompositionLocalProvider(LocalWindowSizeClass provides widthClass, LocalDevicePosture provides devicePosture)`
    - Add `when (widthClass)` branch:
      - `Compact` → existing `Scaffold` + `NavigationBar` unchanged
      - `Medium`, `Expanded` → `AdaptiveMainLayout` wrapper (no `Scaffold` top/bottom bar wrapping)
    - Replace `FastBeatHeader` visibility check: hide header on Medium/Expanded when adaptive nav is active (Requirement 9.2)
    - Replace `VideoNavigationHost` call with `when (widthClass) { Expanded → TwoPaneVideoNavigationHost; else → VideoNavigationHost }`
    - Replace `NowPlayingScreen` routing inside `AudioNavigationHost` with `AdaptiveNowPlayingScreen`
    - Pass `widthClass` to `ImageListScreen` to enable adaptive cell size (Requirement 4)
    - Pass `widthClass` + `devicePosture` to `MeScreen` / `AdaptiveMeScreen` (Requirement 8.1, 10.3)
    - Compact path must remain byte-for-byte equivalent to pre-change behavior
    - _Requirements: 1.1, 1.2, 1.4, 2.1, 2.2, 2.3, 9.2, 13.1, 13.2_

- [ ] 12. Apply adaptive grid columns to grid screens
  - [ ] 12.1 Modify `VideoFolderScreen.kt` and `VideoListScreen.kt`
    - Read `LocalWindowSizeClass.current` and pass to `adaptiveGridColumns()` for `GridCells.Fixed(n)`
    - Ensure `rememberLazyGridState()` is used (keyed by `widthClass`) to preserve scroll position
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ] 12.2 Modify `AlbumListScreen.kt`
    - Read `LocalWindowSizeClass.current` and apply `adaptiveGridColumns()` to album grid
    - Ensure `rememberLazyGridState()` is used
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ] 12.3 Modify `ImageListScreen.kt`
    - Read `LocalWindowSizeClass.current` and apply `adaptiveImageCellSize()` for `GridCells.Adaptive(minSize)`
    - Compact stays `100.dp`, Medium `130.dp`, Expanded `160.dp`
    - _Requirements: 4.1, 4.2, 4.3_

- [ ] 13. Apply MiniPlayer wide-screen adaptation
  - [ ] 13.1 Modify `AudioLibraryScreen.kt` MiniPlayer host
    - Read `LocalWindowSizeClass.current`; when `Expanded`, wrap `MiniPlayer` with `Modifier.widthIn(max = MAX_CONTENT_WIDTH).align(Alignment.BottomCenter)`
    - On Medium/Compact, pass existing modifier unchanged
    - _Requirements: 7.1, 7.2, 7.3_

- [ ] 14. Checkpoint — full compile and all unit tests green
  - Run `./gradlew testDebugUnitTest` to confirm all PBT and unit tests pass.
  - Ask the user if questions arise.

- [ ] 15. Landscape stability and regression fixes
  - [ ] 15.1 Verify `AudioLibraryScreen` landscape layout (`Medium_Width`)
    - Confirm tab row (TRACKS / ALBUMS / PLAYLISTS) and pager content do not overflow or clip in landscape Medium
    - Apply `wrapContentWidth` or `horizontalScroll` only if overflow is detected — do not alter compact path
    - _Requirements: 11.2_

  - [ ] 15.2 Verify configuration-change state retention
    - Confirm `selectedTab` and nav back-stack state survive rotation via `rememberSaveable`
    - Confirm `videoNavController` and `audioNavController` back stacks are retained through fold/unfold
    - _Requirements: 11.5_

- [ ] 16. Final checkpoint — ensure all tests pass
  - Run `./gradlew testDebugUnitTest` and `./gradlew lintDebug`.
  - Confirm compact-width behavior is byte-for-byte unchanged (no `WindowSizeClass` checks inside existing compact-only composables).
  - Ask the user if questions arise.

---

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP; all 13 correctness properties are covered by the `*` sub-tasks.
- The Compact path in `MediaPlayerAppContent` must remain exactly as-is — do not add any `WindowSizeClass` checks inside existing compact-only composables (Requirement 13.2).
- `AdaptiveNowPlayingScreen` wraps the original `NowPlayingScreen`; the original file is never modified.
- `TwoPaneVideoNavigationHost` uses `ListDetailPaneScaffold` from `material3.adaptive` — do not hand-roll a `Row`-based two-pane layout.
- All PBT tests use `kotest-property` on the JVM; no emulator or device is needed for Properties 1–13.
- Property test files live in `src/test/java/com/local/offlinemediaplayer/ui/adaptive/`.

---

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["2.1", "2.3", "2.5"] },
    { "id": 2, "tasks": ["2.2", "2.4", "4.1"] },
    { "id": 3, "tasks": ["4.2", "5.1"] },
    { "id": 4, "tasks": ["5.2", "6.1", "6.2"] },
    { "id": 5, "tasks": ["6.3", "8.1", "9.1"] },
    { "id": 6, "tasks": ["8.2", "9.2", "11.1"] },
    { "id": 7, "tasks": ["11.2"] },
    { "id": 8, "tasks": ["12.1", "12.2", "12.3", "13.1"] },
    { "id": 9, "tasks": ["15.1", "15.2"] }
  ]
}
```
