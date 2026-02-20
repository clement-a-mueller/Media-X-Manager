package com.example.mediaxmanager.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var fullscreenEnabled by remember {
        mutableStateOf(prefs.getBoolean("fullscreen", true))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F))
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

            Spacer(Modifier.height(16.dp))
            SettingsItem(
                title = "Notification access",
                subtitle = "Required to read media sessions"
            )
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = Color.White.copy(alpha = 0.1f)
        )
    }
}