package com.example.mediaxmanager.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.mediaxmanager.media.MediaControllerManager

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = MediaControllerManager(application)

    val mediaState = manager.mediaState

    fun playPause() = manager.playPause()
    fun next() = manager.next()
    fun previous() = manager.previous()
    fun reconnect() = manager.reconnect()
}