package com.example.mediaxmanager.media

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediaxmanager.ui.screens.LocalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = MediaControllerManager(application)
    val mediaState = manager.mediaState

    // Local media state
    private val _isLocalPlaying = MutableStateFlow(false)
    val isLocalPlaying: StateFlow<Boolean> = _isLocalPlaying

    private val _localTrack = MutableStateFlow<LocalTrack?>(null)
    val localTrack: StateFlow<LocalTrack?> = _localTrack

    private val _localArtwork = MutableStateFlow<Bitmap?>(null)
    val localArtwork: StateFlow<Bitmap?> = _localArtwork

    private val _localQueue = MutableStateFlow<List<LocalTrack>>(emptyList())
    val localQueue: StateFlow<List<LocalTrack>> = _localQueue

    private var mediaPlayer: MediaPlayer? = null
    private var internalQueue: MutableList<LocalTrack> = mutableListOf()
    private var localIndex: Int = 0
    private var isLocalShuffling = false
    private var localRepeatMode = 0
    var spotifyWasPausedByUs = false

    // Combined state
    val combinedMediaState = combine(mediaState, _isLocalPlaying, _localTrack) { state, isLocal, track ->
        if (track != null && (isLocal || !state.isConnected)) {
            state.copy(
                title = track.title,
                artist = track.artist,
                isPlaying = mediaPlayer?.isPlaying ?: false,
                isConnected = true,
                isShuffling = isLocalShuffling,
                repeatMode = localRepeatMode
            )
        } else {
            state
        }
    }.let { flow ->
        val stateFlow = MutableStateFlow(mediaState.value)
        viewModelScope.launch {
            flow.collect { stateFlow.value = it }
        }
        stateFlow
    }

    init {
        viewModelScope.launch {
            val app = getApplication<Application>()
            while (true) {
                val refreshRate = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getInt("refresh_rate", 100)
                if (mediaState.value.isPlaying || _isLocalPlaying.value) {
                    manager.updateProgress()
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            val progress = player.currentPosition.toFloat() / player.duration.toFloat()
                            val position = player.currentPosition.toLong()
                            val duration = player.duration.toLong()
                            combinedMediaState.value = combinedMediaState.value.copy(
                                progress = progress,
                                position = position,
                                duration = duration,
                                isPlaying = true
                            )
                        }
                    }
                }
                delay(100)
            }
        }
    }

    // Spotify controls
    fun playPause() = manager.playPause()
    fun next() = manager.next()
    fun previous() = manager.previous()
    fun seekTo(progress: Float) = manager.seekTo(progress)
    fun reconnect() = manager.reconnect()

    fun toggleShuffle() {
        manager.updateShuffleState(!mediaState.value.isShuffling)
    }

    fun toggleRepeat() {
        manager.updateRepeatMode((mediaState.value.repeatMode + 1) % 3)
    }

    // Local media controls
    fun resumeLocalMedia() {
        mediaPlayer?.let {
            if (!it.isPlaying) it.start()
            _isLocalPlaying.value = true
        }
    }

    fun suspendLocalMedia() {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause()
        }
        _isLocalPlaying.value = false
    }

    fun toggleLocalPlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
            _isLocalPlaying.value = it.isPlaying
        }
    }

    fun playLocalTrack(context: Context, track: LocalTrack, queue: List<LocalTrack> = emptyList()) {
        internalQueue = queue.ifEmpty { listOf(track) }.toMutableList()
        _localQueue.value = internalQueue.toList()
        localIndex = internalQueue.indexOf(track).coerceAtLeast(0)
        _localTrack.value = track

        // Extract artwork in background
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, track.uri)
                val bytes = retriever.embeddedPicture
                retriever.release()
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } catch (e: Exception) {
                null
            }
            _localArtwork.value = bitmap
        }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, track.uri)
            prepare()
            start()
            setOnCompletionListener { localNext(context) }
        }
        _isLocalPlaying.value = true
    }

    fun addToLocalQueue(track: LocalTrack) {
        if (mediaPlayer == null || !_isLocalPlaying.value) {
            // Nothing playing — start playing this track
            internalQueue = mutableListOf(track)
            _localQueue.value = internalQueue.toList()
            playLocalTrack(getApplication(), track, internalQueue)
        } else {
            // Add to end of queue
            internalQueue.add(track)
            _localQueue.value = internalQueue.toList()
        }
    }

    fun removeFromLocalQueue(index: Int) {
        if (index < 0 || index >= internalQueue.size) return
        internalQueue.removeAt(index)
        _localQueue.value = internalQueue.toList()
        if (index < localIndex) localIndex--
    }

    fun clearLocalQueue() {
        internalQueue.clear()
        _localQueue.value = emptyList()
    }

    fun localNext(context: Context) {
        if (internalQueue.isEmpty()) return
        localIndex = if (isLocalShuffling) {
            (0 until internalQueue.size).random()
        } else {
            when (localRepeatMode) {
                2 -> localIndex
                else -> (localIndex + 1) % internalQueue.size
            }
        }
        playLocalTrack(context, internalQueue[localIndex], internalQueue)
    }

    fun localPreviousOrRestart(context: Context) {
        mediaPlayer?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0)
                return
            }
        }
        if (internalQueue.isEmpty()) return
        localIndex = (localIndex - 1).coerceAtLeast(0)
        playLocalTrack(context, internalQueue[localIndex], internalQueue)
    }

    fun seekToLocal(progress: Float) {
        mediaPlayer?.let {
            val position = (progress * it.duration).toInt()
            it.seekTo(position)
        }
    }

    fun toggleLocalShuffle() {
        isLocalShuffling = !isLocalShuffling
        combinedMediaState.value = combinedMediaState.value.copy(isShuffling = isLocalShuffling)
    }

    fun toggleLocalRepeat() {
        localRepeatMode = (localRepeatMode + 1) % 3
        combinedMediaState.value = combinedMediaState.value.copy(repeatMode = localRepeatMode)
    }

    // Sleep timer
    private var sleepTimerJob: Job? = null
    private val _sleepTimerSeconds = MutableStateFlow(0)
    val sleepTimerSeconds: StateFlow<Int> = _sleepTimerSeconds

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerSeconds.value = minutes * 60
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                while (_sleepTimerSeconds.value > 0) {
                    delay(1000L)
                    _sleepTimerSeconds.value--
                }
                manager.playPause()
                mediaPlayer?.pause()
                _isLocalPlaying.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}