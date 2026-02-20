package com.example.mediaxmanager.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.components.*

@Composable
fun MediaScreen(viewModel: MediaViewModel) {

    val state by viewModel.mediaState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        tonalElevation = 4.dp
    ) {
        if (!state.isConnected) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No media playing", style = MaterialTheme.typography.headlineMedium)
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                AlbumArt(state.artwork)

                Spacer(Modifier.height(24.dp))

                AnimatedContent(targetState = state.title, label = "") {
                    Text(
                        it,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Text(
                    state.artist,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(32.dp))

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