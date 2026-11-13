package com.focuslens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared visual parts for the redesign (see PLAN.md). Each one shows a number as a picture,
 * so a screen can say a lot with very little text.
 */

/** A progress ring with one short label in the middle. [fraction] is 0..1. */
@Composable
fun MetricRing(
    fraction: Float,
    centerText: String,
    color: Color,
    track: Color,
    diameter: Dp = 96.dp,
    stroke: Dp = 10.dp
) {
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(
                color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arc, style = Stroke(width = w)
            )
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arc, style = Stroke(width = w, cap = StrokeCap.Round)
            )
        }
        Text(centerText, fontSize = 16.sp)
    }
}

/** A thin rounded bar that fills to [fraction] (0..1). */
@Composable
fun MiniBar(fraction: Float, color: Color, track: Color, height: Dp = 8.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(track, RoundedCornerShape(height / 2))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .background(color, RoundedCornerShape(height / 2))
        )
    }
}

/** A row of dots: [filled] of [total] are filled. Good for "used 3 of 5" limits. */
@Composable
fun DotRow(total: Int, filled: Int, color: Color, track: Color, dot: Dp = 10.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until total) {
            Box(Modifier.size(dot).background(if (i < filled) color else track, CircleShape))
        }
    }
}

/** A simple line through [values], scaled to the largest value. Shows a trend without words. */
@Composable
fun TrendLine(values: List<Float>, color: Color, modifier: Modifier = Modifier.fillMaxWidth().height(64.dp)) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val top = values.maxOrNull()?.coerceAtLeast(0.0001f) ?: 1f
        val stepX = this.size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = this.size.height - (v / top) * this.size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** A big number with one small caption under it. */
@Composable
fun StatTile(value: String, caption: String, fill: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(fill, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Text(value, fontSize = 22.sp)
        Text(caption, fontSize = 12.sp)
    }
}
