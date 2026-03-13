package com.example.mediaxmanager.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import com.example.mediaxmanager.media.LyricsLine
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.media.QueueItem
import com.example.mediaxmanager.ui.components.AlbumArt
import com.example.mediaxmanager.ui.components.FullscreenArtwork
import com.example.mediaxmanager.ui.components.PlaybackControls
import com.example.mediaxmanager.ui.components.QueueSheet
import com.example.mediaxmanager.ui.components.WaveSlider
import com.example.mediaxmanager.ui.theme.AppStyle
import com.example.mediaxmanager.ui.utils.formatDuration
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.sin
import kotlinx.coroutines.delay

fun extractDominantColor(bitmap: Bitmap?): Color {
    if (bitmap == null) return Color(0xFF1C1B1F)
    val palette = Palette.from(bitmap).generate()
    val argb = palette.getDarkVibrantColor(
        palette.getDominantColor(0xFF1C1B1F.toInt())
    )
    return Color(argb)
}

private val LocalModeTrackColor    = Color(0xFF9B8EC4)
private val LocalModeTrackColorDim = Color(0xFF9B8EC4).copy(alpha = 0.3f)

@Composable
fun HomeScreen(viewModel: MediaViewModel, appStyle: AppStyle) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }

    val state            by viewModel.combinedMediaState.collectAsStateWithLifecycle()
    val isLocalPlaying   by viewModel.isLocalPlaying.collectAsStateWithLifecycle()
    val localArtwork     by viewModel.localArtwork.collectAsStateWithLifecycle()
    val localTrack       by viewModel.localTrack.collectAsStateWithLifecycle()
    val localQueue       by viewModel.localQueue.collectAsStateWithLifecycle()
    val isLocalShuffling by viewModel.isLocalShuffling.collectAsStateWithLifecycle()
    val localRepeatMode  by viewModel.localRepeatMode.collectAsStateWithLifecycle()
    val localProgress    by viewModel.localProgress.collectAsStateWithLifecycle()
    val localPosition    by viewModel.localPosition.collectAsStateWithLifecycle()
    val localDuration    by viewModel.localDuration.collectAsStateWithLifecycle()
    val lyricsLines      by viewModel.lyricsLines.collectAsStateWithLifecycle()

    var showFullscreenArt by remember { mutableStateOf(false) }
    var showQueue         by remember { mutableStateOf(false) }
    var useLocalMedia     by remember { mutableStateOf(false) }
    var lyricsMode        by remember { mutableStateOf(false) }
    var isSeeking         by remember { mutableStateOf(false) }
    var seekPosition      by remember { mutableFloatStateOf(0f) }

    // Clear isSeeking after a delay so the progress StateFlow has time to
    // update before we stop overriding effectiveProgress with seekPosition.
    LaunchedEffect(isSeeking) {
        if (isSeeking) {
            delay(500L)
            isSeeking = false
        }
    }

    LaunchedEffect(isLocalPlaying) {
        if (isLocalPlaying) useLocalMedia = true
    }

    // ── Effective values ──────────────────────────────────────────────────────
    val effectiveArtwork   = if (useLocalMedia) localArtwork  else state.artwork
    val effectiveTitle     = if (useLocalMedia) (localTrack?.title  ?: state.title)  else state.title
    val effectiveArtist    = if (useLocalMedia) (localTrack?.artist ?: state.artist) else state.artist
    val effectiveIsPlaying = if (useLocalMedia) isLocalPlaying else state.isPlaying
    val effectiveShuffling = if (useLocalMedia) isLocalShuffling else state.isShuffling
    val effectiveRepeat    = if (useLocalMedia) localRepeatMode  else state.repeatMode
    val effectiveProgress  = if (isSeeking) seekPosition else if (useLocalMedia) localProgress else state.progress
    val effectivePosition  = if (useLocalMedia) localPosition else state.position
    val effectiveDuration  = if (useLocalMedia) localDuration else state.duration
    val hasValidDuration   = effectiveDuration > 0

    val effectiveQueue = if (useLocalMedia) {
        localQueue.map { QueueItem(title = it.title, artist = it.artist) }
    } else {
        state.queue
    }

    val externalNextTrack = remember(state.queue) { state.queue.getOrNull(1) }
    val localNextTrack    = remember(localQueue) {
        localQueue.getOrNull(localQueue.indexOfFirst { it.title == (localTrack?.title ?: "") } + 1)
    }

    val gesturesEnabled      = remember { prefs.getBoolean("gestures_enabled", false) }
    val lyricsEnabled        by remember { derivedStateOf { prefs.getBoolean("lyrics_enabled", false) } }
    val karaokeEnabled       by remember { derivedStateOf { prefs.getBoolean("karaoke_enabled", false) } }
    val showTrackInfo        by remember { derivedStateOf { prefs.getBoolean("show_track_info", true) } }
    val showPlaybackControls by remember { derivedStateOf { prefs.getBoolean("show_playback_controls", true) } }
    val showUpNext           by remember { derivedStateOf { prefs.getBoolean("show_up_next", true) } }
    val sliderStyle          by remember { derivedStateOf { prefs.getString("player_slider_style", "wave") ?: "wave" } }

    LaunchedEffect(localTrack?.title, localTrack?.artist, localNextTrack, lyricsEnabled) {
        if (!lyricsEnabled) return@LaunchedEffect
        val title  = localTrack?.title  ?: return@LaunchedEffect
        val artist = localTrack?.artist ?: ""
        viewModel.fetchLyricsIfNeeded(
            title      = title,
            artist     = artist,
            isLocal    = true,
            nextTitle  = localNextTrack?.title  ?: "",
            nextArtist = localNextTrack?.artist ?: ""
        )
    }

    LaunchedEffect(state.title, state.artist, externalNextTrack, lyricsEnabled) {
        if (!lyricsEnabled) return@LaunchedEffect
        val title = state.title.ifEmpty { return@LaunchedEffect }
        viewModel.fetchLyricsIfNeeded(
            title      = title,
            artist     = state.artist,
            isLocal    = false,
            nextTitle  = externalNextTrack?.title  ?: "",
            nextArtist = externalNextTrack?.artist ?: ""
        )
    }

    val currentLineIndex = remember(effectivePosition, lyricsLines) {
        if (lyricsLines.isEmpty()) -1
        else lyricsLines.indexOfLast { it.timeMs <= effectivePosition }
    }

    val lyricsListState = rememberLazyListState()
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0)
            lyricsListState.animateScrollToItem(index = (currentLineIndex - 2).coerceAtLeast(0))
    }

    val currentUseLocalMedia by rememberUpdatedState(useLocalMedia)
    val dominantColor = remember(effectiveArtwork) { extractDominantColor(effectiveArtwork) }

    val backgroundColor = when (appStyle) {
        AppStyle.DYNAMIC -> Color.Transparent
        AppStyle.AMOLED  -> Color.Black
        AppStyle.MINIMAL -> Color(prefs.getInt("minimal_color", 0xFF2C2C2C.toInt()))
        AppStyle.GLASS   -> Color.Black
    }

    val showKaraokeButton = karaokeEnabled && lyricsEnabled && lyricsLines.isNotEmpty()

    fun switchToLocal(toLocal: Boolean) {
        val title  = if (toLocal) (localTrack?.title  ?: "") else state.title
        val artist = if (toLocal) (localTrack?.artist ?: "") else state.artist
        viewModel.syncLyricsForSource(title = title, artist = artist, isLocal = toLocal)
        useLocalMedia = toLocal
    }

    // Shared seek callbacks — reused by all three slider styles
    val onSeekChange: (Float) -> Unit = { newProgress ->
        if (hasValidDuration) {
            isSeeking = true
            seekPosition = newProgress
        }
    }
    val onSeekFinished: () -> Unit = {
        if (hasValidDuration) {
            if (currentUseLocalMedia) viewModel.seekToLocal(seekPosition)
            else viewModel.seekTo(seekPosition)
        }
        // Do NOT set isSeeking = false here — the LaunchedEffect clears it after
        // 500 ms so the StateFlows have time to reflect the new position first.
    }

    // ── Swipe gesture modifier ────────────────────────────────────────────────
    // detectVerticalDragGestures only competes for vertical drags in the
    // gesture arena. When the WaveSlider wins a horizontal drag first, this
    // detector simply loses and never fires — sliders are never blocked.
    val swipeModifier = if (gesturesEnabled) {
        Modifier.pointerInput(useLocalMedia) {
            var totalY = 0f
            detectVerticalDragGestures(
                onDragStart = { totalY = 0f },
                onDragEnd = {
                    if (totalY < -100f) {
                        if (useLocalMedia) viewModel.localNext(context) else viewModel.next()
                    } else if (totalY > 100f) {
                        if (useLocalMedia) viewModel.localPreviousOrRestart(context) else viewModel.previous()
                    }
                },
                onDragCancel = { totalY = 0f },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    totalY += dragAmount
                }
            )
        }
    } else Modifier

    Box(modifier = Modifier.fillMaxSize().then(swipeModifier)) {

        // ── Background ────────────────────────────────────────────────────────
        if (appStyle == AppStyle.GLASS && effectiveArtwork != null) {
            Image(
                bitmap = effectiveArtwork.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.4f
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }

        if (!state.isConnected) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No media playing", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        } else {

            // ── KARAOKE VIEW ──────────────────────────────────────────────────
            AnimatedVisibility(visible = lyricsMode, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent() } } }
                ) {
                    LyricsModeContent(
                        lyricsLines      = lyricsLines,
                        currentLineIndex = currentLineIndex,
                        listState        = lyricsListState,
                        title            = effectiveTitle,
                        artist           = effectiveArtist,
                        accentColor      = dominantColor,
                        onToggle         = { lyricsMode = false }
                    )
                }
            }

            // ── NORMAL PLAYER VIEW ────────────────────────────────────────────
            AnimatedVisibility(visible = !lyricsMode, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (showTrackInfo) {
                        AnimatedContent(
                            targetState = effectiveArtwork,
                            transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
                            label = "artwork"
                        ) { artwork ->
                            AlbumArt(
                                bitmap      = artwork,
                                onSingleTap = {
                                    if (gesturesEnabled) {
                                        if (useLocalMedia) viewModel.toggleLocalPlayPause()
                                        else viewModel.playPause()
                                    }
                                },
                                onDoubleTap = { showFullscreenArt = true }
                            )
                        }

                        Spacer(Modifier.height(32.dp))

                        AnimatedContent(
                            targetState = effectiveTitle,
                            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
                            label = "title"
                        ) { Text(it, style = MaterialTheme.typography.headlineLarge, color = Color.White) }

                        AnimatedContent(
                            targetState = effectiveArtist,
                            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
                            label = "artist"
                        ) { Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f)) }

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
                    } else {
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
                    }

                    // ── Slider ────────────────────────────────────────────────
                    if (sliderStyle == "eq") {
                        EqSlider(
                            value                 = if (hasValidDuration) effectiveProgress else 0f,
                            onValueChange         = onSeekChange,
                            onValueChangeFinished = onSeekFinished,
                            modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                    } else {
                        if (sliderStyle == "minimal" || appStyle == AppStyle.MINIMAL) {
                            MinimalSeekBar(
                                value                 = if (hasValidDuration) effectiveProgress else 0f,
                                onValueChange         = onSeekChange,
                                onValueChangeFinished = onSeekFinished,
                                modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            )
                        } else {
                            WaveSlider(
                                value                 = if (hasValidDuration) effectiveProgress else 0f,
                                onValueChange         = onSeekChange,
                                onValueChangeFinished = onSeekFinished,
                                modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Playback controls ─────────────────────────────────────
                    if (showPlaybackControls) {
                        PlaybackControls(
                            isPlaying   = effectiveIsPlaying,
                            isShuffling = effectiveShuffling,
                            repeatMode  = effectiveRepeat,
                            onPlayPause = {
                                if (useLocalMedia) viewModel.toggleLocalPlayPause()
                                else viewModel.playPause()
                            },
                            onNext = {
                                if (useLocalMedia) viewModel.localNext(context)
                                else viewModel.next()
                            },
                            onPrevious = {
                                if (useLocalMedia) {
                                    viewModel.localPreviousOrRestart(context)
                                } else {
                                    if (effectivePosition > 3_000L) viewModel.seekTo(0f)
                                    else viewModel.previous()
                                }
                            },
                            onShuffle = {
                                if (useLocalMedia) viewModel.toggleLocalShuffle()
                                else viewModel.toggleShuffle()
                            },
                            onRepeat = {
                                if (useLocalMedia) viewModel.toggleLocalRepeat()
                                else viewModel.toggleRepeat()
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (showUpNext) {
                        TextButton(onClick = { showQueue = true }, enabled = !showQueue) {
                            Text(
                                "Up Next",
                                color = if (showQueue) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
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
            }

            // ── KARAOKE ENTRY BUTTON ──────────────────────────────────────────
            if (showKaraokeButton && !lyricsMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 16.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { lyricsMode = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Lyrics, "Karaoke mode", tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Karaoke", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }

            // ── SOURCE SWITCH ─────────────────────────────────────────────────
            if (!lyricsMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 120.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    SourceSwitch(
                        useLocalMedia = useLocalMedia,
                        enabled       = localTrack != null || useLocalMedia,
                        onCheckedChange = { toLocal ->
                            if (toLocal) {
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
                            viewModel.syncLyricsForSource(
                                title   = if (toLocal) (localTrack?.title  ?: "") else state.title,
                                artist  = if (toLocal) (localTrack?.artist ?: "") else state.artist,
                                isLocal = toLocal
                            )
                            useLocalMedia = toLocal
                        }
                    )
                }
            }
        } // end isConnected

        if (showQueue) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { showQueue = false })
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                QueueSheet(
                    queue             = effectiveQueue,
                    currentTitle      = effectiveTitle,
                    isSpotify         = !useLocalMedia,
                    localQueue        = localQueue,
                    onRemoveFromQueue = { index -> viewModel.removeFromLocalQueue(index) },
                    onDismiss         = { showQueue = false }
                )
            }
        }

        FullscreenArtwork(
            bitmap          = effectiveArtwork,
            visible         = showFullscreenArt,
            backgroundColor = backgroundColor,
            onDismiss       = { showFullscreenArt = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EQ-style slider
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EqSlider(
    value: Float,
    onValueChange: (Float) -> Unit = {},
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.15f),
    barCount: Int = 40
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val phases = remember(barCount) { List(barCount) { it.toFloat() / barCount } }
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "eqTime"
    )
    val animatedValue by animateFloatAsState(targetValue = value, animationSpec = tween(200), label = "eqProgress")
    var sliderWidth by remember { mutableFloatStateOf(1f) }

    Canvas(
        modifier = modifier
            .height(48.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onValueChange((offset.x / sliderWidth).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onValueChangeFinished?.invoke() },
                    onDragCancel = { onValueChangeFinished?.invoke() },
                    onHorizontalDrag = { change, _ ->
                        onValueChange((change.position.x / sliderWidth).coerceIn(0f, 1f))
                        change.consume()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / sliderWidth).coerceIn(0f, 1f))
                    onValueChangeFinished?.invoke()
                }
            }
    ) {
        sliderWidth  = size.width
        val barWidth     = (size.width / barCount) * 0.55f
        val gap          = (size.width / barCount) * 0.45f
        val maxBarHeight = size.height * 0.9f
        val minBarHeight = size.height * 0.08f
        val progressX    = size.width * animatedValue

        for (i in 0 until barCount) {
            val x        = i * (barWidth + gap) + gap / 2f
            val centerX  = x + barWidth / 2f
            val isActive = centerX <= progressX
            val phase    = phases[i]
            val h1 = sin((time + phase) * 2f * kotlin.math.PI.toFloat())
            val h2 = sin((time * 1.7f + phase * 1.3f) * 2f * kotlin.math.PI.toFloat())
            val normalised = ((h1 * 0.6f + h2 * 0.4f) + 1f) / 2f
            val barHeight = if (isActive)
                minBarHeight + normalised * (maxBarHeight - minBarHeight)
            else
                minBarHeight + normalised * (maxBarHeight - minBarHeight) * 0.25f
            drawRoundRect(
                color        = if (isActive) activeColor else inactiveColor,
                topLeft      = Offset(x, (size.height - barHeight) / 2f),
                size         = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
private fun MinimalSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit = {},
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.25f),
    thumbColor: Color = Color.White
) {
    val animatedValue by animateFloatAsState(targetValue = value, animationSpec = tween(100), label = "minimalProgress")
    var sliderWidth by remember { mutableFloatStateOf(1f) }

    Canvas(
        modifier = modifier
            .height(36.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onValueChange((offset.x / sliderWidth).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onValueChangeFinished?.invoke() },
                    onDragCancel = { onValueChangeFinished?.invoke() },
                    onHorizontalDrag = { change, _ ->
                        onValueChange((change.position.x / sliderWidth).coerceIn(0f, 1f))
                        change.consume()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / sliderWidth).coerceIn(0f, 1f))
                    onValueChangeFinished?.invoke()
                }
            }
    ) {
        sliderWidth = size.width
        val trackY = size.height / 2f
        val trackHeight = 4.dp.toPx()
        val progressX = size.width * animatedValue
        val thumbRadius = 6.dp.toPx()

        // Inactive track
        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(0f, trackY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )
        // Active track
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, trackY - trackHeight / 2f),
            size = Size(progressX, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )
        // Thumb
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(progressX.coerceIn(thumbRadius, size.width - thumbRadius), trackY)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Full-screen karaoke
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LyricsModeContent(
    lyricsLines: List<LyricsLine>,
    currentLineIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    title: String,
    artist: String,
    accentColor: Color,
    onToggle: () -> Unit
) {
    val highlightColor = remember(accentColor) {
        Color(
            red   = (accentColor.red   + 0.55f).coerceAtMost(1f),
            green = (accentColor.green + 0.55f).coerceAtMost(1f),
            blue  = (accentColor.blue  + 0.55f).coerceAtMost(1f),
            alpha = 1f
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            contentPadding = PaddingValues(top = 120.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(lyricsLines) { index, line ->
                val isCurrent = index == currentLineIndex
                val isPast    = index < currentLineIndex
                AnimatedContent(
                    targetState = isCurrent,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "lyric_$index"
                ) { current ->
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize   = if (current) 24.sp else 18.sp,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = if (current) 32.sp else 26.sp
                        ),
                        color = when {
                            current -> highlightColor
                            isPast  -> Color.White.copy(alpha = 0.35f)
                            else    -> Color.White.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(160.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent))))

        Row(
            modifier = Modifier
                .fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title,  style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(artist, style = MaterialTheme.typography.bodySmall,   color = Color.White.copy(alpha = 0.6f))
            }
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .clickable { onToggle() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.MusicNote, "Back to player", tint = Color.White, modifier = Modifier.size(16.dp))
                Text("Player", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Source switch
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SourceSwitch(
    useLocalMedia: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackColor by animateColorAsState(
        targetValue   = if (useLocalMedia) LocalModeTrackColor else Color.White.copy(alpha = 0.15f),
        animationSpec = tween(300), label = "switchTrack"
    )
    val trackColorDisabled by animateColorAsState(
        targetValue   = if (useLocalMedia) LocalModeTrackColorDim else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(300), label = "switchTrackDim"
    )
    Switch(
        checked         = useLocalMedia,
        onCheckedChange = if (enabled) onCheckedChange else null,
        thumbContent    = {
            Icon(
                imageVector        = if (useLocalMedia) Icons.Rounded.PhoneAndroid else Icons.Rounded.Cloud,
                contentDescription = if (useLocalMedia) "Local device" else "Spotify",
                modifier           = Modifier.size(14.dp)
            )
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor           = Color.White,
            checkedTrackColor           = if (enabled) trackColor else trackColorDisabled,
            checkedIconColor            = LocalModeTrackColor,
            checkedBorderColor          = Color.Transparent,
            uncheckedThumbColor         = Color.White.copy(alpha = 0.9f),
            uncheckedTrackColor         = if (enabled) trackColor else trackColorDisabled,
            uncheckedIconColor          = Color.White.copy(alpha = 0.6f),
            uncheckedBorderColor        = Color.Transparent,
            disabledCheckedThumbColor   = Color.White.copy(alpha = 0.4f),
            disabledCheckedTrackColor   = trackColorDisabled,
            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.3f),
            disabledUncheckedTrackColor = Color.White.copy(alpha = 0.06f)
        )
    )
}