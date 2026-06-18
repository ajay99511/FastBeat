---
slug: fixing-exoplayer-shuffle-playback
title: Fixing Premature Shuffle Playback Stopping with Android Media3 ExoPlayer
description: An in-depth investigation and resolution of a bug where ExoPlayer's shuffle mode prematurely stopped playback in offline media player queues.
author: Ajay
publishedAt: 2026-06-17
updatedAt: 2026-06-17
tags:
  - exoplayer
  - media3
  - android
  - kotlin
  - debugging
category: Engineering
readingTimeMinutes: 10
featured: false
draft: false
---

## The Hook: Why is my Playlist Stopping?

A common expectation for any media player is that hitting "Shuffle All" on a playlist or audio library will randomize the track order and play through every single song exactly once. However, in our Offline Media Player application, a frustrating bug surfaced: shuffle mode would randomly halt playback after playing only a fraction of the songs—sometimes after just two or three tracks.

This wasn't a crash. There were no exceptions in the logs. ExoPlayer simply decided it had reached the end of the queue. This post chronicles the deep dive into AndroidX Media3's queue management, how `MediaController` maps timeline indices, and the subtle logic flaw that caused our playback to end prematurely.

## Context: The Playback Architecture

Our media player architecture relies heavily on Jetpack Compose for the UI layer, feeding user intents down to a `PlaybackViewModel`. The ViewModel acts as the source of truth, managing playback state, playlist contexts, and queue logic. It delegates the actual heavy lifting to AndroidX Media3's `MediaController`, which communicates with a background `MediaSession` service managing an `ExoPlayer` instance.

The execution flow for starting a shuffled playlist looked roughly like this:
1. User taps "Shuffle All" in `MeScreen.kt` or `PlaylistDetailScreen.kt`.
2. The UI layer generates a random index from the list of songs to ensure playback doesn't always start with the first item: `val randomIndex = list.indices.random()`.
3. This index is passed to `PlaybackViewModel.playPlaylist` or `playAll`.
4. The ViewModel eventually calls `setQueue`, invoking `controller.setMediaItems(mediaItems, randomIndex, 0L)` followed by `controller.shuffleModeEnabled = true`.

On the surface, this seemed logical. We wanted a random song to start, and we wanted the queue to be shuffled. 

## The Investigation: Tracing the Silence

When investigating why playback was stopping, my initial hypotheses were:
- Was the `audioList` being inadvertently truncated or filtered mid-playback?
- Was our custom `autoFillQueue` logic incorrectly terminating the queue when transitioning between songs?
- Was `onPlayerError` halting the player silently?

Through exhaustive tracing of `PlaybackViewModel.kt`, I verified that the full list of `MediaItem`s was indeed being mapped and handed over to the `MediaController`. The `autoFillQueue` logic was correctly observing `REPEAT_MODE_OFF` and `STATE_ENDED`.

The anomaly lay in *when* `STATE_ENDED` was firing. 

I took a closer look at the interaction between the `startIndex` parameter and ExoPlayer's `shuffleModeEnabled` state.

## The Root Cause: Timeline Permutations and Indices

The core of the issue was a fundamental misunderstanding of how ExoPlayer handles `setMediaItems` when a `startIndex` is provided while `shuffleModeEnabled` is active.

When you enable shuffle mode on an ExoPlayer instance, it does not physically reorder the list of `MediaItem`s. Instead, it generates a `ShuffleOrder`—a randomized permutation of the timeline indices. 

If you pass a `startIndex` to `setMediaItems(mediaItems, startIndex, startPosition)` representing a random song from the *original* (unshuffled) list, ExoPlayer locates that item. Playback then follows the newly generated *shuffled* sequence starting from wherever that item happens to land in the permutation.

**The Math of the Bug:**
Let's say our playlist has 100 songs (Indices 0-99).
1. The UI picks a random song to start: Index `42`.
2. We hand 100 songs to ExoPlayer and say "Start at item 42, and shuffle."
3. ExoPlayer creates a shuffled timeline. In this random permutation, item `42` might accidentally be placed at position `95` out of 100.
4. ExoPlayer starts playing item `42`.
5. Because our default repeat state is `REPEAT_MODE_OFF`, ExoPlayer will only play the remaining songs in the shuffled timeline (positions 96, 97, 98, and 99).
6. After 5 songs, it hits the end of the shuffled timeline. `STATE_ENDED` is fired, and playback stops.

