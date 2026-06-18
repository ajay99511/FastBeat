---
slug: fixing-obsession-analytics-integer-overflow-reactivity
title: Fixing Obsession Analytics: From Integer Overflows to Reactive Flows
description: A deep dive into fixing a silent analytics bug caused by integer overflow and refactoring for real-time reactivity in an Android media player.
author: Ajay
publishedAt: 2026-06-17
updatedAt: 2026-06-17
tags:
  - android
  - kotlin
  - room
  - flows
  - analytics
category: Engineering
readingTimeMinutes: 5
featured: true
draft: false
---

## The Hook: A Silent Failure in "Obsession"

Users love seeing their habits. In FastBeat, the "Current Obsession" feature is designed to show the track you've played the most in the last 30 days. However, a common complaint was that it stayed stuck on "Keep Listening..." regardless of how much music was played. It wasn't just a UI bug; it was a silent failure in the analytics pipeline.

## The Problem: Integer Overflow and Stale Data

During the investigation, two major issues were identified:

1.  **Integer Overflow in Timestamp Math**: The 30-day window was calculated as:
    ```kotlin
    val monthStart = today - (29 * 24 * 60 * 60 * 1000)
    ```
    In Kotlin/JVM, the expression `29 * 24 * 60 * 60 * 1000` is evaluated as a 32-bit `Int`. Its value (~2.5 billion) exceeds `Int.MAX_VALUE` (2.14 billion), causing it to overflow and become a negative number. Subtracting a negative number from `today` resulted in a `monthStart` timestamp in the **future**. The database queries for "plays since monthStart" were effectively looking for plays that hadn't happened yet.

2.  **Lack of Reactivity**: The analytics were snapshot-based. The `AnalyticsViewModel` calculated stats once and relied on a manual refresh trigger. This meant the UI didn't update in real-time as the user listened to music, breaking the "alive" feel of the dashboard.

## The Solution: Reactive Analytics Pipeline

As a senior engineer, the goal wasn't just to fix the math, but to modernize the data flow to ensure long-term reliability and performance.

### 1. Correcting the Math
By simply adding an `L` suffix to the calculation, we force the compiler to use `Long` math, preventing the overflow:
```kotlin
val monthStart = today - (29L * 24 * 60 * 60 * 1000)
```

### 2. Reactive Room Flows
We refactored the `MediaDao` to return `Flow<Long?>` for analytics queries instead of `suspend` functions. This allows the ViewModel to "observe" the database directly.
```kotlin
@Query("SELECT mediaId FROM play_events WHERE timestamp >= :sinceTimestamp GROUP BY mediaId ORDER BY COUNT(*) DESC LIMIT 1")
fun getMostPlayedMediaIdSinceFlow(sinceTimestamp: Long): Flow<Long?>
```

### 3. The `combine` Strategy
In the `AnalyticsViewModel`, we used the `combine` operator to merge multiple data streams (audio files, video files, and multiple DAO flows) into a single, cohesive `realtimeAnalytics` state. Because we were combining more than 5 flows, we utilized the array-based `combine` API for better type safety and flexibility.

## Implementation Details

The refactoring involved three key components:

- **`MediaDao`**: Converted analytics queries to return `Flow`, enabling real-time observation of play events.
- **`AnalyticsViewModel`**: Implemented a `flatMapLatest` and `combine` pipeline. It now calculates "Current Obsession" and "All Time #1" dynamically whenever the database changes.
- **`PlaybackViewModel`**: Simplified play recording. It now simply writes a `PlayEvent` to the database, and the reactive pipeline automatically handles the UI update.

## Tradeoffs and Decisions

- **Blocking vs. Non-blocking**: We chose to perform small database fetches for play counts inside the `combine` block. While technically blocking, these are indexed queries on small result sets, keeping the implementation simple while maintaining high performance.
- **Window Size**: We kept the 30-day window but ensured the calculation was robust against overflows. Future enhancements could include a weighted "Obsession" score that favors very recent plays.

## Final Takeaway

Engineering "Obsession" isn't just about counting plays; it's about building a reliable, reactive system that respects the user's data. By fixing the integer overflow and moving to a Flow-based architecture, we've transformed a broken feature into a real-time reflection of user habits.

**Key Learnings:**
- Always use `Long` literals for time calculations in milliseconds.
- Prefer Room `Flow` for UIs that need to stay in sync with data changes.
- Use `combine` to build complex UI states from simple, independent data sources.
