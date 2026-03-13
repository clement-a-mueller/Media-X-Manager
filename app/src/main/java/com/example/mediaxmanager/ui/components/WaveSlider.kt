package com.example.mediaxmanager.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun WaveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f)
) {
    var isDragging by remember { mutableStateOf(false) }

    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = if (isDragging) {
            tween(durationMillis = 0)
        } else {
            tween(durationMillis = 100, easing = FastOutLinearInEasing)
        },
        label = "progress"
    )

    val wavePhase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue  = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val waveAmplitude by animateFloatAsState(
        targetValue   = if (isDragging) 12f else 6f,
        animationSpec = tween(200),
        label         = "amplitude"
    )

    var sliderWidth by remember { mutableFloatStateOf(1f) }

    Canvas(
        modifier = modifier
            .height(36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Wait for finger down — don't require it to be unconsumed
                    // so parent scroll containers don't block us
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // Report initial touch position immediately (handles taps too)
                    val initialValue = (down.position.x / sliderWidth).coerceIn(0f, 1f)
                    onValueChange(initialValue)
                    isDragging = true

                    // horizontalDrag will track all subsequent move events until
                    // the pointer is lifted, then return. This is a single unified
                    // gesture handler so there's no competition between recognizers.
                    horizontalDrag(down.id) { change ->
                        val newValue = (change.position.x / sliderWidth).coerceIn(0f, 1f)
                        onValueChange(newValue)
                        change.consume()
                    }

                    // Pointer lifted — gesture complete
                    isDragging = false
                    onValueChangeFinished?.invoke()
                }
            }
    ) {
        sliderWidth = size.width
        val midY        = size.height / 2f
        val progressX   = size.width * animatedValue
        val wavelength  = size.width / 6f
        val amplitudePx = waveAmplitude.dp.toPx()

        // Inactive flat line (right of thumb)
        if (progressX < size.width) {
            drawLine(
                color       = inactiveColor,
                start       = Offset(progressX, midY),
                end         = Offset(size.width, midY),
                strokeWidth = 3.dp.toPx(),
                cap         = StrokeCap.Round
            )
        }

        // Active wavy line (left of thumb)
        if (progressX > 0f) {
            val path  = Path()
            val steps = 300
            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * progressX
                val y = midY + amplitudePx * sin(
                    (x / wavelength) * 2f * Math.PI.toFloat() + wavePhase
                )
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path  = path,
                color = activeColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Thumb dot
        drawCircle(
            color  = activeColor,
            radius = if (isDragging) 8.dp.toPx() else 6.dp.toPx(),
            center = Offset(progressX, midY)
        )
    }
}