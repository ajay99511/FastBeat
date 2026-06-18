---
slug: preparing-offline-media-player-play-store-release
title: Preparing Offline Media Player for its Play Store Debut
description: A comprehensive engineering log on navigating Google Play Store release requirements, resolving version conflicts, and justifying broad media permissions.
author: Ajay
publishedAt: 2026-06-17
updatedAt: 2026-06-17
tags:
  - play-store
  - release
  - permissions
  - android
  - jetpack-compose
category: Engineering
readingTimeMinutes: 7
featured: true
draft: false
---

## The Challenge of a First Release

Releasing a modern Android application to the Google Play Store is more than just building a signed APK. It requires strict adherence to policy declarations, precise version management, and clear communication of the app's value proposition. 

Today, we prepared **Offline Media Player**—a feature-rich, local-first media hub built with Jetpack Compose, Hilt, and Media3—for its first closed testing release on the Play Store. This post documents the strategic decisions, problem-solving, and policy navigation required to achieve a successful submission.

## Codebase Analysis & Value Proposition

Before drafting any store listings, a deep dive into the application's architecture was necessary to understand its core strengths. Analyzing files like `MeScreen.kt`, `PlaybackViewModel.kt`, and `MediaRepository.kt` revealed a highly capable application:

- **Modern Stack:** 100% Jetpack Compose UI, powered by Coroutines/StateFlow and Hilt for dependency injection.
- **Robust Playback:** Integration with Google's Media3 (ExoPlayer) supports Picture-in-Picture (PiP), gesture controls (volume, brightness, seek), and dynamic track selection.
- **Unique Selling Point (USP):** The "Me" screen acts as a personalized analytics dashboard, utilizing a local Room database to track daily playtime, activity trends, streaks, and a "Continue Watching" history.

Understanding these features allowed us to craft both detailed and concise release notes (`release_notes.md` and `release_notes_short.md`) that accurately highlighted the app's premium, local-first experience.

## Resolving Version Code Conflicts

During the upload process, the Play Console rejected the build with a common error:
> *"Version code 1 has already been used. Try another version code."*

### The Solution
Every App Bundle/APK uploaded to a specific track must have a uniquely incrementing `versionCode`. We accessed `app/build.gradle.kts` and applied a targeted fix:

```kotlin
// Old configuration
defaultConfig {
    versionCode = 1
    versionName = "1.0"
}

// Updated configuration
defaultConfig {
    versionCode = 2
    versionName = "1.0.1"
}
```

This surgical update resolved the conflict, allowing the new build to be accepted by the Play Console.

## Navigating Strict Media Permissions

Google Play enforces strict policies regarding broad file access. Apps must justify the use of `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO`. If an app only needs occasional access, developers are forced to use the Android Photo Picker. 

However, Offline Media Player is a **core media hub**. We had to draft highly specific, 250-character justifications to prove the Photo Picker was insufficient.

### Video Permission Justification
> As a core media player, the app requires frequent access to all local videos to provide library indexing, automatic folder organization, custom playlists, gesture-controlled playback, and "Continue Watching" history for the user.

### Images Permission Justification
> Broad access is essential for indexing the user's local gallery to provide a integrated media hub experience. This allows the app to display, organize, and manage local images and photo folders directly within the unified library.

These justifications succeed because they focus on **indexing, background organization, and seamless library integration**—features impossible to achieve with a one-time picker.

## Play Console Declarations

Finally, we mapped out the mandatory App Content declarations based on our codebase analysis:

1. **Privacy Policy:** Required explicit language stating that all media data and analytics are processed *locally* via Room DB and never transmitted off-device.
2. **Data Safety:** Confirmed "No data collection" and "No data sharing."
3. **Ads:** Declared "No ads," verified by the absence of ad SDKs in `build.gradle.kts`.
4. **App Access:** Declared "All functionality is available without special access" (no logins required).
5. **Content Rating & Audience:** Set as a Media Player utility, targeting ages 13+, avoiding the strict "Designed for Families" requirements.

## Final Takeaway

Preparing an app for the Play Store is an exercise in clarity. By deeply understanding the codebase's capabilities, we successfully translated technical features into compelling user-facing release notes, resolved build configuration hurdles, and confidently navigated Google's strict permission policies. Offline Media Player is now fully primed for its closed testing rollout.