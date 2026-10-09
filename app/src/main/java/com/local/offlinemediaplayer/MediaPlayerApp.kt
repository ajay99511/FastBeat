package com.local.offlinemediaplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.local.offlinemediaplayer.data.buildImageLoader
import dagger.hilt.android.HiltAndroidApp

/**
 * Implements [ImageLoaderFactory] so Coil picks the loader up on its own.
 *
 * Coil looks for this interface on the `Application` and uses it for every `AsyncImage` in the app,
 * which is why nothing else had to change: the grid, the viewer and every album-art call site keep
 * their existing code and gain animated decoding.
 */
@HiltAndroidApp
class MediaPlayerApp :
    Application(),
    ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = buildImageLoader(this)
}
