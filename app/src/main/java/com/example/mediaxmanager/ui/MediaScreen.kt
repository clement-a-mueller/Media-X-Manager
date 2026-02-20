package com.example.mediaxmanager.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.screens.HomeScreen
import com.example.mediaxmanager.ui.screens.SettingsScreen
import com.example.mediaxmanager.ui.screens.extractDominantColor

sealed class Screen(val label: String) {
    object Home : Screen("Home")
    object Settings : Screen("Settings")
}

@Composable
fun MediaScreen(viewModel: MediaViewModel) {
    val state by viewModel.mediaState.collectAsStateWithLifecycle()
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    val dominantColor = remember(state.artwork) {
        extractDominantColor(state.artwork)
    }

    val navBarColor by animateColorAsState(
        targetValue = if (selectedScreen is Screen.Home) dominantColor.copy(alpha = 0.85f)
        else Color(0xFF1C1B1F),
        animationSpec = tween(800),
        label = "navColor"
    )

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = navBarColor,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedScreen is Screen.Home,
                    onClick = { selectedScreen = Screen.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White) },
                    label = { Text("Home", color = Color.White) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = selectedScreen is Screen.Settings,
                    onClick = { selectedScreen = Screen.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) },
                    label = { Text("Settings", color = Color.White) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedScreen) {
                is Screen.Home -> HomeScreen(viewModel)
                is Screen.Settings -> SettingsScreen()
            }
        }
    }
}