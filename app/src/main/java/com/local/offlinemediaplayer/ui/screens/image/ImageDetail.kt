package com.local.offlinemediaplayer.ui.screens.image

import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.common.FormatUtils
import java.util.Locale

/** One labelled fact about a photo. */
internal data class ImageDetail(
    val label: String,
    val value: String,
)

/**
 * The facts worth showing about a photo, omitting the ones MediaStore did not supply.
 *
 * The viewer's info button existed before any of this did — as a bare `Icon` with no click handler,
 * because the query read three columns and there was nothing to put behind it (DS-7.4). It is
 * built from the widened projection.
 *
 * **Unknown fields are dropped rather than rendered as zeroes.** MediaStore does not guarantee
 * `WIDTH`, `BUCKET_DISPLAY_NAME` or the rest on every build, and "0 × 0" or a date in 1970 reads as
 * a fact rather than as an absence — the failure mode the whole phase keeps running into.
 */
internal fun imageDetails(image: MediaFile): List<ImageDetail> =
    buildList {
        add(ImageDetail("Name", image.title))
        dimensionsOf(image)?.let { add(ImageDetail("Dimensions", it)) }
        if (image.size > 0) add(ImageDetail("Size", FormatUtils.formatSize(image.size)))
        if (image.dateAdded > 0) add(ImageDetail("Added", FormatUtils.formatDate(image.dateAdded)))
        if (image.bucketName.isNotBlank()) add(ImageDetail("Folder", image.bucketName))
        if (image.mimeType.isNotBlank()) add(ImageDetail("Type", image.mimeType))
    }

/**
 * Pixel dimensions with the megapixel count that makes them meaningful.
 *
 * "4032 × 3024" is precise and says little; "12.2 MP" is what people compare cameras by. Both, or
 * neither — a photo with one dimension and not the other is not a fact worth printing.
 */
internal fun dimensionsOf(image: MediaFile): String? {
    if (image.width <= 0 || image.height <= 0) return null

    val megapixels = image.width.toLong() * image.height.toLong() / MEGA
    return String.format(Locale.getDefault(), "%d × %d (%.1f MP)", image.width, image.height, megapixels)
}

private const val MEGA = 1_000_000.0
