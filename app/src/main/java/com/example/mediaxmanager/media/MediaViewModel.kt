package com.example.mediaxmanager.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = MediaControllerManager(application)
    val mediaState = manager.mediaState
    fun playPause() = manager.playPause()
    fun next() = manager.next()
    fun previous() = manager.previous()
    fun seekTo(progress: Float) = manager.seekTo(progress)
    fun reconnect() = manager.reconnect()
}