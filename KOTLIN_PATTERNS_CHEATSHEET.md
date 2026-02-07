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
8. [Common Recipes](#8-common-recipes)

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
    val albumId: Long = -1
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
    exoPlayer.play()
}

// Safe call chain
val id = currentMediaItem?.mediaId?.toLongOrNull()

// Not-null assertion (use sparingly)
val track = currentTrack!!
```

### Extension Function
```kotlin
private fun MediaFile.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build()
        )
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

fun sort(list: List<MediaFile>, option: SortOption): List<MediaFile> = when(option) {
    SortOption.TITLE_ASC -> list.sortedBy { it.title }
    SortOption.TITLE_DESC -> list.sortedByDescending { it.title }
    SortOption.DURATION_ASC -> list.sortedBy { it.duration }
    SortOption.DURATION_DESC -> list.sortedByDescending { it.duration }
    SortOption.DATE_ADDED_DESC -> list.sortedByDescending { it.id }
}
```

### Scope Functions
```kotlin
// let - Execute block if non-null, return result
currentTrack?.let { track ->
    savePlaybackState(track.id, position)
}

// apply - Configure object, return self
val player = ExoPlayer.Builder(this).apply {
    setAudioAttributes(audioAttributes)
    setHandleAudioBecomingNoisy(true)
}.build()

// run - Execute on receiver, return result
mediaSession?.run {
    player.release()
    release()
}

// also - Side effects, return original
val cursor = query()?.also { Log.d("Count", "${it.count}") }
```

---

## 2. Jetpack Compose Patterns

### Basic Composable Structure
```kotlin
@Composable
fun MyScreen(
    viewModel: MyViewModel = hiltViewModel(),
    onNavigate: () -> Unit
) {
    // Observe ViewModel state
    val data by viewModel.data.collectAsStateWithLifecycle()
    
    // Local UI state
    var isExpanded by remember { mutableStateOf(false) }
    
    // Side effects
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }
    
    // UI
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = data.title)
        Button(onClick = { isExpanded = !isExpanded }) {
            Text("Toggle")
        }
    }
}
```

### Scaffold with TopBar and BottomBar
```kotlin
@Composable
fun MainContent() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FastBeat") },
                actions = {
                    IconButton(onClick = { /* search */ }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Home") }
                )
                // More items...
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Content
        }
    }
}
```

### AnimatedContent for Transitions
```kotlin
AnimatedContent(
    targetState = selectedTab,
    transitionSpec = {
        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
    },
    label = "TabTransition"
) { targetTab ->
    when (targetTab) {
        0 -> HomeScreen()
        1 -> LibraryScreen()
        2 -> SettingsScreen()
    }
}
```

### Custom Theme with CompositionLocal
```kotlin
// Define config
data class AppThemeConfig(
    val id: String,
    val primaryColor: Color,
    val subtitle: String
)

// Create provider
val LocalAppTheme = staticCompositionLocalOf { 
    AppThemeConfig("default", Color.Blue, "Default") 
}

// Theme wrapper
@Composable
fun AppTheme(
    config: AppThemeConfig,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppTheme provides config) {
        MaterialTheme(
            colorScheme = darkColorScheme(primary = config.primaryColor),
            content = content
        )
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
    TextField(
        value = searchQuery,
        onValueChange = onSearchChange
    )
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
class MyApp : Application()

// Manifest
<application android:name=".MyApp" ...>
```

### Activity Setup
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(viewModel = viewModel)
        }
    }
}
```

### ViewModel with Injection
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val repository: PlaylistRepository,
    private val dao: MediaDao
) : AndroidViewModel(app) {
    // Use injected dependencies
}
```

### Module for Database
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
            "app_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideDao(database: AppDatabase): MediaDao {
        return database.mediaDao()
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
    // Repository methods
}
```

### Compose Integration
```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    // viewModel is injected automatically
}
```

---

## 4. Room Database Patterns

### Entity Definitions
```kotlin
// Basic entity
@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey val mediaId: Long,
    val position: Long,
    val duration: Long = 0,
    val timestamp: Long,
    val mediaType: String
)

// Entity with index
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
```

### DAO with All Query Types
```kotlin
@Dao
interface MediaDao {
    // Insert
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PlaybackHistory)
    
    // Insert multiple
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<QueueItemEntity>)
    
    // Query single
    @Query("SELECT * FROM playback_history WHERE mediaId = :id")
    suspend fun getById(id: Long): PlaybackHistory?
    
    // Query list (reactive)
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    
    // Update specific field
    @Query("UPDATE media_analytics SET playCount = playCount + 1 WHERE mediaId = :id")
    suspend fun incrementPlayCount(id: Long)
    
    // Delete
    @Query("DELETE FROM current_queue")
    suspend fun clearQueue()
    
    // Aggregation
    @Query("SELECT SUM(totalPlaytimeMs) FROM daily_playtime WHERE date >= :start")
    fun getTotalPlaytime(start: Long): Flow<Long?>
    
    // Transaction
    @Transaction
    suspend fun replaceQueue(items: List<QueueItemEntity>) {
        clearQueue()
        insertAll(items)
    }
    
    // Relation query
    @Transaction
    @Query("SELECT * FROM playlists")
    fun getPlaylistsWithSongs(): Flow<List<PlaylistWithRefs>>
}
```

