package com.example.mediaxmanager.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.components.AlbumArt
import com.example.mediaxmanager.ui.components.FullscreenArtwork
import com.example.mediaxmanager.ui.components.PlaybackControls
import com.example.mediaxmanager.ui.components.QueueSheet
import com.example.mediaxmanager.ui.components.WaveSlider
import com.example.mediaxmanager.ui.theme.AppStyle
import androidx.compose.runtime.LaunchedEffect

fun extractDominantColor(bitmap: Bitmap?): Color {
    if (bitmap == null) return Color(0xFF1C1B1F)
    val palette = Palette.from(bitmap).generate()
    val argb = palette.getDarkVibrantColor(
        palette.getDominantColor(0xFF1C1B1F.toInt())
    )
    return Color(argb)
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun HomeScreen(viewModel: MediaViewModel, appStyle: AppStyle) {
    val state by viewModel.mediaState.collectAsStateWithLifecycle()
    var showFullscreenArt by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val dominantColor = remember(state.artwork) {
        extractDominantColor(state.artwork)
    }
    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(800),
        label = "bg"
    )
    val backgroundColor = when (appStyle) {
        AppStyle.DYNAMIC -> animatedColor
        AppStyle.AMOLED -> Color.Black
        AppStyle.MINIMAL -> Color(0xFF2C2C2C)
        AppStyle.GLASS -> Color.Black
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (appStyle == AppStyle.GLASS && state.artwork != null) {
            Image(
                bitmap = state.artwork!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.4f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f)
                        )
                    )
                )
        )

        if (!state.isConnected) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No media playing",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AlbumArt(
                    bitmap = state.artwork,
                    onClick = { showFullscreenArt = true }
                )
                Spacer(Modifier.height(32.dp))
                AnimatedContent(targetState = state.title, label = "") {
                    Text(
                        it,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                }
                Text(
                    state.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                )
                // Sleep timer countdown
                val sleepTimerSeconds by viewModel.sleepTimerSeconds.collectAsStateWithLifecycle()

                if (sleepTimerSeconds > 0) {
                    val minutes = sleepTimerSeconds / 60
                    val seconds = sleepTimerSeconds % 60
                    Text(
                        "Sleep: %d:%02d".format(minutes, seconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(16.dp))
                var seekPosition by remember { mutableStateOf<Float?>(null) }

                if (appStyle == AppStyle.MINIMAL) {
                    Slider(
                        value = seekPosition ?: state.progress,
                        onValueChange = { seekPosition = it },
                        onValueChangeFinished = {
                            seekPosition?.let { viewModel.seekTo(it) }
                            seekPosition = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                } else {
                    WaveSlider(
                        value = seekPosition ?: state.progress,
                        onValueChange = { seekPosition = it },
                        onValueChangeFinished = {
                            seekPosition?.let { viewModel.seekTo(it) }
                            seekPosition = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(state.position),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatDuration(state.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                PlaybackControls(
                    isPlaying = state.isPlaying,
                    onPlayPause = viewModel::playPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showQueue = true },
                    enabled = !showQueue
                ) {
                    Text(
                        "Up Next",
                        color = if (showQueue) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }



        if (showQueue) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                QueueSheet(
                    queue = state.queue,
                    currentTitle = state.title,
                    onDismiss = { showQueue = false }
                )
            }
        }

        FullscreenArtwork(
            bitmap = state.artwork,
            visible = showFullscreenArt,
            backgroundColor = backgroundColor,
            onDismiss = { showFullscreenArt = false }
        )
    }
}