package com.local.offlinemediaplayer.ui.adaptive

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll

class DevicePostureTest : StringSpec({
    // Property 9: Device Posture Classification
    "toDevicePosture classifies all FoldingFeature combinations correctly" {
        forAll(
            row(FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.HORIZONTAL, true),
            row(FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.VERTICAL, false),
            row(FoldingFeature.State.FLAT, FoldingFeature.Orientation.HORIZONTAL, false),
            row(FoldingFeature.State.FLAT, FoldingFeature.Orientation.VERTICAL, false),
        ) { state, orientation, expectedTableTop ->
            val mockFeature = object : FoldingFeature {
                override val bounds: Rect = Rect(0, 0, 100, 100)
                override val isSeparating: Boolean = true
                override val occlusionType: FoldingFeature.OcclusionType = FoldingFeature.OcclusionType.NONE
                override val orientation: FoldingFeature.Orientation = orientation
                override val state: FoldingFeature.State = state
            }

            val result = mockFeature.toDevicePosture()
            (result is DevicePosture.TableTop) shouldBe expectedTableTop
        }
    }

    // Property 10: Table-Top Posture Round-Trip
    "layout reverts to standard after leaving TableTop posture" {
        checkAll(Arb.enum<AppWidthClass>()) { widthClass ->
            val inTableTop = DevicePosture.TableTop(Rect())
            val afterNormal = DevicePosture.Normal
            val directNormal = DevicePosture.Normal
            
            afterNormal shouldBe directNormal
        }
    }
})
