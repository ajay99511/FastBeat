package com.local.offlinemediaplayer.service

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.local.offlinemediaplayer.audio.AudioEffectsManager
import com.local.offlinemediaplayer.data.db.MediaDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    companion object {
        private const val TAG = "PlaybackService"
    }

    private var mediaSession: MediaSession? = null

    @Inject lateinit var mediaDao: MediaDao

    // App-scoped owner of the equalizer/effects chain. The service is the only component with a
    // handle on the real ExoPlayer, so it is responsible for feeding it the audio session id.
    @Inject lateinit var audioEffectsManager: AudioEffectsManager

    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()

        // Bridge the ExoPlayer audio session id to the effects manager. onAudioSessionIdChanged
        // fires once the audio track is initialised (and again if it is ever recreated), which is
        // exactly when the equalizer must (re)attach.
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onAudioSessionIdChanged(
                    eventTime: AnalyticsListener.EventTime,
                    audioSessionId: Int,
                ) {
                    audioEffectsManager.onAudioSessionIdChanged(audioSessionId)
                }
            },
        )

        val sessionActivityPendingIntent =
            android.app.PendingIntent.getActivity(
                this,
                0,
                android.content
                    .Intent(
                        this,
                        com.local.offlinemediaplayer.MainActivity::class.java,
                    ).apply { putExtra("open_player", true) },
                android.app.PendingIntent.FLAG_IMMUTABLE or
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )

        mediaSession =
            MediaSession
                .Builder(this, player)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Called when the user swipes the app away from Recents. The ViewModel that normally
     * persists position dies with the UI, so we durably record the latest position here.
     *
     * We only update the position column (see [MediaDao.updateHistoryPosition]) so we never
     * overwrite mediaType or the saved track selections. If audio is still playing we keep the
     * service alive so background playback continues; if playback is stopped/paused we release.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && player.mediaItemCount > 0) {
            val mediaId = player.currentMediaItem?.mediaId?.toLongOrNull()
            val position = player.currentPosition
            val duration = player.duration.coerceAtLeast(0L)
            if (mediaId != null && position > 0) {
                // A single-row UPDATE is a few milliseconds; block briefly so the write is
                // guaranteed to land before the process is potentially killed.
                runBlocking {
                    try {
                        mediaDao.updateHistoryPosition(
                            mediaId,
                            position,
                            duration,
                            System.currentTimeMillis(),
                        )
                    } catch (e: Exception) {
                        // Log only. This runs inside runBlocking in a lifecycle callback with a
                        // short deadline, so anything heavier (retry, fallback write) risks an ANR
                        // or being killed mid-write. Losing one resume position is the lesser harm.
                        Log.e(TAG, "Failed to persist playback position on task removal", e)
                    }
                }
            }

            // Keep playing in the background; only tear down when nothing is actively playing.
            if (!player.playWhenReady) {
                stopSelf()
            }
        } else {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Detach effects before the session (and its audio session id) goes away, so the manager
        // releases its native AudioEffect handles instead of holding a stale session.
        audioEffectsManager.onAudioSessionIdChanged(0)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
