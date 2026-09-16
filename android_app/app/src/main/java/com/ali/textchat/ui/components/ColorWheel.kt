package com.ali.textchat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * HSV color wheel: hue around the ring, saturation from center to edge (value = 1).
 * Tap or drag anywhere on the wheel to pick a color; emits "#RRGGBB".
 */
@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
    diameter: Dp = 220.dp,
    onPick: (String) -> Unit
) {
    val hueColors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
    )

    fun emit(pos: Offset, sizePx: Float) {
        val r = sizePx / 2f
        val dx = pos.x - r
        val dy = pos.y - r
        val dist = hypot(dx, dy)
        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (angle < 0) angle += 360f
        val sat = (dist / r).coerceIn(0f, 1f)
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(angle, sat, 1f))
        onPick(String.format("#%06X", 0xFFFFFF and argb))
    }

    Canvas(
        modifier
            .size(diameter)
            .pointerInput(Unit) {
                detectTapGestures { pos -> emit(pos, min(size.width, size.height).toFloat()) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> change.consume(); emit(change.position, min(size.width, size.height).toFloat()) }
            }
    ) {
        val d = min(size.width, size.height)
        drawCircle(brush = Brush.sweepGradient(hueColors), radius = d / 2f)
        // white center = low saturation
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), radius = d / 2f), radius = d / 2f)
    }
}
