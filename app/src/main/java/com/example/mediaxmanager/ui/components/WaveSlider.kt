package com.example.mediaxmanager.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.math.abs

@Composable
fun WaveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f)
) {
    // Animated progress — snaps fast on big jumps, smooth on small ticks
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = if (abs(value - 0f) > 0.05f) {
            tween(durationMillis = 100, easing = FastOutLinearInEasing)
        } else {
            tween(durationMillis = 400, easing = LinearEasing)
        },
        label = "progress"
    )

    val wavePhase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Wave amplitude pulses when seeking
    var isSeeking by remember { mutableStateOf(false) }
    val waveAmplitude by animateFloatAsState(
        targetValue = if (isSeeking) 12f else 6f,
        animationSpec = tween(200),
        label = "amplitude"
    )

    var sliderWidth by remember { mutableFloatStateOf(1f) }

    Canvas(
        modifier = modifier
            .height(36.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isSeeking = true },
                    onDragEnd = { isSeeking = false },
                    onDragCancel = { isSeeking = false },
                    onHorizontalDrag = { change, _ ->
                        val newValue = (change.position.x / sliderWidth).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newValue = (offset.x / sliderWidth).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
    ) {
        sliderWidth = size.width
        val midY = size.height / 2f
        val progressX = size.width * animatedValue
        val wavelength = size.width / 6f
        val amplitudePx = waveAmplitude.dp.toPx()

        // Inactive flat line
        if (progressX < size.width) {
            drawLine(
                color = inactiveColor,
                start = Offset(progressX, midY),
                end = Offset(size.width, midY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Active wavy line
        if (progressX > 0f) {
            val path = Path()
            val steps = 300
            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * progressX
                val y = midY + amplitudePx * sin(
                    (x / wavelength) * 2f * Math.PI.toFloat() + wavePhase
                )
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = activeColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Thumb — slightly bigger while seeking
        drawCircle(
            color = activeColor,
            radius = if (isSeeking) 8.dp.toPx() else 6.dp.toPx(),
            center = Offset(progressX, midY)
        )
    }
}