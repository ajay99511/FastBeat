# Requirements Document

## Introduction

FastBeat (OfflineMediaPlayer) is a Jetpack Compose-based Android media player with four main tabs: Videos, Music, Images, and Stats. The existing mobile (compact) UI is well-designed and must remain exactly as-is. This feature adds full responsive/adaptive layout support for tablet, foldable, landscape, and large-screen (desktop/laptop) form factors, following Android Material Design 3 and Jetpack WindowManager best practices.

The app uses a `NavigationBar` (bottom tabs) on mobile, fixed 2-column grids on video/audio/album screens, a vertically-stacked `NowPlayingScreen`, and a full-width `MiniPlayer`. None of these patterns are optimal on wide screens. The goal is to adopt `WindowSizeClass`-driven layouts that enhance the experience on larger screens without touching any existing compact-width code paths.

**Scope boundaries:**
- Compact width (< 600 dp): No changes whatsoever — existing behavior preserved exactly.
- Medium width (600–840 dp): Tablet portrait, foldable unfolded, landscape phone.
- Expanded width (≥ 840 dp): Tablet landscape, desktop/laptop, large foldable unfolded.

---

## Glossary

- **App / FastBeat**: The OfflineMediaPlayer Android application.
- **WindowSizeClass**: The Jetpack Compose `WindowSizeClass` API that classifies the available display area into `Compact`, `Medium`, or `Expanded` width/height buckets.
- **Compact_Width**: A window width class `< 600 dp`, corresponding to typical portrait phones.
- **Medium_Width**: A window width class `600–839 dp`, corresponding to tablet portrait, foldable unfolded, or phone landscape.
- **Expanded_Width**: A window width class `≥ 840 dp`, corresponding to tablet landscape, desktop/laptop, or large foldable unfolded.
- **NavigationBar**: The Material Design 3 bottom navigation bar, currently used for tab navigation in FastBeat.
- **NavigationRail**: A Material Design 3 side navigation component suitable for medium/expanded screens.
- **NavigationDrawer**: A Material Design 3 permanent modal navigation drawer suitable for expanded screens.
- **MiniPlayer**: The persistent audio playback bar currently displayed at the bottom of the AudioLibraryScreen.
- **NowPlayingScreen**: The full-screen audio player UI showing album art, track info, and playback controls.
- **VideoPlayerScreen**: The full-screen video player UI with gesture controls and overlay controls.
- **FastBeatHeader**: The custom top app bar showing "FastBeat" branding, section title, and the search button.
- **MainScreen**: The root Composable that hosts the Scaffold, navigation component, and tab content.
- **Content_Pane**: The main scrollable/interactive area of each tab screen, excluding the navigation component.
- **AdaptiveGrid**: A `LazyVerticalGrid` whose column count scales with window width.
- **Fold_Hinge**: The physical fold line on a foldable device, exposed via `FoldingFeature` from Jetpack WindowManager.
- **Table_Top_Posture**: A foldable device posture where the hinge is horizontal and the device rests on a surface, splitting the screen into an upper display area and a lower control area.
- **Max_Content_Width**: A maximum width constraint (840 dp) applied to content on expanded screens to prevent excessive line length and maintain readability.

---

## Requirements

### Requirement 1: WindowSizeClass Integration

**User Story:** As a developer, I want the app to detect the current window size class at runtime, so that all responsive layout decisions can be driven by a single, consistent source of truth.

#### Acceptance Criteria

1. THE App SHALL integrate `WindowSizeClass` from `androidx.compose.material3.adaptive` (or `androidx.window.core`) and provide the current `WindowSizeClass` via a `CompositionLocal` or direct parameter threading from `MainActivity` down to `MainScreen`.
2. WHEN the device is rotated or the window is resized (e.g., multi-window mode), THE App SHALL recompose all affected layout components reflecting the new `WindowSizeClass` within one frame cycle.
3. THE App SHALL classify window width into exactly three categories: `Compact_Width` (< 600 dp), `Medium_Width` (600–839 dp), and `Expanded_Width` (≥ 840 dp).
4. WHEN the `WindowSizeClass` is `Compact_Width`, THE App SHALL use all existing layout implementations without any modification. WHEN the `WindowSizeClass` is `Medium_Width` or `Expanded_Width`, THE App MAY use modified responsive layouts in place of the compact-width defaults.

---

### Requirement 2: Adaptive Navigation Component

