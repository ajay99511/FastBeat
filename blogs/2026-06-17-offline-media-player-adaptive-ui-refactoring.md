---
slug: offline-media-player-adaptive-ui-refactoring
title: Implementing Adaptive Layouts & Investigating Play Protect False Positives
description: A deep dive into refactoring an Android Jetpack Compose media player for foldable and tablet UI responsiveness, and resolving Google Play Protect security warnings.
author: Ajay
publishedAt: 2026-06-17
updatedAt: 2026-06-17
tags:
  - android
  - jetpack-compose
  - adaptive-layouts
  - security
  - play-protect
  - material3
category: Engineering
coverImage: /blogs/adaptive-ui-refactoring/cover.jpg
readingTimeMinutes: 8
featured: true
draft: false
---

## Intro

Modern Android development requires applications to seamlessly scale from compact mobile phones to medium-sized foldables and expansive tablets. Recently, I undertook a significant architectural refactoring of FastBeat (OfflineMediaPlayer), migrating it from a mobile-only Jetpack Compose UI to a fully responsive, adaptive layout utilizing Jetpack WindowManager and Material3 Adaptive frameworks.

However, modernizing the codebase triggered an unexpected security alert from Google Play Protect during local testing: **"This app tries to bypass Android's security protections"**.

This post documents the technical implementation of the adaptive UI and the subsequent security investigation that revealed the underlying cause of the false positive.

## Main Idea: The Adaptive UI Architecture

The core challenge was to introduce full responsive/adaptive layout support for Medium (600–839 dp) and Expanded (≥ 840 dp) window size classes while preserving the existing mobile functionality, zero regressions, and pure state management. 

Instead of cluttering existing composables with `if/else` width checks, I architected a decentralized, pure-function approach.

### 1. Foundation Models & CompositionLocals

First, I implemented core data models mapping `WindowWidthSizeClass` into a simplified `AppWidthClass` (Compact, Medium, Expanded). Crucially, I also mapped `FoldingFeature` into a `DevicePosture` sealed class to handle foldable hinges (e.g., Samsung Z Fold or Pixel Fold).

To avoid prop-drilling, these states are provided at the root of the app via `CompositionLocalProvider`:

```kotlin
val LocalWindowSizeClass = staticCompositionLocalOf { AppWidthClass.Compact }
val LocalDevicePosture = staticCompositionLocalOf<DevicePosture> { DevicePosture.Normal }
```

### 2. Adaptive Navigation

In a compact view, a standard `BottomBar` is appropriate. However, for a Medium screen, a `NavigationRail` is better, and for an Expanded screen, a permanent `ModalNavigationDrawer` offers the best UX. 

I built a factory function to dictate the active navigation component based on the width class and full-screen video state:

```kotlin
fun navigationComponentFor(widthClass: AppWidthClass, isFullscreen: Boolean): NavigationComponentType {
    if (isFullscreen) return NavigationComponentType.Hidden
    return when (widthClass) {
        AppWidthClass.Compact -> NavigationComponentType.BottomBar
        AppWidthClass.Medium -> NavigationComponentType.Rail
        AppWidthClass.Expanded -> NavigationComponentType.Drawer
    }
}
```

This logic cleanly orchestrates the UI in `MainScreen.kt`, using `AnimatedContent` for smooth crossfade transitions between the different navigation paradigms without losing the selected tab state.

### 3. ListDetailPaneScaffold & Grid Scaling

For the Videos tab, I integrated the `ListDetailPaneScaffold` (part of the Experimental Material3 Adaptive API) to create a `TwoPaneVideoNavigationHost`. When on an Expanded screen, users see folder directories on the left and the inner video content on the right, mimicking a desktop file explorer. 

Similarly, hardcoded grid columns (e.g., `GridCells.Fixed(2)`) across `VideoFolderScreen`, `VideoListScreen`, and `AlbumListScreen` were rewritten to scale fluidly using `adaptiveGridColumns(widthClass)`, dynamically switching between 2, 3, or 4 columns based on available space.

## The Problem: Google Play Protect Warning

Upon successfully compiling and deploying these changes via local ADB to the emulator, the Android OS intercepted the installation with a high-severity Google Play Protect warning:

> **"This app tries to bypass Android's security protections"**

### Investigation & Context Analysis

A security warning of this magnitude typically implies Potentially Harmful Applications (PHAs), privilege escalation, or malware. I immediately halted further code modifications and initiated a forensic audit of the `git diff`. 

I verified the following:
1.  **No root access logic:** The app does not request or execute `su` commands.
2.  **Permissions:** The media and notification permissions (`READ_MEDIA_VIDEO`, `POST_NOTIFICATIONS`, etc.) were standard and properly documented in the manifest. 
3.  **Compiler Arguments:** The `-XXLanguage:+PropertyParamAnnotationDefaultTargetMode` flag was legacy and unrelated to runtime execution.

### The Root Cause: Jetpack WindowManager Reflection

The culprit was nestled within the newly added dependency: `androidx.window:window:1.3.0`.

To support dynamic foldable states (`FoldingFeature`) across a heavily fragmented Android ecosystem (where different OEMs like Samsung, Microsoft, and Google implement hardware hinges differently), the `androidx.window` library must interface with low-level, hidden system APIs (`androidx.window.sidecar` and `androidx.window.extensions`).

Because these APIs are not part of the standard public Android SDK, the library internally utilizes **Java Reflection** to probe the system and extract hardware states. 

Google Play Protect employs heuristic scanners. When it evaluated my locally built APK, it saw a "perfect storm" for a false positive:
1.  **Unsigned / Debug Key:** The app was signed locally by Android Studio, lacking a trusted Google Play Console cryptographic signature.
2.  **Sideloaded:** Installed via ADB, not the Play Store.
3.  **Hidden API Access:** The reflection code from `androidx.window` querying low-level system extensions.

The scanner misinterpreted the library's legitimate attempt to read the hinge angle as an unauthorized attempt to circumvent API restrictions or escalate privileges.

## Conclusion & Takeaways

The UI refactoring was a complete success. The app now fluently transitions from mobile bottom-bar layouts to expansive desktop-style dual-panes with robust Kotest coverage verifying every edge case of the logic.

Regarding the Play Protect warning, it serves as a fascinating look into the aggressive heuristics Google employs to safeguard the Android ecosystem. **This is a known false positive.** 

As developers, when we build locally with libraries utilizing reflection for hardware compatibility (like `androidx.window` or certain Macrobenchmark tools), we must expect these heuristic trips. For local development, it is perfectly safe to bypass the warning. Once the application is signed with a production release keystore and published through the official Google Play Console, Google's systems will whitelist the trusted signature, and the warning will entirely disappear for end-users. 
