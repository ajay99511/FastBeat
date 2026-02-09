
package com.local.offlinemediaplayer

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels // Use activity viewModels for shared state
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.ui.MainScreen
import com.local.offlinemediaplayer.ui.theme.OfflineMediaPlayerTheme
import com.local.offlinemediaplayer.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // State to track if PiP was active, to distinguish "Close" vs "Restore"
    private var wasInPipMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val currentThemeConfig by viewModel.currentTheme.collectAsStateWithLifecycle()
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

            OfflineMediaPlayerTheme(
                currentThemeConfig = currentThemeConfig,
                darkTheme = isDarkTheme
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Enter PIP mode when user presses home/swipes up during video playback
        // For Android 12+ (S), this is handled automatically by setAutoEnterEnabled(true) in VideoPlayerScreen.
        // We only need to manually trigger it for older versions.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (viewModel.shouldEnterPipMode()) {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                enterPictureInPictureMode(builder.build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
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
            
            // Simple Logic: If we exit PiP and the lifecycle is not RESUMED, it's likely a dismiss.
            if (lifecycle.currentState != androidx.lifecycle.Lifecycle.State.RESUMED) {
                 // The user closed the PiP window. We should STOP video.
                 viewModel.closeVideo() 
            }
            
            wasInPipMode = false
        }
    }
}