**User Story:** As a user on a tablet or large screen, I want the navigation to appear on the side of the screen, so that the layout uses screen real estate efficiently and follows platform conventions.

#### Acceptance Criteria

1. WHEN the `WindowSizeClass` width is `Compact_Width`, THE MainScreen SHALL display the existing `NavigationBar` at the bottom of the screen, exactly as currently implemented.
2. WHEN the `WindowSizeClass` width is `Medium_Width`, THE MainScreen SHALL replace the bottom `NavigationBar` with a `NavigationRail` displayed on the leading (start) edge of the screen.
3. WHEN the `WindowSizeClass` width is `Expanded_Width`, THE MainScreen SHALL replace the bottom `NavigationBar` with a permanent `NavigationDrawer` displayed on the leading edge of the screen.
4. THE NavigationRail SHALL display the same four destination icons and labels (Videos, Music, Images, Stats) as the existing `NavigationBar`.
5. THE NavigationDrawer SHALL display the same four destination icons and labels as the existing `NavigationBar`, using `NavigationDrawerItem` components.
6. WHEN the navigation destination changes via the `NavigationRail` or `NavigationDrawer`, THE App SHALL update the active tab state with the same behavior as the existing `NavigationBar` (including resetting search visibility).
7. WHEN `isVideoPlayingFullscreen` is `true`, THE MainScreen SHALL hide the `NavigationRail` and `NavigationDrawer` in addition to the existing `NavigationBar` hide behavior.

---

### Requirement 3: Adaptive Grid Column Count

**User Story:** As a user on a tablet or large screen, I want content grids to show more columns, so that I can see more items at once and the layout doesn't feel stretched.

#### Acceptance Criteria

1. WHEN the `WindowSizeClass` width is `Compact_Width`, THE AdaptiveGrid SHALL use 2 fixed columns (preserving the existing behavior of `GridCells.Fixed(2)`).
2. WHEN the `WindowSizeClass` width is `Medium_Width`, THE AdaptiveGrid SHALL use 3 fixed columns.
3. WHEN the `WindowSizeClass` width is `Expanded_Width`, THE AdaptiveGrid SHALL use 4 fixed columns.
4. THE AdaptiveGrid requirement SHALL apply to all grid screens: `VideoFolderScreen` (folder grid), `VideoListScreen` (video grid), `AlbumListScreen` (album grid), and the Movies grid in `VideoFolderScreen`.
5. WHEN the column count changes due to a window size change, THE AdaptiveGrid SHALL recompose with the new column count without losing scroll position (using `rememberLazyGridState`).

---

### Requirement 4: Image Gallery Adaptive Grid

**User Story:** As a user browsing images on a tablet or large screen, I want the image grid to use appropriately sized cells, so that images are displayed at a visually balanced size for the screen.

#### Acceptance Criteria

1. WHEN the `WindowSizeClass` width is `Compact_Width`, THE ImageListScreen SHALL use `GridCells.Adaptive(minSize = 100.dp)` (preserving existing behavior).
2. WHEN the `WindowSizeClass` width is `Medium_Width`, THE ImageListScreen SHALL use `GridCells.Adaptive(minSize = 130.dp)`.
3. WHEN the `WindowSizeClass` width is `Expanded_Width`, THE ImageListScreen SHALL use `GridCells.Adaptive(minSize = 160.dp)`.

---

### Requirement 5: NowPlayingScreen Landscape/Tablet Layout

**User Story:** As a user playing music on a tablet or in landscape mode, I want the Now Playing screen to use a side-by-side layout for album art and controls, so that the vertical space is not wasted and the interface is comfortable to use.

#### Acceptance Criteria

1. WHEN the `WindowSizeClass` width is `Compact_Width`, THE NowPlayingScreen SHALL display the existing vertically-stacked layout (album art on top, controls below) without any changes.
2. WHEN the `WindowSizeClass` width is `Medium_Width` or `Expanded_Width`, THE NowPlayingScreen SHALL display a two-column `Row` layout: album art occupying the leading column and playback controls (track info, progress bar, transport buttons) occupying the trailing column.
3. WHEN displaying the two-column layout, THE NowPlayingScreen SHALL render album art at a maximum size of `min(availableHeight * 0.85f, 400.dp)` to prevent it from overwhelming the screen.
4. WHEN displaying the two-column layout, THE NowPlayingScreen SHALL vertically center the controls column.
5. WHEN displaying the two-column layout, THE NowPlayingScreen SHALL maintain all existing functionality: playback controls, progress seeking, shuffle, repeat, favorites, queue, and the options menu. IF the two-column layout cannot accommodate all controls without clipping or overflow, THEN THE NowPlayingScreen SHALL fall back to the vertical single-column layout.

