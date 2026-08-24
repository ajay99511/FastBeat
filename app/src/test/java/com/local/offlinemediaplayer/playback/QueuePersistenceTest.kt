package com.local.offlinemediaplayer.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.local.offlinemediaplayer.data.db.AppDatabase
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.data.db.PlaybackHistory
import com.local.offlinemediaplayer.model.AudioPlayerState
import com.local.offlinemediaplayer.model.MediaFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Round-trip tests for [QueuePersistence] against a real in-memory Room database and real
 * SharedPreferences.
 *
 * The interrupted-session snapshot in particular had **no coverage at all** before this: it is JSON
 * hand-assembled and hand-parsed, it runs only when a video interrupts music and the process is
 * then killed, and a silent failure there loses the user's whole music session. That is exactly the
 * shape of code that is never exercised in manual testing.
 */
@RunWith(RobolectricTestRunner::class)
class QueuePersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaDao
    private lateinit var persistence: QueuePersistence

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.mediaDao()
        persistence = QueuePersistence(context, dao)
    }

    @After
    fun tearDown() = db.close()

    private fun audio(id: Long) = track(id, isVideo = false)

    private fun video(id: Long) = track(id, isVideo = true)

    private fun track(
        id: Long,
        isVideo: Boolean,
    ) = MediaFile(id = id, uri = Uri.EMPTY, title = "t$id", duration = 1_000, isVideo = isVideo)

    private fun byId(vararg files: MediaFile) = files.associateBy { it.id }

    // ------------------------------------------------------------------ scalar prefs

    @Test
    fun scalarsDefaultToTheDocumentedValuesOnAFreshInstall() {
        assertEquals(0, persistence.queueIndex)
        assertFalse(persistence.shuffleEnabled)
        assertEquals(Player.REPEAT_MODE_OFF, persistence.repeatMode)
        assertNull(persistence.playlistContext)
    }

    @Test
    fun scalarsRoundTrip() {
        persistence.queueIndex = 7
        persistence.shuffleEnabled = true
        persistence.repeatMode = Player.REPEAT_MODE_ALL
        persistence.playlistContext = "ALBUM_42"

        assertEquals(7, persistence.queueIndex)
        assertEquals(true, persistence.shuffleEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, persistence.repeatMode)
        assertEquals("ALBUM_42", persistence.playlistContext)
    }

    @Test
    fun clearingThePlaylistContextRemovesItRatherThanStoringNull() {
        persistence.playlistContext = "ALBUM_42"
        persistence.playlistContext = null

        assertNull(persistence.playlistContext)
    }

    // ------------------------------------------------------------------ queue round trip

    @Test
    fun anAudioQueueSurvivesARoundTripInOrder() =
        runBlocking {
            persistence.saveQueue(listOf(audio(3), audio(1), audio(2)))

            val restored = persistence.loadQueue(byId(audio(1), audio(2), audio(3)))!!
            assertEquals(listOf(3L, 1L, 2L), restored.queue.map { it.id })
        }

    @Test
    fun aQueueContainingAVideoIsNeverWritten() =
        runBlocking {
            persistence.saveQueue(listOf(audio(1), audio(2)))
            persistence.saveQueue(listOf(audio(9), video(8)))

            // The earlier audio queue must still be there, untouched.
            val restored = persistence.loadQueue(byId(audio(1), audio(2), audio(9)))!!
            assertEquals(
                "watching a video must not overwrite the saved music session",
                listOf(1L, 2L),
                restored.queue.map { it.id },
            )
        }

    @Test
    fun loadQueueUsesThePersistedIndex() =
        runBlocking {
            persistence.saveQueue(listOf(audio(1), audio(2), audio(3)))
            persistence.queueIndex = 2

            assertEquals(2, persistence.loadQueue(byId(audio(1), audio(2), audio(3)))!!.index)
        }

    @Test
    fun loadQueueReturnsNullWhenNothingWasEverSaved() =
        runBlocking {
            assertNull(persistence.loadQueue(byId(audio(1))))
        }

    @Test
    fun resumePositionComesFromHistory() =
        runBlocking {
            dao.saveHistory(
                PlaybackHistory(mediaId = 1, position = 4_200, duration = 10_000, timestamp = 1, mediaType = "AUDIO"),
            )

            assertEquals(4_200L, persistence.resumePositionFor(1))
        }

    @Test
    fun resumePositionIsZeroForAnUnknownTrack() =
        runBlocking {
            assertEquals(0L, persistence.resumePositionFor(404))
        }

    // ------------------------------------------------------------------ interrupted session

    private fun session(
        queue: List<MediaFile>,
        index: Int,
        position: Long = 5_000,
    ) = AudioPlayerState(
        queue = queue,
        currentIndex = index,
        position = position,
        isPlaying = true,
        isShuffleEnabled = true,
        repeatMode = Player.REPEAT_MODE_ONE,
    )

    @Test
    fun anInterruptedSessionSurvivesARoundTrip() {
        persistence.saveAudioSession(session(listOf(audio(1), audio(2), audio(3)), index = 1))

        val restored = persistence.loadAudioSession(byId(audio(1), audio(2), audio(3)))!!
        assertEquals(listOf(1L, 2L, 3L), restored.queue.map { it.id })
        assertEquals(1, restored.currentIndex)
        assertEquals(5_000L, restored.position)
        assertEquals(true, restored.isShuffleEnabled)
        assertEquals(Player.REPEAT_MODE_ONE, restored.repeatMode)
    }

    @Test
    fun aRestoredSessionIsAlwaysPaused() {
        persistence.saveAudioSession(session(listOf(audio(1)), index = 0))

        assertFalse(
            "a cold start must never begin playing audio on its own",
            persistence.loadAudioSession(byId(audio(1)))!!.isPlaying,
        )
    }

    /**
     * The current track is located by id, not by index. A track deleted from the library shifts
     * every later index, so trusting the stored index would resume the wrong song.
     */
    @Test
    fun theCurrentTrackIsFoundByIdEvenAfterEarlierTracksDisappear() {
        persistence.saveAudioSession(session(listOf(audio(1), audio(2), audio(3)), index = 2))

        // Track 1 is gone from the library; track 3 is now at index 1, not 2.
        val restored = persistence.loadAudioSession(byId(audio(2), audio(3)))!!
        assertEquals(listOf(2L, 3L), restored.queue.map { it.id })
        assertEquals(1, restored.currentIndex)
        assertEquals(3L, restored.queue[restored.currentIndex].id)
    }

    @Test
    fun videosAreStrippedFromARestoredSession() {
        persistence.saveAudioSession(session(listOf(audio(1), audio(2)), index = 0))

        val restored = persistence.loadAudioSession(byId(audio(1), video(2)))!!
        assertEquals(listOf(1L), restored.queue.map { it.id })
    }

    @Test
    fun aSessionWhoseTracksAllVanishedRestoresAsNull() {
        persistence.saveAudioSession(session(listOf(audio(1), audio(2)), index = 0))

        assertNull(persistence.loadAudioSession(emptyMap()))
    }

    @Test
    fun thereIsNoSessionToLoadOnAFreshInstall() {
        assertNull(persistence.loadAudioSession(byId(audio(1))))
    }

    @Test
    fun clearingRemovesTheSession() {
        persistence.saveAudioSession(session(listOf(audio(1)), index = 0))
        persistence.clearAudioSession()

        assertNull(persistence.loadAudioSession(byId(audio(1))))
    }

    @Test
    fun corruptStoredJsonRestoresAsNullRatherThanCrashing() {
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("saved_audio_session", "{not json")
            .commit()

        assertNull(persistence.loadAudioSession(byId(audio(1))))
    }
}
