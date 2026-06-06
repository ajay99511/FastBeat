package com.local.offlinemediaplayer.ui.adaptive

import android.graphics.Rect
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll

class NowPlayingLayoutTest : StringSpec({
    // Property 6: NowPlaying Layout Type
    "nowPlayingLayoutType returns correct type for all width classes and postures" {
        checkAll(Arb.enum<AppWidthClass>()) { widthClass ->
            val normalResult = nowPlayingLayoutType(widthClass, DevicePosture.Normal)
            val tableTopResult = nowPlayingLayoutType(widthClass, DevicePosture.TableTop(Rect()))
            
            when (widthClass) {
                AppWidthClass.Compact -> normalResult shouldBe NowPlayingLayoutType.Vertical
                else -> normalResult shouldBe NowPlayingLayoutType.TwoColumn
            }
            tableTopResult shouldBe NowPlayingLayoutType.TableTopSplit
        }
    }

    // Property 7: Album Art Size Constraint
    "albumArtMaxSize is always <= 400.dp and <= availableHeight * 0.85" {
        checkAll(Arb.float(1f, 2000f)) { heightDp ->
            val result = computeAlbumArtMaxSize(heightDp)
            result shouldBeLessThanOrEqual 400f
            result shouldBeLessThanOrEqual (heightDp * 0.85f)
        }
    }
})
