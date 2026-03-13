package com.example.mediaxmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    isShuffling: Boolean = false,
    repeatMode: Int = 0,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {}
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle
        IconButton(
            onClick = onShuffle,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (isShuffling) Color.White else Color.White.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Previous — tonal circle
        TonalIconButton(
            icon = Icons.Default.SkipPrevious,
            onClick = onPrevious,
            size = 56.dp,
            iconSize = 28.dp,
            toneAlpha = 0.15f
        )

        // Play/Pause — M3 FAB-style rounded square
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black.copy(alpha = 0.87f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Next — tonal circle
        TonalIconButton(
            icon = Icons.Default.SkipNext,
            onClick = onNext,
            size = 56.dp,
            iconSize = 28.dp,
            toneAlpha = 0.15f
        )

        // Repeat
        IconButton(
            onClick = onRepeat,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = "Repeat",
                tint = if (repeatMode != 0) Color.White else Color.White.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun TonalIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    iconSize: Dp = 28.dp,
    toneAlpha: Float = 0.15f
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = toneAlpha)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

// Legacy alias kept for any other callers
@Composable
fun TransparentIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 64.dp,
    iconSize: Dp = 32.dp,
    backgroundAlpha: Float = 0.2f
) {
    TonalIconButton(
        icon = icon,
        onClick = onClick,
        size = size,
        iconSize = iconSize,
        toneAlpha = backgroundAlpha
    )
}