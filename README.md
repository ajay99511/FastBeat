# FastBeat 🎵🎬

**FastBeat** is a premium, fully offline media player built for Android using modern development standards. It seamlessly handles local video and audio playback with a focus on aesthetics, granular control, and insightful usage analytics.

## 🚀 Features

### 🎥 Professional Video Player
*   **Gesture Controls:** Intuitive swipe gestures for Volume, Brightness, and Seeking.
*   **Smart Playback:** Resume where you left off with "Continue Watching" history.
*   **Picture-in-Picture (PiP):** Multitask seamlessly with Android's native PiP support.
*   **Advanced Display:**
    *   Zoom Modes: Fit, Fill, and Zoom (Crop).
    *   Playback Speed Control (0.5x to 2.0x).
    *   Screen Rotation Lock.
    *   Support for 4K/HD resolutions.
*   **Bookmarks:** Create custom timestamps/chapters within videos.
*   **Organization:** Folder-based navigation and a dedicated "Movies" filter (videos > 1 hour).

### 🎧 Immersive Audio Experience
*   **Library Management:** Browse by Tracks, Albums, and custom Playlists.
*   **Queue System:** robust queue management with Shuffle and Repeat modes.
*   **Persistent Playlists:** Create, rename, and manage playlists that persist locally.
*   **Mini Player:** Global playback control accessible from any screen.
*   **Favorites:** One-tap "Love" system to track your best songs.

### 📊 "Me" Analytics Dashboard
*   **Real-time Stats:** Tracks daily listening/viewing time.
*   **Streak System:** Gamified daily activity tracking.
*   **Smart Favorites:** Calculates "Current Obsession" (recent favorite) vs. "All Time #1" based on a qualifying play threshold (30s+ listening).
*   **History:** Detailed playback history database.

### 🎨 Customization & UI
*   **Dynamic Theming:** Switch between curated themes:
    *   🟠 **Amber Horizon** (Default)
    *   🔵 **Digital Waves**
    *   🟢 **Eco Frequency**
*   **Dark/Light Mode:** Full support for system or manual theme toggling.
*   **Modern Design:** Built 100% with Jetpack Compose using Material 3 components.

---

## 🛠 Tech Stack

FastBeat is engineered with a modern, scalable Android architecture.

*   **Language:** [Kotlin](https://kotlinlang.org/)
*   **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
*   **Architecture:** MVVM (Model-View-ViewModel) + Repository Pattern
*   **Dependency Injection:** [Hilt](https://dagger.dev/hilt/)
*   **Asynchronous Processing:** [Coroutines & Flow](https://kotlinlang.org/docs/coroutines-overview.html)
*   **Local Database:** [Room](https://developer.android.com/training/data-storage/room) (SQLite abstraction)
*   **Media Engine:** [AndroidX Media3](https://developer.android.com/media/media3) (ExoPlayer & MediaSession)
*   **Image Loading:** [Coil](https://coil-kt.github.io/coil/)
*   **Navigation:** Jetpack Navigation Compose

---

## 📂 Project Structure

```
com.local.offlinemediaplayer
├── data
│   └── db          # Room Entities (PlaybackHistory, Playlist, Analytics) & DAO
├── di              # Hilt Modules (DatabaseModule)
├── model           # Domain models (MediaFile, Album, PlayerStates)
├── repository      # Data repositories (PlaylistRepository)
├── service         # Media3 PlaybackService (Background audio)
├── ui
│   ├── components  # Reusable Compose UI (MiniPlayer, Headers, Dialogs)
│   ├── navigation  # NavHosts for Video and Audio flows
│   ├── screens     # Feature screens (VideoPlayer, AudioLibrary, MeScreen)
│   └── theme       # AppTheme, Color, Type definitions
└── viewmodel       # MainViewModel (Shared state holder)
```

---

## 🔐 Permissions

FastBeat respects user privacy and functions completely offline. It requires the following permissions to function:

*   `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` / `READ_MEDIA_IMAGES`: To scan and play local files.
*   `FOREGROUND_SERVICE`: To keep music playing when the app is in the background.

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1.  Fork the project.
2.  Create your feature branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request.

---

## 📄 License
This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
