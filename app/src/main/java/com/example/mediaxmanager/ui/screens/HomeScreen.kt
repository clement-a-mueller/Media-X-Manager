package com.example.mediaxmanager.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.media.QueueItem
import com.example.mediaxmanager.ui.components.AlbumArt
import com.example.mediaxmanager.ui.components.FullscreenArtwork
import com.example.mediaxmanager.ui.components.PlaybackControls
import com.example.mediaxmanager.ui.components.QueueSheet
import com.example.mediaxmanager.ui.components.WaveSlider
import com.example.mediaxmanager.ui.theme.AppStyle
import com.example.mediaxmanager.ui.utils.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable

fun extractDominantColor(bitmap: Bitmap?): Color {
    if (bitmap == null) return Color(0xFF1C1B1F)
    val palette = Palette.from(bitmap).generate()
    val argb = palette.getDarkVibrantColor(
        palette.getDominantColor(0xFF1C1B1F.toInt())
    )
    return Color(argb)
}

data class LyricsLine(val timeMs: Long, val text: String)

fun parseLrc(lrc: String): List<LyricsLine> {
    val lines = mutableListOf<LyricsLine>()
    val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.+)")
    lrc.lines().forEach { line ->
        val match = regex.find(line) ?: return@forEach
        val (min, sec, cs, text) = match.destructured
        val ms = min.toLong() * 60000 + sec.toLong() * 1000 + cs.toLong() * 10
        lines.add(LyricsLine(ms, text.trim()))
    }
    return lines.sortedBy { it.timeMs }
}

private val LocalModeTrackColor = Color(0xFF9B8EC4)
private val LocalModeTrackColorDim = Color(0xFF9B8EC4).copy(alpha = 0.3f)

