package com.local.offlinemediaplayer.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder support for a [androidx.compose.foundation.lazy.LazyColumn] whose items map
 * 1:1 to list indices (no headers inside the lazy list).
 *
 * While a drag is in progress [onMove] is invoked for every position the dragged item crosses so
 * the caller can rearrange a local working copy for live visual feedback. When the drag finishes,
 * [onDragEnd] is invoked exactly once with the item's original and final indices so the caller
 * can commit the reorder (e.g. to a ViewModel) as a single operation.
 *
 * Items are dragged from an explicit handle via [dragHandle]; apply [draggingItemOffset] as a
 * translation to the item currently being dragged.
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: (fromIndex: Int, toIndex: Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val state = remember(lazyListState) {
        DragDropState(
            state = lazyListState,
            scope = scope,
            onMove = { from, to -> currentOnMove.value(from, to) },
            onDragEnd = { from, to -> currentOnDragEnd.value(from, to) }
        )
    }
    // Consume auto-scroll requests emitted while dragging near the viewport edges.
    LaunchedEffect(state) {
        while (true) {
            val diff = state.scrollChannel.receive()
            lazyListState.scrollBy(diff)
        }
    }
    return state
}

class DragDropState internal constructor(
    private val state: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragEnd: (fromIndex: Int, toIndex: Int) -> Unit
) {
    /** Index the dragged item currently occupies, or null when no drag is active. */
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal val scrollChannel = Channel<Float>()

    private var draggingItemStartIndex = -1
    private var draggingItemDraggedDelta = 0f
    private var draggingItemInitialOffset = 0

    /** Vertical translation to apply to the item at [draggingItemIndex]. */
    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    internal fun onDragStart(index: Int) {
        val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggingItemIndex = index
        draggingItemStartIndex = index
        draggingItemInitialOffset = item.offset
        draggingItemDraggedDelta = 0f
    }

    internal fun onDragInterrupted() {
        val startIndex = draggingItemStartIndex
        val endIndex = draggingItemIndex
        draggingItemIndex = null
        draggingItemStartIndex = -1
        draggingItemInitialOffset = 0
        draggingItemDraggedDelta = 0f
        if (startIndex >= 0 && endIndex != null && startIndex != endIndex) {
            onDragEnd(startIndex, endIndex)
        }
    }

    internal fun onDrag(offset: Offset) {
        draggingItemDraggedDelta += offset.y
        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size
        val middleOffset = startOffset + (endOffset - startOffset) / 2f

        val targetItem = state.layoutInfo.visibleItemsInfo.find { item ->
            middleOffset.toInt() in item.offset..(item.offset + item.size) &&
                draggingItem.index != item.index
        }
        if (targetItem != null) {
            if (draggingItem.index == state.firstVisibleItemIndex ||
                targetItem.index == state.firstVisibleItemIndex
            ) {
                // Swapping with the first visible item makes LazyColumn follow the moved item;
                // pin the scroll position so the list doesn't jump.
                scope.launch {
                    state.scrollToItem(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
                }
            }
            onMove(draggingItem.index, targetItem.index)
            draggingItemIndex = targetItem.index
        } else {
            val overscroll = when {
                draggingItemDraggedDelta > 0 ->
                    (endOffset - state.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
                draggingItemDraggedDelta < 0 ->
                    (startOffset - state.layoutInfo.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }
            if (overscroll != 0f) {
                scrollChannel.trySend(overscroll)
            }
        }
    }
}

/**
 * Makes the receiving composable a drag handle that starts reordering the item at [index].
 * Drags begin immediately (no long press) since the handle is an explicit affordance.
 */
fun Modifier.dragHandle(dragDropState: DragDropState, index: Int): Modifier =
    pointerInput(dragDropState, index) {
        detectDragGestures(
            onDragStart = { dragDropState.onDragStart(index) },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropState.onDrag(dragAmount)
            },
            onDragEnd = { dragDropState.onDragInterrupted() },
            onDragCancel = { dragDropState.onDragInterrupted() }
        )
    }
