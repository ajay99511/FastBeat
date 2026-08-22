package com.local.offlinemediaplayer.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.local.offlinemediaplayer.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the connection between the app and [PlaybackService]'s media session.
 *
 * SCOPE — this class handles connection lifecycle ONLY: building the [MediaController], exposing it
 * once ready, and releasing it. It deliberately holds no playback state and registers no
 * [androidx.media3.common.Player.Listener]. Interpreting player callbacks stays in
 * `PlaybackViewModel`, whose listener reaches into ~28 of its own members; moving that here would
 * relocate the coupling rather than remove it. Later extractions (P4-E.2 … E.5) shrink that listener
 * by pulling out analytics, queue and deletion concerns, at which point less of it has anywhere to
 * hide. See P4-E.1 in implementation_plan.md.
 *
 * THREADING — [connect] must be called from the main thread. [MediaController.Builder] captures the
 * calling thread as the controller's application looper, and every `Player.Listener` callback is
 * then delivered on it. The completion callback uses [MoreExecutors.directExecutor] so it runs on
 * whichever thread completes the future — the same main thread — rather than hopping. Introducing a
 * dispatcher here would move player callbacks off the main thread and break every collector that
 * assumes otherwise.
 */
@UnstableApi
@Singleton
class MediaControllerBinder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private companion object {
            const val TAG = "MediaControllerBinder"
        }

        private var controllerFuture: ListenableFuture<MediaController>? = null

        private val _controller = MutableStateFlow<MediaController?>(null)

        /**
         * The connected controller, or null while disconnected. This is a [StateFlow], so a
         * collector that subscribes after the connection completes still receives the controller
         * immediately — there is no window in which a late observer misses it.
         */
        val controller: StateFlow<MediaController?> = _controller.asStateFlow()

        private val _isConnected = MutableStateFlow(false)

        /** True between a successful connection and [release]. False if the connection failed. */
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        /**
         * Connects to the media session if not already connected. Idempotent: calling it repeatedly
         * is a no-op, so a recreated ViewModel reuses the existing connection instead of opening a
         * second one.
         */
        @MainThread
        fun connect() {
            if (controllerFuture != null) return

            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    try {
                        _controller.value = future.get()
                        _isConnected.value = true
                    } catch (e: Exception) {
                        // Left as a broad catch: buildAsync surfaces failures as ExecutionException
                        // wrapping anything the session threw, and there is no useful recovery
                        // beyond reporting it and staying disconnected.
                        Log.e(TAG, "Failed to connect MediaController", e)
                        _controller.value = null
                        _isConnected.value = false
                    }
                },
                MoreExecutors.directExecutor(),
            )
        }

        /**
         * Releases the controller and resets state so a later [connect] establishes a fresh
         * connection. Safe to call when never connected.
         */
        fun release() {
            controllerFuture?.let { MediaController.releaseFuture(it) }
            controllerFuture = null
            _controller.value = null
            _isConnected.value = false
        }
    }
