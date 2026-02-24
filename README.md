# 🎵 FastBeat

## The Ultimate Premium Offline Media Player for Android

**FastBeat** is a powerful, modern, and beautifully designed offline media player built from the ground up to deliver a premium experience for both video and audio. Built with the latest Android technologies (Kotlin, Jetpack Compose, Media3, Room, Hilt).

---

## 🚀 Features

### 🎬 Advanced Video Player
* **High-Performance Playback:** Smooth offline video playback powered by AndroidX Media3 (ExoPlayer).
* **Intuitive Gestures:** Swipe left/right sides for brightness and volume control, swipe horizontally to seek.
* **Picture-in-Picture (PiP):** Seamlessly continue watching videos in a floating window while using other apps.
* **Multi-Track Support:** Select from available audio tracks (dual-audio) and subtitle tracks.
* **Video Bookmarks:** Create custom timestamp chapters to jump to your favorite scenes.
* **Customizable View:** Fit, Fill, or Zoom modes. Adjustable playback speed (0.5x to 2.0x).
* **Screen Lock:** Prevent accidental touches during playback.
* **Smart Organization:** Videos automatically grouped by folder. "Continue Watching" row for unfinished videos.
* **Thumbnail Generation:** Fast, background-cached video thumbnails.

### 🎧 Immersive Audio Experience
* **Library Management:** Powerful sorting (A-Z, duration, date added) and real-time search across tracks, albums, and playlists.
* **Smart Queue System:** Persistent playback queue that remembers your position across app restarts.
* **Infinite Playback:** Auto-refill with shuffled tracks when your queue ends.
* **Album Views:** Toggle between beautiful grid or list representations with advanced sorting (Release Year, Song Count).
* **Mini Player:** Always-visible persistent player bar across the app with gradient progress.

### 🖼️ Rich Image Gallery
* **Instant Gallery:** View all local device images in a sleek staggered grid.
* **Fullscreen Viewer:** Swipeable, edge-to-edge image viewing.
* **Media Management:** Multi-select functionality to safely delete unwanted videos or images with Android 11+ Scoped Storage support.

### 📊 "Me" Dashboard (Advanced Analytics)
* **Real-time Stats:** Track your daily, weekly, and monthly listening time.
* **Activity Streaks:** Maintain your daily listening streak.
* **Smart Insights:** Discover your "Current Obsession" (most played this week) versus your "All-Time #1".
* **Playback History:** Complete log of your recently played media.
* **Theming:** Choose from curated premium themes (Amber Horizon, Digital Waves, Eco Frequency) with full Dark/Light mode support.

---

## 🏗️ Architecture & Tech Stack

FastBeat follows modern Android architecture guidelines, implementing the **MVVM (Model-View-ViewModel) pattern** with a robust **Repository layer**.

* **Language:** Kotlin (100%)
* **UI Framework:** Jetpack Compose (Material 3)
* **Dependency Injection:** Dagger Hilt (with KSP)
* **Local Persistence:** Room Database v2.7.0 (8 Entities) & SharedPreferences
* **Media Engine:** AndroidX Media3 (ExoPlayer & MediaSessionService)
* **Asynchronous Operations:** Kotlin Coroutines & Flow
* **Image Loading:** Coil
* **Navigation:** Compose Navigation with animated transitions

---

## 📁 Project Structure

```text
com.local.offlinemediaplayer/
├── MainActivity.kt              # Setup, PiP, Permissions, Compose entry
├── MediaPlayerApp.kt            # Application scope (Hilt)
├── data/                        # Room DB (8 entities + DAOs), ThumbnailManager, DI modules
├── model/                       # Domain models (MediaFile, Album, Playlist, etc.)
├── repository/                  # Repositories for data operations (PlaylistRepository)
├── service/                     # Background playback service (MediaSessionService)
├── ui/
│   ├── MainScreen.kt            # App Scaffold and Bottom Navigation
│   ├── components/              # Reusable UI parts (MiniPlayer, SearchBars, Dialogs)
│   ├── navigation/              # Audio & Video NavHosts
│   ├── screens/                 # 15 distinct feature screens (Video, Audio, Gallery, Analytics)
│   └── theme/                   # Colors, Typography, AppThemeConfig
└── viewmodel/                   # MainViewModel (Central StateFlow manager, 120+ functions)
```

---

## 🔒 Permissions Explained

FastBeat is designed for **100% offline use**. We only ask for what's necessary to play your local files:
* `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, `READ_MEDIA_IMAGES` (API 33+) or `READ_EXTERNAL_STORAGE` (Legacy): To scan your local device for media.
* `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: To keep music playing when you minimize the app.
* `POST_NOTIFICATIONS`: To show the media playback controls in your notification panel.
* `WAKE_LOCK`: To prevent the CPU from sleeping during uninterrupted video playback.

*Note: No internet permissions are requested. Your data never leaves your device.*

---

## 🛠️ Getting Started & Build Instructions

### Prerequisites
* Android Studio Iguana / Jellyfish (or newer)
* Kotlin 1.9.0+
* JDK 11+
* Android SDK 35 (Target API 35)

### Building
1. Clone the repository.
2. Open the project in Android Studio.
3. Sync project with Gradle files.
4. Run the app on an emulator or physical device (API 26+).

---

## 🤝 Contributing
Contributions, issues, and feature requests are welcome!
Feel free to check the issues page.

## 📝 License
This project is licensed under the MIT License.
