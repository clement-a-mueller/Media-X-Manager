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
import kotlinx.coroutines.withContext
import com.example.mediaxmanager.media.JellyfinRepository
import java.net.URL

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = MediaControllerManager(application)
    val mediaState = manager.mediaState

    // ── Local media state ─────────────────────────────────────────────────────

    private val _isLocalPlaying    = MutableStateFlow(false)
    val isLocalPlaying: StateFlow<Boolean> = _isLocalPlaying

    private val _localTrack        = MutableStateFlow<LocalTrack?>(null)
    val localTrack: StateFlow<LocalTrack?> = _localTrack

    private val _localArtwork      = MutableStateFlow<Bitmap?>(null)
    val localArtwork: StateFlow<Bitmap?> = _localArtwork

    private val _localQueue        = MutableStateFlow<List<LocalTrack>>(emptyList())
    val localQueue: StateFlow<List<LocalTrack>> = _localQueue

    // Exposed as StateFlows so the UI reacts to changes
    private val _isLocalShuffling  = MutableStateFlow(false)
    val isLocalShuffling: StateFlow<Boolean> = _isLocalShuffling

    private val _localRepeatMode   = MutableStateFlow(0)
    val localRepeatMode: StateFlow<Int> = _localRepeatMode

    // Progress flows — updated by the ticker loop
    private val _localProgress     = MutableStateFlow(0f)
    val localProgress: StateFlow<Float> = _localProgress

    private val _localPosition     = MutableStateFlow(0L)
    val localPosition: StateFlow<Long> = _localPosition

    private val _localDuration     = MutableStateFlow(0L)
    val localDuration: StateFlow<Long> = _localDuration

    private var mediaPlayer: MediaPlayer? = null
    private var internalQueue: MutableList<LocalTrack> = mutableListOf()
    private var localIndex: Int = 0
    private var isSeeking = false
    var spotifyWasPausedByUs = false

    // ── Combined state ────────────────────────────────────────────────────────

    val combinedMediaState = combine(
        mediaState, _isLocalPlaying, _localTrack, _isLocalShuffling, _localRepeatMode
    ) { state, isLocal, track, shuffling, repeat ->
        if (track != null && (isLocal || !state.isConnected)) {
            state.copy(
                title       = track.title,
                artist      = track.artist,
                isPlaying   = isLocal,
                isConnected = true,
                isShuffling = shuffling,
                repeatMode  = repeat
            )
        } else {
            state
        }
    }.let { flow ->
        val stateFlow = MutableStateFlow(mediaState.value)
        viewModelScope.launch { flow.collect { stateFlow.value = it } }
        stateFlow
    }

    // ── Lyrics ────────────────────────────────────────────────────────────────

    val lyricsLines = MutableStateFlow<List<LyricsLine>>(emptyList())

    private val localLyricsCache    = mutableMapOf<String, List<LyricsLine>>()
    private val externalLyricsCache = mutableMapOf<String, List<LyricsLine>>()
    private val localNextCache      = mutableMapOf<String, List<LyricsLine>>()
    private val externalNextCache   = mutableMapOf<String, List<LyricsLine>>()
    private val fetchingTitles      = mutableSetOf<String>()

    fun fetchLyricsIfNeeded(
        title: String,
        artist: String,
        isLocal: Boolean,
        nextTitle: String = "",
        nextArtist: String = ""
    ) {
        val currentCache = if (isLocal) localLyricsCache else externalLyricsCache
        val nextCache    = if (isLocal) localNextCache    else externalNextCache
        val currentKey   = "$title|||$artist"
        val nextKey      = "$nextTitle|||$nextArtist"

        viewModelScope.launch {
            if (title.isNotEmpty()) {
                val cached = currentCache[currentKey]
                when {
                    cached != null -> lyricsLines.value = cached
                    !fetchingTitles.contains(currentKey) -> {
                        fetchingTitles.add(currentKey)
                        lyricsLines.value = emptyList()
                        val preloaded = nextCache.remove(currentKey)
                        if (preloaded != null) {
                            currentCache[currentKey] = preloaded
                            lyricsLines.value = preloaded
                        } else {
                            val fetched = fetchLrc(title, artist)
                            currentCache[currentKey] = fetched
                            lyricsLines.value = fetched
                        }
                        fetchingTitles.remove(currentKey)
                    }
                }
            }

            if (nextTitle.isNotEmpty()
                && nextKey != currentKey
                && currentCache[nextKey] == null
                && nextCache[nextKey] == null
                && !fetchingTitles.contains(nextKey)
            ) {
                fetchingTitles.add(nextKey)
                val fetched = fetchLrc(nextTitle, nextArtist)
                nextCache[nextKey] = fetched
                fetchingTitles.remove(nextKey)
            }
        }
    }

    fun syncLyricsForSource(title: String, artist: String, isLocal: Boolean) {
        val cache = if (isLocal) localLyricsCache else externalLyricsCache
        lyricsLines.value = cache["$title|||$artist"] ?: emptyList()
    }

    private suspend fun fetchLrc(title: String, artist: String): List<LyricsLine> =
        try {
            val encodedTitle  = java.net.URLEncoder.encode(title,  "UTF-8")
            val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
            val response = withContext(Dispatchers.IO) { java.net.URL(url).readText() }
            val synced = org.json.JSONObject(response).optString("syncedLyrics")
            if (synced.isNotEmpty()) parseLrc(synced) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    // ── Ticker loop ───────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            while (true) {
                // External (Spotify) progress
                if (mediaState.value.isPlaying) {
                    manager.updateProgress()
                }

                // Local MediaPlayer progress
                mediaPlayer?.let { player ->
                    if (player.isPlaying && !isSeeking) {
                        val dur = player.duration.toLong()
                        if (dur > 0) {
                            val pos = player.currentPosition.toLong()
                            _localPosition.value = pos
                            _localDuration.value = dur
                            _localProgress.value = pos.toFloat() / dur.toFloat()
                        }
                    }
                }

                // When play_on_pc: no MediaPlayer, but track is "playing" — duration
                // comes from the track metadata so the slider still works.
                if (_isLocalPlaying.value && mediaPlayer == null) {
                    val dur = _localDuration.value
                    if (dur > 0) {
                        _localPosition.value = (_localPosition.value + 100L).coerceAtMost(dur)
                        _localProgress.value = _localPosition.value.toFloat() / dur.toFloat()
                    }
                }

                delay(100)
            }
        }
    }

    // ── Spotify controls ──────────────────────────────────────────────────────

    fun playPause()                 = manager.playPause()
    fun next()                      = manager.next()
    fun previous()                  = manager.previous()
    fun seekTo(progress: Float)     = manager.seekTo(progress)
    fun reconnect()                 = manager.reconnect()
    fun toggleShuffle()             = manager.updateShuffleState(!mediaState.value.isShuffling)
    fun toggleRepeat()              = manager.updateRepeatMode((mediaState.value.repeatMode + 1) % 3)

    // ── Local controls ────────────────────────────────────────────────────────

    fun resumeLocalMedia() {
        mediaPlayer?.let { if (!it.isPlaying) it.start() }
        _isLocalPlaying.value = true
    }

    fun suspendLocalMedia() {
        mediaPlayer?.let { if (it.isPlaying) it.pause() }
        _isLocalPlaying.value = false
    }

    fun switchToPcStreamingMode() {
        mediaPlayer?.release()
        mediaPlayer = null
        // Keep _isLocalPlaying = true so the UI and HTTP server stay active
    }

    fun toggleLocalPlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
            _isLocalPlaying.value = it.isPlaying
        } ?: run {
            // play_on_pc mode: toggle the virtual playing state
            _isLocalPlaying.value = !_isLocalPlaying.value
        }
    }

    /** Called by the HTTP server to pause without needing a MediaPlayer. */
    fun pauseForPcStreaming() {
        mediaPlayer?.let { if (it.isPlaying) it.pause() }
        _isLocalPlaying.value = false
    }

    /** Called by the HTTP server when the PC client disconnects. */
    fun resumeForPcStreaming() {
        mediaPlayer?.let { if (!it.isPlaying) it.start() }
        _isLocalPlaying.value = true
    }

    fun playLocalTrack(context: Context, track: LocalTrack, queue: List<LocalTrack> = emptyList()) {
        val prefs    = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val playOnPc = prefs.getBoolean("play_on_pc", false)

        internalQueue  = queue.ifEmpty { listOf(track) }.toMutableList()
        _localQueue.value  = internalQueue.toList()
        localIndex     = internalQueue.indexOf(track).coerceAtLeast(0)
        _localTrack.value  = track

        // ── Artwork — always load, regardless of play_on_pc ──────────────────
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = if (track.path.startsWith("jellyfin://")) {
                val session = JellyfinRepository.session
                if (session != null) {
                    val itemId = track.path.removePrefix("jellyfin://")
                    val artUrl = JellyfinRepository.artUrl(session, itemId)
                    try { BitmapFactory.decodeStream(java.net.URL(artUrl).openStream()) }
                    catch (e: Exception) { null }
                } else null
            } else {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, track.uri)
                    val bytes = retriever.embeddedPicture
                    retriever.release()
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } catch (e: Exception) { null }
            }
            _localArtwork.value = bitmap

            // Cache art in TrackCache for the HTTP server's /art endpoint
            val key = track.uri.toString().ifBlank { track.path }
            if (bitmap != null) {
                com.example.mediaxmanager.ui.screens.TrackCache.artCache[key] = bitmap
            }
        }

        // ── Duration metadata — always extract so slider works in play_on_pc ─
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, track.uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                val dur = durStr?.toLongOrNull() ?: 0L
                _localDuration.value = dur
                _localPosition.value = 0L
                _localProgress.value = 0f
            } catch (e: Exception) {
                _localDuration.value = 0L
            }
        }

        if (playOnPc) {
            // PC streams via /now-playing polling — no local MediaPlayer needed
            mediaPlayer?.release()
            mediaPlayer = null
            _isLocalPlaying.value = true
            return
        }

        // ── Normal local playback ─────────────────────────────────────────────
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
        if (mediaPlayer == null && !_isLocalPlaying.value) {
            internalQueue = mutableListOf(track)
            _localQueue.value = internalQueue.toList()
            playLocalTrack(getApplication(), track, internalQueue)
        } else {
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
        localIndex = if (_isLocalShuffling.value) {
            (0 until internalQueue.size).random()
        } else {
            when (_localRepeatMode.value) {
                2    -> localIndex
                else -> (localIndex + 1) % internalQueue.size
            }
        }
        playLocalTrack(context, internalQueue[localIndex], internalQueue)
    }

    fun localPreviousOrRestart(context: Context) {
        // In play_on_pc mode mediaPlayer is null, so fall back to _localPosition
        val currentPositionMs = mediaPlayer?.currentPosition?.toLong() ?: _localPosition.value
        if (currentPositionMs > 3000) {
            // More than 3 seconds in — restart current song
            mediaPlayer?.seekTo(0) ?: run {
                // play_on_pc: reset virtual position so server reports 0
                // and the client's drift check seeks mpv back to 0
                _localPosition.value = 0L
                _localProgress.value = 0f
            }
            return
        }
        // Within first 3 seconds — go to previous track
        if (internalQueue.isEmpty()) return
        localIndex = (localIndex - 1).coerceAtLeast(0)
        playLocalTrack(context, internalQueue[localIndex], internalQueue)
    }

    fun seekToLocal(progress: Float) {
        mediaPlayer?.let { player ->
            val position = (progress * player.duration).toInt()
            isSeeking = true
            player.seekTo(position)
            viewModelScope.launch {
                delay(200)
                _localPosition.value = position.toLong()
                _localProgress.value = progress
                player.start()
                _isLocalPlaying.value = true
                isSeeking = false
            }
        } ?: run {
            val dur = _localDuration.value
            if (dur > 0) {
                _localProgress.value = progress
                _localPosition.value = (progress * dur).toLong()
            }
        }
    }

    fun toggleLocalShuffle() {
        _isLocalShuffling.value = !_isLocalShuffling.value
    }

    fun toggleLocalRepeat() {
        _localRepeatMode.value = (_localRepeatMode.value + 1) % 3
    }

    // ── Sleep timer ───────────────────────────────────────────────────────────

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

    // ── Misc ──────────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun getAudioSessionId(): Int = mediaPlayer?.audioSessionId ?: 0

    suspend fun prefetchLyrics(title: String, artist: String) {
        val key = "$title|||$artist"
        if (localLyricsCache.containsKey(key) || fetchingTitles.contains(key)) return
        fetchingTitles.add(key)
        val fetched = fetchLrc(title, artist)
        localLyricsCache[key] = fetched
        fetchingTitles.remove(key)
    }
}