package com.example.mediaxmanager.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerSeconds = MutableStateFlow(0)
    val sleepTimerSeconds: StateFlow<Int> = _sleepTimerSeconds

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerSeconds.value = minutes * 60
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                while (_sleepTimerSeconds.value > 0) {
                    kotlinx.coroutines.delay(1000L)
                    _sleepTimerSeconds.value--
                }
                if (_sleepTimerSeconds.value == 0) {
                    manager.playPause()
                }
            }
        }
    }
}