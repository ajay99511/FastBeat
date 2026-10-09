package com.local.offlinemediaplayer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * That the animated-image decoder is actually registered.
 *
 * This is a one-line piece of configuration, which is exactly the kind that silently stops being
 * true — nothing fails to compile and nothing throws if the decoder is missing. A GIF just renders
 * as its first frame, which reads as a corrupt file rather than as a missing feature.
 *
 * Two SDK levels because `minSdk` is 26 and `ImageDecoder` is platform API 28. A device on 26–27
 * needs Coil's own `GifDecoder`, and registering only the modern one would leave those devices on
 * static frames — invisible to anyone testing on a current phone, which is everyone.
 */
@RunWith(RobolectricTestRunner::class)
class AppImageLoaderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [34])
    fun aModernDeviceUsesThePlatformDecoder() {
        val factories = buildImageLoader(context).components.decoderFactories

        assertTrue(
            "expected ImageDecoderDecoder among $factories",
            factories.any { it is ImageDecoderDecoder.Factory },
        )
    }

    @Test
    @Config(sdk = [27])
    fun aDeviceBelowApi28FallsBackToCoilsOwnDecoder() {
        val factories = buildImageLoader(context).components.decoderFactories

        assertTrue(
            "expected GifDecoder among $factories",
            factories.any { it is GifDecoder.Factory },
        )
    }

    /** Whatever the API level, something must be there to decode an animation. */
    @Test
    @Config(sdk = [34])
    fun someAnimatedDecoderIsAlwaysRegistered() {
        val factories = buildImageLoader(context).components.decoderFactories

        assertTrue(factories.isNotEmpty())
    }
}