### Database Class
```kotlin
@Database(
    entities = [
        PlaybackHistory::class,
        PlaylistEntity::class,
        PlaylistMediaCrossRef::class
    ],
    version = 1,
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
    val songs: List<PlaylistMediaCrossRef>
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
    
    fun togglePlay() {
        _isPlaying.value = !_isPlaying.value
    }
}
```

### SharedFlow for Events
```kotlin
private val _events = MutableSharedFlow<Event>()
val events = _events.asSharedFlow()

fun triggerEvent(event: Event) {
    viewModelScope.launch {
        _events.emit(event)
    }
}
```

### Flow Operators
```kotlin
// combine - Merge multiple flows
val combined = combine(flow1, flow2, flow3) { a, b, c ->
    compute(a, b, c)
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), default)

// map - Transform values
val mapped = originalFlow.map { item ->
    item.transform()
}

// filter
val filtered = originalFlow.map { list ->
    list.filter { it.isValid }
}

// flatMapLatest - Switch to new flow
val dynamic = selectedId.flatMapLatest { id ->
    if (id != null) dao.getItemFlow(id)
    else flowOf(null)
}
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
private suspend fun fetchAndProcess(): Result {
    return withContext(Dispatchers.IO) {
        val raw = database.fetch()
        // Heavy processing on IO thread
        Result(raw.process())
    }
}
```

### Periodic Updates
```kotlin
private fun startUpdates() {
    updateJob = viewModelScope.launch {
        while (isActive) {
            _position.value = player.currentPosition
            delay(500)  // Update every 500ms
        }
    }
}

private fun stopUpdates() {
    updateJob?.cancel()
    updateJob = null
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

### PlaybackService
```kotlin
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
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
```

### MediaController Connection
```kotlin
private fun initializeController() {
    val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
    controllerFuture = MediaController.Builder(app, token).buildAsync()
    controllerFuture?.addListener({
        _player.value = controllerFuture?.get()
        setupListener()
    }, MoreExecutors.directExecutor())
}
```

### Player Listener
```kotlin
controller?.addListener(object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }
    
    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_READY -> _duration.value = controller.duration
            Player.STATE_ENDED -> playNext()
        }
    }
    
    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
        updateCurrentTrack()
    }
})
```

### Building MediaItem
```kotlin
fun MediaFile.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(albumArtUri)
                .build()
        )
        .build()
}
```

### Playing a Queue
```kotlin
fun playQueue(items: List<MediaFile>, startIndex: Int) {
    player?.let {
        val mediaItems = items.map { it.toMediaItem() }
        it.setMediaItems(mediaItems, startIndex, 0L)
        it.prepare()
        it.play()
    }
}
```

---

## 7. Navigation Patterns

### NavHost Setup
```kotlin
@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { slideIntoContainer(SlideDirection.Left, tween(300)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.Right, tween(300)) }
    ) {
        composable("home") {
            HomeScreen(onNavigate = { id -> navController.navigate("detail/$id") })
        }
        
        composable("detail/{id}") { backStack ->
            val id = backStack.arguments?.getString("id") ?: return@composable
            DetailScreen(id = id, onBack = { navController.popBackStack() })
        }
    }
}
```

### Observe Current Route
```kotlin
val navBackStackEntry by navController.currentBackStackEntryAsState()
val currentRoute = navBackStackEntry?.destination?.route

val showBottomBar = currentRoute in listOf("home", "library", "settings")
```

### Navigate with Arguments
```kotlin
// Navigate
navController.navigate("detail/${item.id}")

// Receive
composable("detail/{itemId}") { backStack ->
    val itemId = backStack.arguments?.getString("itemId")
}
```

---

## 8. Common Recipes

### Permission Handling
```kotlin
@Composable
fun PermissionHandler(onGranted: () -> Unit) {
    val context = LocalContext.current
    val permissions = if (Build.VERSION.SDK_INT >= 33) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    var granted by remember {
        mutableStateOf(permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PERMISSION_GRANTED
        })
    }
    
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        granted = results.values.all { it }
        if (granted) onGranted()
    }
    
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(permissions.toTypedArray())
        else onGranted()
    }
}
```

### MediaStore Query
```kotlin
fun queryAudio(context: Context): List<MediaFile> {
    val list = mutableListOf<MediaFile>()
    val uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST
    )
    
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol)
            val contentUri = ContentUris.withAppendedId(uri, id)
            list.add(MediaFile(id, contentUri, title, ...))
        }
    }
    return list
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

### Save to SharedPreferences
```kotlin
private val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

fun saveTheme(themeId: String) {
    sharedPrefs.edit().putString("theme_id", themeId).apply()
}

fun loadTheme(): String {
    return sharedPrefs.getString("theme_id", "default") ?: "default"
}
```

### Debounced Search
```kotlin
private val _searchQuery = MutableStateFlow("")

val searchResults = _searchQuery
    .debounce(300)  // Wait 300ms after typing stops
    .flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList())
        else repository.search(query)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

fun onSearchChange(query: String) {
    _searchQuery.value = query
}
```

---

## 🔖 Gradle Dependencies Reference

```kotlin
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Media3
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")
}
```

---

*Keep this cheat sheet handy for quick pattern reference during development!*
