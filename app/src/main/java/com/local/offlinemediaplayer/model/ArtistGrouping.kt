package com.local.offlinemediaplayer.model

/**
 * Helpers for turning a song's raw `artist` tag into one or more reliable
 * artist entries.
 *
 * Offline metadata is messy in two ways:
 *
 *  1. The tag follows the common "Artist - Title" file-naming convention, where
 *     a SPACE-PADDED dash separates the performer(s) from the song title, e.g.
 *     "Dhanush, Anirudh - Why This Kolaveri Di". Everything from that dash
 *     onward is the title, never an artist, so we drop it.
 *  2. Multiple performers are packed into one tag, comma/semicolon separated,
 *     e.g. "Dhanush, Anirudh". We split these so the song shows up under BOTH
 *     "Dhanush" and "Anirudh".
 *  3. The same performer is spelled inconsistently ("The Beatles", "beatles ",
 *     "THE BEATLES"). We match case-insensitively, ignore stray whitespace and
 *     a leading "The ", then pick the nicest spelling for display.
 *
 * Pipeline:  raw tag --[splitTokens]--> individual names --[key]--> match key
 *            ;  group's names --[displayName]--> label shown in the UI
 *
 * Reliability notes:
 *  - We split the title only on a *spaced* dash (" - ", " – ", " — "). A bare
 *    hyphen is left intact so real names survive: "Jay-Z", "Jean-Michel Jarre",
 *    "A-ha", "AC-DC".
 *  - Fragments with no letters (stray delimiters, track numbers) are discarded
 *    so junk strings never become artists.
 *  - Comma splitting can still over-split a name that legitimately contains a
 *    comma ("Earth, Wind & Fire"); the SEPARATORS regex is the place to tune it.
 */
object ArtistGrouping {

    const val UNKNOWN_ARTIST = "Unknown Artist"

    private val WHITESPACE = Regex("\\s+")

    /**
     * Boundary between the artist portion and the song title in an
     * "Artist - Title" tag: a hyphen / en-dash / em-dash that has whitespace on
     * BOTH sides. The surrounding spaces are what distinguish it from a hyphen
     * inside a real name ("Jay-Z").
     */
    private val TITLE_BOUNDARY = Regex("\\s[-–—]\\s")

    /** Characters that delimit several artists inside one tag. */
    private val SEPARATORS = Regex("\\s*[,;]\\s*")

    /** A usable name must contain at least one letter (any script). */
    private val HAS_LETTER = Regex("\\p{L}")

    /**
     * Splits a raw `artist` tag into the individual, cleaned artist names it
     * mentions: drops the "- Title" suffix, splits the remainder on
     * comma/semicolon, normalizes whitespace and discards letter-less junk.
     * A blank/null tag (or one that reduces to nothing) yields a single
     * [UNKNOWN_ARTIST] entry so those songs still group together.
     */
    fun splitTokens(rawArtist: String?): List<String> {
        val cleaned = rawArtist?.replace(WHITESPACE, " ")?.trim().orEmpty()
        if (cleaned.isEmpty()) return listOf(UNKNOWN_ARTIST)

        // Keep only the artist portion before the first spaced dash; if the tag
        // starts with the boundary, fall back to the whole string.
        val artistPortion = cleaned.split(TITLE_BOUNDARY, limit = 2).first().trim()
            .ifEmpty { cleaned }

        val names = artistPortion.split(SEPARATORS)
            .map { it.trim() }
            .filter { it.isNotEmpty() && HAS_LETTER.containsMatchIn(it) }

        return names.ifEmpty { listOf(UNKNOWN_ARTIST) }
    }

    /**
     * Stable grouping key for a single artist name. Two spellings of the same
     * artist (ignoring case, surrounding/duplicated whitespace, dots and a
     * leading "The ") produce the same key. Pass one name from [splitTokens],
     * not a whole multi-artist tag.
     */
    fun key(name: String?): String {
        val cleaned = name?.replace(WHITESPACE, " ")?.trim().orEmpty()
        if (cleaned.isEmpty()) return UNKNOWN_ARTIST.lowercase()

        // Drop dots so initial-style spellings collapse together
        // ("A.R. Rahman" == "AR Rahman" == "a r rahman"), then re-normalize the
        // whitespace the removal may have exposed. Fall back to the plain
        // lowercase form if stripping dots would leave nothing.
        val lower = cleaned.lowercase()
            .replace(".", "")
            .replace(WHITESPACE, " ")
            .trim()
            .ifEmpty { cleaned.lowercase() }
        // Strip a single leading "the " but never reduce the name to nothing.
        val withoutThe = if (lower.startsWith("the ")) lower.removePrefix("the ").trim() else lower
        return withoutThe.ifEmpty { lower }
    }

    /**
     * Picks the nicest display name for a group of name spellings that share a
     * [key]. Preference: most frequently used spelling; ties broken by the
     * longest variant (so "The Beatles" wins over "Beatles") then alphabetically
     * for determinism.
     */
    fun displayName(names: Collection<String?>): String {
        val cleaned = names
            .map { it?.replace(WHITESPACE, " ")?.trim().orEmpty() }
            .filter { it.isNotEmpty() }

        if (cleaned.isEmpty()) return UNKNOWN_ARTIST

        return cleaned
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key.length }
                    .thenBy { it.key }
            )
            .first()
            .key
    }
}
