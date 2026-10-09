# Changelog

All notable changes to FastBeat are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **How releases work here, because it is not the usual flow.**
> Version numbers are **derived, never hand-edited**: `versionCode` is the commit count and
> `versionName` comes from `git describe`. The `v*` tags those read are created by
> [`release.yml`](.github/workflows/release.yml), which picks the bump level by reading the
> **non-merge commit subjects** since the previous tag — `feat:` → minor, `fix:`/`perf:` → patch,
> `feat!:` or a `BREAKING CHANGE:` footer → major. A range of only docs, tests, refactors and chores
> produces **no release**, which is a normal outcome and why some work below appears under *Changed*
> rather than in a version of its own.
>
> That also means **this file is written by hand** and the tags are not. If an entry here disagrees
> with the tags, the tags are the truth.
>
> Entries were reconstructed from the git history at each tag. Dates are the tag's commit date.

## [Unreleased]

Nothing yet. Add entries here as you merge, under the headings
*Added · Changed · Deprecated · Removed · Fixed · Security*.

---

## [1.4.0] — 2026-10-08

The images surface, which until now was a grid and little else.

### Added

- **Full image viewer gestures** — pinch to zoom, double-tap to zoom, and pan a zoomed photo.
- **Multi-select** — act on several photos at once instead of one at a time.
- **Sorting** by date, name or size.
- **Share**, and an info button that actually shows file details rather than nothing.
- **Animated GIF and WebP playback** in the gallery and viewer, instead of a still first frame.
- **Deletion now moves photos to the system trash** on supported versions, and the UI says so —
  a recoverable delete rather than a permanent one.
- **Accessibility pass over the grid and viewer**, so both are usable without sight.
- **Responsive grid** that reads the number of columns the screen can actually fit.

### Fixed

- Text in the UI can grow and keeps its content when space runs out, instead of being clipped.
- Chart axis labels in the statistics screen stay readable at any size.
- The viewer opens on the photo the list says it should, rather than on a guessed index.
- The image list has a single owner, removing a class of inconsistency between grid and viewer.

### Changed

- The images screen was split into testable pieces — it previously could not be tested at all.

---

## [1.3.0] — 2026-09-22

The statistics ("Me") surface, reworked.

### Added

- Statistics are **readable before there is any data**, and readable without sight.
- The **streak** has something to measure against, so the number means something.
- Rankings show **more than first place**.
- The activity chart can show **more than the current week**.
- An **all-time total** and the direction it is trending.

### Fixed

- The library total counts the whole library.
- Statistics windows are measured against a day that moves, so yesterday's numbers stop being
  treated as today's.

### Changed

- Every screen is grouped into a feature package, and playlist sort enums moved out of the UI layer
  into the ViewModel layer where they belong.
- Detekt baseline entries made obsolete by the refactor were retired rather than left to rot.

---

## [1.2.0] — 2026-09-10

### Changed

- **Listening Activity and the "Me" screen were offloaded onto the continue-watching data**, and the
  UI logic was separated across multiple files instead of accumulating in one.

---

## [1.1.0] — 2026-09-08

### Removed

- Dead code and redundant logic, removed outright rather than deprecated.

### Changed

- Dependabot was regrouped onto a low-touch cadence — monthly for libraries, majors silenced,
  runtime-sensitive libraries isolated from safe ones. The reasoning is recorded at length in
  [`.github/dependabot.yml`](.github/dependabot.yml).

---

## [1.0.1] — 2026-08-27

The release that made the project's own machinery trustworthy. Most of this is invisible to users
and was the point: before it, versions were hand-edited literals, nothing compiled the merge result
of a PR, and a schema change could have taken a user's library with it.

### Added

- **`MIGRATION_1_5`**, preserving v1 user data across the upgrade. `fallbackToDestructiveMigration()`
  is banned in this project; this is why.
- **Derived versioning and automatic release tags** — `release.yml`, plus `versionCode`/`versionName`
  computed from git.
- **A CI build gate** that compiles, lints, unit-tests, runs the release (R8) variant, detekt and
  ktlint on every PR — see [`build.yml`](.github/workflows/build.yml) for why it exists.
- **A Baseline Profile module** and its startup journeys.
- **An `AppError` hierarchy** with string-resource messages, replacing ad-hoc error strings.
- **Dependabot**, CodeQL, `LICENSE`, `CODEOWNERS` and `SECURITY.md`.
- **Tests** pinning the behaviour that had no coverage: queue persistence rules, the no-double-count
  analytics invariant, media deletion on the legacy path, the real `DatabaseModule` configuration
  against launch crashes, and Compose tests for the mini player and the permission flow.

### Fixed

- Playtime is persisted on stop and **credited to the correct day**.
- Crash and side-effect issues on version upgrade.
- Queue drag-and-drop tracks the dragged item **by key, not by index**.
- Swallowed exceptions are logged rather than discarded — `AudioEffectsManager`, `ThumbnailManager`
  release, `PlaybackService.onTaskRemoved` — and `printStackTrace` calls were replaced with
  contextual `Log.e`. An empty `catch` is a defect in this codebase.

### Changed

- `PlaybackViewModel` was decomposed into focused collaborators: `QueueManager`, `QueuePersistence`,
  `BookmarkManager`, `MediaDeletionHandler`, plus extracted domain use cases.
- Preferences migrated to **DataStore** (`app_prefs` and sort preferences, each with a legacy
  migration path), and **Gson was replaced with kotlinx.serialization** for legacy playlist JSON.
- User-facing strings externalised for `LibraryViewModel` and `EqualizerSheet`.

---

## [1.0.0] — 2026-07-06

Initial release. Tagged `FastBeatV1` at the time — that tag and the earlier `fastbeatv0` predate the
`v*` semver scheme and are kept only for history. The published feature list for this release is in
[`release_notes.md`](release_notes.md).

### Added

- Universal offline player for local **audio, video and images**.
- **Advanced video playback** — Picture-in-Picture, swipe gestures for volume, brightness and seek,
  audio-track and subtitle selection, and Fit/Fill/Zoom resize modes.
- **Continue watching** — resume audio and video where you left off.
- **Smart library** — automatic organisation by artist and album, custom playlists for audio and
  video, and sorting by title, date added, duration or play count.
- **The "Me" dashboard** — daily and weekly playtime, streaks, and all-time favourites.
- **Dynamic theming** — orange, blue and green, each in light and dark, with an edge-to-edge UI.
- Built on Jetpack Compose, Media3 (ExoPlayer), Room and Hilt, 100% offline.

[Unreleased]: https://github.com/ajay99511/FastBeat/compare/v1.4.0...HEAD
[1.4.0]: https://github.com/ajay99511/FastBeat/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/ajay99511/FastBeat/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/ajay99511/FastBeat/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/ajay99511/FastBeat/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/ajay99511/FastBeat/compare/FastBeatV1...v1.0.1
[1.0.0]: https://github.com/ajay99511/FastBeat/releases/tag/FastBeatV1
