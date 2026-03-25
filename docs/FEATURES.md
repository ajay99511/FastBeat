# ✨ FastBeat - Features Guide

A deep dive into all the premium features and capabilities of FastBeat.

---

## 📋 Table of Contents
- [Core Media Engine](#-core-media-engine)
- [Video Player Experience](#-video-player-experience)
- [Audio Player Experience](#-audio-player-experience)
- [Analytics & Dashboards](#-analytics--dashboards)
- [Image Gallery](#-image-gallery)
- [Premium Themes](#-premium-themes)
- [Roadmap](#-roadmap)

---

## 🎯 Core Media Engine

### AndroidX Media3 (ExoPlayer)
**Status:** ✅ Available

The backbone of FastBeat is **Media3**, the modern replacement for ExoPlayer and MediaSessionService. This unified engine provides:
- **Low-latency playback** for high-bitrate files.
- **Adaptive streaming** (for future HLS/DASH support).
- **Background Playback** with full notification control.

---

## 🎬 Video Player Experience

### Advanced Gestures
**Status:** ✅ Available

FastBeat features a highly intuitive gesture system:
- **Vertical Swipe (Left Side):** Controls display brightness.
- **Vertical Swipe (Right Side):** Controls system volume.
- **Horizontal Swipe:** Seek through the video with a preview of the new timestamp.
- **Double Tap:** Jump 10 seconds forward or backward.

### Picture-in-Picture (PiP)
**Status:** ✅ Available

Multitask without missing a second of your video. PiP mode:
- Activates automatically when you swipe home during video playback (Android 12+).
- Supports aspect-ratio matching to ensure no black bars in the floating window.

### Multi-Track & Subtitles
**Status:** ✅ Available

Support for dual-audio movies and custom subtitle tracks (.srt, .vtt). Users can toggle tracks on-the-fly via the settings menu in the player.

---

## 🎧 Audio Player Experience

### Smart Queue System
**Status:** ✅ Available

A robust playback queue that persists even if the app is closed.
- **Auto-Refill:** When the queue reaches the end, it automatically refills with shuffled tracks from your library.
- **Dynamic Shuffling:** Switch between linear and shuffle mode instantly.

### Library Management
**Status:** ✅ Available

FastBeat organizes your music automatically:
- **Smart Sorting:** Sort by track name, album name, date added, or duration.
- **Persistent Playlists:** Create, update, and manage your own custom collections.
- **Real-Time Search:** Instant results as you type across all media categories.

---

## 📊 Analytics & Dashboards

### The "Me" Screen
**Status:** ✅ Available

A personal dashboard that visualizes your media habits:
- **Listening Time:** Daily, weekly, and monthly time spent listening.
- **Current Obsession:** The track you've played the most in the last 7 days.
- **Streaks:** Gamified listening streaks to encourage regular use.

---

## 🖼️ Image Gallery

### Instant Media Scanning
**Status:** ✅ Available

View your device's entire photo library in a beautiful staggered grid.
- **Fullscreen Viewer:** Immersive, edge-to-edge photo viewing.
- **Scoped Storage Integration:** Safely delete images or videos with system-prompted confirmation.

---

## 🎨 Premium Themes

### Visual Style
**Status:** ✅ Available

Choose the look that fits your vibe:
- **Amber Horizon:** A warm, energetic theme.
- **Digital Waves:** A sleek, tech-focused blue/purple palette.
- **Eco Frequency:** A soothing, green-based natural theme.
- **Dynamic Progress:** Playback progress bars use theme-specific gradients.

---

## 🚀 Roadmap

### Q2 2026: Equalizer & DSP
- [ ] **10-band Graphic EQ** with custom presets.
- [ ] **Bass Boost** and Virtualizer for enhanced audio.

### Q3 2026: lyrics Integration
- [ ] **LRC Parsing:** Support for synchronized .lrc files.
- [ ] **Floating Lyrics:** View lyrics even when using other apps.

### Q4 2026: Network & Casting
- [ ] **Chromecast Support:** Cast to large screens.
- [ ] **SMB/SFTP Access:** Stream media from your home server.

---

## 🙋 Feature Requests
Have an idea for a feature? We value your feedback!
1. Check the [Issues Page](https://github.com/yourusername/FastBeat/issues).
2. If it's new, click **New Issue** and select **Feature Request**.

---

## 📚 Navigation
- [Back to README](../README.md)
- [Getting Started](GETTING_STARTED.md)
- [Contributing Guidelines](../CONTRIBUTING.md)

---

## 📊 Feature Status Legend

| Status | Badge | Description |
|--------|-------|-------------|
| **Available** | ✅ | Fully implemented and stable. |
| **In Progress** | 🚧 | Currently in development. |
| **Planned** | 📅 | Committed to the roadmap. |
| **Proposed** | 💡 | Under consideration. |

---

## 📝 Version History

### Version 1.0.0 (March 2026)
- **Initial Release**
- Full Video and Audio engine.
- Picture-in-Picture mode.
- Analytics dashboard.
- 3 Premium Themes.
