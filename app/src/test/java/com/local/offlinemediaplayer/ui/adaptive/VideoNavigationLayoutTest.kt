package com.local.offlinemediaplayer.ui.adaptive

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class VideoNavigationLayoutTest : StringSpec({
    // Property 11: Video Navigation Layout Selection
    "videoNavigationLayout returns correct pane mode for all width classes and routes" {
        checkAll(Arb.enum<AppWidthClass>(), Arb.string()) { widthClass, route ->
            val result = videoNavigationLayout(widthClass, route)
            when {
                widthClass == AppWidthClass.Expanded && route == "video_folders" ->
                    result shouldBe VideoNavigationLayout.TwoPane
                else ->
                    result shouldBe VideoNavigationLayout.SinglePane
            }
        }
    }

    // Property 12: Two-Pane Folder Selection
    "selecting folderId sets state and detail content matches folderId" {
        checkAll(Arb.string(1, 20)) { folderId ->
            val state = TwoPaneVideoState()
            val newState = state.selectFolder(folderId)
            newState.selectedFolderId shouldBe folderId
        }
    }
})
