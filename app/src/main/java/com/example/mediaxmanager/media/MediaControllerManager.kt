package com.example.mediaxmanager.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MediaControllerManager(private val context: Context) {
    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private var mediaController: MediaController? = null
    private val _mediaState = MutableStateFlow(MediaUiState())
    val mediaState: StateFlow<MediaUiState> = _mediaState

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { updateState() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { updateState() }
        override fun onSessionDestroyed() {
            _mediaState.value = MediaUiState(isConnected = false)
        }
    }

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
            _mediaState.value = MediaUiState(isConnected = false)
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
        val position = playbackState?.position ?: 0L
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

        _mediaState.value = MediaUiState(
            isConnected = true,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
            isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
            playbackState = playbackState?.state ?: PlaybackState.STATE_NONE,
            progress = progress,
            duration = duration,
            position = position
        )
    }

    fun playPause() {
        mediaController?.let {
            if (_mediaState.value.isPlaying) it.transportControls.pause()
            else it.transportControls.play()
        }
    }

    fun seekTo(progress: Float) {
        val duration = _mediaState.value.duration
        mediaController?.transportControls?.seekTo((progress * duration).toLong())
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

    fun updateProgress() {
        val controller = mediaController ?: return
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        val position = playbackState?.position ?: 0L
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
        _mediaState.value = _mediaState.value.copy(
            progress = progress,
            position = position
        )
    }
}