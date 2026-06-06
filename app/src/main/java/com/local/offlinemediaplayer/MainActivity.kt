package com.local.offlinemediaplayer

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.local.offlinemediaplayer.ui.MainScreen
import com.local.offlinemediaplayer.ui.adaptive.AppWidthClass
import com.local.offlinemediaplayer.ui.adaptive.DevicePosture
import com.local.offlinemediaplayer.ui.adaptive.toAppWidthClass
import com.local.offlinemediaplayer.ui.adaptive.toDevicePosture
import com.local.offlinemediaplayer.ui.theme.OfflineMediaPlayerTheme
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: PlaybackViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    private var wasInPipMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val currentThemeConfig by themeViewModel.currentTheme.collectAsStateWithLifecycle()
            val isDarkTheme by themeViewModel.isDarkTheme.collectAsStateWithLifecycle()

            val context = LocalContext.current
            val windowInfoTracker = remember(context) { WindowInfoTracker.getOrCreate(context) }
            val devicePosture by produceState<DevicePosture>(initialValue = DevicePosture.Normal) {
                windowInfoTracker.windowLayoutInfo(context)
                    .map { layoutInfo ->
                        val foldingFeature = layoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull()
                        foldingFeature?.toDevicePosture() ?: DevicePosture.Normal
                    }
                    .catch { emit(DevicePosture.Normal) }
                    .collect { value = it }
            }

            // Using Material3 Adaptive for WindowSizeClass
            val appWidthClass = androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()
                .windowSizeClass.windowWidthSizeClass.toAppWidthClass()

            OfflineMediaPlayerTheme(
                    currentThemeConfig = currentThemeConfig,
                    darkTheme = isDarkTheme
            ) { 
                MainScreen(
                    viewModel = viewModel,
                    widthClass = appWidthClass,
                    devicePosture = devicePosture
                )
            }
        }

        viewModel.handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        viewModel.handleIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Enter PIP mode when user presses home/swipes up during video playback
        // For Android 12+ (S), this is handled automatically by setAutoEnterEnabled(true) in
        // VideoPlayerScreen.
        // We only need to manually trigger it for older versions.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (viewModel.shouldEnterPipMode()) {
                val player = viewModel.player.value
                val videoSize = player?.videoSize
                val aspectRatio =
                        if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
                            val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                            val clampedRatio = ratio.coerceIn(0.41841f, 2.39f)
                            if (ratio == clampedRatio) {
                                Rational(videoSize.width, videoSize.height)
                            } else {
                                Rational(
                                        (videoSize.height * clampedRatio).toInt(),
                                        videoSize.height
                                )
                            }
                        } else {
                            Rational(16, 9)
                        }

                try {
                    val builder = PictureInPictureParams.Builder().setAspectRatio(aspectRatio)
                    enterPictureInPictureMode(builder.build())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
            isInPictureInPictureMode: Boolean,
            newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        // Update ViewModel state for UI (hide controls in PiP)
        viewModel.setPipMode(isInPictureInPictureMode)

        if (isInPictureInPictureMode) {
            wasInPipMode = true
        } else {
            // Exited PiP Mode.
            // Determine if it was "Restored" (Maximize) or "Closed" (Dismissed/Stop).
            // When restored, the activity is usually in STARTED or RESUMED state.
            // When closed/dismissed from background, the activity might be STOPPED.
            // However, a reliable way is to check if the user *intended* to leave video.

            // If lifecycle is CREATED/STOPPED, detection is tricky.
            // BUT: We can check if the app is now in the foreground.
            // If not in foreground, it was likely dismissed.

            // Simple Logic: If we exit PiP and the lifecycle is not RESUMED or STARTED, it's likely
            // a dismiss.
            // When maximizing, the activity is usually STARTED and about to be RESUMED.
            // When dismissing, it goes to STOPPED/DESTROYED.
            if (lifecycle.currentState == androidx.lifecycle.Lifecycle.State.CREATED ||
                            lifecycle.currentState == androidx.lifecycle.Lifecycle.State.DESTROYED
            ) {
                // The user closed the PiP window. We should STOP video.
                viewModel.closeVideo()
            }

            wasInPipMode = false
        }
    }
    override fun onStop() {
        super.onStop()
        // If we are NOT in PIP mode, pausing/stopping the activity should pause the video.
        // This handles cases where PIP failed to enter, or user just minimized the app without PIP
        // intent.
        if (!isInPictureInPictureMode) {
            // Only pause if it's a VIDEO. Audio should keep playing.
            if (viewModel.currentTrack.value?.isVideo == true) {
                viewModel.pauseVideo()
            }
        }
    }
}
