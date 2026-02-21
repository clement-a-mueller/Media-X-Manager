package com.example.mediaxmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle
        Icon(
            imageVector = Icons.Default.Shuffle,
            contentDescription = "Shuffle",
            tint = if (isShuffling) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier
                .size(28.dp)
                .clickable { onShuffle() }
        )

        // Previous
        TransparentIconButton(
            icon = Icons.Default.SkipPrevious,
            onClick = onPrevious,
            size = 64.dp,
            iconSize = 32.dp
        )

        // Play/Pause
        TransparentIconButton(
            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            onClick = onPlayPause,
            size = 80.dp,
            iconSize = 44.dp,
            backgroundAlpha = 0.35f
        )

        // Next
        TransparentIconButton(
            icon = Icons.Default.SkipNext,
            onClick = onNext,
            size = 64.dp,
            iconSize = 32.dp
        )

        // Repeat
        Icon(
            imageVector = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
            contentDescription = "Repeat",
            tint = if (repeatMode != 0) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier
                .size(28.dp)
                .clickable { onRepeat() }
        )
    }
}

@Composable
fun TransparentIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 64.dp,
    iconSize: Dp = 32.dp,
    backgroundAlpha: Float = 0.2f
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = backgroundAlpha))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}