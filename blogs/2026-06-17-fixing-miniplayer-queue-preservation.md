---
slug: fixing-miniplayer-queue-preservation
title: Preserving the ExoPlayer Queue During Safe Media Deletion
description: How we refactored media deletion logic to preserve the active ExoPlayer queue, handle real-time sync, and ensure robust database cleanup.
author: Ajay
publishedAt: 2026-06-17
updatedAt: 2026-06-17
tags:
  - android
  - exoplayer
  - room-database
  - coroutines
  - state-management
category: Engineering
readingTimeMinutes: 7
featured: false
draft: false
---

## The Problem: The Destructive Delete

In our offline media player, the "Now Playing" screen and the global mini-player share the same underlying queue and ExoPlayer state via the `PlaybackViewModel`. However, we identified a critical UX bug regarding the delete functionality.

When a user decided to delete the *currently playing song* directly from the Now Playing screen, the active audio queue was entirely wiped out. The `PlaybackViewModel` would naively handle the successful deletion by attempting to manually reset the controller, clear the `_currentQueue`, and blindly queue up the next track using `p.setMediaItem(mediaItem, 0)`. 

This brittle approach broke state persistence, caused jarring playback interruptions, and forced the user to manually rebuild their listening session. We needed a "safe deletion" mechanism that preserved the music queue, shifted indices intelligently, and guaranteed database integrity.

## Context

Our application relies on several interconnected layers for media playback:
1. **ExoPlayer (`Player.Controller`)**: The source of truth for current playback.
2. **Room Database (`MediaDao`)**: Persists queues, play history, analytics, and playlist structures.
3. **ViewModels (`PlaybackViewModel`, `LibraryViewModel`)**: Manage state streams (Flows) that the Compose UI observes.

Deleting a file isn't just about calling `java.io.File.delete()`. In a sophisticated media app, a deleted track leaves behind orphaned analytics, broken bookmarks, and invalid playlist cross-references.

## Solution Approach

We tackled the problem in three distinct phases:

1. **Comprehensive Database Cleanup**: Centralizing the removal of orphaned data across all Room tables.
2. **ExoPlayer Queue Preservation**: Utilizing native ExoPlayer queue mutation methods instead of tearing down the entire list.
3. **Real-time Synchronization**: Ensuring deletions originating from anywhere in the app (like the Library or Album screens) immediately update the active playback queue.

## Implementation Details

### 1. Centralized Database Cleanup

Previously, database cleanup was scattered and incomplete. We introduced targeted batch-deletion queries within `MediaDao` to surgically excise a list of `mediaIds` from every relational table:

```kotlin
@Query("DELETE FROM playlist_media_cross_ref WHERE mediaId IN (:mediaIds)")
suspend fun removeMediaFromAllPlaylists(mediaIds: List<Long>)

@Query("DELETE FROM current_queue WHERE mediaId IN (:mediaIds)")
suspend fun removeMediaFromQueue(mediaIds: List<Long>)

@Query("DELETE FROM media_analytics WHERE mediaId IN (:mediaIds)")
suspend fun deleteAnalytics(mediaIds: List<Long>)

// ... similar queries for history, bookmarks, and play events
```

We then encapsulated these inside a single, transactional coroutine block in `PlaylistRepository` called `cleanupDeletedMedia()`. By centralizing this, both `LibraryViewModel` and `PlaybackViewModel` could call it confidently knowing no orphaned rows would be left behind.

### 2. Surgical Queue Mutation

Inside `PlaybackViewModel.onCurrentTrackDeleteSuccess()`, we replaced the heavy-handed reset logic with ExoPlayer's `removeMediaItem` function.

By finding the index of the deleted item inside `_currentQueue` and passing it to the controller, ExoPlayer seamlessly handles the transition logic. If the deleted item was currently playing, ExoPlayer automatically advances to the next track in the queue without any manual intervention from us.

```kotlin
// Remove from local memory state
queue.removeAt(deletedIndex)
_currentQueue.value = queue
_displayQueue.value = queue
persistQueue(queue) // Update Room's current_queue table

// Remove from ExoPlayer gracefully
_player.value?.let { controller ->
    if (deletedIndex < controller.mediaItemCount) {
        controller.removeMediaItem(deletedIndex)
    }

    if (controller.mediaItemCount == 0) {
        _currentTrack.value = null
        _isPlaying.value = false
    } else {
        updateCurrentTrackFromPlayer(controller)
    }
}
```

### 3. Real-time Reactive Synchronization

Fixing the Now Playing screen wasn't enough. What if a user minimizes the player, navigates to the Album view, and deletes a batch of songs that happen to be queued up?

To handle this, we set up a reactive observer inside the `init` block of `PlaybackViewModel`. It continuously collects updates from `mediaRepository.audioList`. Whenever the audio list updates, we check our active `_currentQueue` against the new list of available `mediaIds`.

```kotlin
audioList.collect { allAudio ->
    val currentQueueList = _currentQueue.value
    if (currentQueueList.isEmpty()) return@collect

    val allIds = allAudio.map { it.id }.toSet()
    val itemsToRemove = mutableListOf<Int>()
    
    // Identify orphaned queue items and remove them backwards to preserve index alignment
    for (i in 0 until controller.mediaItemCount) {
        val mId = controller.getMediaItemAt(i).mediaId.toLongOrNull()
        if (mId != null && mId !in allIds) {
            itemsToRemove.add(i)
        }
    }
    
    if (itemsToRemove.isNotEmpty()) {
        itemsToRemove.reversed().forEach { idx ->
            controller.removeMediaItem(idx)
        }
        updateCurrentTrackFromPlayer(controller)
    }
}
```

## Tradeoffs

- **Performance vs. Correctness**: Calculating the diff between the `currentQueue` and the `audioList` inside a flow collection runs in $O(N + M)$ time (by casting `audioList` IDs to a `Set`). While this is slightly more computationally expensive than blindly assuming the queue is fine, it guarantees 100% state correctness and avoids `IndexOutOfBoundsExceptions` or unplayable tracks stalling the player.
- **Index Shifting**: When removing multiple items from a live ExoPlayer queue, the indices shift immediately. Iterating and removing them in reverse order (`itemsToRemove.reversed().forEach`) was a necessary constraint to ensure we didn't remove the wrong tracks.

## Final Takeaway

State persistence in media applications requires trusting your framework's native tools. By delegating the heavy lifting of queue transition to `ExoPlayer` (`removeMediaItem`), and relying on Room's relational integrity and batch deletions, we transformed a fragile, destructive feature into a rock-solid, seamless UX. The active queue now serves as a resilient, real-time reflection of the user's actual media library.