package com.example.mediaxmanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.mediaxmanager.media.QueueItem
import kotlin.math.roundToInt

@Composable
fun QueueSheet(
    queue: List<QueueItem>,
    currentTitle: String,
    onDismiss: () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    var sheetHeight by remember { mutableStateOf(0) }
    var isDismissing by remember { mutableStateOf(false) }

// Simple fixed speed: 800ms for full sheet, scales down if already dragged
    val remainingDistance = if (sheetHeight > 0) sheetHeight - offsetY else 0f
    val dismissDuration = ((remainingDistance / sheetHeight.toFloat()) * 800).toInt().coerceAtLeast(100)

    val animatedOffset by animateFloatAsState(
        targetValue = if (isDismissing) sheetHeight.toFloat() else offsetY,
        animationSpec = tween(durationMillis = if (isDismissing) dismissDuration else 80),
        finishedListener = { if (isDismissing) onDismiss() },
        label = "sheetOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .onGloballyPositioned { sheetHeight = it.size.height }
            .offset { IntOffset(0, animatedOffset.roundToInt().coerceAtLeast(0)) }
            .background(
                Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
    ) {
        // Drag area covers entire top section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (offsetY > sheetHeight * 0.3f) {
                                isDismissing = true
                            } else {
                                offsetY = 0f
                            }
                        }
                    ) { _, dragAmount ->
                        if (!isDismissing) {
                            offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Pill indicator
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                "Up Next",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(Modifier.height(12.dp))
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No queue available",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn {
                    itemsIndexed(queue) { index, item ->
                        val isCurrent = item.title == currentTitle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.8f),
                                    style = if (isCurrent) MaterialTheme.typography.bodyMedium
                                    else MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.artist.isNotEmpty()) {
                                    Text(
                                        text = item.artist,
                                        color = Color.White.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}