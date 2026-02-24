# 🚀 FastBeat Code Patterns Cheat Sheet
## Quick Reference for Senior Kotlin Android Developers

> This companion document provides copy-paste-ready patterns from your FastBeat application.

---

## Table of Contents
1. [Kotlin Patterns](#1-kotlin-patterns)
2. [Jetpack Compose Patterns](#2-jetpack-compose-patterns)
3. [Hilt Dependency Injection](#3-hilt-dependency-injection)
4. [Room Database Patterns](#4-room-database-patterns)
5. [Coroutines & Flow Patterns](#5-coroutines--flow-patterns)
6. [Media3 Patterns](#6-media3-patterns)
7. [Navigation Patterns](#7-navigation-patterns)
8. [Video Player Patterns](#8-video-player-patterns)
9. [Thumbnail & Image Patterns](#9-thumbnail--image-patterns)
10. [Media Deletion Patterns](#10-media-deletion-patterns)
11. [Common Recipes](#11-common-recipes)

---

## 1. Kotlin Patterns

### Data Class with Defaults
```kotlin
data class MediaFile(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String? = null,       // Nullable with default
    val duration: Long,
    val isVideo: Boolean,
    val isImage: Boolean = false,     // Optional with default
    val albumArtUri: Uri? = null,
    val albumId: Long = -1,
    val bucketId: String = "",        // Folder grouping
    val bucketName: String = "",
    val size: Long = 0,
    val resolution: String = "",
    val dateModified: Long = 0,       // For thumbnail cache invalidation
    val thumbnailPath: String? = null  // Cached video thumbnail path
)

// Usage
val file = MediaFile(id = 1, uri = uri, title = "Song", duration = 180000, isVideo = false)
val copy = file.copy(title = "New Title")
```

### Null Safety Patterns
```kotlin
// Elvis operator
val artist = cursor.getString(col) ?: "Unknown Artist"

// Safe call with let
_player.value?.let { exoPlayer ->
    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.prepare()
    exoPlayer.play()
}

// Safe call chain
val id = currentMediaItem?.mediaId?.toLongOrNull()

// Coerce for safe ranges
val duration = player.duration.coerceAtLeast(0L)
val safeIndex = savedIndex.coerceIn(0, restoredQueue.size - 1)
```

### Extension Function
```kotlin
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

// Usage
val items = mediaList.map { it.toMediaItem() }
```

### Enum with When Expression
```kotlin
enum class SortOption {
    TITLE_ASC, TITLE_DESC, DURATION_ASC, DURATION_DESC, DATE_ADDED_DESC
}

enum class AlbumSortOption {
    NAME_ASC, NAME_DESC, ARTIST_ASC, YEAR_DESC, SONG_COUNT_DESC
}

enum class ResizeMode {
    FIT, FILL, ZOOM
}

fun sort(list: List<MediaFile>, option: SortOption): List<MediaFile> = when(option) {
    SortOption.TITLE_ASC -> list.sortedBy { it.title }
    SortOption.TITLE_DESC -> list.sortedByDescending { it.title }
    SortOption.DURATION_ASC -> list.sortedBy { it.duration }
    SortOption.DURATION_DESC -> list.sortedByDescending { it.duration }
    SortOption.DATE_ADDED_DESC -> list.sortedByDescending { it.id }
}
// No 'else' needed — Kotlin ensures all cases are handled
```

### Scope Functions
```kotlin
// let - Execute block if non-null, return result
_currentTrack.value?.let { track ->
    savePlaybackState(track.id, position, track.duration, track.isVideo)
}

// apply - Configure object, return self
val player = ExoPlayer.Builder(this)
    .setAudioAttributes(audioAttributes, true)
    .setHandleAudioBecomingNoisy(true)
    .setWakeMode(C.WAKE_MODE_LOCAL)
    .build()

// run - Execute on receiver, return result
mediaSession?.run {
    player.release()
    release()
    mediaSession = null
}

// also - Side effects, return original
val cursor = query()?.also { Log.d("Count", "${it.count}") }
```

---

## 2. Jetpack Compose Patterns

### Basic Composable Structure
```kotlin
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    // Observe ViewModel state
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    // Local UI state
    var selectedTab by remember { mutableIntStateOf(0) }
    var isSearchVisible by remember { mutableStateOf(false) }

    // Side effects
    LaunchedEffect(selectedTab) {
        isSearchVisible = false  // Reset search when tab changes
    }

    // UI
    Scaffold(
        topBar = { /* Header */ },
        bottomBar = { NavigationBar { /* tabs */ } }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Content based on state
        }
    }
}
```

### Five-Tab Navigation with AnimatedContent
```kotlin
@Composable
fun MediaPlayerAppContent(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Video", "Audio", "Albums", "Images", "Me").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tabIcons[index], label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "TabTransition"
        ) { tab ->
            when (tab) {
                0 -> VideoNavigationHost(viewModel)
                1 -> AudioNavigationHost(viewModel)
                2 -> AlbumListScreen(viewModel)
                3 -> ImageListScreen(viewModel)
                4 -> MeScreen(viewModel)
            }
        }
    }
}
```

### Custom Theme with CompositionLocal
```kotlin
// Define config
data class AppThemeConfig(
    val id: String,
    val primaryColor: Color,
    val subtitle: String,
    val curatedTitle: String
)

// Default fallback
val DefaultTheme = AppThemeConfig(
    id = "orange",
    primaryColor = Color(0xFFFF5500),
    subtitle = "HIDDEN LEAF MEDIA SCROLL",
    curatedTitle = "Hokage Selections"
)

// Create provider
val LocalAppTheme = staticCompositionLocalOf { DefaultTheme }

// Theme wrapper
@Composable
fun OfflineMediaPlayerTheme(
    currentThemeConfig: AppThemeConfig? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val activeTheme = currentThemeConfig ?: DefaultTheme
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = activeTheme.primaryColor,
            background = Color(0xFF0B0B0F),
            surface = Color(0xFF1E1E24)
        )
    } else {
        lightColorScheme(
            primary = activeTheme.primaryColor,
            background = Color(0xFFF2F2F7),
            surface = Color(0xFFFFFFFF)
        )
    }

    CompositionLocalProvider(LocalAppTheme provides activeTheme) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

// Usage anywhere in tree
val themeColor = LocalAppTheme.current.primaryColor
```

### State Hoisting Pattern
```kotlin
// Parent hoists state
@Composable
fun ParentScreen() {
    var searchQuery by remember { mutableStateOf("") }

    ChildScreen(
        searchQuery = searchQuery,
        onSearchChange = { searchQuery = it }
    )
}

// Child receives state and callback
@Composable
fun ChildScreen(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    TextField(value = searchQuery, onValueChange = onSearchChange)
}
```

### Gradient Background
```kotlin
val gradientBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFF5500),
        Color(0xFF9656CE),
        Color(0xFFE44CD8)
    )
)

Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .background(gradientBrush)
)
```

---

## 3. Hilt Dependency Injection

### Application Setup
```kotlin
// Application class
@HiltAndroidApp
class MediaPlayerApp : Application()

// Manifest
<application android:name=".MediaPlayerApp" ...>
```

### Activity Setup
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

            OfflineMediaPlayerTheme(
                currentThemeConfig = currentTheme,
                darkTheme = isDarkTheme
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
```

### ViewModel with Injection
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val playlistRepository: PlaylistRepository,
    private val mediaDao: MediaDao,
    private val thumbnailManager: ThumbnailManager
) : AndroidViewModel(app) {
    // Use injected dependencies
}
```

### Module for Database + ThumbnailManager
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mediaplayer_db"
        ).fallbackToDestructiveMigration(false).build()
    }

    @Provides
    fun provideDao(database: AppDatabase): MediaDao {
        return database.mediaDao()
    }

    @Provides
    @Singleton
    fun provideThumbnailManager(@ApplicationContext context: Context): ThumbnailManager {
        return ThumbnailManager(context)
    }
}
```

### Repository with Injection
```kotlin
@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao
) {
    val playlistsFlow: Flow<List<Playlist>> = mediaDao.getAllPlaylistsWithRefs().map { ... }

    suspend fun createPlaylist(name: String, isVideo: Boolean) { ... }
    suspend fun addSongToPlaylist(playlistId: String, mediaId: Long) { ... }
    suspend fun migrateLegacyData() { ... }
    suspend fun ensureDefaultPlaylists() { ... }
}
```

### Compose Integration
```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    // viewModel is injected automatically with all dependencies
}
```

---

## 4. Room Database Patterns

### Entity Definitions
```kotlin
// Basic entity with persistence for audio/subtitle track selection
@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey val mediaId: Long,
    val position: Long,
    val duration: Long = 0,
    val timestamp: Long,
    val mediaType: String,             // "AUDIO" or "VIDEO"
    val audioTrackIndex: Int = -1,     // Track persistence
    val subtitleTrackIndex: Int = -1   // Subtitle persistence
)

// Entity with indexes for performance
@Entity(
    tableName = "play_events",
    indices = [Index(value = ["mediaId"]), Index(value = ["timestamp"])]
)
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val timestamp: Long = System.currentTimeMillis()
)

// Cross-reference with foreign key
@Entity(
    tableName = "playlist_media_cross_ref",
    primaryKeys = ["playlistId", "mediaId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["mediaId"])],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistMediaCrossRef(
    val playlistId: String,
    val mediaId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

// Analytics entities
@Entity(tableName = "media_analytics")
data class MediaAnalytics(
    @PrimaryKey val mediaId: Long,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0
)

@Entity(tableName = "daily_playtime")
data class DailyPlaytime(
    @PrimaryKey val date: Long,
    val totalPlaytimeMs: Long
)

// Bookmarks for video timestamps
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val timestamp: Long,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

// Persistent queue
@Entity(tableName = "current_queue")
data class QueueItemEntity(
    @PrimaryKey val mediaId: Long,
    val sortOrder: Int
)
```

### DAO with All Query Types
```kotlin
@Dao
interface MediaDao {
    // Insert
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHistory(history: PlaybackHistory)

    // Insert multiple
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItemEntity>)

    // Query single
    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId")
    suspend fun getHistory(mediaId: Long): PlaybackHistory?

    // Query list (reactive)
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsWithRefs(): Flow<List<PlaylistWithRefs>>

    // Complex query with calculation
    @Query("""
        SELECT * FROM playback_history
        WHERE mediaType = 'VIDEO'
        AND position > 0
        AND (duration = 0 OR position < (duration * 0.95))
        ORDER BY timestamp DESC LIMIT 10
    """)
    fun getContinueWatching(): Flow<List<PlaybackHistory>>

    // Update specific field
    @Query("UPDATE media_analytics SET playCount = playCount + 1, lastPlayed = :timestamp WHERE mediaId = :mediaId")
    suspend fun incrementPlayCount(mediaId: Long, timestamp: Long)

    // Aggregation
    @Query("SELECT SUM(totalPlaytimeMs) FROM daily_playtime WHERE date >= :startDate AND date <= :endDate")
    fun getPlaytimeRange(startDate: Long, endDate: Long): Flow<Long?>

    // Transaction
    @Transaction
    suspend fun replaceQueue(items: List<QueueItemEntity>) {
        clearQueue()
        insertQueueItems(items)
    }

    // Relation query
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsWithRefs(): Flow<List<PlaylistWithRefs>>
}
```

### Database Class (Version 5, 8 Entities)
```kotlin
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

### Relation Helper
```kotlin
data class PlaylistWithRefs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val refs: List<PlaylistMediaCrossRef>
)
```

---

## 5. Coroutines & Flow Patterns

### StateFlow in ViewModel
```kotlin
class MainViewModel : ViewModel() {
    // Private mutable, public read-only
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<MediaFile?>(null)
    val currentTrack = _currentTrack.asStateFlow()
}
```

### SharedFlow for One-Time Events
```kotlin
private val _deleteIntentEvent = MutableSharedFlow<IntentSender>()
val deleteIntentEvent = _deleteIntentEvent.asSharedFlow()

fun triggerDelete(intentSender: IntentSender) {
    viewModelScope.launch {
        _deleteIntentEvent.emit(intentSender)
    }
}
```

### Flow Operators
```kotlin
// combine - Merge multiple flows (search + sort)
val filteredAudioList = combine(_audioList, _searchQuery, _sortOption) { list, query, sort ->
    var result = list
    if (query.isNotEmpty()) {
        result = result.filter { it.title.contains(query, ignoreCase = true) }
    }
    when(sort) {
        SortOption.TITLE_ASC -> result.sortedBy { it.title }
        SortOption.TITLE_DESC -> result.sortedByDescending { it.title }
        // ...
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// map - Derive state (movies = videos > 1 hour)
val moviesList = _videoList.map { list ->
    list.filter { it.duration >= 3600000 }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// flatMapLatest - Switch to new flow
val currentBookmarks = _currentTrack.flatMapLatest { track ->
    if (track != null) mediaDao.getBookmarks(track.id)
    else flowOf(emptyList())
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// combine for "Continue Watching"
val continueWatchingList = combine(_videoList, mediaDao.getContinueWatching()) { videos, history ->
    history.mapNotNull { h -> videos.find { it.id == h.mediaId }?.let { it to h } }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

### Launch Coroutine in ViewModel
```kotlin
fun saveData(data: Data) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.save(data)
    }
}
```

### Context Switching
```kotlin
private suspend fun calculateAnalytics(allMedia: List<MediaFile>): RealtimeAnalytics {
    return withContext(Dispatchers.IO) {
        val today = getNormalizedToday()
        val todayMs = mediaDao.getPlaytimeForDay(today).firstOrNull() ?: 0L
        // ... more database operations
        RealtimeAnalytics(...)  // Return result
    }
}
```

### Periodic Updates (Heartbeat Pattern)
```kotlin
private fun startPositionUpdates() {
    positionUpdateJob = viewModelScope.launch {
        while (isActive) {
            _player.value?.let { player ->
                _currentPosition.value = player.currentPosition
                _duration.value = player.duration.coerceAtLeast(0L)

                // Periodic save every 5 seconds (10 ticks × 500ms)
                if (saveCounter % 10 == 0) {
                    savePlaybackState(...)
                }

                // Analytics accumulation
                if (_isPlaying.value) {
                    accumulatedPlaytime += 500L
                }
            }
            delay(500)  // 500ms tick
        }
    }
}

private fun stopPositionUpdates() {
    positionUpdateJob?.cancel()
    positionUpdateJob = null
}
```

### Collecting Flow in Compose
```kotlin
// Best practice - lifecycle-aware
val state by viewModel.state.collectAsStateWithLifecycle()

// Alternative
val state by viewModel.state.collectAsState()
```

---

## 6. Media3 Patterns

### PlaybackService (with Audio Attributes & Notification Intent)
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
                    .build(),
                true  // Handle audio focus
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Notification taps open "Now Playing" screen
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession
    override fun onDestroy() {
        mediaSession?.run { player.release(); release(); mediaSession = null }
        super.onDestroy()
    }
}
```

### MediaController Connection
```kotlin
private fun initializeMediaController() {
    val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
    controllerFuture = MediaController.Builder(app, token).buildAsync()
    controllerFuture?.addListener({
        try {
            val controller = controllerFuture?.get()
            _player.value = controller
            setupPlayerListener(controller)
        } catch (e: Exception) { e.printStackTrace() }
    }, MoreExecutors.directExecutor())
}
```

### Player Listener (Full)
```kotlin
controller?.addListener(object : Player.Listener {
    override fun onVideoSizeChanged(videoSize: VideoSize) {
        _videoSize.value = videoSize
    }
    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_READY) {
            _duration.value = controller.duration.coerceAtLeast(0L)
        }
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
        if (isPlaying) startPositionUpdates() else stopPositionUpdates()
    }
    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        _isShuffleEnabled.value = enabled
        updateDisplayQueue()
    }
    override fun onRepeatModeChanged(repeatMode: Int) {
        _repeatMode.value = repeatMode
    }
    override fun onPlaybackParametersChanged(params: PlaybackParameters) {
        _playbackSpeed.value = params.speed
    }
    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
        updateCurrentTrackFromPlayer(controller)
    }
    override fun onTracksChanged(tracks: Tracks) {
        // Update available audio/subtitle track lists
    }
})
```

### Playing a Queue
```kotlin
fun setQueue(mediaList: List<MediaFile>, startIndex: Int, shuffle: Boolean = false, startPosition: Long = 0L) {
    _player.value?.let { controller ->
        val mediaItems = mediaList.map { it.toMediaItem() }
        controller.setMediaItems(mediaItems, startIndex, startPosition)
        controller.shuffleModeEnabled = shuffle
        controller.prepare()
        controller.play()

        _currentQueue.value = mediaList
        _currentIndex.value = startIndex
        persistQueue(mediaList)
    }
}
```

---

## 7. Navigation Patterns

### NavHost Setup (with Slide Transitions)
```kotlin
@Composable
fun VideoNavigationHost(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
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
        composable("video_folders") {
            VideoFolderScreen(
                onFolderClick = { folderId -> navController.navigate("video_list/$folderId") }
            )
        }
        composable("video_list/{bucketId}") { backStack ->
            val bucketId = backStack.arguments?.getString("bucketId") ?: ""
            VideoListScreen(bucketId = bucketId)
        }
    }
}
```

### Observe Current Route
```kotlin
val navBackStackEntry by navController.currentBackStackEntryAsState()
val currentRoute = navBackStackEntry?.destination?.route

val showBottomBar = currentRoute in listOf("video_folders", "audio_library")
```

### Navigate with Arguments
```kotlin
// Navigate
navController.navigate("video_playlist_detail/${playlist.id}")

// Receive
composable("video_playlist_detail/{playlistId}") { backStack ->
    val playlistId = backStack.arguments?.getString("playlistId") ?: return@composable
    VideoPlaylistDetailScreen(playlistId = playlistId)
}
```

---

## 8. Video Player Patterns

### Picture-in-Picture (PiP)
```kotlin
// In ViewModel
fun shouldEnterPipMode(): Boolean {
    return _isVideoPlayerVisible.value && _isPlaying.value
}

fun setPipMode(isPip: Boolean) {
    _isInPipMode.value = isPip
}

// In MainActivity
override fun onUserLeaveHint() {
    if (viewModel.shouldEnterPipMode()) {
        enterPictureInPictureMode(pipParams)
    }
}
```

### Audio/Video State Preservation
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
        savedAudioState = AudioPlayerState(...)
    }
    // ... play video
}

fun closeVideo() {
    // Restore audio state when video closes
    restoreAudioSession()
}
```

### Playback Speed Control
```kotlin
fun cyclePlaybackSpeed() {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val current = _playbackSpeed.value
    val nextIndex = (speeds.indexOf(current) + 1) % speeds.size
    _player.value?.setPlaybackSpeed(speeds[nextIndex])
}
```

### Audio/Subtitle Track Selection
```kotlin
fun getAudioTracks(): List<TrackInfo> {
    val tracks = _player.value?.currentTracks ?: return emptyList()
    // Iterate over track groups filtering for C.TRACK_TYPE_AUDIO
    // Return list of TrackInfo(groupIndex, trackIndex, label, language, isSelected)
}

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

---

## 9. Thumbnail & Image Patterns

### ThumbnailManager (Flow-Based Generation)
```kotlin
@Singleton
class ThumbnailManager @Inject constructor(private val context: Context) {
    private val cacheDir: File by lazy {
        File(context.filesDir, "video_thumbnails").also { it.mkdirs() }
    }

    private fun cacheKey(video: MediaFile): String =
        "thumb_${video.id}_${video.size}_${video.dateModified}.jpg"

    fun getCachedPath(video: MediaFile): String? {
        val file = File(cacheDir, cacheKey(video))
        return if (file.exists()) file.absolutePath else null
    }

    // Emits (mediaId, path) as each thumbnail is generated
    fun generateThumbnails(videos: List<MediaFile>): Flow<Pair<Long, String>> = flow {
        for (video in videos) {
            yield()  // Cooperative cancellation
            val cached = File(cacheDir, cacheKey(video))
            if (cached.exists()) { emit(video.id to cached.absolutePath); continue }
            extractThumbnail(video, cached)?.let { emit(video.id to it) }
        }
    }.flowOn(Dispatchers.IO)

    fun cleanStaleThumbnails(currentVideos: List<MediaFile>) {
        val validKeys = currentVideos.map { cacheKey(it) }.toSet()
        cacheDir.listFiles()?.forEach { if (it.name !in validKeys) it.delete() }
    }
}
```

### MediaStore Image Query
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

---

## 10. Media Deletion Patterns

### Scoped Storage Deletion (Android 11+)
```kotlin
fun deleteSelectedMedia() {
    viewModelScope.launch(Dispatchers.IO) {
        val uris = _selectedIds.value.mapNotNull { id ->
            _videoList.value.find { it.id == id }?.uri
        }
        if (uris.isEmpty()) return@launch

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Use system confirmation dialog
            val pendingIntent = MediaStore.createDeleteRequest(
                app.contentResolver, uris
            )
            _deleteIntentEvent.emit(pendingIntent.intentSender)
        } else {
            // Android 10-: Delete directly
            uris.forEach { uri ->
                app.contentResolver.delete(uri, null, null)
            }
            onDeleteSuccess()
        }
    }
}

// Handle system confirmation result
fun onDeleteSuccess(ids: List<Long>) {
    _videoList.value = _videoList.value.filterNot { it.id in ids }
    _imageList.value = _imageList.value.filterNot { it.id in ids }
    _selectedIds.value = emptySet()
    _isSelectionMode.value = false
}
```

---

## 11. Common Recipes

### Permission Handling (API 33+ Granular Permissions)
```kotlin
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var permissionsGranted by remember {
        mutableStateOf(permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PERMISSION_GRANTED
        })
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) viewModel.scanMedia()
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) launcher.launch(permissions.toTypedArray())
        else viewModel.scanMedia()
    }
}
```

### Format Duration
```kotlin
fun formatDuration(millis: Long): String {
    val hours = millis / 3600000
    val minutes = (millis % 3600000) / 60000
    val seconds = (millis % 60000) / 1000

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
```

### Save/Load SharedPreferences
```kotlin
private val sharedPrefs = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

// Theme
fun updateTheme(themeId: String) {
    _currentTheme.value = themes[themeId] ?: return
    sharedPrefs.edit().putString("current_theme_id", themeId).apply()
}

fun toggleThemeMode() {
    val newMode = !_isDarkTheme.value
    _isDarkTheme.value = newMode
    sharedPrefs.edit().putBoolean("is_dark_mode", newMode).apply()
}

// Queue persistence
fun persistQueueIndex(index: Int) {
    sharedPrefs.edit().putInt("last_queue_index", index).apply()
}
```

### Analytics Threshold Validation
```kotlin
// Only count as "played" if listened 30s+ or 50% of track
if (!hasLoggedCurrentTrack && _isPlaying.value) {
    currentTrackPlaytimeAccumulator += 500L

    val threshold = if (duration > 0) min(30000L, duration / 2) else 30000L
    val safeThreshold = max(5000L, threshold)  // Min 5s for short clips

    if (currentTrackPlaytimeAccumulator >= safeThreshold) {
        recordPlay(track.id)
        hasLoggedCurrentTrack = true  // Prevent duplicates
    }
}
```

### Auto-Fill Queue with Shuffle
```kotlin
fun autoFillQueue(playNext: Boolean = false, playPrevious: Boolean = false) {
    val fullLibrary = _audioList.value
    if (fullLibrary.isEmpty()) return

    val shuffled = fullLibrary.shuffled()
    setQueue(shuffled, startIndex = 0, shuffle = true)
}
```

---

## 🔖 Gradle Dependencies Reference

```kotlin
dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Hilt (with KSP)
    implementation("com.google.dagger:hilt-android:2.58")
    ksp("com.google.dagger:hilt-compiler:2.58")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room (with KSP)
    implementation("androidx.room:room-runtime:2.7.0-alpha11")
    implementation("androidx.room:room-ktx:2.7.0-alpha11")
    ksp("androidx.room:room-compiler:2.7.0-alpha11")

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Coil (Image loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Gson (Legacy migration)
    implementation("com.google.code.gson:gson:2.10.1")
}
```

---

*Keep this cheat sheet handy for quick pattern reference during development!*
