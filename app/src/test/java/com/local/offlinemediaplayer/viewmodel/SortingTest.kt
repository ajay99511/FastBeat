package com.local.offlinemediaplayer.viewmodel

import android.net.Uri
import com.local.offlinemediaplayer.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the pure sorting logic in `Sorting.kt`.
 *
 * The functions under test are pure and use no mocks, as the card requires. Robolectric is present
 * only to supply `android.net.Uri`: `MediaFile.uri` is a non-null `Uri`, and on the bare JVM test
 * classpath the mockable `android.jar` strips static initialisers, so `Uri.EMPTY` is literally
 * `null` and Kotlin's non-null parameter check rejects it. Verified, not assumed — see F-23.
 */
@RunWith(RobolectricTestRunner::class)
class SortingTest {
    private fun media(
        id: Long,
        title: String = "t",
        duration: Long = 0,
        dateAdded: Long = 0,
    ) = MediaFile(
        id = id,
        uri = Uri.EMPTY,
        title = title,
        duration = duration,
        isVideo = false,
        dateAdded = dateAdded,
    )

    private fun List<MediaFile>.ids() = map { it.id }

    // ------------------------------------------------------------------ field defaults

    @Test
    fun sortFields_declareTheDirectionThatMakesSenseWhenFirstSelected() {
        // Names/dates read naturally A-Z and oldest-first; "newest" and "most played" only make
        // sense descending. Getting these backwards is a silently wrong-feeling UI, not a crash.
        assertTrue("Title should start A-Z", SortField.TITLE.defaultAscending)
        assertTrue("Runtime should start shortest-first", SortField.DURATION.defaultAscending)
        assertFalse("Date Added should start newest-first", SortField.DATE_ADDED.defaultAscending)
        assertFalse("Play Count should start most-played-first", SortField.MOST_PLAYED.defaultAscending)
    }

    @Test
    fun albumSortFields_declareTheirOwnSensibleDefaults() {
        assertTrue(AlbumSortField.NAME.defaultAscending)
        assertTrue(AlbumSortField.ARTIST.defaultAscending)
        assertFalse("Year should start newest-first", AlbumSortField.YEAR.defaultAscending)
        assertFalse("Song Count should start largest-first", AlbumSortField.SONG_COUNT.defaultAscending)
    }

    @Test
    fun everySortFieldHasANonBlankLabel() {
        SortField.entries.forEach { assertTrue(it.name, it.label.isNotBlank()) }
        AlbumSortField.entries.forEach { assertTrue(it.name, it.label.isNotBlank()) }
    }

    // ------------------------------------------------------------------ SortState.select

    @Test
    fun sortState_singleArgConstructorAdoptsTheFieldsDefaultDirection() {
        assertFalse(SortState(SortField.DATE_ADDED).ascending)
        assertTrue(SortState(SortField.TITLE).ascending)
    }

    @Test
    fun select_sameField_flipsDirection() {
        val state = SortState(SortField.TITLE) // ascending
        val flipped = state.select(SortField.TITLE)

        assertEquals(SortField.TITLE, flipped.field)
        assertFalse(flipped.ascending)
    }

    @Test
    fun select_sameFieldTwice_returnsToTheOriginalDirection() {
        val state = SortState(SortField.TITLE)

        assertEquals(state, state.select(SortField.TITLE).select(SortField.TITLE))
    }

    @Test
    fun select_differentField_appliesTheNewFieldsDefaultRatherThanKeepingTheDirection() {
        // TITLE ascending -> DATE_ADDED must become descending (its default), not stay ascending.
        val state = SortState(SortField.TITLE)

        val next = state.select(SortField.DATE_ADDED)

        assertEquals(SortField.DATE_ADDED, next.field)
        assertFalse("switching fields must not carry the previous direction over", next.ascending)
    }

    @Test
    fun select_differentField_resetsEvenWhenCurrentDirectionWasFlipped() {
        val flipped = SortState(SortField.TITLE).select(SortField.TITLE) // TITLE, descending

        val next = flipped.select(SortField.DURATION)

        assertEquals(SortField.DURATION, next.field)
        assertTrue(next.ascending)
    }

    @Test
    fun select_leavesTheOriginalStateUnchanged() {
        val state = SortState(SortField.TITLE)
        state.select(SortField.TITLE)

        assertTrue("SortState is a value; select must return a new one", state.ascending)
    }

    // ------------------------------------------------------------------ applySort: TITLE

    @Test
    fun applySort_title_ascendingIsAlphabetical() {
        val list = listOf(media(1, "Charlie"), media(2, "alpha"), media(3, "Bravo"))

        val sorted = list.applySort(SortState(SortField.TITLE, ascending = true))

        assertEquals(listOf(2L, 3L, 1L), sorted.ids())
    }

    @Test
    fun applySort_title_descendingIsReverseAlphabetical() {
        val list = listOf(media(1, "Charlie"), media(2, "alpha"), media(3, "Bravo"))

        val sorted = list.applySort(SortState(SortField.TITLE, ascending = false))

        assertEquals(listOf(1L, 3L, 2L), sorted.ids())
    }

