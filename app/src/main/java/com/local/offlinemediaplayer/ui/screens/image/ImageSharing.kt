package com.local.offlinemediaplayer.ui.screens.image

import android.content.Intent
import com.local.offlinemediaplayer.model.MediaFile

/**
 * The share intent for one photo.
 *
 * Built as a value rather than launched here so it can be asserted without starting an activity,
 * and so the composable that offers the button stays free of a `Context`.
 *
 * `FLAG_GRANT_READ_URI_PERMISSION` is what makes this work at all: the receiving app has no rights
 * to the user's media store, and without the grant it gets a URI it cannot open. The MIME type is
 * passed through from the store rather than hardcoded to a wildcard, so a receiver that only handles
 * PNG is not offered a JPEG.
 */
internal fun shareIntentFor(image: MediaFile): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = image.mimeType.ifBlank { FALLBACK_MIME_TYPE }
        putExtra(Intent.EXTRA_STREAM, image.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

/** Used when MediaStore did not report a type; every gallery and messenger accepts it. */
internal const val FALLBACK_MIME_TYPE = "image/*"
