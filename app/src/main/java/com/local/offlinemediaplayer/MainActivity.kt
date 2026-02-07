
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
        // Enter PIP mode when user presses home/swipes up during video playback (like MX Player)
        if (true && viewModel.shouldEnterPipMode()) {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))

            // Android 12+ gets smoother auto-enter PIP transition for gesture navigation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
            }

            enterPictureInPictureMode(builder.build())
        }
    }
}
