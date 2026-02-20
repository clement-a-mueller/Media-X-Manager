package com.example.mediaxmanager.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mediaxmanager.ui.theme.AppStyle
import androidx.compose.foundation.layout.statusBarsPadding
import com.example.mediaxmanager.media.MediaViewModel


@Composable
fun SettingsScreen(
    currentStyle: AppStyle,
    onStyleChange: (AppStyle) -> Unit,
    viewModel: MediaViewModel
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var fullscreenEnabled by remember {
        mutableStateOf(prefs.getBoolean("fullscreen", true))
    }
    var sleepTimerMinutes by remember {
        mutableStateOf(prefs.getInt("sleep_timer", 0))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .statusBarsPadding()  // ← add this
            .padding(24.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(Modifier.height(32.dp))

            // Style selector
            Text(
                "App Style",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                "Choose the visual style of the app",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleOption(
                    label = "Dynamic",
                    description = "Background adapts to album art colors",
                    selected = currentStyle == AppStyle.DYNAMIC,
                    onClick = { onStyleChange(AppStyle.DYNAMIC) }
                )
                StyleOption(
                    label = "AMOLED",
                    description = "Pure black background, easy on OLED screens",
                    selected = currentStyle == AppStyle.AMOLED,
                    onClick = { onStyleChange(AppStyle.AMOLED) }
                )
                StyleOption(
                    label = "Minimal",
                    description = "Clean black and white, no color",
                    selected = currentStyle == AppStyle.MINIMAL,
                    onClick = { onStyleChange(AppStyle.MINIMAL) }
                )
                StyleOption(
                    label = "Glass",
                    description = "Frosted glass effect over album art",
                    selected = currentStyle == AppStyle.GLASS,
                    onClick = { onStyleChange(AppStyle.GLASS) }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            // Fullscreen toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Fullscreen mode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        "Hide status and navigation bar (may need to restart)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = fullscreenEnabled,
                    onCheckedChange = { enabled ->
                        fullscreenEnabled = enabled
                        prefs.edit().putBoolean("fullscreen", enabled).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.White.copy(alpha = 0.4f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = Color.White.copy(alpha = 0.1f)
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            Text(
                "Sleep Timer",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                "Stop playback after a set time",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
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
            .background(
                if (selected) Color.White.copy(alpha = 0.15f)
                else Color.White.copy(alpha = 0.05f)
            )
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
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black)
                )
            }
        }
    }
}