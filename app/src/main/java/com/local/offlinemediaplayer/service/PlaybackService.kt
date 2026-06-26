package com.local.offlinemediaplayer.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.local.offlinemediaplayer.data.db.MediaDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @Inject lateinit var mediaDao: MediaDao

    override fun onCreate() {
        super.onCreate()
        val player =
                ExoPlayer.Builder(this)
                        .setAudioAttributes(
                                AudioAttributes.Builder()
                                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                        .setUsage(C.USAGE_MEDIA)
                                        .build(),
                                true
                        )
                        .setHandleAudioBecomingNoisy(true)
                        .setWakeMode(C.WAKE_MODE_LOCAL)
                        .build()

        val sessionActivityPendingIntent =
                android.app.PendingIntent.getActivity(
                        this,
                        0,
                        android.content.Intent(
                                        this,
                                        com.local.offlinemediaplayer.MainActivity::class.java
                                )
                                .apply { putExtra("open_player", true) },
                        android.app.PendingIntent.FLAG_IMMUTABLE or
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )

        mediaSession =
                MediaSession.Builder(this, player)
                        .setSessionActivity(sessionActivityPendingIntent)
                        .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

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
                                System.currentTimeMillis()
                        )
                    } catch (_: Exception) {}
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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
