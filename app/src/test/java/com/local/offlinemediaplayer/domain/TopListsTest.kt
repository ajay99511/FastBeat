package com.local.offlinemediaplayer.domain

import android.net.Uri
import com.local.offlinemediaplayer.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The three rankings.
 *
 * Robolectric only so [Uri] is a real class; [MediaFile] carries one and nothing here calls a method
 * on it.
 *
 * Two rules are worth more than the arithmetic. **Artists are summed from all their tracks**, not
 * taken from the track ranking, or the list silently becomes "artists with the single best song".
 * And **ties break deterministically**, or rows swap places between emissions and look like data
 * changing when nothing has.
 */
@RunWith(RobolectricTestRunner::class)
class TopListsTest {
    private var nextId = 1L

    private fun track(
        title: String,
        artist: String? = "Artist",
        album: String? = "Album",
    ) = MediaFile(
        id = nextId++,
        uri = Uri.EMPTY,
        title = title,
        artist = artist,
        album = album,
        duration = 0,
        isVideo = false,
    )

    private fun countsFor(vararg pairs: Pair<MediaFile, Int>) = pairs.map { (media, plays) -> media.id to plays }

    // ------------------------------------------------------------------ tracks

    @Test
    fun tracksAreRankedByPlaysDescending() {
        val quiet = track("Quiet")
        val loud = track("Loud")
        val middling = track("Middling")

        val top =
            TopLists
                .from(countsFor(quiet to 1, loud to 9, middling to 4), listOf(quiet, loud, middling))
                .tracks

        assertEquals(listOf("Loud", "Middling", "Quiet"), top.map { it.name })
        assertEquals(listOf(9, 4, 1), top.map { it.plays })
    }

    @Test
    fun aTrackRowCarriesItsMediaSoItCanBePlayed() {
        val song = track("Song")

        val top = TopLists.from(countsFor(song to 3), listOf(song)).tracks

        assertEquals(song, top.single().media)
        assertEquals("Artist", top.single().detail)
    }

    @Test
    fun onlyTheTopFiveAreKept() {
        val library = (1..9).map { track("Track $it") }
        val counts = library.mapIndexed { index, media -> media.id to (index + 1) }

        val snapshot = TopLists.from(counts, library)

        assertEquals(TopLists.TOP_N, snapshot.tracks.size)
        assertEquals("Track 9", snapshot.tracks.first().name)
    }

    // ------------------------------------------------------------------ artists and albums

    /**
     * The rule that makes this worth computing separately. An artist with five moderately played
     * tracks beats one with a single hit, and taking the top of the *track* list would report the
     * opposite.
     */
    @Test
    fun anArtistTotalSumsEveryTrackNotJustItsBest() {
        val hit = track("One Hit", artist = "Soloist")
        val steady = (1..5).map { track("Steady $it", artist = "Band") }
        val library = steady + hit

        val artists =
            TopLists
                .from(countsFor(hit to 10, *steady.map { it to 3 }.toTypedArray()), library)
                .artists

        assertEquals("Band", artists.first().name)
        assertEquals(15, artists.first().plays)
        assertEquals(10, artists[1].plays)
    }

    @Test
    fun anArtistRowSaysHowManyOfItsTracksWerePlayed() {
        val band = (1..3).map { track("Song $it", artist = "Band") }
        val solo = track("Solo", artist = "Soloist")

        val artists = TopLists.from(countsFor(*band.map { it to 2 }.toTypedArray(), solo to 1), band + solo).artists

        assertEquals("3 tracks", artists.first { it.name == "Band" }.detail)
        assertEquals("1 track", artists.first { it.name == "Soloist" }.detail)
    }

    @Test
    fun albumsAreSummedTheSameWay() {
        val a = track("A", album = "First")
        val b = track("B", album = "First")
        val c = track("C", album = "Second")

        val albums = TopLists.from(countsFor(a to 2, b to 3, c to 4), listOf(a, b, c)).albums

        assertEquals("First", albums.first().name)
        assertEquals(5, albums.first().plays)
    }

    /** Artist and album rows are not playable, so they must not pretend to be. */
    @Test
    fun groupedRowsCarryNoMedia() {
        val song = track("Song")

        val snapshot = TopLists.from(countsFor(song to 1), listOf(song))

        assertNull(snapshot.artists.single().media)
        assertNull(snapshot.albums.single().media)
    }

    // ------------------------------------------------------------------ stability

    /**
     * Equal plays must produce one fixed order. Without a tie-break the rows depend on map iteration
     * order and can swap between emissions, which reads as the data changing when it has not.
     */
    @Test
    fun tiesBreakByNameSoTheOrderIsStable() {
        val zed = track("Zebra", artist = "Zed")
        val abe = track("Apple", artist = "Abe")
        val mid = track("Mango", artist = "Mid")

        val first = TopLists.from(countsFor(zed to 4, abe to 4, mid to 4), listOf(zed, abe, mid))
        val second = TopLists.from(countsFor(mid to 4, zed to 4, abe to 4), listOf(mid, zed, abe))

        assertEquals(listOf("Apple", "Mango", "Zebra"), first.tracks.map { it.name })
        assertEquals(first.tracks.map { it.name }, second.tracks.map { it.name })
        assertEquals(first.artists.map { it.name }, second.artists.map { it.name })
    }

    // ------------------------------------------------------------------ missing data

    @Test
    fun aPlayCountForMediaNoLongerInTheLibraryIsDropped() {
        val present = track("Present")

        val snapshot = TopLists.from(listOf(present.id to 3, 9_999L to 100), listOf(present))

        assertEquals(listOf("Present"), snapshot.tracks.map { it.name })
    }

    @Test
    fun nothingPlayedIsAnEmptySnapshotRatherThanAnException() {
        val snapshot = TopLists.from(emptyList(), listOf(track("Song")))

        assertTrue(snapshot.tracks.isEmpty())
        assertTrue(snapshot.artists.isEmpty())
        assertTrue(snapshot.albums.isEmpty())
    }

    @Test
    fun anEmptyLibraryIsAnEmptySnapshot() {
        val snapshot = TopLists.from(listOf(1L to 5), emptyList())

        assertTrue(snapshot.tracks.isEmpty())
    }

    /** Missing tags group together under one heading rather than vanishing from the ranking. */
    @Test
    fun tracksWithNoArtistOrAlbumTagAreGroupedNotDropped() {
        val untagged = track("Untagged", artist = null, album = null)

        val snapshot = TopLists.from(countsFor(untagged to 7), listOf(untagged))

        assertEquals(TopLists.UNKNOWN_ARTIST, snapshot.artists.single().name)
        assertEquals(TopLists.UNKNOWN_ALBUM, snapshot.albums.single().name)
        assertEquals(7, snapshot.artists.single().plays)
    }
}
