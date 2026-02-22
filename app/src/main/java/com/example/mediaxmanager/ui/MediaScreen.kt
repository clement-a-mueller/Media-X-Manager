package com.example.mediaxmanager.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.components.GlassNavBar
import com.example.mediaxmanager.ui.screens.HomeScreen
import com.example.mediaxmanager.ui.screens.SearchScreen
import com.example.mediaxmanager.ui.screens.SettingsScreen
import com.example.mediaxmanager.ui.screens.extractDominantColor
import com.example.mediaxmanager.ui.theme.AppStyle

sealed class Screen {
    object Home : Screen()
    object Search : Screen()
    object Settings : Screen()
}

@Composable
fun MediaScreen(viewModel: MediaViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val state by viewModel.combinedMediaState.collectAsStateWithLifecycle()
    val localArtwork by viewModel.localArtwork.collectAsStateWithLifecycle()
    val isLocalPlaying by viewModel.isLocalPlaying.collectAsStateWithLifecycle()

    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var appStyle by remember {
        mutableStateOf(
            try {
                AppStyle.valueOf(
                    prefs.getString("style", AppStyle.DYNAMIC.name) ?: AppStyle.DYNAMIC.name
                )
            } catch (e: IllegalArgumentException) {
                prefs.edit().putString("style", AppStyle.DYNAMIC.name).apply()
                AppStyle.DYNAMIC
            }
        )
    }

    val selectedIndex = when (selectedScreen) {
        is Screen.Home     -> 0
        is Screen.Search   -> 1
        is Screen.Settings -> 2
    }

    // Use whichever artwork is currently active so all screens stay in sync
    val activeArtwork = if (isLocalPlaying) localArtwork else state.artwork
    val dominantColor = remember(activeArtwork) { extractDominantColor(activeArtwork) }
    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(800),
        label = "dominantColor"
    )

    // Navbar color: dynamic mode follows dominant color; all others go black
    val navBarColor by animateColorAsState(
        targetValue = when (appStyle) {
            AppStyle.DYNAMIC -> animatedDominantColor.copy(alpha = 0.85f)
            else             -> Color(0xFF000000)
        },
        animationSpec = tween(800),
        label = "navColor"
    )

    val content: @Composable () -> Unit = {
        when (selectedScreen) {
            is Screen.Home     -> HomeScreen(viewModel, appStyle)
            is Screen.Search   -> SearchScreen(viewModel, appStyle, animatedDominantColor)
            is Screen.Settings -> SettingsScreen(
                currentStyle = appStyle,
                onStyleChange = { newStyle ->
                    appStyle = newStyle
                    prefs.edit().putString("style", newStyle.name).apply()
                },
                viewModel = viewModel,
                appStyle = appStyle,
                dominantColor = animatedDominantColor
            )
        }
    }

    if (appStyle == AppStyle.GLASS) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            GlassNavBar(
                selectedIndex = selectedIndex,
                onHomeClick = { selectedScreen = Screen.Home },
                onSearchClick = { selectedScreen = Screen.Search },
                onSettingsClick = { selectedScreen = Screen.Settings },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)) {
                content()
            }
            NavigationBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                containerColor = navBarColor,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0.dp)
            ) {
                NavigationBarItem(
                    selected = selectedScreen is Screen.Home,
                    onClick = { selectedScreen = Screen.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White) },
                    label = { Text("Home", color = Color.White) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.White.copy(alpha = 0.2f))
                )
                NavigationBarItem(
                    selected = selectedScreen is Screen.Search,
                    onClick = { selectedScreen = Screen.Search },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White) },
                    label = { Text("Search", color = Color.White) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.White.copy(alpha = 0.2f))
                )
                NavigationBarItem(
                    selected = selectedScreen is Screen.Settings,
                    onClick = { selectedScreen = Screen.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) },
                    label = { Text("Settings", color = Color.White) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.White.copy(alpha = 0.2f))
                )
            }
        }
    }
}