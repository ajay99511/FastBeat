package com.local.offlinemediaplayer.playback

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.local.offlinemediaplayer.data.AppPreferencesManager
import com.local.offlinemediaplayer.data.db.AppDatabase
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.data.db.PlaybackHistory
import com.local.offlinemediaplayer.model.AudioPlayerState
import com.local.offlinemediaplayer.model.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Round-trip tests for [QueuePersistence] against a real in-memory Room database and a real
 * Preferences DataStore.
 *
 * The interrupted-session snapshot in particular had **no coverage at all** before this: it is JSON
 * hand-assembled and hand-parsed, it runs only when a video interrupts music and the process is
 * then killed, and a silent failure there loses the user's whole music session. That is exactly the
 * shape of code that is never exercised in manual testing.
 */
@RunWith(RobolectricTestRunner::class)
class QueuePersistenceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var dao: MediaDao
    private lateinit var appPrefs: AppPreferencesManager
    private lateinit var persistence: QueuePersistence
    private lateinit var dataStoreScope: CoroutineScope

    /**
     * Each test gets its **own** DataStore file. DataStore refuses two live instances over one
     * file, and a shared file would also leak state between methods now that there is no
     * synchronous `clear()`. The scope is cancelled in [tearDown] to release the file again.
     */
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.mediaDao()
        dataStoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        appPrefs =
            AppPreferencesManager(
                PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                    File(tempFolder.newFolder(), "app_prefs.preferences_pb")
                },
            )
        persistence = QueuePersistence(appPrefs, dao)
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreScope.cancel()
    }

    private fun audio(id: Long) = track(id, isVideo = false)

    private fun video(id: Long) = track(id, isVideo = true)

    private fun track(
        id: Long,
        isVideo: Boolean,
    ) = MediaFile(id = id, uri = Uri.EMPTY, title = "t$id", duration = 1_000, isVideo = isVideo)

    private fun byId(vararg files: MediaFile) = files.associateBy { it.id }

    // ------------------------------------------------------------------ scalar prefs

    @Test
    fun scalarsDefaultToTheDocumentedValuesOnAFreshInstall() =
        runBlocking {
            assertEquals(0, persistence.getQueueIndex())
            assertFalse(persistence.getShuffleEnabled())
            assertEquals(Player.REPEAT_MODE_OFF, persistence.getRepeatMode())
            assertNull(persistence.getPlaylistContext())
        }

    @Test
    fun scalarsRoundTrip() =
        runBlocking {
            persistence.setQueueIndex(7)
            persistence.setShuffleEnabled(true)
            persistence.setRepeatMode(Player.REPEAT_MODE_ALL)
            persistence.setPlaylistContext("ALBUM_42")

            assertEquals(7, persistence.getQueueIndex())
            assertEquals(true, persistence.getShuffleEnabled())
            assertEquals(Player.REPEAT_MODE_ALL, persistence.getRepeatMode())
            assertEquals("ALBUM_42", persistence.getPlaylistContext())
        }

    @Test
    fun clearingThePlaylistContextRemovesItRatherThanStoringNull() =
        runBlocking {
            persistence.setPlaylistContext("ALBUM_42")
            persistence.setPlaylistContext(null)

            assertNull(persistence.getPlaylistContext())
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
            persistence.setQueueIndex(2)

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
    fun anInterruptedSessionSurvivesARoundTrip() =
        runBlocking {
            persistence.saveAudioSession(session(listOf(audio(1), audio(2), audio(3)), index = 1))

            val restored = persistence.loadAudioSession(byId(audio(1), audio(2), audio(3)))!!
            assertEquals(listOf(1L, 2L, 3L), restored.queue.map { it.id })
            assertEquals(1, restored.currentIndex)
            assertEquals(5_000L, restored.position)
            assertEquals(true, restored.isShuffleEnabled)
            assertEquals(Player.REPEAT_MODE_ONE, restored.repeatMode)
        }

    @Test
    fun aRestoredSessionIsAlwaysPaused() =
        runBlocking {
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
    fun theCurrentTrackIsFoundByIdEvenAfterEarlierTracksDisappear() =
        runBlocking {
            persistence.saveAudioSession(session(listOf(audio(1), audio(2), audio(3)), index = 2))

            // Track 1 is gone from the library; track 3 is now at index 1, not 2.
            val restored = persistence.loadAudioSession(byId(audio(2), audio(3)))!!
            assertEquals(listOf(2L, 3L), restored.queue.map { it.id })
            assertEquals(1, restored.currentIndex)
            assertEquals(3L, restored.queue[restored.currentIndex].id)
        }

    @Test
    fun videosAreStrippedFromARestoredSession() =
        runBlocking {
            persistence.saveAudioSession(session(listOf(audio(1), audio(2)), index = 0))

            val restored = persistence.loadAudioSession(byId(audio(1), video(2)))!!
            assertEquals(listOf(1L), restored.queue.map { it.id })
        }

    @Test
    fun aSessionWhoseTracksAllVanishedRestoresAsNull() =
        runBlocking {
            persistence.saveAudioSession(session(listOf(audio(1), audio(2)), index = 0))

            assertNull(persistence.loadAudioSession(emptyMap()))
        }

    @Test
    fun thereIsNoSessionToLoadOnAFreshInstall() =
        runBlocking {
            assertNull(persistence.loadAudioSession(byId(audio(1))))
        }

    @Test
    fun clearingRemovesTheSession() =
        runBlocking {
            persistence.saveAudioSession(session(listOf(audio(1)), index = 0))
            persistence.clearAudioSession()

            assertNull(persistence.loadAudioSession(byId(audio(1))))
        }

    @Test
    fun corruptStoredJsonRestoresAsNullRatherThanCrashing() =
        runBlocking {
            appPrefs.setSavedAudioSession("{not json")

            assertNull(persistence.loadAudioSession(byId(audio(1))))
        }
}