    @Test
    fun applySort_title_isCaseInsensitive() {
        // Without lowercase(), ASCII ordering would put every capital before every lowercase,
        // so "apple" would sort after "Zebra".
        val list = listOf(media(1, "Zebra"), media(2, "apple"))

        val sorted = list.applySort(SortState(SortField.TITLE, ascending = true))

        assertEquals(listOf(2L, 1L), sorted.ids())
    }

    // ------------------------------------------------------------------ applySort: DATE_ADDED

    @Test
    fun applySort_dateAdded_defaultPutsNewestFirst() {
        val list = listOf(media(1, dateAdded = 100), media(2, dateAdded = 300), media(3, dateAdded = 200))

        val sorted = list.applySort(SortState(SortField.DATE_ADDED))

        assertEquals(listOf(2L, 3L, 1L), sorted.ids())
    }

    @Test
    fun applySort_dateAdded_ascendingPutsOldestFirst() {
        val list = listOf(media(1, dateAdded = 100), media(2, dateAdded = 300), media(3, dateAdded = 200))

        val sorted = list.applySort(SortState(SortField.DATE_ADDED, ascending = true))

        assertEquals(listOf(1L, 3L, 2L), sorted.ids())
    }

    // ------------------------------------------------------------------ applySort: DURATION

    @Test
    fun applySort_duration_ascendingIsShortestFirst() {
        val list = listOf(media(1, duration = 300), media(2, duration = 100), media(3, duration = 200))

        val sorted = list.applySort(SortState(SortField.DURATION, ascending = true))

        assertEquals(listOf(2L, 3L, 1L), sorted.ids())
    }

    @Test
    fun applySort_duration_descendingIsLongestFirst() {
        val list = listOf(media(1, duration = 300), media(2, duration = 100), media(3, duration = 200))

        val sorted = list.applySort(SortState(SortField.DURATION, ascending = false))

        assertEquals(listOf(1L, 3L, 2L), sorted.ids())
    }

    // ------------------------------------------------------------------ applySort: MOST_PLAYED

    @Test
    fun applySort_mostPlayed_usesTheSuppliedPlayCounts() {
        val list = listOf(media(1), media(2), media(3))
        val counts = mapOf(1L to 5, 2L to 50, 3L to 20)

        val sorted = list.applySort(SortState(SortField.MOST_PLAYED), counts)

        assertEquals(listOf(2L, 3L, 1L), sorted.ids())
    }

    @Test
    fun applySort_mostPlayed_treatsMissingEntriesAsNeverPlayed() {
        val list = listOf(media(1), media(2))
        val counts = mapOf(2L to 3) // id 1 absent

        val sorted = list.applySort(SortState(SortField.MOST_PLAYED), counts)

        assertEquals("an unplayed track counts as 0, not as an error", listOf(2L, 1L), sorted.ids())
    }

    @Test
    fun applySort_mostPlayed_defaultsToAllZeroWhenNoCountsAreSupplied() {
        val list = listOf(media(1), media(2), media(3))

        val sorted = list.applySort(SortState(SortField.MOST_PLAYED))

        assertEquals("all tied at 0, so original order is preserved", listOf(1L, 2L, 3L), sorted.ids())
    }

    @Test
    fun applySort_playCountsAreIgnoredByEveryOtherField() {
        val list = listOf(media(1, "b"), media(2, "a"))
        val counts = mapOf(1L to 999)

        val sorted = list.applySort(SortState(SortField.TITLE, ascending = true), counts)

        assertEquals(listOf(2L, 1L), sorted.ids())
    }

    // ------------------------------------------------------------------ edge cases

    @Test
    fun applySort_emptyListStaysEmptyForEveryField() {
        SortField.entries.forEach { field ->
            listOf(true, false).forEach { ascending ->
                assertTrue(
                    "$field/$ascending",
                    emptyList<MediaFile>().applySort(SortState(field, ascending)).isEmpty(),
                )
            }
        }
    }

    @Test
    fun applySort_singleElementIsReturnedUnchangedForEveryField() {
        val list = listOf(media(1, "only"))

        SortField.entries.forEach { field ->
            listOf(true, false).forEach { ascending ->
                assertEquals("$field/$ascending", listOf(1L), list.applySort(SortState(field, ascending)).ids())
            }
        }
    }

    @Test
    fun applySort_tiedValuesKeepTheirOriginalRelativeOrder() {
        val list = listOf(media(1, duration = 5), media(2, duration = 5), media(3, duration = 5))

        assertEquals(
            "a stable sort must not shuffle equal items",
            listOf(1L, 2L, 3L),
            list.applySort(SortState(SortField.DURATION, ascending = true)).ids(),
        )
    }

    @Test
    fun applySort_tiedValuesKeepOriginalOrderWhenDescendingToo() {
        // `comparator.reversed()` reverses the comparison, not the list, and sortedWith is stable —
        // so equal items must still come back in insertion order, not reversed.
        val list = listOf(media(1, duration = 5), media(2, duration = 5), media(3, duration = 5))

        assertEquals(
            listOf(1L, 2L, 3L),
            list.applySort(SortState(SortField.DURATION, ascending = false)).ids(),
        )
    }

    @Test
    fun applySort_doesNotMutateTheReceiver() {
        val list = listOf(media(1, "c"), media(2, "a"), media(3, "b"))

        list.applySort(SortState(SortField.TITLE, ascending = true))

        assertEquals(listOf(1L, 2L, 3L), list.ids())
    }
}
