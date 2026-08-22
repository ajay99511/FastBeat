package com.local.offlinemediaplayer.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.local.offlinemediaplayer.data.db.AppDatabase
import com.local.offlinemediaplayer.data.db.BookmarkEntity
import com.local.offlinemediaplayer.data.db.MediaDao
import com.local.offlinemediaplayer.data.db.PlayEvent
import com.local.offlinemediaplayer.data.db.PlaybackHistory
import com.local.offlinemediaplayer.data.db.PlaylistMediaCrossRef
import com.local.offlinemediaplayer.data.db.QueueItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Tests for [PlaylistRepository] against a real in-memory Room database and a real filesystem
 * (Robolectric's temp `filesDir`), rather than a mocked DAO.
 *
 * A mocked DAO here would assert that the repository *calls* certain methods, which is close to
 * asserting that the code is written the way it is written. The behaviour worth protecting is what
 * ends up in the database — duplicate playlists not appearing, legacy JSON landing intact, deleted
 * media leaving no dangling references — so the DAO is real.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaDao
    private lateinit var context: Context
    private lateinit var repo: PlaylistRepository
    private lateinit var legacyFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.mediaDao()
        repo = PlaylistRepository(context, dao)
        legacyFile = File(context.filesDir, "playlists.json")
        legacyFile.delete()
    }

    @After
    fun tearDown() {
        legacyFile.delete()
        db.close()
    }

    // ---------------------------------------------------------------- duplicate prevention

    @Test
    fun createPlaylist_createsThePlaylist() =
        runBlocking {
            repo.createPlaylist("Road trip", isVideo = false)

            assertEquals(listOf("Road trip"), repo.playlistsFlow.first().map { it.name })
        }

    @Test
    fun createPlaylist_isIdempotentForTheSameNameAndType() =
        runBlocking {
            repo.createPlaylist("Favorites", isVideo = false)
            repo.createPlaylist("Favorites", isVideo = false)
            repo.createPlaylist("Favorites", isVideo = false)

            assertEquals(1, repo.playlistsFlow.first().size)
        }

    @Test
    fun createPlaylist_treatsAudioAndVideoNamesAsDistinct() =
        runBlocking {
            repo.createPlaylist("Favorites", isVideo = false)
            repo.createPlaylist("Favorites", isVideo = true)

            assertEquals(
                "the same name may exist once for audio and once for video",
                2,
                repo.playlistsFlow.first().size,
            )
        }

    @Test
    fun ensureDefaultPlaylists_canBeCalledRepeatedlyWithoutDuplicating() =
        runBlocking {
            repo.ensureDefaultPlaylists()
            repo.ensureDefaultPlaylists()

            val playlists = repo.playlistsFlow.first()
            assertEquals(2, playlists.size)
            assertEquals(setOf("Favorites", "Love"), playlists.map { it.name }.toSet())
        }

    // ---------------------------------------------------------------- getOrCreatePlaylistId

    @Test
    fun getOrCreatePlaylistId_createsOnFirstCall() =
        runBlocking {
            val id = repo.getOrCreatePlaylistId("Favorites", isVideo = false)

            assertNotNull(id)
            assertEquals(1, repo.playlistsFlow.first().size)
        }

    @Test
    fun getOrCreatePlaylistId_returnsTheSameIdOnSubsequentCalls() =
        runBlocking {
            val first = repo.getOrCreatePlaylistId("Favorites", isVideo = false)
            val second = repo.getOrCreatePlaylistId("Favorites", isVideo = false)

            assertEquals(first, second)
            assertEquals("must not create a second playlist", 1, repo.playlistsFlow.first().size)
        }

    @Test
    fun getOrCreatePlaylistId_findsAPlaylistCreatedByCreatePlaylist() =
        runBlocking {
            repo.createPlaylist("Favorites", isVideo = false)
            val existingId =
                repo.playlistsFlow
                    .first()
                    .single()
                    .id

            assertEquals(existingId, repo.getOrCreatePlaylistId("Favorites", isVideo = false))
        }

    // ---------------------------------------------------------------- legacy migration

    private fun writeLegacy(json: String) = legacyFile.writeText(json)

    @Test
    fun migrateLegacyData_importsPlaylistsUsingTheOldStringItemsField() =
        runBlocking {
            writeLegacy(
                """
                [{"id":"p1","name":"Old Mix","items":["10","20","30"],
                  "createdAt":1234,"isVideo":false}]
                """.trimIndent(),
            )

            repo.migrateLegacyData()

            val playlist = repo.playlistsFlow.first().single()
            assertEquals("Old Mix", playlist.name)
            assertEquals(1234L, playlist.createdAt)
            assertFalse(playlist.isVideo)
            assertEquals(listOf(10L, 20L, 30L), playlist.mediaIds)
        }

    @Test
    fun migrateLegacyData_alsoAcceptsTheNumericMediaIdsField() =
        runBlocking {
            writeLegacy(
                """[{"id":"p1","name":"Mix","mediaIds":[7,8],"createdAt":1,"isVideo":true}]""",
            )

            repo.migrateLegacyData()

            val playlist = repo.playlistsFlow.first().single()
            assertTrue(playlist.isVideo)
            assertEquals(listOf(7L, 8L), playlist.mediaIds)
        }

    @Test
    fun migrateLegacyData_skipsNonNumericIdsInsteadOfFailingTheWholeImport() =
        runBlocking {
            writeLegacy(
                """[{"id":"p1","name":"Mix","items":["10","not-an-id","20"],
                "createdAt":1,"isVideo":false}]""",
            )

            repo.migrateLegacyData()

            assertEquals(
                "one unparseable id must not discard the rest of the playlist",
                listOf(10L, 20L),
                repo.playlistsFlow
                    .first()
                    .single()
                    .mediaIds,
            )
        }

    @Test
    fun migrateLegacyData_deduplicatesIdsAcrossBothFields() =
        runBlocking {
            writeLegacy(
                """[{"id":"p1","name":"Mix","items":["5","5"],"mediaIds":[5,6],
                "createdAt":1,"isVideo":false}]""",
            )

            repo.migrateLegacyData()

            assertEquals(
                listOf(5L, 6L),
                repo.playlistsFlow
                    .first()
                    .single()
                    .mediaIds,
            )
        }

    @Test
    fun migrateLegacyData_deletesTheLegacyFileOnSuccessSoItRunsOnlyOnce() =
        runBlocking {
            writeLegacy("""[{"id":"p1","name":"Mix","items":["1"],"createdAt":1,"isVideo":false}]""")

            repo.migrateLegacyData()

            assertFalse("legacy file must be removed after a successful import", legacyFile.exists())
        }

    @Test
    fun migrateLegacyData_keepsTheFileWhenTheJsonIsMalformed() =
        runBlocking {
            writeLegacy("{ this is not valid json")

            repo.migrateLegacyData()

            assertTrue(
                "a failed import must not destroy the user's only copy of their playlists",
                legacyFile.exists(),
            )
            assertTrue(repo.playlistsFlow.first().isEmpty())
        }

    @Test
    fun migrateLegacyData_isANoOpWhenThereIsNoLegacyFile() =
        runBlocking {
            assertFalse(legacyFile.exists())

            repo.migrateLegacyData()

            assertTrue(repo.playlistsFlow.first().isEmpty())
        }

    // ---------------------------------------------------------------- track updates

    @Test
    fun updatePlaylistTracks_replacesTheContentsAndKeepsTheGivenOrder() =
        runBlocking {
            val id = repo.getOrCreatePlaylistId("Mix", isVideo = false)
            repo.updatePlaylistTracks(id, listOf(1, 2, 3))

            repo.updatePlaylistTracks(id, listOf(30, 10, 20))

            assertEquals(
                listOf(30L, 10L, 20L),
                repo.playlistsFlow
                    .first()
                    .single()
                    .mediaIds,
            )
        }

    @Test
    fun updatePlaylistTracks_canEmptyAPlaylist() =
        runBlocking {
            val id = repo.getOrCreatePlaylistId("Mix", isVideo = false)
            repo.updatePlaylistTracks(id, listOf(1, 2))

            repo.updatePlaylistTracks(id, emptyList())

            assertTrue(
                repo.playlistsFlow
                    .first()
                    .single()
                    .mediaIds
                    .isEmpty(),
            )
        }

    @Test
    fun addAndRemoveSong_areReflectedInTheFlow() =
        runBlocking {
            val id = repo.getOrCreatePlaylistId("Mix", isVideo = false)

            repo.addSongToPlaylist(id, 42)
            assertEquals(
                listOf(42L),
                repo.playlistsFlow
                    .first()
                    .single()
                    .mediaIds,
            )

            repo.removeSongFromPlaylist(id, 42)
            assertTrue(
                repo.playlistsFlow
                    .first()
                    .single()
                    .mediaIds
                    .isEmpty(),
            )
        }

    @Test
    fun renameAndDeletePlaylist_areReflectedInTheFlow() =
        runBlocking {
            val id = repo.getOrCreatePlaylistId("Mix", isVideo = false)

            repo.renamePlaylist(id, "Renamed")
            assertEquals(
                "Renamed",
                repo.playlistsFlow
                    .first()
                    .single()
                    .name,
            )

            repo.deletePlaylist(id)
            assertTrue(repo.playlistsFlow.first().isEmpty())
        }

    // ---------------------------------------------------------------- cleanupDeletedMedia

    /**
     * When a file disappears from the device, every trace of it must go. A reference left behind in
     * any one of these six tables resurfaces as a phantom entry the user cannot play or remove.
     */
    @Test
    fun cleanupDeletedMedia_removesEveryReferenceToTheDeletedMedia() =
        runBlocking {
            val gone = 1L
            val kept = 2L
            val playlistId = repo.getOrCreatePlaylistId("Mix", isVideo = false)

            listOf(gone, kept).forEach { id ->
                dao.saveHistory(
                    PlaybackHistory(
                        mediaId = id,
                        position = 1,
                        duration = 2,
                        timestamp = 3,
                        mediaType = "VIDEO",
                    ),
                )
                dao.initAnalytics(id, timestamp = 1)
                dao.addMediaToPlaylist(PlaylistMediaCrossRef(playlistId, id, addedAt = id))
                dao.addBookmark(BookmarkEntity(mediaId = id, timestamp = 1, label = "b"))
                dao.logPlayEvent(PlayEvent(mediaId = id, timestamp = 1))
            }
            dao.replaceQueue(listOf(QueueItemEntity(gone, 0), QueueItemEntity(kept, 1)))

            repo.cleanupDeletedMedia(listOf(gone))

            assertNull("playback history", dao.getHistory(gone))
            assertNull("analytics", dao.getAnalytics(gone))
            assertTrue("bookmarks", dao.getBookmarks(gone).first().isEmpty())
            assertEquals("queue", listOf(kept), dao.getSavedQueue().map { it.mediaId })
            assertEquals(
                "playlist membership",
                listOf(kept),
                repo.playlistsFlow
                    .first()
                    .single()
                    .mediaIds,
            )
        }

    @Test
    fun cleanupDeletedMedia_leavesOtherMediaCompletelyIntact() =
        runBlocking {
            val kept = 2L
            dao.saveHistory(
                PlaybackHistory(
                    mediaId = kept,
                    position = 55,
                    duration = 100,
                    timestamp = 3,
                    mediaType = "AUDIO",
                ),
            )
            dao.initAnalytics(kept, timestamp = 1)
            dao.addBookmark(BookmarkEntity(mediaId = kept, timestamp = 9, label = "keep me"))

            repo.cleanupDeletedMedia(listOf(1L, 3L, 4L))

            assertEquals(55L, dao.getHistory(kept)!!.position)
            assertNotNull(dao.getAnalytics(kept))
            assertEquals(
                "keep me",
                dao
                    .getBookmarks(kept)
                    .first()
                    .single()
                    .label,
            )
        }

    @Test
    fun cleanupDeletedMedia_handlesAnEmptyIdListWithoutDeletingAnything() =
        runBlocking {
            dao.saveHistory(
                PlaybackHistory(
                    mediaId = 1,
                    position = 1,
                    duration = 2,
                    timestamp = 3,
                    mediaType = "VIDEO",
                ),
            )

            repo.cleanupDeletedMedia(emptyList())

            assertNotNull("an empty deletion list must be a no-op", dao.getHistory(1))
        }

    // ---------------------------------------------------------------- flow mapping

    @Test
    fun playlistsFlow_ordersMediaIdsByWhenTheyWereAdded() =
        runBlocking {
            val id = repo.getOrCreatePlaylistId("Mix", isVideo = false)
            dao.addMediaToPlaylist(PlaylistMediaCrossRef(id, 3, addedAt = 300))
            dao.addMediaToPlaylist(PlaylistMediaCrossRef(id, 1, addedAt = 100))
            dao.addMediaToPlaylist(PlaylistMediaCrossRef(id, 2, addedAt = 200))

            assertEquals(
                listOf(1L, 2L, 3L),
                repo.playlistsFlow
                    .first()
                    .single()
                    .mediaIds,
            )
        }
}
