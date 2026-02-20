package com.example.mediaxmanager.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = MediaControllerManager(application)
    val mediaState = manager.mediaState

    init {
        // Poll progress every 500ms while playing
        viewModelScope.launch {
            while (true) {
                if (mediaState.value.isPlaying) {
                    manager.updateProgress()
                }
                delay(100)
            }
        }
    }

    fun playPause() = manager.playPause()
    fun next() = manager.next()
    fun previous() = manager.previous()
    fun seekTo(progress: Float) = manager.seekTo(progress)
    fun reconnect() = manager.reconnect()
}