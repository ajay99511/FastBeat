# 🎵 FastBeat Developer Guide
## Complete Setup & Architecture Reference

> **FastBeat** is a premium, fully offline Android media player built with Kotlin, Jetpack Compose, and Media3. This guide documents the complete technical setup, architecture, and development workflows.

---

## 📑 Table of Contents

1. [Build Configuration](#1-build-configuration)
2. [Android Manifest](#2-android-manifest)
3. [Application Architecture](#3-application-architecture)
4. [Project Structure](#4-project-structure)
5. [Data Layer](#5-data-layer)
6. [ViewModel Layer](#6-viewmodel-layer)
7. [UI Layer](#7-ui-layer)
8. [Playback Service](#8-playback-service)
9. [Feature Guide](#9-feature-guide)
10. [Development Workflows](#10-development-workflows)

---

## 1. Build Configuration

### `build.gradle.kts` (Module: app)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)  // Kotlin Symbol Processing (replaces kapt)
    id("com.google.dagger.hilt.android") version "2.58"
}

android {
    namespace = "com.local.offlinemediaplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.local.offlinemediaplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = listOf("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }

    buildFeatures {
        compose = true
    }

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName = "FastBeat.apk"
        }
    }
}

dependencies {
    // Compose Platform
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Hilt Dependency Injection
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("com.google.dagger:hilt-android:2.58")
    ksp("com.google.dagger:hilt-compiler:2.58")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Media3 (Modern ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Coil (Image Loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Gson (Legacy playlist migration)
    implementation("com.google.code.gson:gson:2.10.1")

    // Room Database
    implementation("androidx.room:room-runtime:2.7.0-alpha11")
    implementation("androidx.room:room-ktx:2.7.0-alpha11")
    ksp("androidx.room:room-compiler:2.7.0-alpha11")
}
```

---

## 2. Android Manifest

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Permissions for Media Access -->
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".MediaPlayerApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:icon="@mipmap/app_logo"
        android:roundIcon="@mipmap/app_logo_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar"
        tools:targetApi="31">

        <!-- Main Activity (supports PiP) -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:supportsPictureInPicture="true"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Media Playback Service (Foreground) -->
        <service
            android:name=".service.PlaybackService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

### Key Manifest Configuration:
- **PiP Support**: `supportsPictureInPicture="true"` + `configChanges` flags prevent recreation
- **Media Permissions**: Granular for API 33+, fallback `READ_EXTERNAL_STORAGE` for older
- **Foreground Service**: Required for background music playback
- **WAKE_LOCK**: Keeps CPU awake during playback
- **POST_NOTIFICATIONS**: Required for media notification on API 33+

---

## 3. Application Architecture

### Architecture Pattern: MVVM + Repository

```
┌──────────────────────────────────────────────────────────────┐
│                     UI LAYER (Presentation)                   │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  MainActivity → MainScreen → 15 Feature Screens         │   │
│  │  (Jetpack Compose with Material 3)                      │   │
│  └────────────────────────────────────────────────────────┘   │
│                              │                                │
│                              ▼                                │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  ViewModel Layer (MainViewModel - 2063 lines)           │   │
│  │  - 123 functions managing all app state                 │   │
│  │  - StateFlow for reactive UI updates                    │   │
│  │  - SharedFlow for one-time events                       │   │
│  └────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                     DATA LAYER                                │
│  ┌──────────────────┐  ┌──────────────────────────────────┐  │
│  │ PlaylistRepository│  │ ThumbnailManager                  │  │
│  │ (Playlist CRUD)   │  │ (Video thumbnail disk cache)      │  │
│  └──────────────────┘  └──────────────────────────────────┘  │
│              │                      │                         │
│              ▼                      ▼                         │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  Data Sources                                           │   │
│  │  ├── Room Database (8 entities, version 5)              │   │
│  │  │   ├── PlaybackHistory (with track selection)         │   │
│  │  │   ├── MediaAnalytics, PlayEvent, DailyPlaytime       │   │
│  │  │   ├── PlaylistEntity, PlaylistMediaCrossRef           │   │
│  │  │   ├── BookmarkEntity, QueueItemEntity                 │   │
│  │  ├── MediaStore (Audio, Video, Images via ContentResolver)│  │
│  │  └── SharedPreferences (Theme, queue index)             │   │
│  └────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                  SERVICE LAYER                                │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  PlaybackService (MediaSessionService)                  │   │
│  │  - ExoPlayer with audio attributes & focus              │   │
│  │  - MediaSession for system notifications                │   │
│  │  - Wake mode for uninterrupted playback                 │   │
│  │  - Notification intent → Now Playing screen             │   │
│  └────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### Dependency Injection (Hilt)

```
                    @HiltAndroidApp
                   ┌────────────────┐
                   │ MediaPlayerApp  │
                   └───────┬────────┘
                           │
         ┌─────────────────┼──────────────────┐
         ▼                 ▼                  ▼
  @AndroidEntryPoint  @HiltViewModel    @Module @InstallIn
  ┌──────────────┐   ┌────────────┐   ┌────────────────┐
  │ MainActivity  │   │MainViewModel│   │DatabaseModule   │
  └──────────────┘   └────────────┘   │ - AppDatabase   │
                      │ @Inject:      │ - MediaDao      │
                      │ - Application │ - ThumbnailMgr  │
                      │ - Repository  └────────────────┘
                      │ - MediaDao
                      │ - ThumbnailMgr
                      └────────────
```

---

## 4. Project Structure

```
com.local.offlinemediaplayer/
├── MainActivity.kt              # Entry point, permissions, theme, PiP handling
├── MediaPlayerApp.kt            # @HiltAndroidApp Application class
│
├── data/
│   ├── ThumbnailManager.kt      # Video thumbnail generation + disk caching
│   ├── db/
│   │   ├── AppDatabase.kt       # Room database (v5, 8 entities)
│   │   ├── Entities.kt          # All Room entity data classes
│   │   └── MediaDao.kt          # Data Access Object (35 queries)
│   └── di/
│       └── DatabaseModule.kt    # Hilt module: DB + DAO + ThumbnailManager
│
├── model/
│   ├── Album.kt                 # Album domain model
│   ├── MediaFile.kt             # Core media model (19 fields)
│   ├── PlayerStates.kt          # AudioPlayerState, VideoPlayerState snapshots
│   ├── Playlist.kt              # Playlist domain model
│   └── VideoFolder.kt           # Folder grouping model
│
├── repository/
│   └── PlaylistRepository.kt    # Playlist CRUD + legacy JSON migration
│
├── service/
│   └── PlaybackService.kt       # Media3 MediaSessionService
│
├── ui/
│   ├── MainScreen.kt            # Main scaffold, tabs, navigation
│   ├── common/
│   │   └── FormatUtils.kt       # Duration, size formatting utilities
│   ├── components/
│   │   ├── Dialogs.kt           # Reusable dialog composables
│   │   ├── MediaPropertiesDialog.kt  # Media info dialog
│   │   ├── MiniPlayer.kt        # Global mini player bar
│   │   └── SearchComponents.kt  # Search bar composables
│   ├── navigation/
│   │   ├── AudioNavigationHost.kt  # Audio tab NavHost
│   │   └── VideoNavigationHost.kt  # Video tab NavHost
│   ├── screens/
│   │   ├── AccessibilityGuideScreen.kt  # App guide/tutorial
│   │   ├── AlbumDetailScreen.kt         # Album songs view
│   │   ├── AlbumListScreen.kt           # Album grid/list with sorting
│   │   ├── AudioLibraryScreen.kt        # Audio tab (Tracks/Albums/Playlists)
│   │   ├── AudioListScreen.kt           # Song list with search/sort
│   │   ├── ImageListScreen.kt           # Image gallery + fullscreen viewer
│   │   ├── MeScreen.kt                  # Analytics dashboard (88KB)
│   │   ├── NowPlayingScreen.kt          # Full-screen music player
│   │   ├── PermissionScreens.kt         # Permission request UI
│   │   ├── PlaylistDetailScreen.kt      # Audio playlist detail
│   │   ├── PlaylistListScreen.kt        # Playlist list management
│   │   ├── VideoFolderScreen.kt         # Video folder browser
│   │   ├── VideoListScreen.kt           # Video list with multi-select/delete
│   │   ├── VideoPlayerScreen.kt         # Full video player (52KB)
│   │   └── VideoPlaylistDetailScreen.kt # Video playlist detail
│   └── theme/
│       ├── Color.kt               # Color palette definitions
│       ├── Theme.kt               # AppThemeConfig + OfflineMediaPlayerTheme
│       ├── Type.kt                # Typography definitions
│       └── Headers/
│           ├── AppHeader.kt       # Base header composable
│           ├── AudioHeader.kt     # Audio tab header
│           ├── FastBeatHeader.kt  # Main branded header
│           ├── ImageHeader.kt     # Image tab header
│           └── VideoHeader.kt     # Video tab header
│
└── viewmodel/
    └── MainViewModel.kt          # Central ViewModel (2063 lines, 123 functions)
```

---

## 5. Data Layer

### Room Database (8 Entities, Version 5)

| Entity | Purpose | Key Fields |
|--------|---------|------------|
| `PlaybackHistory` | Resume playback position | `mediaId`, `position`, `duration`, `mediaType`, `audioTrackIndex`, `subtitleTrackIndex` |
| `MediaAnalytics` | Track play/skip counts | `mediaId`, `playCount`, `skipCount`, `lastPlayed` |
| `DailyPlaytime` | Daily listening time | `date` (normalized midnight), `totalPlaytimeMs` |
| `PlayEvent` | Individual play events | `mediaId`, `timestamp` (for "Current Obsession") |
| `PlaylistEntity` | Playlist metadata | `id`, `name`, `createdAt`, `isVideo` |
| `PlaylistMediaCrossRef` | Many-to-many playlist↔songs | `playlistId`, `mediaId`, `addedAt` (FK with CASCADE) |
| `BookmarkEntity` | Video timestamp bookmarks | `mediaId`, `timestamp`, `label` |
| `QueueItemEntity` | Persistent queue | `mediaId`, `sortOrder` |

### MediaDao (35 Queries)

Organized into sections:
- **Playback History**: Save/get position, "Continue Watching" query, last played audio
- **Analytics**: Play count, daily playtime tracking, active days for streaks
- **Play Events**: Log plays, calculate most-played since timestamp
- **Playlists**: Full CRUD, relation queries with `@Transaction`
- **Bookmarks**: CRUD for video timestamp bookmarks
- **Persistent Queue**: Save/restore/replace queue with `@Transaction`

### ThumbnailManager

- **Purpose**: Background video thumbnail generation with disk caching
- **Cache Key**: `thumb_{mediaId}_{size}_{dateModified}.jpg` (auto-invalidates when video changes)
- **Strategy**: Try embedded cover art first, then extract frame at ~10% of duration
- **Output**: Scaled JPEG (max 360px wide, 75% quality)
- **API**: `generateThumbnails()` returns `Flow<Pair<Long, String>>` for progressive UI updates
- **Cleanup**: `cleanStaleThumbnails()` removes orphaned cache files

### PlaylistRepository

- **Pattern**: Single source of truth via Room, observed with `Flow`
- **Legacy Migration**: Reads old JSON playlist files, migrates to Room, deletes legacy file
- **Default Playlists**: Auto-creates "Favorites" (audio) and "Love" (video) on first launch
- **Duplicate Prevention**: Checks `getPlaylistCount(name, isVideo)` before insertion

---

## 6. ViewModel Layer

### MainViewModel (2063 Lines, 123 Functions)

**Injected Dependencies:**
- `Application` — Context for ContentResolver, SharedPreferences
- `PlaylistRepository` — Playlist CRUD operations
- `MediaDao` — Direct database access for analytics, history, queue
- `ThumbnailManager` — Video thumbnail generation

**Key State Groups:**

| Category | StateFlows | Purpose |
|----------|-----------|---------|
| **Media Lists** | `videoList`, `audioList`, `_imageList`, `_albums` | Raw scanned media |
| **Filtered/Derived** | `filteredAudioList`, `filteredAlbums`, `moviesList`, `videoFolders` | Combined with search/sort |
| **Playlists** | `playlists`, `audioPlaylists`, `videoPlaylists` | From PlaylistRepository |
| **Playback** | `currentTrack`, `isPlaying`, `currentPosition`, `duration`, `playbackSpeed` | Player state |
| **Queue** | `currentQueue`, `currentIndex`, `isShuffleEnabled`, `repeatMode` | Queue management |
| **Video** | `isVideoPlayerVisible`, `isInPipMode`, `isLocked`, `resizeMode`, `videoSize` | Video player state |
| **Analytics** | `realtimeAnalytics`, `continueWatchingList` | Me screen data |
| **Theme** | `currentTheme`, `isDarkTheme` | Persisted in SharedPreferences |
| **UI Events** | `deleteIntentEvent` (SharedFlow), `navigateToPlayer` | One-time events |

**Key Function Categories:**

| Category | Functions | Examples |
|----------|----------|---------|
| **Media Scanning** | `scanMedia`, `queryMedia`, `queryImages`, `queryAlbums` | ContentResolver queries |
| **Playback** | `playMedia`, `playVideo`, `playVideoFromList`, `playPlaylist`, `playAlbum` | All playback entry points |
| **Queue** | `setQueue`, `playNext`, `playPrevious`, `addToQueue`, `playTrackFromQueue` | Queue management |
| **Persistent Queue** | `restoreQueue`, `persistQueue`, `persistQueueIndex` | Survive app restarts |
| **Auto Shuffle** | `autoFillQueue`, `toggleShuffle` | Auto-fill when queue ends |
| **Controls** | `togglePlayPause`, `seekTo`, `rewind`, `forward`, `cyclePlaybackSpeed` | Playback controls |
| **Video** | `closeVideo`, `toggleLock`, `toggleResizeMode`, `setPipMode` | Video-specific |
| **Track Selection** | `getAudioTracks`, `getSubtitleTracks`, `selectAudioTrack`, `selectSubtitleTrack`, `disableSubtitles` | Audio/subtitle tracks |
| **Bookmarks** | `addBookmark`, `deleteBookmark` | Video timestamp bookmarks |
| **Favorites** | `toggleFavorite`, `toggleAlbumInFavorites` | Add/remove from "Favorites" playlist |
| **Playlists** | `createPlaylist`, `renamePlaylist`, `deletePlaylist`, `addSongToPlaylist`, `removeSongFromPlaylist` | Playlist CRUD |
| **Deletion** | `deleteSelectedMedia`, `deleteImage`, `onDeleteSuccess` | Scoped storage deletion |
| **Selection** | `toggleSelectionMode`, `toggleSelection`, `selectAll` | Multi-select for bulk ops |
| **Search/Sort** | `updateSearchQuery`, `updateSortOption`, `updateAlbumSortOption` | Filtering & sorting |
| **Theme** | `updateTheme`, `toggleThemeMode` | Theme switching |
| **Analytics** | `calculateAnalytics`, `recordPlay`, `getNormalizedToday` | Usage analytics |
| **State Preservation** | `playVideo`→save audio state, `closeVideo`→restore | Audio/video switching |

---

## 7. UI Layer

### Screen Hierarchy (5 Tabs)

```
MainScreen (Scaffold + BottomNav)
├── Tab 0: Video → VideoNavigationHost
│   ├── VideoFolderScreen (folder grid + search + playlists + "Continue Watching")
│   ├── VideoListScreen (video list + multi-select + delete + sort)
│   ├── VideoPlayerScreen (full player: gestures, PiP, bookmarks, tracks)
│   └── VideoPlaylistDetailScreen
│
├── Tab 1: Audio → AudioNavigationHost
│   ├── AudioLibraryScreen (sub-tabs: Tracks, Albums, Playlists)
│   │   ├── AudioListScreen (song list + search + sort + favorites)
│   │   ├── AlbumListScreen (grid/list toggle + sort)
│   │   └── PlaylistListScreen (create, rename, delete)
│   ├── AlbumDetailScreen (album songs + play/shuffle + mini player)
│   ├── PlaylistDetailScreen (playlist songs + mini player)
│   └── NowPlayingScreen (full player UI)
│
├── Tab 2: Albums → AlbumListScreen (standalone)
│
├── Tab 3: Images → ImageListScreen (grid + fullscreen viewer + deletion)
│
└── Tab 4: Me → MeScreen (analytics dashboard)
    ├── Daily/Weekly/Monthly playtime stats
    ├── Activity streak tracking
    ├── "Current Obsession" vs "All Time #1"
    ├── Playback history
    ├── Theme switcher (3 themes)
    ├── Dark/Light mode toggle
    └── Accessibility guide
```

### Reusable Components

| Component | File | Purpose |
|-----------|------|---------|
| **MiniPlayer** | `MiniPlayer.kt` | Global mini player with gradient progress, play/pause, skip, previous |
| **Dialogs** | `Dialogs.kt` | Create playlist, rename, delete confirmation dialogs |
| **MediaPropertiesDialog** | `MediaPropertiesDialog.kt` | Display file info (resolution, size, duration) |
| **SearchComponents** | `SearchComponents.kt` | Animated search bar for all list screens |
| **FastBeatHeader** | `FastBeatHeader.kt` | Branded app header with gradient accent bar |
| **VideoHeader/AudioHeader/ImageHeader** | `Headers/` | Per-tab contextual headers |
| **FormatUtils** | `FormatUtils.kt` | Duration and file size formatting |

---

## 8. Playback Service

### PlaybackService.kt

```kotlin
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(), true  // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)  // Pause on headphone disconnect
            .setWakeMode(C.WAKE_MODE_LOCAL)     // Keep CPU awake
            .build()

        // Notification tap opens Now Playing screen
        val intent = Intent(this, MainActivity::class.java)
            .apply { putExtra("open_player", true) }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo) = mediaSession
    override fun onDestroy() {
        mediaSession?.run { player.release(); release(); mediaSession = null }
        super.onDestroy()
    }
}
```

**Key Design Decisions:**
- **Audio Attributes**: `AUDIO_CONTENT_TYPE_MUSIC` + `USAGE_MEDIA` for proper audio focus handling
- **Audio Becoming Noisy**: Auto-pause when headphones are disconnected
- **Wake Mode**: `WAKE_MODE_LOCAL` keeps CPU awake during playback
- **Session Activity**: Notification click navigates to Now Playing via `"open_player"` Intent extra

---

## 9. Feature Guide

### Video Player (VideoPlayerScreen.kt — 52KB)
- **Gesture Controls**: Swipe for volume/brightness/seeking
- **Zoom Modes**: Fit, Fill, Zoom (cycling through `ResizeMode` enum)
- **Playback Speed**: 0.5x → 0.75x → 1.0x → 1.25x → 1.5x → 2.0x
- **Screen Lock**: Toggle to prevent accidental touches
- **Picture-in-Picture**: Auto-enters PiP when leaving app during video playback
- **Bookmarks**: Create/delete timestamp chapters, seek to any bookmark
- **Audio Track Selection**: Choose between multiple audio tracks
- **Subtitle Track Selection**: Choose subtitles or disable them
- **Continue Watching**: Videos with >0 and <95% progress shown on folder screen
- **Track Persistence**: Audio/subtitle track indices saved to `PlaybackHistory`

### Audio Player
- **Queue System**: Persistent queue saved to Room DB, restored on app restart
- **Shuffle**: When queue reaches end, auto-refill with shuffled full library
- **Repeat Modes**: Off → All → One (synchronized with Media3 controller)
- **Now Playing**: Full-screen player with album art, seekbar, controls
- **Mini Player**: Always visible, with previous/play/skip buttons + gradient progress bar
- **Favorites**: One-tap toggle adds/removes from "Favorites" playlist

### Album Management
- **Album Scanning**: `queryAlbums()` groups songs by `album_id` from MediaStore
- **View Modes**: Grid and List toggle
- **Sorting**: Name (A-Z/Z-A), Artist, Year, Song Count
- **Search**: Filter albums by name or artist
- **Play Album**: Play all songs or shuffle entire album

### Image Gallery
- **Image Scanning**: Queries `MediaStore.Images.Media` for all images
- **Grid Display**: Thumbnails with Coil `AsyncImage`
- **Fullscreen Viewer**: Swipeable fullscreen image viewing
- **Image Deletion**: Delete with confirmation dialog, uses `MediaStore.createDeleteRequest` on Android 11+

### Me Analytics Dashboard (MeScreen.kt — 88KB)
- **Real-time Playtime**: Today, this week, this month stats
- **Activity Streaks**: Consecutive days with >1 minute of listening
- **Current Obsession**: Most-played track in the last 7 days (via `PlayEvent` table)
- **All Time #1**: Overall most-played track (via `MediaAnalytics` play count)
- **Play Threshold**: A play only counts after 30s or 50% of track (min 5s)
- **Theme Switcher**: 3 curated themes (Amber Horizon, Digital Waves, Eco Frequency)
- **Dark/Light Toggle**: Persisted to SharedPreferences
- **Accessibility Guide**: In-app help screen

### Playlist System
- **Creation**: Audio and video playlists with duplicate name prevention
- **Default Playlists**: "Favorites" (audio), "Love" (video) auto-created on first launch
- **Management**: Create, rename, delete playlists
- **Song Management**: Add/remove songs from playlists
- **Legacy Migration**: Old JSON-based playlists automatically migrated to Room DB
- **Reactive Updates**: Room `Flow` → Repository → ViewModel → UI chain

---

## 10. Development Workflows

### Adding a New Screen

1. Create screen composable in `ui/screens/NewScreen.kt`
2. Add route to appropriate `NavHost` (`AudioNavigationHost` or `VideoNavigationHost`)
3. Add any new state to `MainViewModel` as `StateFlow`
4. Navigate with `navController.navigate("new_screen/{arg}")`

### Adding a New Room Entity

1. Define entity in `data/db/Entities.kt` with `@Entity` annotation
2. Add to `AppDatabase.kt` entities list
3. Add DAO methods in `MediaDao.kt`
4. Bump database version number
5. Add `@Provides` in `DatabaseModule.kt` if new DAO needed
6. Update ViewModel to use new DAO methods

### Adding a New Theme

1. Add theme configuration in `MainViewModel.kt`'s `themes` map:
   ```kotlin
   "new_id" to AppThemeConfig("new_id", Color(0xFFHEXCODE), "SUBTITLE", "Curated Title")
   ```
2. The Me Screen will automatically show the new theme option

### Media Deletion Workflow

1. Enable selection mode → `toggleSelectionMode(true)`
2. User selects items → `toggleSelection(id)` / `selectAll(ids)`
3. Trigger delete → `deleteSelectedMedia()`
4. Android 11+: System shows confirmation → `MediaStore.createDeleteRequest`
5. On success: `onDeleteSuccess(ids)` removes from state lists

---

*This guide is a living document. Update it as new features are added to FastBeat.*
