package com.local.offlinemediaplayer.ui.screens.video

import android.app.Activity
import android.util.Rational
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.VideoSize

/**
 * Window and picture-in-picture helpers for the video player.
 *
 * These are plain functions rather than composables: they reach the `Activity` window directly and
 * are called from effects in [VideoPlayerScreen], not during composition.
 */
internal fun hideSystemBars(activity: Activity?) {
    activity?.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(
                androidx.core.view.WindowInsetsCompat.Type
                    .systemBars(),
            )
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

internal fun showSystemBars(activity: Activity?) {
    activity?.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(
                androidx.core.view.WindowInsetsCompat.Type
                    .systemBars(),
            )
    }
}

internal fun calculatePipAspectRatio(videoSize: VideoSize): Rational =
    if (videoSize.width > 0 && videoSize.height > 0) {
        val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
        val clampedRatio = ratio.coerceIn(0.41841f, 2.39f)
        if (ratio == clampedRatio) {
            Rational(videoSize.width, videoSize.height)
        } else {
            Rational((videoSize.height * clampedRatio).toInt(), videoSize.height)
        }
    } else {
        Rational(16, 9)
    }
