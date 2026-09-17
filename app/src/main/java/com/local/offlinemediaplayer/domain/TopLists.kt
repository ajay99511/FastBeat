package com.local.offlinemediaplayer.domain

import com.local.offlinemediaplayer.model.MediaFile

/** One row of a top list: what it is, and how many plays it got in the window. */
data class TopEntry(
    val name: String,
    val detail: String,
    val plays: Int,
    /** Present only for track rows, so tapping one can play it. Null for artists and albums. */
    val media: MediaFile? = null,
)

/** The three rankings for one window, computed together from a single pass over the play counts. */
data class TopListsSnapshot(
    val tracks: List<TopEntry> = emptyList(),
    val artists: List<TopEntry> = emptyList(),
    val albums: List<TopEntry> = emptyList(),
)

/**
 * Ranks tracks, artists and albums by plays in a window.
 *
 * The screen previously showed exactly two tracks — "Current Obsession" and "All Time #1" — while
 * the data behind them was a full ranking that was computed and then thrown away. This keeps the
 * ranking.
 *
 * **Artists and albums are summed from tracks, not taken from the track ranking.** A track outside
 * the track top ten still counts towards its artist's total, and ignoring that would rank artists
 * by their single best song, which is a different and much less interesting question.
 *
 * **Ties break by name, not by arbitrary order.** Two artists on four plays each must not swap
 * places between emissions; a list that reorders itself while the user is reading it looks like
 * data changing when nothing has.
 */
object TopLists {
    /** How many rows each list shows. */
    const val TOP_N = 5

    const val UNKNOWN_ARTIST = "Unknown artist"
    const val UNKNOWN_ALBUM = "Unknown album"

    /**
     * @param playCounts plays per media id in the window, in any order.
     * @param library every track the app knows about, used to resolve ids to names.
     *
     * Play counts whose media id is no longer in [library] are dropped. That is the deleted-file
     * case: `cleanupDeletedMedia` removes the play events too, so such rows only exist in the gap
     * between a file disappearing from MediaStore and the next cleanup — and a top list naming a
     * track that cannot be played is worse than a shorter list.
     */
    fun from(
        playCounts: List<Pair<Long, Int>>,
        library: List<MediaFile>,
    ): TopListsSnapshot {
        if (playCounts.isEmpty() || library.isEmpty()) return TopListsSnapshot()

        val byId = library.associateBy { it.id }
        val played = playCounts.mapNotNull { (id, plays) -> byId[id]?.let { it to plays } }
        if (played.isEmpty()) return TopListsSnapshot()

        return TopListsSnapshot(
            tracks =
                played
                    .map { (media, plays) ->
                        TopEntry(
                            name = media.title,
                            detail = media.artist ?: UNKNOWN_ARTIST,
                            plays = plays,
                            media = media,
                        )
                    }.rankedTopN(),
            artists = played.groupedBy { it.artist ?: UNKNOWN_ARTIST }.rankedTopN(),
            albums = played.groupedBy { it.album ?: UNKNOWN_ALBUM }.rankedTopN(),
        )
    }

    /**
     * Sums plays per group and reports how many distinct tracks contributed, which is what makes an
     * artist row readable: "42 plays" alone cannot distinguish one song on repeat from a whole
     * discography.
     */
    private fun List<Pair<MediaFile, Int>>.groupedBy(key: (MediaFile) -> String): List<TopEntry> =
        groupBy { (media, _) -> key(media) }
            .map { (name, entries) ->
                val trackCount = entries.distinctBy { (media, _) -> media.id }.size
                TopEntry(
                    name = name,
                    detail = if (trackCount == 1) "1 track" else "$trackCount tracks",
                    plays = entries.sumOf { (_, plays) -> plays },
                )
            }

    /** Busiest first, ties broken by name so the ordering is total and therefore stable. */
    private fun List<TopEntry>.rankedTopN(): List<TopEntry> =
        sortedWith(compareByDescending<TopEntry> { it.plays }.thenBy { it.name })
            .take(TOP_N)
}
