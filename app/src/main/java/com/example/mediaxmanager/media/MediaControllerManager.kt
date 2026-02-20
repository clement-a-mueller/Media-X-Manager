package com.example.mediaxmanager.media

import com.example.mediaxmanager.media.MyNotificationListenerService
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.java

class MediaControllerManager(private val context: Context) {

    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private var mediaController: MediaController? = null

    private val _mediaState = MutableStateFlow(MediaUiState())
    val mediaState: StateFlow<MediaUiState> = _mediaState

    private val controllerCallback = object : MediaController.Callback() {

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateState()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateState()
        }

        override fun onSessionDestroyed() {
            _mediaState.value = MediaUiState(isConnected = false)
        }
    }

    // In MediaControllerManager.kt, wrap the init block:
    init {
        try {
            val componentName = ComponentName(context, MyNotificationListenerService::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(
                { controllers -> connectToSession(controllers?.firstOrNull()) },
                componentName
            )
            val controllers = mediaSessionManager.getActiveSessions(componentName) ?: emptyList()
            connectToSession(controllers.firstOrNull())
        } catch (e: SecurityException) {
            // Permission not granted yet — state stays isConnected = false
        }
    }

    private fun connectToSession(controller: MediaController?) {
        mediaController?.unregisterCallback(controllerCallback)
        mediaController = controller
        mediaController?.registerCallback(controllerCallback)
        updateState()
    }

    private fun updateState() {
        val controller = mediaController ?: run {
            _mediaState.value = MediaUiState(isConnected = false)
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState

        _mediaState.value = MediaUiState(
            isConnected = true,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
            isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
            playbackState = playbackState?.state ?: PlaybackState.STATE_NONE
        )
    }

    fun playPause() {
        mediaController?.let {
            if (_mediaState.value.isPlaying) it.transportControls.pause()
            else it.transportControls.play()
        }
    }

    fun next() = mediaController?.transportControls?.skipToNext()
    fun previous() = mediaController?.transportControls?.skipToPrevious()

    fun reconnect() {
        try {
            val componentName = ComponentName(context, MyNotificationListenerService::class.java)
            val controllers = mediaSessionManager.getActiveSessions(componentName) ?: emptyList()
            connectToSession(controllers.firstOrNull())
        } catch (e: SecurityException) {
            _mediaState.value = MediaUiState(isConnected = false)
        }
    }
}