---

### Requirement 6: VideoPlayerScreen Full-Screen Invariant

**User Story:** As a user playing a video on any device, I want the video player to always fill the entire screen, so that I get an immersive full-screen viewing experience.

#### Acceptance Criteria

1. THE VideoPlayerScreen SHALL always apply `Modifier.fillMaxSize()` as the root size modifier, regardless of `WindowSizeClass` or device form factor.
2. THE VideoPlayerScreen SHALL continue to manage orientation changes, PiP mode, and gesture controls exactly as currently implemented on all screen sizes. At a minimum, swipe-to-seek and tap-to-toggle-controls gestures SHALL remain functional on all screen sizes.
3. WHEN the `VideoPlayerScreen` is active, THE App SHALL hide all navigation components (`NavigationBar`, `NavigationRail`, `NavigationDrawer`) and system UI, as currently implemented.

---

### Requirement 7: MiniPlayer Wide-Screen Adaptation

**User Story:** As a user on a tablet or large screen listening to music, I want the MiniPlayer to be appropriately sized and positioned, so that it doesn't feel stretched across the full width of a wide screen.

#### Acceptance Criteria

1. WHEN the `WindowSizeClass` width is `Compact_Width`, THE MiniPlayer SHALL retain its current full-width, bottom-anchored behavior without any changes.
2. WHEN the `WindowSizeClass` width is `Medium_Width` or `Expanded_Width` and the `NavigationRail` or `NavigationDrawer` is visible, THE MiniPlayer SHALL be positioned within the `Content_Pane` (not overlapping the navigation component).
3. WHEN the `WindowSizeClass` width is `Expanded_Width`, THE MiniPlayer SHALL constrain its width to a maximum of `Max_Content_Width` (840 dp) and be horizontally centered within the `Content_Pane`.

---

### Requirement 8: Content Max-Width Constraint

**User Story:** As a user on a desktop or large tablet, I want content to be constrained to a readable width, so that text lines are not excessively long and the layout doesn't feel stretched.

#### Acceptance Criteria

1. WHEN the `WindowSizeClass` width is `Expanded_Width`, THE Content_Pane of `MeScreen` (Stats tab) SHALL apply a `widthIn(max = 840.dp)` modifier and be horizontally centered.
2. WHEN the `WindowSizeClass` width is `Expanded_Width`, THE Content_Pane of `NowPlayingScreen` (in the two-column layout) SHALL not exceed `Max_Content_Width` (840 dp) total.
3. WHEN the `WindowSizeClass` width is `Compact_Width` or `Medium_Width`, THE Content_Pane SHALL NOT apply any maximum width constraint (preserving full-width layout).

---

### Requirement 9: FastBeatHeader Wide-Screen Adaptation

**User Story:** As a user on a tablet or large screen, I want the app header to make better use of horizontal space, so that the header feels polished and not awkwardly sparse.

#### Acceptance Criteria

1. WHEN the `WindowSizeClass` width is `Compact_Width`, THE FastBeatHeader SHALL render exactly as currently implemented without any changes.
2. WHEN the `WindowSizeClass` width is `Medium_Width` or `Expanded_Width` and the `NavigationRail` or `NavigationDrawer` is active, THE FastBeatHeader SHALL NOT be rendered (the section context is already visible in the navigation component, removing duplication).
3. WHERE the `FastBeatHeader` is rendered on `Medium_Width` or `Expanded_Width` (e.g., for screens that retain their own inline header), THE FastBeatHeader SHALL apply `widthIn(max = Max_Content_Width)` and be horizontally centered.

---

### Requirement 10: Foldable Device Table-Top Posture Support

**User Story:** As a user with a foldable device in table-top posture, I want the Now Playing and Stats screens to split content at the fold hinge, so that the device can rest on a surface without obscuring the important content.

#### Acceptance Criteria

