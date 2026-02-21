package com.example.mediaxmanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.example.mediaxmanager.ui.screens.LocalTrack
import kotlin.math.roundToInt

@Composable
fun QueueSheet(
    queue: List<QueueItem>,
    currentTitle: String,
    isSpotify: Boolean = false,
    localQueue: List<LocalTrack> = emptyList(),
    onRemoveFromQueue: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    var sheetHeight by remember { mutableStateOf(0) }
    var isDismissing by remember { mutableStateOf(false) }

    val remainingDistance = if (sheetHeight > 0) sheetHeight - offsetY else 0f
    val dismissDuration = ((remainingDistance / sheetHeight.toFloat()) * 800).toInt().coerceAtLeast(100)

    val animatedOffset by animateFloatAsState(
        targetValue = if (isDismissing) sheetHeight.toFloat() else offsetY,
        animationSpec = tween(durationMillis = if (isDismissing) dismissDuration else 80),
        finishedListener = { if (isDismissing) onDismiss() },
        label = "sheetOffset"
    )

    val displayQueue = remember(isSpotify, localQueue, queue) {
        if (!isSpotify && localQueue.isNotEmpty())
            localQueue.map { QueueItem(it.title, it.artist) }
        else
            queue
    }

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
        // Drag handle
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
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Up Next",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                if (!isSpotify && localQueue.isNotEmpty()) {
                    Text(
                        "${localQueue.size} track${if (localQueue.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isSpotify) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Queue unavailable for Spotify",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (displayQueue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
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
                    itemsIndexed(displayQueue) { index, item ->
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
                            if (onRemoveFromQueue != null && localQueue.isNotEmpty()) {
                                IconButton(
                                    onClick = { onRemoveFromQueue(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
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