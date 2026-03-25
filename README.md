# ⏱️ FastBeat

<div align="center">

[![Android](https://img.shields.io/badge/Platform-Android-green.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**The Ultimate Premium Offline Media Player for Android**

[Features](#-features) • [Quick Start](#-quick-start) • [Architecture](#-architecture) • [Documentation](#-documentation) • [Contributing](#-contributing)

</div>

---

## 🌟 What is FastBeat?

FastBeat is a powerful, modern, and beautifully designed offline media player built from the ground up to deliver a premium experience for both video and audio. Built with the latest Android technologies (Kotlin, Jetpack Compose, Media3, Room, Hilt), it provides a seamless and immersive way to enjoy your local media without any internet connection.

Whether you're watching high-definition movies with advanced gesture controls and Picture-in-Picture mode, or listening to your music library with a smart queue system and rich analytics, FastBeat is designed to be your all-in-one media companion. It respects your privacy by remaining 100% offline, ensuring your data never leaves your device.

### Why FastBeat?

| Traditional Media Players | FastBeat Solution |
|---------------------|------------------------|
| Clunky, outdated UI | **Modern, Fluid Jetpack Compose UI** |
| Ad-heavy and online-focused | **100% Offline & Ad-Free Experience** |
| Basic playback controls | **Advanced Gestures, PiP, & Smart Queue** |
| No insights into habits | **Rich Analytics & Activity Dashboard** |

---

## ✨ Features

### 🎬 Advanced Video Player
- **High-Performance Playback** — Smooth offline video playback powered by AndroidX Media3 (ExoPlayer).
- **Intuitive Gestures** — Swipe left/right for brightness/volume, swipe horizontally to seek.
- **Picture-in-Picture (PiP)** — Continue watching in a floating window while using other apps.
- **Multi-Track Support** — Easily switch between audio tracks and subtitle tracks.

### 🎧 Immersive Audio Experience
- **Smart Queue System** — Persistent playback queue that remembers your position across restarts.
- **Infinite Playback** — Auto-refills with shuffled tracks when your queue ends.
- **Rich Library Management** — Advanced sorting and real-time search across tracks, albums, and playlists.
- **Mini Player** — Always-visible persistent player bar with gradient progress.

### 📊 "Me" Dashboard (Analytics)
- **Real-time Stats** — Track your daily, weekly, and monthly listening time.
- **Activity Streaks** — Maintain your daily listening streak.
- **Smart Insights** — Discover your most played tracks and current obsessions.
- **Playback History** — Complete log of your recently played media.

### 🖼️ Rich Image Gallery
- **Instant Gallery** — View all device images in a sleek, staggered grid.
- **Fullscreen Viewer** — Edge-to-edge, swipeable image viewing experience.
- **Scoped Storage Support** — Safely delete or manage media with Android 11+ compatibility.

### 🎨 Premium Theming
- **Curated Themes** — Choose from Amber Horizon, Digital Waves, or Eco Frequency.
- **Dynamic Dark Mode** — Full support for Light and Dark modes across all themes.
- **Animated Transitions** — Smooth navigation and UI interactions.

### 📁 Smart Organization
- **Auto-Grouping** — Videos automatically organized by folders.
- **Continue Watching** — Resume unfinished videos exactly where you left off.
- **Fast Thumbnails** — Background-cached video thumbnails for instant browsing.

---

## 📸 Screenshots

<div align="center">

| Video Player | Audio Library |
|:---:|:---:|
| ![Video Player](docs/screenshots/video_player.png) | ![Audio Library](docs/screenshots/audio_library.png) |
| *Advanced Gestures & PiP* | *Smart Search & Sorting* |

| Analytics Dashboard | Image Gallery |
|:---:|:---:|
| ![Analytics](docs/screenshots/analytics.png) | ![Gallery](docs/screenshots/gallery.png) |
| *Listening Habits & Streaks* | *Staggered Grid View* |

</div>

---

## 🚀 Quick Start

### Prerequisites

Ensure you have the following installed:

- **Android Studio** (Iguana / Jellyfish or newer)
- **JDK 11+**
- **Android SDK 35** (Target API 35)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/FastBeat.git
   cd FastBeat
   ```

2. **Sync Project**
   Open the project in Android Studio and let Gradle sync.

3. **Install Dependencies**
   The project uses a Version Catalog (`gradle/libs.versions.toml`).
   ```bash
   ./gradlew build
   ```

4. **Run the application**
   Press **Shift + F10** or the Play button in Android Studio with an emulator or physical device (API 26+) connected.

### Build for Production

```bash
# Generate Debug APK
./gradlew assembleDebug

# Generate Release APK
./gradlew assembleRelease
```

---

## 🏗️ Architecture

FastBeat follows the **MVVM (Model-View-ViewModel)** architectural pattern with a clean separation of concerns and a robust **Repository layer**.

```
[ UI Layer (Compose) ]
         |
         v
[ ViewModel (StateFlow) ] <---> [ Media3 Playback Service ]
         |
         v
[ Repository Layer ]
         |
         +-----------------------+
         |                       |
[ Room Database ]       [ MediaStore (Local) ]
```

### Key Design Decisions

| Pattern/Decision | Purpose | Benefit |
|-----------------|---------|---------|
| **Jetpack Compose** | UI Framework | Declarative UI, faster development, and modern animations. |
| **AndroidX Media3** | Media Engine | Unified API for playback and sessions, replacing legacy ExoPlayer. |
| **Dagger Hilt** | Dependency Injection | Compile-time validation and easy management of scoped dependencies. |
| **Room DB** | Local Persistence | Type-safe database access for playlists, history, and stats. |

---

## 📦 Tech Stack

### Core Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| **Kotlin** | 2.2.10 | Core programming language. |
| **Jetpack Compose** | 1.7.x | Declarative UI framework. |
| **AndroidX Media3** | 1.2.1 | Playback engine and media session management. |
| **Room** | 2.7.0-alpha11 | Local database for offline storage. |
| **Dagger Hilt** | 2.58 | Dependency injection framework. |

### Key Packages

```toml
[libraries]
androidx-media3-exoplayer = "1.2.1"
androidx-room-runtime = "2.7.0-alpha11"
hilt-android = "2.58"
coil-compose = "2.6.0"  # Image Loading
gson = "2.10.1"        # JSON Persistence
```

---

## 📚 Documentation

Comprehensive documentation is available in the `docs/` directory:

| Document | Description |
|----------|-------------|
| [Getting Started](docs/GETTING_STARTED.md) | Detailed installation and developer onboarding. |
| [Features Guide](docs/FEATURES.md) | Deep dive into all available features and roadmap. |
| [Contributing](CONTRIBUTING.md) | Guidelines for contributing to the project. |
| [License](LICENSE) | MIT License details. |

---

## 🛠️ Development

### Project Structure

```text
app/src/main/java/com/local/offlinemediaplayer/
├── data/           # Room DB, ThumbnailManager, DI modules
├── model/          # Domain models (MediaFile, Album, Playlist)
├── repository/     # Data repositories (Playlist, Media)
├── service/        # Background playback service (Media3)
├── ui/             # Compose Screens, Navigation, Themes
└── viewmodel/      # Central StateFlow managers (Playback, Analytics)
```

### Running Tests

```bash
# Run all Unit Tests
./gradlew test

# Run Instrumentation Tests
./gradlew connectedAndroidTest
```

---

## 🔮 Roadmap

### Coming Soon
- [ ] **Equalizer** — Advanced 10-band equalizer with presets (Q2 2026).
- [ ] **Lyrics Support** — Local .lrc file parsing and synchronized display (Q3 2026).
- [ ] **Chromecast Support** — Cast your local media to large screens (Q4 2026).

### Under Consideration
- [ ] DLNA/UPnP streaming from local servers.
- [ ] Custom sound profiles per device.
- [ ] AI-powered smart playlists based on mood.

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. **Fork** the repository.
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`).
3. **Commit** your changes (`git commit -m 'feat: add amazing feature'`).
4. **Push** to the branch (`git push origin feature/amazing-feature`).
5. **Open** a Pull Request.

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [AndroidX Media3 Team](https://developer.android.com/guide/topics/media/media3) for the robust playback engine.
- [Material Design 3](https://m3.material.io/) for the beautiful UI components.
- [Coil](https://coil-kt.github.io/coil/) for the efficient image loading library.

---

<div align="center">

**Made with ❤️ using Jetpack Compose**

[Report Bug](https://github.com/yourusername/FastBeat/issues) • [Request Feature](https://github.com/yourusername/FastBeat/issues)

</div>
