package com.local.offlinemediaplayer.viewmodel

import com.local.offlinemediaplayer.model.MediaFile

/**
 * A sortable field shown in a sort menu. Each field carries its display label
 * and the direction that makes sense when the field is first selected
 * (e.g. Title starts A-Z, Date Added starts newest-first).
 */
interface SortableField {
    val label: String
    val defaultAscending: Boolean
}

/**
 * Sort state = which field plus the current direction. Selecting the already
 * active field flips the direction; selecting a new field applies that
 * field's default direction.
 */
data class SortState<T>(
    val field: T,
    val ascending: Boolean
) where T : Enum<T>, T : SortableField {

    constructor(field: T) : this(field, field.defaultAscending)

    fun select(clicked: T): SortState<T> =
        if (clicked == field) copy(ascending = !ascending) else SortState(clicked)
}

/** Sort fields for the audio, video and movie library lists. */
enum class SortField(
    override val label: String,
    override val defaultAscending: Boolean
) : SortableField {
    DATE_ADDED("Date Added", false),
    TITLE("Title", true),
    DURATION("Runtime", true),
    MOST_PLAYED("Play Count", false)
}

/**
 * Applies a [SortState] to a media list. [playCounts] is only consulted for
 * [SortField.MOST_PLAYED].
 */
fun List<MediaFile>.applySort(
    state: SortState<SortField>,
    playCounts: Map<Long, Int> = emptyMap()
): List<MediaFile> {
    val comparator: Comparator<MediaFile> = when (state.field) {
        SortField.DATE_ADDED -> compareBy { it.dateAdded }
        SortField.TITLE -> compareBy { it.title.lowercase() }
        SortField.DURATION -> compareBy { it.duration }
        SortField.MOST_PLAYED -> compareBy { playCounts[it.id] ?: 0 }
    }
    return sortedWith(if (state.ascending) comparator else comparator.reversed())
}

/** Sort fields for the album grid/list. */
enum class AlbumSortField(
    override val label: String,
    override val defaultAscending: Boolean
) : SortableField {
    NAME("Name", true),
    ARTIST("Artist", true),
    YEAR("Year", false),
    SONG_COUNT("Song Count", false)
}
