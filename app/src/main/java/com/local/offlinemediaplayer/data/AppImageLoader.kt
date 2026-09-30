package com.local.offlinemediaplayer.data

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/**
 * The app-wide Coil loader, with animated image decoding registered.
 *
 * Without this a GIF renders as its first frame — which does not look like a missing feature, it
 * looks like a broken file, and the Images tab indexes GIFs like any other `MediaStore.Images` row.
 * Animated WebP comes along with the same decoder.
 *
 * **Two decoders because `minSdk` is 26.** `ImageDecoder` is platform API 28, so devices on 26–27
 * need Coil's own `GifDecoder`. Registering only the modern one would silently downgrade those
 * devices to static frames — the failure would be invisible to anyone testing on a current phone.
 *
 * A function rather than a method on `MediaPlayerApp` so the registration is testable: a
 * `@HiltAndroidApp` class cannot be instantiated in a unit test, and "did we actually add the
 * decoder" is exactly the kind of one-line configuration that silently stops being true.
 */
internal fun buildImageLoader(context: Context): ImageLoader =
    ImageLoader
        .Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }.build()
