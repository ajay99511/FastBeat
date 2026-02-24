# 🎓 Senior Kotlin Android Developer Guide
## FastBeat Media Player - A Complete Learning Reference

> **Purpose:** This comprehensive documentation serves as your career-long reference for understanding modern Android development with Kotlin. Every concept explained here is derived directly from the FastBeat application you built, ensuring you can confidently articulate how things work in real-world scenarios.

---

## 📑 Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Kotlin Language Fundamentals](#2-kotlin-language-fundamentals)
3. [Jetpack Compose Deep Dive](#3-jetpack-compose-deep-dive)
4. [Dependency Injection with Hilt](#4-dependency-injection-with-hilt)
5. [Room Database Mastery](#5-room-database-mastery)
6. [Coroutines & Flow for Asynchronous Programming](#6-coroutines--flow-for-asynchronous-programming)
7. [Media3 & ExoPlayer Integration](#7-media3--exoplayer-integration)
8. [MVVM + Repository Pattern](#8-mvvm--repository-pattern)
9. [State Management](#9-state-management)
10. [Navigation in Compose](#10-navigation-in-compose)
11. [Image Loading with Coil](#11-image-loading-with-coil)
12. [Android Permissions & Lifecycle](#12-android-permissions--lifecycle)
13. [Video Player & Advanced Media Features](#13-video-player--advanced-media-features)
14. [Image Gallery & Thumbnail Caching](#14-image-gallery--thumbnail-caching)
15. [Media Deletion with Scoped Storage](#15-media-deletion-with-scoped-storage)
16. [Advanced Patterns & Best Practices](#16-advanced-patterns--best-practices)
17. [Interview-Ready Explanations](#17-interview-ready-explanations)

---

## 1. Architecture Overview

### What is Clean Architecture?

FastBeat follows a **layered architecture** that separates concerns into distinct layers:

```
┌─────────────────────────────────────────────────────────────┐
│                    UI LAYER (Presentation)                   │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  MainActivity.kt → MainScreen.kt → Feature Screens       │ │
│  │  (Jetpack Compose UI components)                         │ │
│  └─────────────────────────────────────────────────────────┘ │
│                              │                               │
│                              ▼                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  ViewModel Layer (MainViewModel)                         │ │
│  │  - Holds UI state as StateFlow                           │ │
│  │  - Exposes actions for UI                                │ │
│  │  - Orchestrates business logic                           │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    DATA LAYER                                │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Repository Layer (PlaylistRepository)                   │ │
│  │  - Single source of truth                                │ │
│  │  - Abstracts data sources                                │ │
│  └─────────────────────────────────────────────────────────┘ │
│                              │                               │
│                              ▼                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Data Sources                                            │ │
│  │  ├── Room Database (MediaDao, AppDatabase)               │ │
│  │  ├── MediaStore (ContentResolver queries)                │ │
│  │  └── SharedPreferences (Settings)                        │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Why This Architecture Matters

| Benefit | How FastBeat Achieves It |
|---------|-------------------------|
| **Testability** | ViewModel can be unit tested without UI. Repository can be mocked. |
| **Separation of Concerns** | UI only displays data; ViewModel handles logic; Repository manages data. |
| **Scalability** | Adding new features (like bookmarks) is isolated to specific layers. |
| **Maintainability** | Changes to data storage don't affect UI code. |

### File Structure in FastBeat

```
com.local.offlinemediaplayer/
├── MainActivity.kt              # Entry point, permissions, PiP, theme
├── MediaPlayerApp.kt            # @HiltAndroidApp Application class
├── data/
│   ├── ThumbnailManager.kt      # Video thumbnail generation + disk caching
│   ├── db/
│   │   ├── AppDatabase.kt       # Room database (v5, 8 entities)
│   │   ├── Entities.kt          # All Room entity data classes
│   │   └── MediaDao.kt          # Data Access Object (35 queries)
│   └── di/
│       └── DatabaseModule.kt    # Hilt module: DB + DAO + ThumbnailManager
├── model/
│   ├── Album.kt                 # Album domain model
│   ├── MediaFile.kt             # Core media model (19 fields)
│   ├── PlayerStates.kt          # AudioPlayerState, VideoPlayerState snapshots
│   ├── Playlist.kt              # Playlist domain model
│   └── VideoFolder.kt           # Folder grouping model
├── repository/
│   └── PlaylistRepository.kt    # Playlist CRUD + legacy JSON migration
├── service/
│   └── PlaybackService.kt       # Media3 MediaSessionService
├── ui/
│   ├── MainScreen.kt            # Main scaffold, 5 tabs, navigation
│   ├── common/
│   │   └── FormatUtils.kt       # Duration, size formatting
│   ├── components/
│   │   ├── Dialogs.kt           # Reusable dialog composables
│   │   ├── MediaPropertiesDialog.kt  # Media info dialog
│   │   ├── MiniPlayer.kt        # Global mini player bar
│   │   └── SearchComponents.kt  # Animated search bar
│   ├── navigation/
│   │   ├── AudioNavigationHost.kt  # Audio tab NavHost
│   │   └── VideoNavigationHost.kt  # Video tab NavHost
│   ├── screens/                 # 15 Feature Screens
│   │   ├── AccessibilityGuideScreen.kt
│   │   ├── AlbumDetailScreen.kt
│   │   ├── AlbumListScreen.kt (grid/list + sorting)
│   │   ├── AudioLibraryScreen.kt
│   │   ├── AudioListScreen.kt
│   │   ├── ImageListScreen.kt (gallery + viewer + deletion)
│   │   ├── MeScreen.kt (analytics dashboard)
│   │   ├── NowPlayingScreen.kt
│   │   ├── PermissionScreens.kt
│   │   ├── PlaylistDetailScreen.kt
│   │   ├── PlaylistListScreen.kt
│   │   ├── VideoFolderScreen.kt
│   │   ├── VideoListScreen.kt (multi-select + delete)
│   │   ├── VideoPlayerScreen.kt (gestures, PiP, bookmarks)
│   │   └── VideoPlaylistDetailScreen.kt
│   └── theme/
│       ├── Color.kt, Theme.kt, Type.kt
│       └── Headers/             # 5 per-tab header composables
└── viewmodel/
    └── MainViewModel.kt         # Central ViewModel (2063 lines, 123 functions)
```

---

## 2. Kotlin Language Fundamentals

### 2.1 Data Classes

**Definition:** Data classes automatically generate `equals()`, `hashCode()`, `toString()`, `copy()`, and component functions.

**FastBeat Example:**
```kotlin
// From model/MediaFile.kt
data class MediaFile(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String? = null,       // Nullable with default
    val duration: Long,
    val isVideo: Boolean,
    val isImage: Boolean = false,     // Default parameter
    val albumArtUri: Uri? = null,
    val albumId: Long = -1,
    val bucketId: String = "",        // Folder grouping
    val bucketName: String = "",
    val size: Long = 0,
    val resolution: String = "",
    val dateModified: Long = 0,       // For thumbnail cache invalidation
    val thumbnailPath: String? = null  // Cached video thumbnail path
)
```

**Key Benefits:**
- **Immutability:** Properties are `val` (read-only), making objects thread-safe
- **Copy pattern:** `mediaFile.copy(title = "New Title")` creates a new instance with one field changed
- **Destructuring:** `val (id, uri, title) = mediaFile` extracts values

### 2.2 Null Safety

**Kotlin eliminates NullPointerException through its type system:**

```kotlin
// From MainViewModel.kt - Null-safe property access
val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
//                                          ^^
//                          Elvis operator: returns right side if left is null

// Safe call operator
val albumId = currentMediaItem?.mediaId?.toLongOrNull()
//                            ^^
//              Only calls next method if not null

// Smart cast after null check
_currentTrack.value?.let { track ->
    // Inside this block, 'track' is guaranteed non-null
    savePlaybackState(track.id, position, track.duration, track.isVideo)
}
```

### 2.3 Extension Functions

**Add functionality to existing classes without inheritance:**

```kotlin
// From MainViewModel.kt - MediaFile extension
private fun MediaFile.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setArtworkUri(albumArtUri)
        .build()
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(metadata)
        .build()
}

// Usage: Clean, readable code
val mediaItems = mediaList.map { it.toMediaItem() }
```

### 2.4 Higher-Order Functions & Lambdas

**Functions that take or return other functions:**

```kotlin
// From MainViewModel.kt - Collection transformation with lambdas
val moviesList = _videoList.map { list ->
    list.filter { it.duration >= 3600000 }  // Lambda parameter: 'it'
}

// Multi-line lambda with explicit parameter
player.addListener(object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
        if (isPlaying) startPositionUpdates() else stopPositionUpdates()
    }
})

// Trailing lambda syntax
LaunchedEffect(selectedTab) {
    isSearchVisible = false
}
```

### 2.5 Sealed Classes and Enums

**Restricted class hierarchies for type-safe handling:**

```kotlin
// From MainViewModel.kt - Enums for finite state
enum class SortOption {
    TITLE_ASC, TITLE_DESC, DURATION_ASC, DURATION_DESC, DATE_ADDED_DESC
}

enum class ResizeMode {
    FIT, FILL, ZOOM
}

// Using enums with when (exhaustive checking)
val sortedList = when(sort) {
    SortOption.TITLE_ASC -> result.sortedBy { it.title }
    SortOption.TITLE_DESC -> result.sortedByDescending { it.title }
    SortOption.DURATION_ASC -> result.sortedBy { it.duration }
    SortOption.DURATION_DESC -> result.sortedByDescending { it.duration }
    SortOption.DATE_ADDED_DESC -> result.sortedByDescending { it.id }
}
// No 'else' needed because Kotlin ensures all cases are handled
```

### 2.6 Scope Functions (let, apply, run, with, also)

```kotlin
// 'let' - Execute block if non-null
_player.value?.let { exoPlayer ->
    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.prepare()
    exoPlayer.play()
}

// 'apply' - Configure and return the same object
val player = ExoPlayer.Builder(this)
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build(),
        true
    )
    .setHandleAudioBecomingNoisy(true)
    .build()

// 'run' - Execute on receiver and return result
mediaSession?.run {
    player.release()
    release()
    mediaSession = null
}

// 'also' - Side effects while keeping the original object
val cursor = contentResolver.query(...)?.also {
    Log.d("Debug", "Query returned ${it.count} rows")
}
```

---

## 3. Jetpack Compose Deep Dive

### 3.1 What is Compose?

Jetpack Compose is Android's modern **declarative UI toolkit**. Instead of imperatively saying "do this, then that," you describe **what** the UI should look like for a given state.

**Traditional Imperative UI:**
```kotlin
// XML + findViewById - AVOID
textView.text = "Hello"
button.setOnClickListener { textView.text = "Clicked" }
```

**Declarative Compose:**
```kotlin
@Composable
fun Greeting() {
    var text by remember { mutableStateOf("Hello") }
    Column {
        Text(text = text)
        Button(onClick = { text = "Clicked" }) {
            Text("Click Me")
        }
    }
}
```

### 3.2 Composable Functions

**From FastBeat - MainScreen.kt:**
```kotlin
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    // State hoisting - declarative state management
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentMedia by remember { mutableStateOf<MediaFile?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }

    // Side effects
    LaunchedEffect(selectedTab) {
        isSearchVisible = false  // Reset search when tab changes
    }

    // Scaffold provides app structure
    Scaffold(
        topBar = { /* Custom header */ },
        bottomBar = { 
            NavigationBar { /* Bottom navigation */ }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Content based on state
        }
    }
}
```

### 3.3 State Management in Compose

**Three Key Concepts:**

1. **remember** - Survives recomposition
2. **mutableStateOf** - Observable state
3. **collectAsStateWithLifecycle** - Converts Flow to Compose State

```kotlin
// Local UI state
var selectedTab by remember { mutableIntStateOf(0) }

// ViewModel state as Compose state
val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

// Derived state
val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
```

### 3.4 Modifiers - The Power of Compose

**Modifiers chain to describe layout and behavior:**

```kotlin
// From MiniPlayer.kt
Surface(
    modifier = modifier
        .fillMaxWidth()
        .height(80.dp)
        .clickable(onClick = onTap),
    color = Color(0xFF181818),
    tonalElevation = 0.dp
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)  // Dynamic width based on state
                    .background(gradientBrush)
            )
        }
    }
}
```

### 3.5 Side Effects

**When you need to do something that escapes the composable function:**

```kotlin
// LaunchedEffect - Run suspend function when key changes
LaunchedEffect(Unit) {
    if (!permissionsGranted) {
        permissionLauncher.launch(permissions.toTypedArray())
    } else {
        viewModel.scanMedia()
    }
}

// SideEffect - Run on every successful recomposition
val view = LocalView.current
if (!view.isInEditMode) {
    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = colorScheme.background.toArgb()
    }
}
```

### 3.6 Animation

**FastBeat uses sophisticated animations:**

```kotlin
// From MainScreen.kt - Animated content transitions
AnimatedContent(
    targetState = currentMedia != null && currentMedia!!.isVideo,
    transitionSpec = {
        if (targetState) {
            // Opening Video: Scale up and Fade In
            scaleIn(initialScale = 0.9f, animationSpec = tween(300)) + 
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        } else {
            // Closing Video: Fade Out
            fadeIn(tween(300)) togetherWith 
                scaleOut(targetScale = 0.9f, animationSpec = tween(300)) + 
                fadeOut(tween(300))
        }
    },
    label = "VideoPlayerTransition"
) { isVideoMode ->
    if (isVideoMode) {
        VideoPlayerScreen(...)
    } else {
        // Tab content
    }
}
```

### 3.7 Theming with CompositionLocal

**Custom theme propagation through the component tree:**

```kotlin
// From Theme.kt
data class AppThemeConfig(
    val id: String,
    val primaryColor: Color,
    val subtitle: String,
    val curatedTitle: String
)

val LocalAppTheme = staticCompositionLocalOf { DefaultTheme }

@Composable
fun OfflineMediaPlayerTheme(
    currentThemeConfig: AppThemeConfig? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val activeTheme = currentThemeConfig ?: DefaultTheme
    
    // Provide theme to entire subtree
    CompositionLocalProvider(LocalAppTheme provides activeTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Usage anywhere in the tree
val themeColor = LocalAppTheme.current.primaryColor
```

---

## 4. Dependency Injection with Hilt

### 4.1 What is Dependency Injection?

**Problem without DI:**
```kotlin
class MainViewModel : ViewModel() {
    // Hard-coded dependencies - DIFFICULT TO TEST
    private val database = AppDatabase.getInstance(application)
    private val dao = database.mediaDao()
    private val repository = PlaylistRepository(context, dao)
}
```

**Solution with Hilt:**
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val playlistRepository: PlaylistRepository,
    private val mediaDao: MediaDao,
    private val thumbnailManager: ThumbnailManager
) : AndroidViewModel(app) {
    // Dependencies injected - EASY TO TEST
}
```

### 4.2 Hilt Setup in FastBeat

**Step 1: Application Class**
```kotlin
// MediaPlayerApp.kt
@HiltAndroidApp
class MediaPlayerApp : Application()
```

**Step 2: Activity Annotation**
```kotlin
// MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
}
```

**Step 3: Module for Database**
```kotlin
// DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mediaplayer_db"
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideMediaDao(database: AppDatabase): MediaDao {
        return database.mediaDao()
    }

    @Provides
    @Singleton
    fun provideThumbnailManager(@ApplicationContext context: Context): ThumbnailManager {
        return ThumbnailManager(context)
    }
}
```

### 4.3 Key Hilt Concepts

| Annotation | Purpose | FastBeat Usage |
|------------|---------|----------------|
| `@HiltAndroidApp` | Application entry point | MediaPlayerApp.kt |
| `@AndroidEntryPoint` | Enable injection in Activity/Fragment | MainActivity.kt, PlaybackService.kt |
| `@HiltViewModel` | ViewModel injection support | MainViewModel.kt |
| `@Singleton` | Single instance across app | AppDatabase, ThumbnailManager |
| `@Inject constructor` | Constructor injection | PlaylistRepository, ThumbnailManager |
| `@Module` + `@Provides` | Custom object creation | DatabaseModule |

### 4.4 Hilt in Compose

```kotlin
// Get ViewModel with Hilt in Compose
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    // viewModel is automatically injected with all its dependencies
}

// Activity-scoped ViewModel
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
}
```

---

## 5. Room Database Mastery

### 5.1 What is Room?

Room is an **SQLite abstraction layer** that provides:
- Compile-time SQL verification
- Reduced boilerplate
- Seamless Flow/LiveData integration

### 5.2 Database Architecture in FastBeat

```kotlin
// AppDatabase.kt - The central database class
@Database(
    entities = [
        PlaybackHistory::class,
        MediaAnalytics::class,
        PlaylistEntity::class,
        PlaylistMediaCrossRef::class,
        BookmarkEntity::class,
        QueueItemEntity::class,
        DailyPlaytime::class,
        PlayEvent::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}
```

### 5.3 Entity Definitions

**Basic Entity:**
```kotlin
// Entities.kt
@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey val mediaId: Long,
    val position: Long,
    val duration: Long = 0,
    val timestamp: Long,
    val mediaType: String,              // "AUDIO" or "VIDEO"
    val audioTrackIndex: Int = -1,      // Persists selected audio track
    val subtitleTrackIndex: Int = -1    // Persists selected subtitle track
)
```

**Entity with Indexes (Performance Optimization):**
```kotlin
@Entity(
    tableName = "play_events",
    indices = [Index(value = ["mediaId"]), Index(value = ["timestamp"])]
)
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val timestamp: Long = System.currentTimeMillis()
)
```

**Entity with Foreign Key (Referential Integrity):**
```kotlin
@Entity(
    tableName = "playlist_media_cross_ref",
    primaryKeys = ["playlistId", "mediaId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["mediaId"])],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE  // Delete refs when playlist deleted
        )
    ]
)
data class PlaylistMediaCrossRef(
    val playlistId: String,
    val mediaId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
```

### 5.4 Data Access Object (DAO)

**From MediaDao.kt - Complete Examples:**

```kotlin
@Dao
interface MediaDao {
    // Simple Insert
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHistory(history: PlaybackHistory)

    // Simple Query
    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId")
    suspend fun getHistory(mediaId: Long): PlaybackHistory?

    // Complex Query with Calculation
    @Query("""
        SELECT * FROM playback_history 
        WHERE mediaType = 'VIDEO' 
        AND position > 0 
        AND (duration = 0 OR position < (duration * 0.95)) 
        ORDER BY timestamp DESC LIMIT 10
    """)
    fun getContinueWatching(): Flow<List<PlaybackHistory>>

    // Aggregation Query
    @Query("SELECT SUM(totalPlaytimeMs) FROM daily_playtime WHERE date >= :startDate AND date <= :endDate")
    fun getPlaytimeRange(startDate: Long, endDate: Long): Flow<Long?>

    // Update Query
    @Query("UPDATE media_analytics SET playCount = playCount + 1, lastPlayed = :timestamp WHERE mediaId = :mediaId")
    suspend fun incrementPlayCount(mediaId: Long, timestamp: Long)

    // Transaction for Atomic Operations
    @Transaction
    suspend fun replaceQueue(items: List<QueueItemEntity>) {
        clearQueue()
        insertQueueItems(items)
    }

    // Relation Query
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsWithRefs(): Flow<List<PlaylistWithRefs>>
}
```

### 5.5 Room Relations

**One-to-Many Relationship for Playlists:**

```kotlin
// Helper class for Room Relations
data class PlaylistWithRefs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val refs: List<PlaylistMediaCrossRef>
)
```

**Why Relations Matter:**
- Single query fetches playlist AND all its songs
- Room handles the JOIN automatically
- Changes to cross-ref table trigger Flow updates

### 5.6 Database Migrations

```kotlin
// Auto-migration (destructive for simplicity)
Room.databaseBuilder(context, AppDatabase::class.java, "mediaplayer_db")
    .fallbackToDestructiveMigration()  // Drops and recreates on schema change
    .build()

// Production migration (preserves data)
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS...")
    }
}
```

---

## 6. Coroutines & Flow for Asynchronous Programming

### 6.1 What Are Coroutines?

Coroutines are **lightweight threads** that enable:
- Non-blocking asynchronous code
- Structured concurrency
- Sequential-looking code that's actually async

### 6.2 Key Concepts in FastBeat

**Suspend Functions - Can pause without blocking:**
```kotlin
// MediaDao.kt - Suspend function for database operation
suspend fun saveHistory(history: PlaybackHistory)

// Usage in ViewModel
private fun savePlaybackState(mediaId: Long, position: Long, duration: Long, isVideo: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {  // Launch coroutine on IO thread
        mediaDao.saveHistory(
            PlaybackHistory(
                mediaId = mediaId,
                position = position,
                duration = duration,
                timestamp = System.currentTimeMillis(),
                mediaType = if (isVideo) "VIDEO" else "AUDIO"
            )
        )
    }
}
```

**Dispatchers - Where coroutines run:**

| Dispatcher | Use Case | FastBeat Usage |
|------------|----------|----------------|
| `Dispatchers.Main` | UI updates | Default in ViewModel |
| `Dispatchers.IO` | Database, network | `launch(Dispatchers.IO)` |
| `Dispatchers.Default` | CPU-intensive work | Heavy computations |

### 6.3 Flow - Reactive Streams

**Flow is a cold asynchronous stream that emits multiple values:**

```kotlin
// MediaDao.kt - Room returns Flow for reactive updates
@Query("SELECT * FROM playback_history ORDER BY timestamp DESC")
fun getContinueWatching(): Flow<List<PlaybackHistory>>

// MainViewModel.kt - Flow transformation
val continueWatchingList = combine(
    _videoList,
    mediaDao.getContinueWatching()
) { videos, historyItems ->
    historyItems.mapNotNull { history ->
        val video = videos.find { it.id == history.mediaId }
        if (video != null) video to history else null
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

### 6.4 StateFlow vs SharedFlow

**StateFlow - Always has a value, replays last:**
```kotlin
// MainViewModel.kt
private val _videoList = MutableStateFlow<List<MediaFile>>(emptyList())
val videoList = _videoList.asStateFlow()  // Read-only exposure

private val _isPlaying = MutableStateFlow(false)
val isPlaying = _isPlaying.asStateFlow()
```

**SharedFlow - No initial value, configurable replay:**
```kotlin
// For one-time events that shouldn't replay
private val _deleteIntentEvent = MutableSharedFlow<IntentSender>()
val deleteIntentEvent = _deleteIntentEvent.asSharedFlow()
```

### 6.5 Flow Operators

**combine - Merge multiple flows:**
```kotlin
val filteredAudioList = combine(
    _audioList,
    _searchQuery,
    _sortOption
) { list, query, sort ->
    var result = list
    if (query.isNotEmpty()) {
        result = result.filter { 
            it.title.contains(query, ignoreCase = true) 
        }
    }
    when(sort) {
        SortOption.TITLE_ASC -> result.sortedBy { it.title }
        // ... other cases
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

**flatMapLatest - Switch to new flow when source emits:**
```kotlin
val currentBookmarks = _currentTrack.flatMapLatest { track ->
    if (track != null) {
        mediaDao.getBookmarks(track.id)
    } else {
        flowOf(emptyList())
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

**stateIn - Convert cold Flow to hot StateFlow:**
```kotlin
val moviesList = _videoList.map { list ->
    list.filter { it.duration >= 3600000 }
}.stateIn(
    scope = viewModelScope,           // Lifecycle scope
    started = SharingStarted.WhileSubscribed(5000),  // Sharing strategy
    initialValue = emptyList()        // Initial value
)
```

### 6.6 Coroutine Context and Switching

```kotlin
private suspend fun calculateAnalytics(allMedia: List<MediaFile>): RealtimeAnalytics {
    return withContext(Dispatchers.IO) {  // Switch to IO for database work
        val today = getNormalizedToday()
        val todayMs = mediaDao.getPlaytimeForDay(today).firstOrNull() ?: 0L
        // ... more database operations
        
        RealtimeAnalytics(...)  // Return result (automatically switches back)
    }
}
```

---

## 7. Media3 & ExoPlayer Integration

### 7.1 What is Media3?

Media3 is Android's modern media library that provides:
- **ExoPlayer** - Powerful media player
- **MediaSession** - System media controls integration
- **Background playback** - Service for continuous playback

### 7.2 PlaybackService Architecture

```kotlin
// PlaybackService.kt
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
                    .build(),
                true  // handleAudioFocus = true
            )
            .setHandleAudioBecomingNoisy(true)  // Pause when headphones disconnected
            .setWakeMode(C.WAKE_MODE_LOCAL)     // Keep CPU awake during playback
            .build()

        // Notification tap opens Now Playing screen
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("open_player", true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
```

### 7.3 MediaController in ViewModel

```kotlin
// MainViewModel.kt - Connecting to the service
private fun initializeMediaController() {
    val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
    controllerFuture = MediaController.Builder(app, sessionToken).buildAsync()
    controllerFuture?.addListener({
        try {
            val controller = controllerFuture?.get()
            _player.value = controller
            setupPlayerListener(controller)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }, MoreExecutors.directExecutor())
}
```

### 7.4 Player Listener for State Synchronization

```kotlin
private fun setupPlayerListener(controller: MediaController?) {
    controller?.addListener(object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _duration.value = controller.duration.coerceAtLeast(0L)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrentTrackFromPlayer(controller)
        }
    })
}
```

### 7.5 Building MediaItems

```kotlin
private fun MediaFile.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setArtworkUri(albumArtUri)
        .build()
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())  // Important for tracking
        .setMediaMetadata(metadata)
        .build()
}

// Setting a queue
fun setQueue(mediaList: List<MediaFile>, startIndex: Int, shuffle: Boolean, startPosition: Long = 0L) {
    _player.value?.let { controller ->
        val mediaItems = mediaList.map { it.toMediaItem() }
        controller.setMediaItems(mediaItems, startIndex, startPosition)
        controller.shuffleModeEnabled = shuffle
        controller.prepare()
        controller.play()
    }
}
```

---

## 8. MVVM + Repository Pattern

### 8.1 Why MVVM?

**Model-View-ViewModel separates:**
- **Model:** Data and business logic (Repository, Database)
- **View:** UI (Composables)
- **ViewModel:** UI state and logic (Survives configuration changes)

### 8.2 ViewModel in FastBeat

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val playlistRepository: PlaylistRepository,
    private val mediaDao: MediaDao
) : AndroidViewModel(app) {

    // STATE - Observable by UI
    private val _videoList = MutableStateFlow<List<MediaFile>>(emptyList())
    val videoList = _videoList.asStateFlow()

    private val _currentTrack = MutableStateFlow<MediaFile?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    // DERIVED STATE - Computed from other state
    val moviesList = _videoList.map { list ->
        list.filter { it.duration >= 3600000 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ACTIONS - Called by UI
    fun playMedia(media: MediaFile) {
        if (media.isVideo) {
            playVideo(media)
        } else if (!media.isImage) {
            // ... audio playback logic
        }
    }

    fun togglePlayPause() {
        _player.value?.let { 
            if (it.isPlaying) it.pause() else it.play() 
        }
    }
}
```

### 8.3 Repository Pattern

**Single source of truth for data operations:**

```kotlin
@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao
) {
    // Expose data as Flow
    val playlistsFlow: Flow<List<Playlist>> = mediaDao.getAllPlaylistsWithRefs().map { playlistWithRefs ->
        playlistWithRefs.map { item ->
            Playlist(
                id = item.playlist.id,
                name = item.playlist.name,
                mediaIds = item.refs.sortedBy { it.addedAt }.map { it.mediaId },
                createdAt = item.playlist.createdAt,
                isVideo = item.playlist.isVideo
            )
        }
    }

    // Write operations
    suspend fun createPlaylist(name: String, isVideo: Boolean) {
        if (mediaDao.getPlaylistCount(name, isVideo) > 0) return  // Prevent duplicates
        
        mediaDao.insertPlaylist(
            PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis(),
                isVideo = isVideo
            )
        )
    }

    suspend fun addSongToPlaylist(playlistId: String, mediaId: Long) {
        mediaDao.addMediaToPlaylist(
            PlaylistMediaCrossRef(playlistId = playlistId, mediaId = mediaId)
        )
    }
}
```

### 8.4 Data Flow Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│  UI (Compose)                                                    │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ val playlists by viewModel.playlists.collectAsState()       ││
│  │ Button(onClick = { viewModel.createPlaylist("New") })       ││
│  └──────────────────────────────────────────────────────────────┘│
└────────────────────────────────│─────────────────────────────────┘
                                 │ Calls action
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│  ViewModel                                                       │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ val playlists = playlistRepository.playlistsFlow.stateIn()  ││
│  │                                                              ││
│  │ fun createPlaylist(name: String) {                          ││
│  │     viewModelScope.launch(Dispatchers.IO) {                 ││
│  │         playlistRepository.createPlaylist(name, false)      ││
│  │     }                                                       ││
│  │ }                                                           ││
│  └──────────────────────────────────────────────────────────────┘│
└────────────────────────────────│─────────────────────────────────┘
                                 │ Delegates to repository
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│  Repository                                                      │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ suspend fun createPlaylist(name: String, isVideo: Boolean)  ││
│  │     mediaDao.insertPlaylist(PlaylistEntity(...))            ││
│  │ }                                                           ││
│  └──────────────────────────────────────────────────────────────┘│
└────────────────────────────────│─────────────────────────────────┘
                                 │ Writes to database
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│  Room Database                                                   │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ INSERT INTO playlists ...                                   ││
│  │ → Triggers Flow emission → Repository → ViewModel → UI     ││
│  └──────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

---

## 9. State Management

### 9.1 Types of State in FastBeat

| State Type | Storage | Example |
|------------|---------|---------|
| **UI State** | Compose remember | `selectedTab`, `isSearchVisible` |
| **App State** | ViewModel StateFlow | `currentTrack`, `isPlaying`, `playlists` |
| **Persistent State** | SharedPreferences | Theme preference, last queue index |
| **Database State** | Room | Playback history, analytics, playlists |

### 9.2 State Hoisting

**Moving state up to share between composables:**

```kotlin
// MainScreen.kt - State hoisted to parent
@Composable
fun MediaPlayerAppContent(viewModel: MainViewModel) {
    // State lives here
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentMedia by remember { mutableStateOf<MediaFile?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }

    // Passed down to children
    VideoNavigationHost(
        viewModel = viewModel,
        onVideoClick = { file ->
            currentMedia = file
            viewModel.playMedia(file)
        },
        isSearchVisible = isSearchVisible  // Passed as parameter
    )
}
```

### 9.3 State Preservation Across Scenarios

**Audio/Video Switching:**
```kotlin
data class AudioPlayerState(
    val queue: List<MediaFile>,
    val currentIndex: Int,
    val position: Long,
    val isPlaying: Boolean,
    val isShuffleEnabled: Boolean,
    val repeatMode: Int
)

private var savedAudioState: AudioPlayerState? = null

private fun playVideo(media: MediaFile) {
    // Save audio state before playing video
    if (_currentQueue.value.isNotEmpty() && _currentTrack.value?.isVideo != true) {
        savedAudioState = AudioPlayerState(
            queue = _currentQueue.value,
            currentIndex = _currentIndex.value ?: 0,
            position = _currentPosition.value,
            isPlaying = _isPlaying.value,
            isShuffleEnabled = _isShuffleEnabled.value,
            repeatMode = _repeatMode.value
        )
    }
    // ... play video
}

fun closeVideo() {
    // Restore audio state when video closes
    restoreAudioSession()
}
```

### 9.4 Persistent Queue

```kotlin
// Saving queue to database
private fun persistQueue(queue: List<MediaFile>) {
    viewModelScope.launch(Dispatchers.IO) {
        val entities = queue.mapIndexed { index, media ->
            QueueItemEntity(media.id, index)
        }
        mediaDao.replaceQueue(entities)
    }
}

// Saving index to SharedPreferences
private fun persistQueueIndex(index: Int) {
    sharedPrefs.edit().putInt("last_queue_index", index).apply()
}

// Restoring on app launch
private suspend fun restoreQueue(allMedia: List<MediaFile>) {
    val savedQueueItems = mediaDao.getSavedQueue()
    if (savedQueueItems.isNotEmpty()) {
        val restoredQueue = savedQueueItems.mapNotNull { item ->
            allMedia.find { it.id == item.mediaId }
        }
        // ... restore to player
    }
}
```

---

## 10. Navigation in Compose

### 10.1 Navigation Setup

```kotlin
// VideoNavigationHost.kt
@Composable
fun VideoNavigationHost(
    viewModel: MainViewModel,
    navController: NavHostController,
    onVideoClick: (MediaFile) -> Unit,
    isSearchVisible: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = "video_folders",
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
        }
    ) {
        // Define routes
        composable("video_folders") {
            VideoFolderScreen(
                onFolderClick = { folderId ->
                    navController.navigate("video_list/$folderId")
                }
            )
        }

        composable("video_list/{bucketId}") { backStackEntry ->
            val bucketId = backStackEntry.arguments?.getString("bucketId") ?: ""
            VideoListScreen(title = bucketId)
        }
    }
}
```

### 10.2 Passing Arguments

```kotlin
// Navigation with arguments
navController.navigate("video_playlist_detail/$playlistId")

// Receiving arguments
composable("video_playlist_detail/{playlistId}") { backStackEntry ->
    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
    VideoPlaylistDetailScreen(playlistId = playlistId)
}
```

### 10.3 Observing Navigation State

```kotlin
val audioNavBackStackEntry by audioNavController.currentBackStackEntryAsState()
val currentAudioRoute = audioNavBackStackEntry?.destination?.route

// Conditional UI based on current route
val isAudioDetailScreen = currentAudioRoute != "audio_library"
```

---

## 11. Image Loading with Coil

### 11.1 Basic Usage

```kotlin
// MiniPlayer.kt
AsyncImage(
    model = track.albumArtUri ?: "android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground",
    contentDescription = track.title,
    modifier = Modifier.fillMaxSize().background(Color.DarkGray),
    contentScale = ContentScale.Crop
)
```

### 11.2 Why Coil?

| Feature | Benefit |
|---------|---------|
| **Built for Compose** | AsyncImage composable |
| **Kotlin-first** | Coroutines, extension functions |
| **Memory efficient** | Automatic caching, bitmap pooling |
| **Lightweight** | ~2000 methods |

---

## 12. Android Permissions & Lifecycle

### 12.1 Permission Handling

```kotlin
// MainScreen.kt
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current

    // Define permissions based on API level
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var permissionsGranted by remember { mutableStateOf(/* check initial state */) }

    // Launcher for permission request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        permissionsGranted = permissionsMap.values.all { it }
        if (permissionsGranted) viewModel.scanMedia()
    }

    // Request on first launch
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
```

### 12.2 Lifecycle-Aware Collection

```kotlin
// Compose lifecycle-aware state collection
val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
//                                         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// Automatically stops collection when lifecycle is not at least STARTED
// Prevents updates when app is in background
```

### 12.3 MediaStore Query

```kotlin
private fun queryMedia(isVideo: Boolean): List<MediaFile> {
    val collection = if (Build.VERSION.SDK_INT >= 29) {
        if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    app.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val contentUri = ContentUris.withAppendedId(collection, id)
            mediaList.add(MediaFile(id, contentUri, ...))
        }
    }
    return mediaList
}
```

---

## 13. Video Player & Advanced Media Features

### 13.1 Picture-in-Picture (PiP)

**Allows video playback in a floating window while using other apps:**

```kotlin
// Manifest Configuration
<activity
    android:supportsPictureInPicture="true"
    android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation" />

// ViewModel logic
fun shouldEnterPipMode(): Boolean {
    return _isVideoPlayerVisible.value && _isPlaying.value
}

fun setPipMode(isPip: Boolean) {
    _isInPipMode.value = isPip
}

// MainActivity responds to home button
override fun onUserLeaveHint() {
    if (viewModel.shouldEnterPipMode()) {
        enterPictureInPictureMode(pipParams)
    }
}
```

### 13.2 Audio/Subtitle Track Selection

```kotlin
// Data class for track info
data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean
)

// Get available audio tracks
fun getAudioTracks(): List<TrackInfo> {
    val tracks = _player.value?.currentTracks ?: return emptyList()
    // Iterate groups, filter C.TRACK_TYPE_AUDIO, build TrackInfo list
}

// Select a track using TrackSelectionOverride
fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
    _player.value?.let { player ->
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(
                    player.currentTracks.groups[groupIndex].mediaTrackGroup,
                    listOf(trackIndex)
                )
            )
            .build()
    }
}
```

### 13.3 Video Bookmarks

```kotlin
// Save a timestamp bookmark
fun addBookmark(timestamp: Long, label: String) {
    _currentTrack.value?.let { track ->
        viewModelScope.launch(Dispatchers.IO) {
            mediaDao.addBookmark(BookmarkEntity(
                mediaId = track.id,
                timestamp = timestamp,
                label = label
            ))
        }
    }
}

// Observed reactively via flatMapLatest
val currentBookmarks = _currentTrack.flatMapLatest { track ->
    if (track != null) mediaDao.getBookmarks(track.id)
    else flowOf(emptyList())
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

### 13.4 Audio/Video State Preservation

**Saves audio session before playing video, restores when video closes:**

```kotlin
data class AudioPlayerState(
    val queue: List<MediaFile>,
    val currentIndex: Int,
    val position: Long,
    val isPlaying: Boolean,
    val isShuffleEnabled: Boolean,
    val repeatMode: Int
)

private var savedAudioState: AudioPlayerState? = null

private fun playVideo(media: MediaFile) {
    // Save current audio state before switching to video
    if (_currentQueue.value.isNotEmpty() && _currentTrack.value?.isVideo != true) {
        savedAudioState = AudioPlayerState(...)
    }
    // ... prepare and play video
}

fun closeVideo() {
    // Save video position, then restore audio session
    restoreAudioSession()
}
```

---

## 14. Image Gallery & Thumbnail Caching

### 14.1 Image Scanning

**Uses MediaStore to query all images on device:**

```kotlin
private fun queryImages(): List<MediaFile> {
    val list = mutableListOf<MediaFile>()
    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_MODIFIED
    )
    app.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(collection, id)
            list.add(MediaFile(id, uri, name, duration = 0, isVideo = false, isImage = true))
        }
    }
    return list
}
```

### 14.2 ThumbnailManager (Video Thumbnail Caching)

**Generates and caches video thumbnails on disk with Flow-based progressive loading:**

```kotlin
@Singleton
class ThumbnailManager @Inject constructor(private val context: Context) {
    private val cacheDir: File by lazy {
        File(context.filesDir, "video_thumbnails").also { it.mkdirs() }
    }

    // Cache key invalidates when video file changes
    private fun cacheKey(video: MediaFile): String =
        "thumb_${video.id}_${video.size}_${video.dateModified}.jpg"

    // Emits (mediaId, path) as each thumbnail is generated
    fun generateThumbnails(videos: List<MediaFile>): Flow<Pair<Long, String>> = flow {
        for (video in videos) {
            yield()  // Cooperative cancellation
            val cached = File(cacheDir, cacheKey(video))
            if (cached.exists()) { emit(video.id to cached.absolutePath); continue }
            extractThumbnail(video, cached)?.let { emit(video.id to it) }
        }
    }.flowOn(Dispatchers.IO)

    // Removes orphaned thumbnails after media scan
    fun cleanStaleThumbnails(currentVideos: List<MediaFile>) { ... }
}
```

**Key Design Decisions:**
- **Cache Key**: Uses `mediaId + size + dateModified` to auto-invalidate when video changes
- **Extraction Strategy**: Tries embedded cover art first, then extracts frame at ~10% duration
- **Scaling**: Max 360px wide, 75% JPEG quality for minimal disk usage
- **Flow API**: Progressive emission allows UI to show thumbnails as they're generated

---

## 15. Media Deletion with Scoped Storage

### 15.1 Android 11+ Scoped Storage Deletion

**Uses `MediaStore.createDeleteRequest` for system-managed confirmation:**

```kotlin
fun deleteSelectedMedia() {
    viewModelScope.launch(Dispatchers.IO) {
        val uris = _selectedIds.value.mapNotNull { id ->
            _videoList.value.find { it.id == id }?.uri
        }
        if (uris.isEmpty()) return@launch

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: System shows confirmation dialog
            val pendingIntent = MediaStore.createDeleteRequest(
                app.contentResolver, uris
            )
            _deleteIntentEvent.emit(pendingIntent.intentSender)
        } else {
            // Android 10-: Direct deletion
            uris.forEach { app.contentResolver.delete(it, null, null) }
            onDeleteSuccess()
        }
    }
}
```

### 15.2 SharedFlow for System Intent Events

```kotlin
// One-time event (no replay, no initial value)
private val _deleteIntentEvent = MutableSharedFlow<IntentSender>()
val deleteIntentEvent = _deleteIntentEvent.asSharedFlow()

// Collected in Composable
LaunchedEffect(Unit) {
    viewModel.deleteIntentEvent.collect { intentSender ->
        deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
    }
}
```

### 15.3 Post-Deletion State Update

```kotlin
fun onDeleteSuccess(ids: List<Long>) {
    _videoList.value = _videoList.value.filterNot { it.id in ids }
    _imageList.value = _imageList.value.filterNot { it.id in ids }
    _selectedIds.value = emptySet()
    _isSelectionMode.value = false
}
```

---

## 16. Advanced Patterns & Best Practices

### 16.1 Heartbeat Pattern for Realtime Updates

```kotlin
// Position update loop with heartbeat
private fun startPositionUpdates() {
    positionUpdateJob = viewModelScope.launch {
        while (isActive) {
            _player.value?.let { player ->
                _currentPosition.value = player.currentPosition
                _duration.value = player.duration.coerceAtLeast(0L)

                // Periodic save every 5 seconds (10 ticks * 500ms)
                if (saveCounter % 10 == 0) {
                    savePlaybackState(...)
                }

                // Analytics accumulation
                if (_isPlaying.value) {
                    accumulatedPlaytime += 500L
                }
            }
            delay(500)  // Update every 500ms
        }
    }
}
```

### 16.2 Analytics with Threshold Validation

```kotlin
// Only count as "played" if user listened for 30+ seconds or 50% of track
if (!hasLoggedCurrentTrack && _isPlaying.value) {
    currentTrackPlaytimeAccumulator += 500L    // Accumulate listen time
    
    val threshold = if (duration > 0) min(30000L, duration / 2) else 30000L
    val safeThreshold = max(5000L, threshold)  // Minimum 5s even for short clips
    
    if (currentTrackPlaytimeAccumulator >= safeThreshold) {
        recordPlay(track.id)
        hasLoggedCurrentTrack = true  // Prevent duplicate counts
    }
}
```

### 16.3 Legacy Data Migration

```kotlin
// PlaylistRepository.kt - Migrate from JSON to Room
suspend fun migrateLegacyData() {
    withContext(Dispatchers.IO) {
        if (legacyFile.exists()) {
            try {
                val json = legacyFile.readText()
                val legacyPlaylists: List<LegacyPlaylist>? = gson.fromJson(json, type)
                
                legacyPlaylists?.forEach { playlist ->
                    mediaDao.insertPlaylist(PlaylistEntity(...))
                    // ... migrate songs
                }
                
                legacyFile.delete()  // Remove old file after successful migration
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
```

### 16.4 Defensive Programming

```kotlin
// Null safety with sensible defaults
val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
val duration = player.duration.coerceAtLeast(0L)  // Never negative

// Safe index access
val safeIndex = savedIndex.coerceIn(0, restoredQueue.size - 1)

// Safe percentage calculation
val progress = if (duration > 0) position / duration.toFloat() else 0f
```

---

## 17. Interview-Ready Explanations

### How would you explain the architecture?

> "FastBeat uses **MVVM with Repository pattern**. The UI layer is built entirely with **Jetpack Compose** and observes **StateFlow** from the ViewModel. The ViewModel doesn't know about UI implementation—it just exposes state and actions. The Repository layer abstracts data sources, currently Room database and MediaStore. **Hilt** handles dependency injection, making everything testable and modular."

### How does state management work?

> "We use **StateFlow** for reactive state that the UI observes. Changes automatically trigger recomposition. For UI-only state like 'selected tab', we use Compose's **remember + mutableStateOf**. Persistent state goes to **Room** (playlists, history) or **SharedPreferences** (theme preferences). This multi-tier approach ensures each type of state survives its appropriate lifecycle."

### How does the audio background playback work?

> "We use **Media3's MediaSessionService**. The **PlaybackService** creates an ExoPlayer and wraps it in a MediaSession. The ViewModel connects via **MediaController** which survives across the app lifecycle. The service runs as a foreground service with proper audio attributes for music content, and handles audio focus automatically."

### How do you handle configuration changes?

> "**ViewModels survive configuration changes** by design. The UI state in Compose is either scoped to ViewModel (like currentTrack) or uses **rememberSaveable** for values that need to survive process death. The Repository is a **Singleton** provided by Hilt, so database connections persist."

### How does the Room database handle relationships?

> "We use **@Relation** annotations with a cross-reference table for the many-to-many relationship between playlists and songs. Room's **@Transaction** annotation ensures queries that fetch related data are atomic. We observe changes via **Flow** which automatically emits when any related table changes."

### What's your approach to testing this architecture?

> "The architecture is highly testable. ViewModels can be unit tested by injecting **fake repositories**. Repositories can be tested with **in-memory Room databases**. Compose UI can be tested with **Compose Testing** library. Hilt's **@TestInstallIn** allows swapping dependencies for tests."

---

## 🎯 Quick Reference Card

### Kotlin Essentials
```kotlin
data class X(...)             // Automatic equals, hashCode, copy
val x: String? = null         // Nullable
x?.let { /* safe block */ }   // Null-safe execution
x ?: "default"                // Elvis operator
when(x) { ... }               // Exhaustive pattern matching
fun Type.ext() { }            // Extension function
list.map { }.filter { }       // Lambda chains
```

### Compose Patterns
```kotlin
@Composable
fun Screen(vm: ViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var local by remember { mutableStateOf(x) }
    
    LaunchedEffect(key) { /* side effect */ }
    
    Scaffold(topBar = {}, bottomBar = {}) { padding ->
        Content(Modifier.padding(padding))
    }
}
```

### Flow Patterns
```kotlin
private val _state = MutableStateFlow(initial)
val state = _state.asStateFlow()

val derived = combine(flow1, flow2) { a, b -> compute(a, b) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), default)

viewModelScope.launch(Dispatchers.IO) { dao.insert(item) }
```

### Room Patterns
```kotlin
@Entity @PrimaryKey @ForeignKey @Index
@Dao @Query @Insert @Update @Delete @Transaction
@Database(entities = [...], version = N)
```

---

## 📚 Recommended Next Steps

1. **Study the actual code** - This documentation references real files in your project
2. **Add unit tests** - Practice testing ViewModel and Repository layers
3. **Try modifications** - Add new features to reinforce understanding
4. **Review Android documentation** - Official Compose, Room, Hilt guides
5. **Practice explaining** - Verbalize these concepts as interview preparation

---

*This guide is your career companion. Every pattern, every concept, every example comes directly from the FastBeat application you built. You can confidently discuss any of these topics because you've implemented them yourself.*
