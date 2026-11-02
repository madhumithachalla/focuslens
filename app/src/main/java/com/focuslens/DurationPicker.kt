package com.focuslens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "1h 30m" / "45m" style label for a minute value — used by the picker's chips. */
fun formatMinutesHM(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/**
 * A horizontally scrollable row of selectable duration chips, each labelled in h:mm format
 * (seconds are always :00 — session lengths are picked to the minute, same as every other
 * duration control in this app; this is the "scrollable range" version of that same picker,
 * not a finer-grained one). Scrolls to the selected value on first composition so it's never
 * off-screen when the picker opens.
 */
@Composable
fun DurationPickerRow(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    maxMinutes: Int,
    stepMinutes: Int = 5,
    dark: Boolean = false
) {
    val options = remember(maxMinutes, stepMinutes) { (stepMinutes..maxMinutes step stepMinutes).toList() }
    val selectedIndex = options.indexOf(selectedMinutes).let { if (it < 0) 0 else it }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedMinutes) {
        listState.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }

    val unselectedText = if (dark) Color(0xFFB0B0B0) else Color.Gray
    val selectedText = if (dark) Color.Black else Color.White
    val selectedBg = if (dark) Color.White else Color(0xFF1F3A34)
    val unselectedBg = if (dark) Color(0xFF2A2A2A) else Color(0xFFE8EFEA)

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
    ) {
        items(options) { minutes ->
            val selected = minutes == selectedMinutes
            Row(
                modifier = Modifier
                    .background(if (selected) selectedBg else unselectedBg, RoundedCornerShape(16.dp))
                    .clickable { onSelect(minutes) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    formatMinutesHM(minutes),
                    color = if (selected) selectedText else unselectedText,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
