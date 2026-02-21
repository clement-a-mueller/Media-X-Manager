package com.example.mediaxmanager.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.theme.AppStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    currentStyle: AppStyle,
    onStyleChange: (AppStyle) -> Unit,
    viewModel: MediaViewModel
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var fullscreenEnabled by remember { mutableStateOf(prefs.getBoolean("fullscreen", true)) }
    var sleepTimerMinutes by remember { mutableStateOf(prefs.getInt("sleep_timer", 0)) }
    var aodEnabled by remember { mutableStateOf(prefs.getBoolean("aod_enabled", false)) }
    var gesturesEnabled by remember { mutableStateOf(prefs.getBoolean("gestures_enabled", false)) }
    var lyricsEnabled by remember { mutableStateOf(prefs.getBoolean("lyrics_enabled", false)) }
    var searchRefreshMinutes by remember { mutableStateOf(prefs.getInt("search_refresh_minutes", -1)) }
    var selectedColor by remember {
        mutableStateOf(Color(prefs.getInt("minimal_color", 0xFF2C2C2C.toInt())))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(32.dp))
            }

            // App Style
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

            // After the style options item:
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
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = name,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fullscreen
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

            // Sleep Timer
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
                            label = {
                                Text(
                                    if (minutes == 0) "Off" else "${minutes}m",
                                    color = Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White.copy(alpha = 0.3f),
                                containerColor = Color.White.copy(alpha = 0.05f)
                            )
                        )
                    }
                }
            }

            // Local Music Refresh
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

            // AOD
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

            // Gestures
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

            // Lyrics
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