The player wasn't crashing; it was executing exactly what we commanded. We had inadvertently instructed it to start near the end of its shuffled queue.

## Solution Approach: Native Shuffle vs. In-Memory Shuffle

To fix this, we needed to ensure that when a user requests "Shuffle All", the player traverses the entire randomized list. I considered two approaches:

### Approach A: Manual In-Memory Shuffle
Shuffle the list of `MediaFile`s in Kotlin *before* passing it to the ViewModel.
- **Pros:** We have absolute control over the order. We just start at index 0 and let it play.
- **Cons:** We would have to disable ExoPlayer's native `shuffleModeEnabled`. This causes a state disconnect with the UI. The "Shuffle" icon in the `NowPlaying` screen relies on `controller.shuffleModeEnabled` to highlight. If we fake the shuffle, we have to fake the UI state too, leading to complex double-state management and issues when the user subsequently toggles shuffle on or off.

### Approach B: Leveraging ExoPlayer Native Shuffle (Chosen)
Let Media3 do the heavy lifting, but feed it the correct initialization parameters.
- **Pros:** Perfect synchronization with UI state. Handles playlist wrap-arounds naturally if the user toggles `RepeatMode`.

## Implementation Details: The Fix

The fix required adjusting the handoff between the UI and the ViewModel, specifically omitting the random `startIndex` when initializing a new shuffled session.

**1. Cleaning up the UI Layer**
We removed the redundant random index generation from the screens. For example, in `MeScreen.kt`:
```kotlin
// Before (Buggy)
ShuffleAllButton(
    onShuffle = { list ->
        val randomIndex = list.indices.random()
        viewModel.setQueue(list, randomIndex, true)
    }
)

// After (Clean)
ShuffleAllButton(
    onShuffle = { list ->
        viewModel.setQueue(list, 0, true)
    }
)
```

**2. Updating the ViewModel Facades**
In `PlaybackViewModel.kt`, we removed the pseudo-random logic from methods like `playPlaylist` and `playAll`:
```kotlin
fun playPlaylist(playlist: Playlist, songs: List<MediaFile>, shuffle: Boolean) {
    if (songs.isNotEmpty()) {
        _currentPlaylistContext.value = playlist.id
        persistPlaylistContext(playlist.id)
        val startIndex = 0 // setQueue will handle the starting point
        setQueue(songs, startIndex, shuffle)
    }
}
```

**3. Fixing the Core `setQueue` Logic**
Inside `setQueue`, we updated the controller initialization. If the intent is to shuffle a *new* session (identified by `startPosition == 0L`), we omit the `startIndex`. 

```kotlin
_player.value?.let { controller ->
    // 1. Set shuffle mode BEFORE setting items to ensure the 
    // initial playback point respects the shuffle order.
    controller.shuffleModeEnabled = shuffle

    if (shuffle && startPosition == 0L) {
        // When "Shuffle All" is triggered, omit the startIndex.
        // ExoPlayer will automatically start at the FIRST window 
        // of its internal shuffled timeline.
        controller.setMediaItems(mediaItems)
    } else {
        // For non-shuffled playback or restoring a saved session, 
        // explicitly use the provided index.
        controller.setMediaItems(mediaItems, startIndex, startPosition)
    }

    controller.prepare()
    controller.play()
}
```

By calling `controller.setMediaItems(mediaItems)` without a specific starting index while `shuffleModeEnabled` is true, Media3 automatically selects a random song to serve as the *first* item in its internal permutation. Playback seamlessly traverses the entire 100-song timeline from position 0 to 99, completely solving the premature stopping issue.

## Final Takeaway

When orchestrating playback with powerful libraries like AndroidX Media3, it is tempting to manually enforce behaviors (like picking a random start index) to achieve a specific user experience. However, mixing manual array manipulation with a native state machine often leads to edge cases and race conditions. 

The most efficient and bug-free path is almost always to trust the platform's native capabilities, provided you take the time to deeply understand how its internal indexing and state timelines operate under the hood.