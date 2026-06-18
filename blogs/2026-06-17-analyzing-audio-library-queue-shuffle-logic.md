---
slug: analyzing-audio-library-queue-shuffle-logic
title: "Analyzing Audio Library Logic: Play Next, Add to Queue, and Shuffle Dynamics"
description: A deep dive into identifying logical bugs in audio playback queues, shuffle behavior, and filter interactions in Android's Media3.
author: Ajay
publishedAt: 2026-06-17
updatedAt: 2026-06-17
tags:
  - android
  - media3
  - architecture
  - exoplayer
category: Engineering
coverImage: /blogs/audio-library-logic/cover.jpg
readingTimeMinutes: 5
featured: false
draft: false
---

## The Hook: Why Queue Logic Matters

Building a seamless audio player is deceivingly complex. Features like "Play Next", "Add to Queue", and "Shuffle All" sound simple, but their implementation requires meticulous attention to state synchronization and user intent. A robust media player must respect active filters and guarantee predictable queue behavior, even when Media3's internal shuffle order throws a wrench into the mix.

During a comprehensive code review of our Android `OfflineMediaPlayer`, I investigated the `AudioListScreen` and `PlaybackViewModel` to analyze how these features behave in edge cases. What I found were classic logical disconnects between UI state, user expectations, and the underlying player engine.

## Context: The State of the Audio Library

The app employs a modern, reactive architecture using Jetpack Compose and Hilt, with a clear separation of concerns:
- `LibraryViewModel`: Handles sorting, filtering, and database queries.
- `PlaybackViewModel`: Manages the ExoPlayer/Media3 `MediaController` and the playback queue state.

While the separation is clean, the transition of state from the UI filters into the playback queue revealed several functional gaps that compromise the user experience.

## Finding 1: The "Shuffle All" Filter Disconnect

When a user searches for a specific genre (e.g., "Rock") and taps the "Shuffle" button, they expect a randomized playlist of their search results. 

### The Bug
In the `AudioListScreen`, clicking the shuffle action called `viewModel.playAll(shuffle = true)`. However, the ViewModel implementation ignored the active UI filters:

```kotlin
// PlaybackViewModel.kt
fun playAll(shuffle: Boolean) {
    val currentList = audioList.value // This uses the UNFILTERED repository list
    if (currentList.isNotEmpty()) {
        setQueue(currentList, 0, shuffle)
    }
}
```

By referencing the unfiltered `audioList.value`, the app bypassed the `filteredAudioList` state managed by the `LibraryViewModel`. As a result, the user's search query was completely ignored, and the entire library began playing.

### The Solution
The UI must explicitly pass the active filtered list down to the playback layer, or the `PlaybackViewModel` must combine its state with the active filters before initiating playback. Respecting the "current view" is paramount for contextual audio playback.

## Finding 2: The "Play Next" Upcoming Queue Bug

The "Play Next" feature is designed to move a song to the immediate next slot in the queue (`current + 1`). However, the existing logic penalized songs that were already in the upcoming queue.

### The Bug
The `playNext` function in `PlaybackViewModel` evaluated the track's existing index in the queue:

```kotlin
val existingIndex = queue.indexOfFirst { it.id == media.id }
if (existingIndex != -1) {
    // CASE 2: In Upcoming List (Index > Current) -> Ignore
    if (existingIndex > currentIdx) return 
}
```

If a user saw a song 5 slots away and tapped "Play Next", the application simply dropped the request. To the user, this feels like a broken button. 

### The Solution
User intent should override the existing queue state. We must remove the early return and allow the track to be repositioned. If `existingIndex > currentIdx`, the track should be removed from its future slot and inserted at `currentIdx + 1`.

## Finding 3: The Illusion of Order in Shuffle Mode

A technical limitation arises when combining "Play Next" with `shuffleModeEnabled = true` in Media3.

### The Technical Hurdle
The queue uses `controller.addMediaItem(insertIndex, mediaItem)` for both "Play Next" and "Add to Queue". However, when ExoPlayer's shuffle mode is active, inserting an item at index `currentIdx + 1` does **not** guarantee it will play next. The internal `ShuffleOrder` dictates the sequence, meaning the newly added track will be placed at a randomized future position.

### The Tradeoff
To solve this, we have three architectural paths:
1. **Disable Shuffle Temporarily:** Pause the shuffle state, insert the item, and carefully rebuild the shuffle order (clunky and error-prone).
2. **Custom ShuffleOrder:** Implement a highly customized `ShuffleOrder` class that forces dynamically added "Play Next" items to bypass the randomizer.
3. **UX Acknowledgment:** Keep the native Media3 behavior, but provide UI feedback indicating the song was added to the shuffled queue (most common in modern streaming apps).

## Implementation Detail: Missing UI Feedback

Underlying these logical bugs is a lack of immediate visual feedback. 

Currently, actions like "Add to Queue" are processed silently. By introducing a `SharedFlow` to emit transient UI events (like `Snackbar` triggers), we can confirm to the user that their action was recognized, closing the loop between the interaction and the background MediaController update.

## Final Takeaway

Architecting a media player requires more than just piping data to ExoPlayer. It requires a rigid synchronization between what the user sees (filtered lists), what the user intends ("Play *this* next"), and how the player engine handles sequence (Shuffle Orders). By resolving these specific logical disconnects, the app's audio playback will transition from functionally acceptable to industry-standard smooth.