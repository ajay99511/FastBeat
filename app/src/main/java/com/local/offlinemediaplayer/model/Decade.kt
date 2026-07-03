package com.local.offlinemediaplayer.model

import android.net.Uri

/**
 * A decade bucket derived from [MediaFile.year] (e.g. startYear = 2010 represents the 2010s).
 * [startYear] == 0 is the catch-all bucket for songs with no known year ("Unknown").
 */
data class Decade(
    val startYear: Int,
    val songCount: Int,
    val albumArtUri: Uri?
) {
    /** User-facing label, e.g. "2010s" or "Unknown". */
    val label: String
        get() = if (startYear <= 0) "Unknown" else "${startYear}s"
}
