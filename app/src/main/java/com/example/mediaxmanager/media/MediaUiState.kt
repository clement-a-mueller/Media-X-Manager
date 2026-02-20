package com.example.mediaxmanager.media

import android.graphics.Bitmap
import android.media.session.PlaybackState

data class MediaUiState(
    val isConnected: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: Bitmap? = null,
    val isPlaying: Boolean = false,
    val playbackState: Int = PlaybackState.STATE_NONE,
    val progress: Float = 0f,        // ← 0.0 to 1.0
    val duration: Long = 0L,
    val position: Long = 0L
)