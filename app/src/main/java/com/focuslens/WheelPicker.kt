package com.focuslens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val WHEEL_ITEM_HEIGHT = 40.dp

/**
 * A drag-or-scroll number picker, like the time picker: drag or fling the list, and the value
 * in the middle highlight is the one selected. [onChange] runs when the list stops moving.
 * [value] is clamped to [min]..[max].
 */
// rememberSnapFlingBehavior is still marked experimental in this Compose version.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() }
) {
    val count = (max - min + 1).coerceAtLeast(1)
    val shown = value.coerceIn(min, max)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = shown - min)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // Follow a value that changed elsewhere, but never fight a finger that is still moving.
    LaunchedEffect(shown) {
        if (!listState.isScrollInProgress) {
            val target = shown - min
            val centred = centredIndex(listState)
            if (centred != target) listState.scrollToItem(target)
        }
    }

    // When the list settles, the item in the middle is the new value.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { moving ->
            if (!moving) {
                val picked = centredIndex(listState)?.let { (it + min).coerceIn(min, max) }
                if (picked != null && picked != shown) onChange(picked)
            }
        }
    }

    Box(
        modifier = modifier.width(140.dp).height(WHEEL_ITEM_HEIGHT * 3),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = WHEEL_ITEM_HEIGHT),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(count) { index ->
                val v = min + index
                val selected = v == shown
                Box(
                    modifier = Modifier.fillMaxWidth().height(WHEEL_ITEM_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        format(v),
                        fontSize = if (selected) 18.sp else 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }
            }
        }
        // The highlight band marks the selected row in the middle.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )
    }
}

/** Index (in the list) of the item nearest the vertical middle of the visible list, or null. */
private fun centredIndex(state: androidx.compose.foundation.lazy.LazyListState): Int? {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return visible.minByOrNull { abs((it.offset + it.size / 2) - middle) }?.index
}