1. WHEN a `FoldingFeature` is detected with `state == FoldingFeature.State.HALF_OPENED` and `orientation == FoldingFeature.Orientation.HORIZONTAL`, THE App SHALL classify the device as being in `Table_Top_Posture`.
2. WHEN in `Table_Top_Posture` on `NowPlayingScreen`, THE NowPlayingScreen SHALL display album art in the upper half and playback controls in the lower half, split at the `Fold_Hinge` position.
3. WHEN in `Table_Top_Posture` on `MeScreen`, THE MeScreen SHALL display the analytics summary cards in the upper half and the scrollable details list in the lower half, split at the `Fold_Hinge` position.
4. WHEN the device is no longer in `Table_Top_Posture` (fold state changes to `FLAT` or `HALF_OPENED` with vertical orientation), THE App SHALL revert to the standard layout for the current `WindowSizeClass`. IF the layout transition fails for any reason, THE App SHALL guarantee that a standard `WindowSizeClass`-based layout is displayed to ensure the screen is never blank or unresponsive.
5. IF the `Fold_Hinge` bounds cannot be determined OR IF a `FoldingFeature` is not detected, THEN THE App SHALL fall back to the standard `WindowSizeClass`-based layout without entering `Table_Top_Posture` mode.

---

### Requirement 11: Landscape Mode Layout Stability

**User Story:** As a user rotating my phone to landscape, I want the app screens to remain fully usable without content being clipped or overflowing, so that I can use the app comfortably in any orientation.

#### Acceptance Criteria

1. WHEN the device is in landscape orientation with `Compact_Width` (portrait phone rotated), THE App SHALL display the `NavigationBar` at the bottom, preserving the existing mobile layout exactly.
2. WHEN `AudioLibraryScreen` is displayed in landscape with `Medium_Width`, THE AudioLibraryScreen SHALL display the tab row (TRACKS / ALBUMS / PLAYLISTS) and pager content without overflow or clipping, and MAY use less than the full available width if that is necessary to prevent overflow.
3. WHEN `VideoFolderScreen` is displayed in landscape with `Medium_Width`, THE VideoFolderScreen SHALL display the folder/movie grid with the correct adaptive column count per Requirement 3.
4. WHEN `MeScreen` is displayed in landscape with any window size, THE MeScreen vertical scroll SHALL remain functional and all analytics sections SHALL be fully accessible.
5. IF a screen transition occurs during a configuration change (rotation), THEN THE App SHALL preserve the current active tab selection and navigation back stack state.

---

### Requirement 12: Two-Pane Video Navigation on Expanded Screens

**User Story:** As a user browsing videos on a tablet in landscape mode, I want to see a video list alongside a preview or folder panel, so that I can browse and select videos efficiently without navigating away from context.

#### Acceptance Criteria

1. WHEN the `WindowSizeClass` width is `Expanded_Width` and the user is on the Videos tab at the `video_folders` destination, THE VideoNavigationHost SHALL display a two-pane layout: the folders/movies list in the leading pane and a detail placeholder (or selected folder's video list) in the trailing pane.
2. WHEN the user selects a folder in the leading pane of the two-pane layout, THE App SHALL display that folder's video list in the trailing pane without navigating away from the `video_folders` destination. WHEN the two-pane layout is not active (i.e., `WindowSizeClass` is not `Expanded_Width`), THE App SHALL use the existing single-pane folder navigation behavior and ignore any two-pane selection state.
3. WHEN the `WindowSizeClass` width is `Compact_Width` or `Medium_Width`, THE VideoNavigationHost SHALL use the existing single-pane navigation behavior without any changes.
4. WHEN a video is selected in the trailing pane, THE App SHALL initiate video playback using the same `onVideoClick` mechanism as the existing single-pane behavior.

---

### Requirement 13: Existing Functionality Preservation

**User Story:** As an existing user of FastBeat on a phone, I want to confirm that the responsive layout changes have not broken any existing functionality, so that I can continue to use the app exactly as before.

#### Acceptance Criteria

1. WHEN the `WindowSizeClass` is `Compact_Width`, THE App SHALL preserve all current behaviors including: bottom `NavigationBar` display, 2-column grids, full-width `MiniPlayer`, vertical `NowPlayingScreen` layout, search collapse behavior, PiP mode, orientation lock on video playback, deep link handling, and theme switching. Individual behavior failures SHALL be evaluated independently and SHALL NOT cause all other preservation requirements to fail.
2. WHEN the responsive layout code is active on any window size, THE App SHALL not introduce any additional recompositions in `Compact_Width` mode (verified by ensuring no `WindowSizeClass` checks exist inside existing compact-only composables).
3. IF the app receives a deep link to the audio player on any screen size, THEN THE App SHALL navigate to the `NowPlayingScreen` and activate the Music tab, exactly as currently implemented.
