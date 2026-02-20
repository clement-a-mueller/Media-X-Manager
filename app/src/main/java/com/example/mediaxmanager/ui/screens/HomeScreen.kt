package com.example.mediaxmanager.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.components.AlbumArt
import com.example.mediaxmanager.ui.components.PlaybackControls
import com.example.mediaxmanager.ui.components.WaveSlider

fun extractDominantColor(bitmap: Bitmap?): Color {
    if (bitmap == null) return Color(0xFF1C1B1F)
    val palette = Palette.from(bitmap).generate()
    val argb = palette.getDarkVibrantColor(
        palette.getDominantColor(0xFF1C1B1F.toInt())
    )
    return Color(argb)
}

@Composable
fun HomeScreen(viewModel: MediaViewModel) {
    val state by viewModel.mediaState.collectAsStateWithLifecycle()

    val dominantColor = remember(state.artwork) {
        extractDominantColor(state.artwork)
    }

    val backgroundColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 800),
        label = "bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
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
                AlbumArt(state.artwork)
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
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))
                WaveSlider(
                    value = state.progress,
                    onValueChange = { viewModel.seekTo(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                PlaybackControls(
                    isPlaying = state.isPlaying,
                    onPlayPause = viewModel::playPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous
                )
            }
        }
    }
}