package com.example.mediaxmanager.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.theme.AppStyle

// Bright minimal colors that need a darker version on secondary screens
private val brightMinimalColors = setOf(
    0xFF8B4513.toInt(), // Rust
    0xFF2E8B57.toInt(), // Emerald
    0xFF6A0DAD.toInt(), // Violet
    0xFFB8860B.toInt(), // Gold
)

fun darkenColor(color: Color, factor: Float = 0.45f): Color {
    return Color(
        red = color.red * factor,
        green = color.green * factor,
        blue = color.blue * factor,
        alpha = color.alpha
    )
}

fun resolveSecondaryBackground(appStyle: AppStyle, minimalColor: Color): Color {
    return when (appStyle) {
        AppStyle.DYNAMIC -> Color.Black
        AppStyle.AMOLED  -> Color.Black
        AppStyle.GLASS   -> Color.Black
        AppStyle.MINIMAL -> {
            if (minimalColor.toArgb() in brightMinimalColors) {
                darkenColor(minimalColor)
            } else {
                minimalColor
            }
        }
    }
}

@Composable
fun SettingsScreen(
    currentStyle: AppStyle,
    onStyleChange: (AppStyle) -> Unit,
    viewModel: MediaViewModel,
    appStyle: AppStyle = AppStyle.DYNAMIC,
    dominantColor: Color = Color.Black
) {
    var showDesignScreen by remember { mutableStateOf(false) }

    if (showDesignScreen) {
        DesignSettingsScreen(
            currentStyle = currentStyle,
            onStyleChange = onStyleChange,
            onBack = { showDesignScreen = false },
            appStyle = appStyle,
            dominantColor = dominantColor
        )
    } else {
        MainSettingsScreen(
            viewModel = viewModel,
            onOpenDesign = { showDesignScreen = true },
            appStyle = appStyle,
            dominantColor = dominantColor
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainSettingsScreen(
    viewModel: MediaViewModel,
    onOpenDesign: () -> Unit,
    appStyle: AppStyle = AppStyle.DYNAMIC,
    dominantColor: Color = Color.Black
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var sleepTimerMinutes by remember { mutableStateOf(prefs.getInt("sleep_timer", 0)) }
    var aodEnabled by remember { mutableStateOf(prefs.getBoolean("aod_enabled", false)) }
    var gesturesEnabled by remember { mutableStateOf(prefs.getBoolean("gestures_enabled", false)) }
    var lyricsEnabled by remember { mutableStateOf(prefs.getBoolean("lyrics_enabled", false)) }
    var searchRefreshMinutes by remember { mutableStateOf(prefs.getInt("search_refresh_minutes", -1)) }

    val minimalColor = remember { Color(prefs.getInt("minimal_color", 0xFF2C2C2C.toInt())) }
    val backgroundColor = resolveSecondaryBackground(appStyle, minimalColor)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        if (appStyle == AppStyle.GLASS) {
            val localArtwork by viewModel.localArtwork.collectAsStateWithLifecycle()
            val combinedState by viewModel.combinedMediaState.collectAsStateWithLifecycle()
            val artwork = localArtwork ?: combinedState.artwork
            if (artwork != null) {
                Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.4f
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(32.dp))
            }

            item {
                SettingsGroupRow(
                    title = "Design",
                    subtitle = "App style, colors, fullscreen, lyrics & karaoke",
                    onClick = onOpenDesign
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                Text("Sleep Timer", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Text(
                    "Stop playback after a set time",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 1, 15, 30, 45, 60).forEach { minutes ->
                        FilterChip(
                            selected = sleepTimerMinutes == minutes,
                            onClick = {
                                sleepTimerMinutes = minutes
                                viewModel.setSleepTimer(minutes)
                            },
                            label = { Text(if (minutes == 0) "Off" else "${minutes}m", color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White.copy(alpha = 0.3f),
                                containerColor = Color.White.copy(alpha = 0.05f)
                            )
                        )
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                Text("Local Music Refresh", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Text(
                    "How often to rescan your local music library",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(-1 to "Never", 2 to "2min", 5 to "5min", 30 to "30min").forEach { (minutes, label) ->
                        FilterChip(
                            selected = searchRefreshMinutes == minutes,
                            onClick = {
                                searchRefreshMinutes = minutes
                                prefs.edit().putInt("search_refresh_minutes", minutes).apply()
                            },
                            label = { Text(label, color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White.copy(alpha = 0.3f),
                                containerColor = Color.White.copy(alpha = 0.05f)
                            )
                        )
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SettingsToggle(
                    title = "Always-On Display",
                    subtitle = "Show album art & track info when charging",
                    checked = aodEnabled,
                    onCheckedChange = { enabled ->
                        aodEnabled = enabled
                        prefs.edit().putBoolean("aod_enabled", enabled).apply()
                        if (enabled) {
                            val currentDream = android.provider.Settings.Secure.getString(
                                context.contentResolver, "screensaver_component"
                            )
                            if (currentDream?.contains(context.packageName) != true) {
                                context.startActivity(
                                    android.content.Intent("com.android.settings.ACTION_DREAM_SETTINGS")
                                )
                            }
                        }
                    }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SettingsToggle(
                    title = "Gestures",
                    subtitle = "Swipe left/right to switch source, up/down to skip",
                    checked = gesturesEnabled,
                    onCheckedChange = {
                        gesturesEnabled = it
                        prefs.edit().putBoolean("gestures_enabled", it).apply()
                    }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SettingsToggle(
                    title = "Lyrics",
                    subtitle = "Show synced lyrics below the player",
                    checked = lyricsEnabled,
                    onCheckedChange = {
                        lyricsEnabled = it
                        prefs.edit().putBoolean("lyrics_enabled", it).apply()
                    }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignSettingsScreen(
    currentStyle: AppStyle,
    onStyleChange: (AppStyle) -> Unit,
    onBack: () -> Unit,
    appStyle: AppStyle = AppStyle.DYNAMIC,
    dominantColor: Color = Color.Black
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var fullscreenEnabled by remember { mutableStateOf(prefs.getBoolean("fullscreen", true)) }
    var lyricsEnabled by remember { mutableStateOf(prefs.getBoolean("lyrics_enabled", false)) }
    var karaokeEnabled by remember { mutableStateOf(prefs.getBoolean("karaoke_enabled", false)) }
    var showTrackInfo by remember { mutableStateOf(prefs.getBoolean("show_track_info", true)) }
    var showPlaybackControls by remember { mutableStateOf(prefs.getBoolean("show_playback_controls", true)) }
    var showUpNext by remember { mutableStateOf(prefs.getBoolean("show_up_next", true)) }
    var sliderStyle by remember { mutableStateOf(prefs.getString("player_slider_style", "wave") ?: "wave") }
    var selectedColor by remember {
        mutableStateOf(Color(prefs.getInt("minimal_color", 0xFF2C2C2C.toInt())))
    }

    val minimalColor = remember(selectedColor) { selectedColor }
    val backgroundColor = resolveSecondaryBackground(appStyle, minimalColor)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Design", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text("App Style", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Text(
                    "Choose the visual style of the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleOption("Dynamic", "Background adapts to album art colors", currentStyle == AppStyle.DYNAMIC) { onStyleChange(AppStyle.DYNAMIC) }
                    StyleOption("AMOLED", "Pure black background, easy on OLED screens", currentStyle == AppStyle.AMOLED) { onStyleChange(AppStyle.AMOLED) }
                    StyleOption("Minimal", "Clean dark background, no color", currentStyle == AppStyle.MINIMAL) { onStyleChange(AppStyle.MINIMAL) }
                    StyleOption("Glass", "Frosted glass effect over album art", currentStyle == AppStyle.GLASS) { onStyleChange(AppStyle.GLASS) }
                }
            }

            item {
                if (currentStyle == AppStyle.MINIMAL) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                    Text("Background Color", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    val palette = listOf(
                        Color(0xFF1C1B1F) to "Default",
                        Color(0xFF1A1A2E) to "Navy",
                        Color(0xFF1B2A1B) to "Forest",
                        Color(0xFF2A1B1B) to "Crimson",
                        Color(0xFF1B1B2A) to "Indigo",
                        Color(0xFF2A2A1B) to "Olive",
                        Color(0xFF2A1B2A) to "Plum",
                        Color(0xFF1B2A2A) to "Teal",
                        Color(0xFF2A2010) to "Amber",
                        Color(0xFF101020) to "Midnight",
                        Color(0xFF8B4513) to "Rust",
                        Color(0xFF2E8B57) to "Emerald",
                        Color(0xFF6A0DAD) to "Violet",
                        Color(0xFFB8860B) to "Gold",
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        palette.forEach { (color, name) ->
                            val isSelected = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = Color.White,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedColor = color
                                        prefs.edit().putInt("minimal_color", color.toArgb()).apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = name, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SettingsToggle(
                    title = "Fullscreen mode",
                    subtitle = "Hide status and navigation bar",
                    checked = fullscreenEnabled,
                    onCheckedChange = {
                        fullscreenEnabled = it
                        prefs.edit().putBoolean("fullscreen", it).apply()
                    }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                Text("Slider style", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Text(
                    "How the progress bar looks on the player",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleOption(
                        label = "Wave",
                        description = "Animated wave line with timestamps",
                        selected = sliderStyle == "wave"
                    ) {
                        sliderStyle = "wave"
                        prefs.edit().putString("player_slider_style", "wave").apply()
                    }
                    StyleOption(
                        label = "Minimal",
                        description = "Simple flat bar with timestamps",
                        selected = sliderStyle == "minimal"
                    ) {
                        sliderStyle = "minimal"
                        prefs.edit().putString("player_slider_style", "minimal").apply()
                    }
                    StyleOption(
                        label = "EQ",
                        description = "Full-width animated equaliser bars, no timestamps",
                        selected = sliderStyle == "eq"
                    ) {
                        sliderStyle = "eq"
                        prefs.edit().putString("player_slider_style", "eq").apply()
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                Text("Player sections", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Text(
                    "Choose which sections are visible on the player",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsToggle(
                        title = "Track info",
                        subtitle = "Album art, title and artist",
                        checked = showTrackInfo,
                        onCheckedChange = {
                            showTrackInfo = it
                            prefs.edit().putBoolean("show_track_info", it).apply()
                        }
                    )
                    SettingsToggle(
                        title = "Playback controls",
                        subtitle = "Play/pause, skip, shuffle and repeat",
                        checked = showPlaybackControls,
                        onCheckedChange = {
                            showPlaybackControls = it
                            prefs.edit().putBoolean("show_playback_controls", it).apply()
                        }
                    )
                    SettingsToggle(
                        title = "Up Next",
                        subtitle = "Button to view the queue",
                        checked = showUpNext,
                        onCheckedChange = {
                            showUpNext = it
                            prefs.edit().putBoolean("show_up_next", it).apply()
                        }
                    )
                }
            }

            // Lyrics toggle + karaoke sub-toggle
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))

                SettingsToggle(
                    title = "Lyrics",
                    subtitle = "Fetch and show synced lyrics below the player",
                    checked = lyricsEnabled,
                    onCheckedChange = {
                        lyricsEnabled = it
                        prefs.edit().putBoolean("lyrics_enabled", it).apply()
                        // Disable karaoke if lyrics are turned off
                        if (!it && karaokeEnabled) {
                            karaokeEnabled = false
                            prefs.edit().putBoolean("karaoke_enabled", false).apply()
                        }
                    }
                )

                // Karaoke sub-toggle — slides in when lyrics are enabled
                AnimatedVisibility(
                    visible = lyricsEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = if (karaokeEnabled) Color.White else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "Karaoke mode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                "Shows a button on the player to switch to full-screen lyrics view",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = karaokeEnabled,
                            onCheckedChange = {
                                karaokeEnabled = it
                                prefs.edit().putBoolean("karaoke_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.White.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SettingsGroupRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
    }
}

@Composable
fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun StyleOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        if (selected) {
            Box(
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(50)).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color.Black))
            }
        }
    }
}