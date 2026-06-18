---
slug: optimizing-media3-playback-queues
title: "Optimizing Media3 Playback: Thread Safety and Advanced Queue Logic"
description: A deep dive into fixing Media3 thread-safety crashes, building true random shuffle, and managing complex media queues in Android.
author: Ajay
publishedAt: 2026-06-17
updatedAt: 2026-06-17
tags:
  - android
  - media3
  - exoplayer
  - performance
  - architecture
category: Engineering
readingTimeMinutes: 8
featured: true
draft: false
---

## The Challenge: Complex Queues and UI Performance

Building a modern local media player requires robust queue management. Users expect seamless transitions, instant "Play Next" capabilities, and flawless "Shuffle All" experiences across vast libraries of thousands of songs. 

During a recent code review of our `OfflineMediaPlayer` app's Audio Playlist and Album screens, we encountered a classic mobile engineering tension: **UI performance vs. Media Player thread constraints.**

We set out to ensure our "Play Next", "Add to Queue", "Play All", and "Shuffle All" features met premium industry standards. Here is a breakdown of the challenges we uncovered, the mistakes we made, and how we ultimately solved them.

## The Problem with "Shuffle All"

The first logical flaw we discovered was in our `playAll` and `playAlbum` implementations. When a user clicked "Shuffle All", the player correctly loaded the filtered list and instructed Media3 to shuffle the order. 

However, we were hardcoding the `startIndex` to `0`:

```kotlin
// The Old Way
fun playAll(list: List<MediaFile>, shuffle: Boolean) {
    val startIndex = 0 // Always started at the very first song!
    setQueue(list, startIndex, shuffle)
}
```

The result? "Shuffle All" always started with the exact same song before randomly shuffling the rest. It felt predictable. 

**The Fix:** We implemented a true random start using idiomatic Kotlin, ensuring every shuffle session begins uniquely:

```kotlin
// The New Way
val startIndex = if (shuffle) list.indices.random() else 0
```

## The Performance Bottleneck: Mapping the Display Queue

When shuffle is enabled, we need to show the user the *actual* upcoming order of songs, not just the original list. To do this, we maintain a `_displayQueue` state flow.

We determine this order by querying Media3's internal `Timeline`. Our original implementation iterated through this timeline on the **Main Thread**:

```kotlin
// Running on Main Thread
var windowIndex = timeline.getFirstWindowIndex(true)
while (windowIndex != C.INDEX_UNSET) {
    timeline.getWindow(windowIndex, window)
    val mediaId = window.mediaItem.mediaId
    mediaIdToMediaFile[mediaId]?.let { shuffledQueue.add(it) }
    windowIndex = timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)
}
```

For an album of 12 songs, this is fine. For an "All Audio" playlist of 5,000 songs, mapping thousands of string IDs to heavy `MediaFile` domain objects on the Main Thread causes massive UI jank.

## The Thread-Safety Trap

Our immediate instinct as engineers was to offload this heavy loop to a background thread (`Dispatchers.Default`). 

We wrapped the entire `updateDisplayQueue` method in a Coroutine and shipped it. The UI was buttery smooth. But suddenly, the application started crashing completely on startup and whenever the shuffle button was clicked.

**The Crash:** `java.lang.IllegalStateException: Player is accessed on the wrong thread.`

**The Lesson:** Media3 (and ExoPlayer) is incredibly strict about thread safety. You *must* access the player instance, its state, and its `Timeline` on the application's Main Thread (or the specific thread the player was created on). You cannot query the `Timeline` from a background coroutine.

## The Solution: Split the Workload

We needed the Main Thread to satisfy Media3, but we needed a background thread to satisfy Compose UI performance.

Our final solution splits the task into two distinct phases:
1. **Safe Extraction (Main Thread):** Quickly loop through the `Timeline` and extract *only* the lightweight String `mediaId`s.
2. **Heavy Mapping (Background Thread):** Pass that list of raw strings to `Dispatchers.Default`, build the hash map, match the IDs to `MediaFile` objects, and emit the final state.

```kotlin
private fun updateDisplayQueue() {
    // ... setup and early returns ...

    val timeline = controller.currentTimeline

    // 1. SAFELY extract the shuffled sequence of media IDs on the Main thread.
    val shuffledMediaIds = mutableListOf<String>()
    val window = androidx.media3.common.Timeline.Window()
    
    var windowIndex = timeline.getFirstWindowIndex(true)
    while (windowIndex != androidx.media3.common.C.INDEX_UNSET) {
        timeline.getWindow(windowIndex, window)
        shuffledMediaIds.add(window.mediaItem.mediaId)
        windowIndex = timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)
    }

    // 2. Offload the heavy object mapping to Default dispatcher
    displayQueueUpdateJob = viewModelScope.launch(Dispatchers.Default) {
        val mediaIdToMediaFile = currentQueueSnapshot.associateBy { it.id.toString() }
        val shuffledQueue = shuffledMediaIds.mapNotNull { mediaIdToMediaFile[it] }

        withContext(Dispatchers.Main) {
            _displayQueue.value = shuffledQueue
        }
    }
}
```

## Expanding Capabilities: Batch Queue Additions

With performance and stability secured, we finalized the feature set by introducing batch additions. Previously, our `playNext` and `addToQueue` functions only accepted a single `MediaFile`.

To support adding an entire Album to the queue from the newly implemented "More Options" menu in `AlbumDetailScreen.kt`, we created overloaded functions that take `List<MediaFile>`.

The logic required careful handling of existing items:
1. Identify if the songs to add already exist in the queue.
2. Tell Media3 to remove them from their current positions (avoiding duplicates).
3. Tell Media3 to add the new batch of songs to the correct index (either at the end of the queue, or immediately following the current playing track).

By maintaining a single source of truth (`PlaybackViewModel`) and delegating the heavy lifting of playback sequence to Media3, we achieved a highly responsive, crash-free media experience.

## Final Takeaway

When dealing with low-level Android media APIs, respect their thread constraints above all else. You can always split your data-processing pipelines—extract the raw state safely on the Main thread, and do your expensive domain mapping in the background.