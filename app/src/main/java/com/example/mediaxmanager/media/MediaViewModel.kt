package com.example.mediaxmanager.media

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
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
import kotlinx.coroutines.withContext

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

    private var mediaPlayer: MediaPlayer? = null
    private var localQueue: List<LocalTrack> = emptyList()
    private var localIndex: Int = 0
    private var isLocalShuffling = false
    private var localRepeatMode = 0
    var spotifyWasPausedByUs = false

    // Combined state that merges Spotify and local
    val combinedMediaState = combine(mediaState, _isLocalPlaying, _localTrack) { state, isLocal, track ->
        if (isLocal && track != null) {
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
            while (true) {
                val refreshRate = application
                    .getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getInt("refresh_rate", 100)
                if (mediaState.value.isPlaying || _isLocalPlaying.value) {
                    manager.updateProgress()
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            val progress = player.currentPosition.toFloat() / player.duration.toFloat()
                            combinedMediaState.value = combinedMediaState.value.copy(
                                progress = progress,
                                position = player.currentPosition.toLong(),
                                duration = player.duration.toLong(),
                                isPlaying = true
                            )
                        }
                    }
                }
                delay(refreshRate.toLong())
            }
        }
    }

    // Artwork loading
    private suspend fun loadArtworkForTrack(context: Context, track: LocalTrack): Bitmap? =
        withContext(Dispatchers.IO) {
            // Method A: MediaStore album art table
            try {
                val albumId = context.contentResolver.query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(android.provider.MediaStore.Audio.Media.ALBUM_ID),
                    "${android.provider.MediaStore.Audio.Media._ID} = ?",
                    arrayOf(track.id.toString()),
                    null
                )?.use { c ->
                    if (c.moveToFirst()) c.getLong(0) else null
                }
                if (albumId != null) {
                    val artUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )
                    val bitmap = context.contentResolver.openInputStream(artUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                    if (bitmap != null) return@withContext bitmap
                }
            } catch (_: Exception) {}

            // Method B: file descriptor via ContentResolver
            try {
                val retriever = MediaMetadataRetriever()
                context.contentResolver.openFileDescriptor(track.uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                }
                val bytes = retriever.embeddedPicture
                retriever.release()
                if (bytes != null) return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {}

            // Method C: direct path
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(track.path)
                val bytes = retriever.embeddedPicture
                retriever.release()
                if (bytes != null) return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {}

            null
        }

    // Spotify controls
    fun playPause() = manager.playPause()
    fun next() = manager.next()
    fun previous() = manager.previous()
    fun seekTo(progress: Float) = manager.seekTo(progress)
    fun reconnect() = manager.reconnect()
    fun toggleShuffle() = manager.updateShuffleState(!mediaState.value.isShuffling)
    fun toggleRepeat() = manager.updateRepeatMode((mediaState.value.repeatMode + 1) % 3)

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
        localQueue = queue.ifEmpty { listOf(track) }
        localIndex = localQueue.indexOf(track).coerceAtLeast(0)
        _localTrack.value = track
        _localArtwork.value = null  // clear old art immediately
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, track.uri)
            prepare()
            start()
            setOnCompletionListener { localNext(context) }
        }
        _isLocalPlaying.value = true
        // Load artwork asynchronously
        viewModelScope.launch {
            _localArtwork.value = loadArtworkForTrack(context, track)
        }
    }

    fun localNext(context: Context) {
        if (localQueue.isEmpty()) return
        localIndex = if (isLocalShuffling) {
            (0 until localQueue.size).random()
        } else {
            when (localRepeatMode) {
                2 -> localIndex
                else -> (localIndex + 1) % localQueue.size
            }
        }
        playLocalTrack(context, localQueue[localIndex], localQueue)
    }

    fun localPreviousOrRestart(context: Context) {
        mediaPlayer?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0)
                return
            }
        }
        if (localQueue.isEmpty()) return
        localIndex = (localIndex - 1).coerceAtLeast(0)
        playLocalTrack(context, localQueue[localIndex], localQueue)
    }

    fun seekToLocal(progress: Float) {
        mediaPlayer?.let {
            it.seekTo((progress * it.duration).toInt())
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