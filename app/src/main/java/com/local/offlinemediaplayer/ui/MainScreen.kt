package com.local.offlinemediaplayer.ui

// import com.local.offlinemediaplayer.model.MediaFile
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.local.offlinemediaplayer.ui.navigation.AudioNavigationHost
import com.local.offlinemediaplayer.ui.navigation.VideoNavigationHost
import com.local.offlinemediaplayer.ui.screens.AccessibilityGuideScreen
import com.local.offlinemediaplayer.ui.screens.ImageListScreen
import com.local.offlinemediaplayer.ui.screens.MeScreen
import com.local.offlinemediaplayer.ui.screens.PermissionRationaleScreen
import com.local.offlinemediaplayer.ui.screens.PermissionRequestScreen
import com.local.offlinemediaplayer.ui.screens.VideoPlayerScreen
import com.local.offlinemediaplayer.ui.theme.Headers.FastBeatHeader
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel

// import androidx.compose.ui.Alignment
// import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MainScreen(viewModel: PlaybackViewModel = hiltViewModel()) {
    val context = LocalContext.current

    // Define permissions based on Android version
    val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

    // Track permission state
    var permissionsGranted by remember {
        mutableStateOf(
                permissions.all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) ==
                            PermissionChecker.PERMISSION_GRANTED
                }
        )
    }

    var shouldShowRationale by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionsMap ->
                val allGranted = permissionsMap.values.all { it }
                permissionsGranted = allGranted

                if (allGranted) {
                    viewModel.scanMedia()
                } else {
                    // Check if we should show rationale
                    shouldShowRationale =
                            permissions.any { permission ->
                                (context as? ComponentActivity)
                                        ?.shouldShowRequestPermissionRationale(permission) == true
                            }
                }
            }

    // Request permissions on first launch
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            viewModel.scanMedia()
        }
    }

    // Main content or permission request UI
    when {
        permissionsGranted -> {
            MediaPlayerAppContent(viewModel)
        }
        shouldShowRationale -> {
            PermissionRationaleScreen(
                    onRequestPermission = { permissionLauncher.launch(permissions.toTypedArray()) },
                    onOpenSettings = {
                        val intent =
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS.let { action ->
                                    Intent(action).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                }
                        context.startActivity(intent)
                    }
            )
        }
        else -> {
            PermissionRequestScreen(
                    onRequestPermission = { permissionLauncher.launch(permissions.toTypedArray()) }
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MediaPlayerAppContent(viewModel: PlaybackViewModel) {
    // 0 = Videos, 1 = Music, 2 = Images, 3 = Stats
    var selectedTab by remember { mutableIntStateOf(0) }

    // UI Logic: Video Player Visibility is now controlled by ViewModel state
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isVideoPlayingFullscreen by viewModel.isVideoPlayerVisible.collectAsStateWithLifecycle()

    // Global Search Visibility State (Controlled by Header)
    var isSearchVisible by remember { mutableStateOf(false) }

    // Accessibility Guide Screen State
    var showAccessibilityGuide by remember { mutableStateOf(false) }

    // Reset search visibility when changing tabs
    LaunchedEffect(selectedTab) { isSearchVisible = false }

    // Handle deep link navigation to player
    val navigateToPlayer by viewModel.navigateToPlayer.collectAsStateWithLifecycle()
    LaunchedEffect(navigateToPlayer) {
        if (navigateToPlayer) {
            selectedTab = 1 // Switch to Music Tab
            // Navigation within the tab is handled by AudioNavigationHost
        }
    }

    // Hoist Navigation State
    val audioNavController = rememberNavController()
    val videoNavController = rememberNavController()

    // Determine current routes for conditional UI logic
    val audioNavBackStackEntry by audioNavController.currentBackStackEntryAsState()
    val videoNavBackStackEntry by videoNavController.currentBackStackEntryAsState()

    val currentAudioRoute = audioNavBackStackEntry?.destination?.route
    val currentVideoRoute = videoNavBackStackEntry?.destination?.route

    val isAudioDetailScreen = currentAudioRoute != "audio_library"

    // Video root is either folders or list, we usually show header on root folders
    val isVideoRoot = currentVideoRoute == "video_folders"

    // Theme Color
    val themeColor = LocalAppTheme.current.primaryColor

    // Show Header Logic - don't show when full screen video
    val showHeader =
            !isVideoPlayingFullscreen &&
                    ((selectedTab == 0 && isVideoRoot) || // Videos (Folders only)
                    (selectedTab == 1 && !isAudioDetailScreen) || // Audio (Library only)
                            selectedTab == 2 || // Images (Always)
                            selectedTab == 3 // Stats (Always)
                    )

    Scaffold(
            // Custom Top Bar
            topBar = {
                if (showHeader) {
                    // Crossfade animation for headers to match tab switch
                    AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                            },
                            label = "HeaderTransition"
                    ) { targetTab ->
                        val sectionTitle =
                                when (targetTab) {
                                    0 -> "Videos"
                                    1 -> "Music"
                                    2 -> "Gallery"
                                    3 -> "Stats"
                                    else -> "App"
                                }
                        FastBeatHeader(
                                sectionTitle = sectionTitle,
                                themeColor = themeColor,
                                onSearchClick = { isSearchVisible = !isSearchVisible }
                        )
                    }
                }
            },
            bottomBar = {
                if (!isVideoPlayingFullscreen) {
                    // FastBeat Custom Navigation Bar
                    val navContainerColor = MaterialTheme.colorScheme.background
                    val primaryColor = LocalAppTheme.current.primaryColor
                    val activeIndicatorColor = primaryColor.copy(alpha = 0.15f)
                    val activeIconColor = primaryColor
                    val inactiveIconColor =
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

                    NavigationBar(
                            containerColor = navContainerColor,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            tonalElevation = 0.dp
                    ) {
                        // 0. Videos
                        NavigationBarItem(
                                icon = {
                                    Icon(
                                            if (selectedTab == 0) Icons.Filled.PlayArrow
                                            else Icons.Outlined.PlayArrow,
                                            "Videos"
                                    )
                                },
                                label = {
                                    Text(
                                            "Videos",
                                            fontWeight =
                                                    if (selectedTab == 0) FontWeight.Bold
                                                    else FontWeight.Normal
                                    )
                                },
                                selected = selectedTab == 0,
                                onClick = {
                                    selectedTab = 0
                                    showAccessibilityGuide = false
                                },
                                colors =
                                        NavigationBarItemDefaults.colors(
                                                selectedIconColor = activeIconColor,
                                                selectedTextColor = activeIconColor,
                                                indicatorColor = activeIndicatorColor,
                                                unselectedIconColor = inactiveIconColor,
                                                unselectedTextColor = inactiveIconColor
                                        )
                        )

                        // 1. Music
                        NavigationBarItem(
                                icon = {
                                    Icon(
                                            if (selectedTab == 1) Icons.Filled.MusicNote
                                            else Icons.Outlined.MusicNote,
                                            "Music"
                                    )
                                },
                                label = {
                                    Text(
                                            "Music",
                                            fontWeight =
                                                    if (selectedTab == 1) FontWeight.Bold
                                                    else FontWeight.Normal
                                    )
                                },
                                selected = selectedTab == 1,
                                onClick = {
                                    selectedTab = 1
                                    showAccessibilityGuide = false
                                },
                                colors =
                                        NavigationBarItemDefaults.colors(
                                                selectedIconColor = activeIconColor,
                                                selectedTextColor = activeIconColor,
                                                indicatorColor = activeIndicatorColor,
                                                unselectedIconColor = inactiveIconColor,
                                                unselectedTextColor = inactiveIconColor
                                        )
                        )

                        // 2. Images
                        NavigationBarItem(
                                icon = {
                                    Icon(
                                            if (selectedTab == 2) Icons.Filled.Image
                                            else Icons.Outlined.Image,
                                            "Images"
                                    )
                                },
                                label = {
                                    Text(
                                            "Images",
                                            fontWeight =
                                                    if (selectedTab == 2) FontWeight.Bold
                                                    else FontWeight.Normal
                                    )
                                },
                                selected = selectedTab == 2,
                                onClick = {
                                    selectedTab = 2
                                    showAccessibilityGuide = false
                                },
                                colors =
                                        NavigationBarItemDefaults.colors(
                                                selectedIconColor = activeIconColor,
                                                selectedTextColor = activeIconColor,
                                                indicatorColor = activeIndicatorColor,
                                                unselectedIconColor = inactiveIconColor,
                                                unselectedTextColor = inactiveIconColor
                                        )
                        )

                        // 3. Stats
                        NavigationBarItem(
                                icon = {
                                    Icon(
                                            if (selectedTab == 3) Icons.Filled.Analytics
                                            else Icons.Outlined.Analytics,
                                            "Stats"
                                    )
                                },
                                label = {
                                    Text(
                                            "Stats",
                                            fontWeight =
                                                    if (selectedTab == 3) FontWeight.Bold
                                                    else FontWeight.Normal
                                    )
                                },
                                selected = selectedTab == 3,
                                onClick = {
                                    selectedTab = 3
                                    showAccessibilityGuide = false
                                },
                                colors =
                                        NavigationBarItemDefaults.colors(
                                                selectedIconColor = activeIconColor,
                                                selectedTextColor = activeIconColor,
                                                indicatorColor = activeIndicatorColor,
                                                unselectedIconColor = inactiveIconColor,
                                                unselectedTextColor = inactiveIconColor
                                        )
                        )
                    }
                }
            }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Animate transition between Main Content and Fullscreen Video Player
            AnimatedContent(
                    targetState = isVideoPlayingFullscreen,
                    transitionSpec = {
                        if (targetState) {
                            // Opening Video: Scale up and Fade In
                            scaleIn(initialScale = 0.9f, animationSpec = tween(300)) +
                                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        } else {
                            // Closing Video: Fade Out (revealing content behind)
                            fadeIn(tween(300)) togetherWith
                                    scaleOut(targetScale = 0.9f, animationSpec = tween(300)) +
                                            fadeOut(tween(300))
                        }
                    },
                    label = "VideoPlayerTransition"
            ) { isVideoMode ->
                if (isVideoMode) {
                    VideoPlayerScreen(viewModel = viewModel, onBack = { viewModel.closeVideo() })
                } else if (showAccessibilityGuide) {
                    // Accessibility Guide Screen (overlay on Stats tab)
                    AccessibilityGuideScreen(onBack = { showAccessibilityGuide = false })
                } else {
                    // Animate transition between Tabs
                    AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                // Standard Fade Through for Bottom Tabs
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(300))
                            },
                            label = "TabTransition"
                    ) { targetTab ->
                        when (targetTab) {
                            0 ->
                                    VideoNavigationHost(
                                            viewModel = viewModel,
                                            navController = videoNavController,
                                            onVideoClick = { file, list ->
                                                viewModel.playVideoFromList(file, list)
                                            },
                                            isSearchVisible = isSearchVisible // Pass visibility
                                    )
                            1 ->
                                    AudioNavigationHost(
                                            viewModel = viewModel,
                                            navController = audioNavController,
                                            isSearchVisible = isSearchVisible // Pass visibility
                                    )
                            2 ->
                                    ImageListScreen(
                                            viewModel = viewModel,
                                            isSearchVisible = isSearchVisible // Pass visibility
                                    )
                            3 ->
                                    MeScreen(
                                            viewModel = viewModel,
                                            onPlayMedia = { file ->
                                                viewModel.playMedia(file)
                                                if (!file.isVideo) {
                                                    selectedTab = 1 // Switch to Music tab
                                                }
                                            },
                                            onNavigateToAccessibilityGuide = {
                                                showAccessibilityGuide = true
                                            },
                                            isSearchVisible = isSearchVisible // Pass visibility
                                    )
                        }
                    }
                }
            }
        }
    }
}