@Composable
fun HomeScreen(viewModel: MediaViewModel, appStyle: AppStyle) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }

    val state by viewModel.combinedMediaState.collectAsStateWithLifecycle()
    val isLocalPlaying by viewModel.isLocalPlaying.collectAsStateWithLifecycle()
    val localArtwork by viewModel.localArtwork.collectAsStateWithLifecycle()
    val localTrack by viewModel.localTrack.collectAsStateWithLifecycle()
    val localQueue by viewModel.localQueue.collectAsStateWithLifecycle()

    var showFullscreenArt by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf<Float?>(null) }
    var useLocalMedia by remember { mutableStateOf(false) }

    LaunchedEffect(isLocalPlaying) {
        if (isLocalPlaying) useLocalMedia = true
    }

    // All display values driven by useLocalMedia as single source of truth
    val effectiveArtwork = if (useLocalMedia) localArtwork else state.artwork
    val effectiveTitle = if (useLocalMedia) (localTrack?.title ?: state.title) else state.title
    val effectiveArtist = if (useLocalMedia) (localTrack?.artist ?: state.artist) else state.artist
    val effectiveProgress = seekPosition ?: state.progress
    val effectivePosition = state.position
    val effectiveDuration = state.duration
    val hasValidDuration = effectiveDuration > 0

    // Queue: use local track list when in local mode, Spotify queue otherwise
    val effectiveQueue = if (useLocalMedia) {
        localQueue.map { QueueItem(title = it.title, artist = it.artist) }
    } else {
        state.queue
    }

    val gesturesEnabled = remember { prefs.getBoolean("gestures_enabled", false) }
    val lyricsEnabled = remember { prefs.getBoolean("lyrics_enabled", false) }

    var lyricsLines by remember { mutableStateOf<List<LyricsLine>>(emptyList()) }

    LaunchedEffect(effectiveTitle, effectiveArtist) {
        if (effectiveTitle.isEmpty() || !lyricsEnabled) return@LaunchedEffect
        lyricsLines = emptyList()
        try {
            val encodedTitle = java.net.URLEncoder.encode(effectiveTitle, "UTF-8")
            val encodedArtist = java.net.URLEncoder.encode(effectiveArtist, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
            val response = withContext(Dispatchers.IO) {
                java.net.URL(url).readText()
            }
            val json = org.json.JSONObject(response)
            val syncedLyrics = json.optString("syncedLyrics")
            if (syncedLyrics.isNotEmpty()) {
                lyricsLines = parseLrc(syncedLyrics)
            }
        } catch (e: Exception) {
            lyricsLines = emptyList()
        }
    }

    val currentLineIndex = remember(effectivePosition, lyricsLines) {
        if (lyricsLines.isEmpty()) -1
        else lyricsLines.indexOfLast { it.timeMs <= effectivePosition }
    }

    val lyricsListState = rememberLazyListState()
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            lyricsListState.animateScrollToItem(
                index = (currentLineIndex - 2).coerceAtLeast(0)
            )
        }
    }

    val currentUseLocalMedia by rememberUpdatedState(useLocalMedia)

    val dominantColor = remember(effectiveArtwork) { extractDominantColor(effectiveArtwork) }
    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(800),
        label = "bg"
    )

    val backgroundColor = when (appStyle) {
        AppStyle.DYNAMIC -> animatedColor
        AppStyle.AMOLED  -> Color.Black
        AppStyle.MINIMAL -> Color(prefs.getInt("minimal_color", 0xFF2C2C2C.toInt()))
        AppStyle.GLASS   -> Color.Black
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (gesturesEnabled) Modifier.pointerInput(useLocalMedia) {
                    var totalX = 0f
                    var totalY = 0f
                    detectDragGestures(
                        onDragStart = { totalX = 0f; totalY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalX += dragAmount.x
                            totalY += dragAmount.y
                        },
                        onDragEnd = {
                            val absX = kotlin.math.abs(totalX)
                            val absY = kotlin.math.abs(totalY)
                            if (absX > absY && absX > 100f) {
                                if (totalX < 0 && !useLocalMedia) {
                                    if (viewModel.mediaState.value.isPlaying) {
                                        viewModel.playPause()
                                        viewModel.spotifyWasPausedByUs = true
                                    }
                                    viewModel.resumeLocalMedia()
                                    useLocalMedia = true
                                } else if (totalX > 0 && useLocalMedia) {
                                    viewModel.suspendLocalMedia()
                                    if (viewModel.spotifyWasPausedByUs) {
                                        viewModel.playPause()
                                        viewModel.spotifyWasPausedByUs = false
                                    }
                                    useLocalMedia = false
                                }
                            } else if (absY > absX && absY > 100f) {
                                if (totalY < 0) {
                                    if (useLocalMedia) viewModel.localNext(context) else viewModel.next()
                                } else {
                                    if (useLocalMedia) viewModel.localPreviousOrRestart(context) else viewModel.previous()
                                }
                            }
                        }
                    )
                } else Modifier
            )
    ) {
        // Background
        if (appStyle == AppStyle.GLASS && effectiveArtwork != null) {
            Image(
                bitmap = effectiveArtwork.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.4f
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        } else {
            Box(modifier = Modifier.fillMaxSize().background(backgroundColor))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))))
        )

        if (!state.isConnected) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No media playing", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = effectiveArtwork,
                    transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
                    label = "artwork"
                ) { artwork ->
                    AlbumArt(bitmap = artwork, onClick = { showFullscreenArt = true })
                }

                Spacer(Modifier.height(32.dp))

                AnimatedContent(
                    targetState = effectiveTitle,
                    transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
                    label = "title"
                ) {
                    Text(it, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                }
                AnimatedContent(
                    targetState = effectiveArtist,
                    transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
                    label = "artist"
                ) {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
                }

                val sleepTimerSeconds by viewModel.sleepTimerSeconds.collectAsStateWithLifecycle()
                if (sleepTimerSeconds > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sleep: %d:%02d".format(sleepTimerSeconds / 60, sleepTimerSeconds % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (appStyle == AppStyle.MINIMAL) {
                    Slider(
                        value = if (hasValidDuration) effectiveProgress else 0f,
                        onValueChange = { if (hasValidDuration) seekPosition = it },
                        onValueChangeFinished = {
                            if (hasValidDuration) {
                                seekPosition?.let {
                                    if (currentUseLocalMedia) viewModel.seekToLocal(it)
                                    else viewModel.seekTo(it)
                                }
                            }
                            seekPosition = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                } else {
                    WaveSlider(
                        value = if (hasValidDuration) effectiveProgress else 0f,
                        onValueChange = { if (hasValidDuration) seekPosition = it },
                        onValueChangeFinished = {
                            if (hasValidDuration) {
                                seekPosition?.let {
                                    if (currentUseLocalMedia) viewModel.seekToLocal(it)
                                    else viewModel.seekTo(it)
                                }
                            }
                            seekPosition = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (hasValidDuration) formatDuration(effectivePosition) else "--:--",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        if (hasValidDuration) formatDuration(effectiveDuration) else "--:--",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                PlaybackControls(
                    isPlaying = state.isPlaying,
                    isShuffling = state.isShuffling,
                    repeatMode = state.repeatMode,
                    onPlayPause = { if (useLocalMedia) viewModel.toggleLocalPlayPause() else viewModel.playPause() },
                    onNext = { if (useLocalMedia) viewModel.localNext(context) else viewModel.next() },
                    onPrevious = { if (useLocalMedia) viewModel.localPreviousOrRestart(context) else viewModel.previous() },
                    onShuffle = { if (useLocalMedia) viewModel.toggleLocalShuffle() else viewModel.toggleShuffle() },
                    onRepeat = { if (useLocalMedia) viewModel.toggleLocalRepeat() else viewModel.toggleRepeat() }
                )

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = { showQueue = true }, enabled = !showQueue) {
                    Text(
                        "Up Next",
                        color = if (showQueue) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (lyricsEnabled && lyricsLines.isNotEmpty() && currentLineIndex >= 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = lyricsLines[currentLineIndex].text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxSize().padding(end = 20.dp, bottom = 32.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                SourceSwitch(
                    useLocalMedia = useLocalMedia,
                    enabled = localTrack != null || useLocalMedia,
                    onCheckedChange = { switchToLocal ->
                        if (switchToLocal) {
                            if (viewModel.mediaState.value.isPlaying) {
                                viewModel.playPause()
                                viewModel.spotifyWasPausedByUs = true
                            }
                            viewModel.resumeLocalMedia()
                        } else {
                            viewModel.suspendLocalMedia()
                            if (viewModel.spotifyWasPausedByUs) {
                                viewModel.playPause()
                                viewModel.spotifyWasPausedByUs = false
                            }
                        }
                        useLocalMedia = switchToLocal
                    }
                )
            }
        }

        if (showQueue) {
            // Scrim — catches taps outside the sheet to dismiss it
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showQueue = false }
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                QueueSheet(
                    queue = effectiveQueue,
                    currentTitle = effectiveTitle,
                    isSpotify = !useLocalMedia,
                    localQueue = localQueue,
                    onRemoveFromQueue = { index -> viewModel.removeFromLocalQueue(index) },
                    onDismiss = { showQueue = false }
                )
            }
        }

        FullscreenArtwork(
            bitmap = effectiveArtwork,
            visible = showFullscreenArt,
            backgroundColor = backgroundColor,
            onDismiss = { showFullscreenArt = false }
        )
    }
}

@Composable
private fun SourceSwitch(
    useLocalMedia: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackColor by animateColorAsState(
        targetValue = if (useLocalMedia) LocalModeTrackColor else Color.White.copy(alpha = 0.15f),
        animationSpec = tween(300),
        label = "switchTrack"
    )
    val trackColorDisabled by animateColorAsState(
        targetValue = if (useLocalMedia) LocalModeTrackColorDim else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(300),
        label = "switchTrackDim"
    )
    Switch(
        checked = useLocalMedia,
        onCheckedChange = if (enabled) onCheckedChange else null,
        thumbContent = {
            Icon(
                imageVector = if (useLocalMedia) Icons.Rounded.PhoneAndroid else Icons.Rounded.Cloud,
                contentDescription = if (useLocalMedia) "Local device" else "Spotify",
                modifier = Modifier.size(14.dp)
            )
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = if (enabled) trackColor else trackColorDisabled,
            checkedIconColor = LocalModeTrackColor,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
            uncheckedTrackColor = if (enabled) trackColor else trackColorDisabled,
            uncheckedIconColor = Color.White.copy(alpha = 0.6f),
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color.White.copy(alpha = 0.4f),
            disabledCheckedTrackColor = trackColorDisabled,
            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.3f),
            disabledUncheckedTrackColor = Color.White.copy(alpha = 0.06f)
        )
    )
}