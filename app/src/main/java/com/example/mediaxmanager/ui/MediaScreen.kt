package com.example.mediaxmanager.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource

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
    var m3Enabled by remember { mutableStateOf(prefs.getBoolean("m3_design", true)) }

    val selectedIndex = when (selectedScreen) {
        is Screen.Home     -> 0
        is Screen.Search   -> 1
        is Screen.Settings -> 2
    }

    val localTrack by viewModel.localTrack.collectAsStateWithLifecycle()
    val activeArtwork = when {
        localTrack != null && (isLocalPlaying || localArtwork != null) -> localArtwork
        else -> state.artwork
    }
    val dominantColor = remember(activeArtwork) { extractDominantColor(activeArtwork) }
    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(800),
        label = "dominantColor"
    )

    val navBarColor by animateColorAsState(
        targetValue = when (appStyle) {
            AppStyle.DYNAMIC -> animatedDominantColor.copy(alpha = 0.92f)
            else             -> Color(0xFF0E0E0E)
        },
        animationSpec = tween(800),
        label = "navColor"
    )

    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedScreen is Screen.Home) 1f else 0f)
                    .graphicsLayer { alpha = if (selectedScreen is Screen.Home) 1f else 0f }
            ) {
                HomeScreen(viewModel, appStyle)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedScreen is Screen.Search) 1f else 0f)
                    .graphicsLayer { alpha = if (selectedScreen is Screen.Search) 1f else 0f }
            ) {
                if (selectedScreen is Screen.Search) BackHandler { selectedScreen = Screen.Home }
                SearchScreen(viewModel, appStyle, animatedDominantColor)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedScreen is Screen.Settings) 1f else 0f)
                    .graphicsLayer { alpha = if (selectedScreen is Screen.Settings) 1f else 0f }
            ) {
                if (selectedScreen is Screen.Settings) BackHandler { selectedScreen = Screen.Home }
                SettingsScreen(
                    currentStyle = appStyle,
                    onStyleChange = { newStyle ->
                        appStyle = newStyle
                        prefs.edit().putString("style", newStyle.name).apply()
                    },
                    viewModel = viewModel,
                    appStyle = appStyle,
                    dominantColor = animatedDominantColor.copy(alpha = 1f),
                    m3Enabled = m3Enabled,
                    onM3Change = {
                        m3Enabled = it
                        prefs.edit().putBoolean("m3_design", it).apply()
                    }
                )
            }
        }
    }

    if (appStyle == AppStyle.GLASS) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)) { content() }
            GlassNavBar(
                selectedIndex = selectedIndex,
                onHomeClick = { selectedScreen = Screen.Home },
                onSearchClick = { selectedScreen = Screen.Search },
                onSettingsClick = { selectedScreen = Screen.Settings },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Block touches from passing through the GlassNavBar
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            )
        }
    } else {
        val rootBackground = when (appStyle) {
            // ... (Your existing rootBackground logic)
            AppStyle.DYNAMIC -> Color(
                red   = animatedDominantColor.red   * 0.65f,
                green = animatedDominantColor.green * 0.65f,
                blue  = animatedDominantColor.blue  * 0.65f,
                alpha = 1f
            )
            AppStyle.AMOLED  -> Color.Black
            AppStyle.GLASS   -> Color.Black
            else             -> Color(prefs.getInt("minimal_color", 0xFF2C2C2C.toInt()))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(rootBackground)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }

            if (m3Enabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp, start = 24.dp, end = 24.dp)
                ) {
                    val navPadding = 8.dp

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when (appStyle) {
                                    AppStyle.DYNAMIC -> navBarColor
                                    else             -> Color(0xFF1A1A1A)
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                            .padding(navPadding),
                        horizontalArrangement = Arrangement.spacedBy(navPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val homeSelected     = selectedScreen is Screen.Home
                        val searchSelected   = selectedScreen is Screen.Search
                        val settingsSelected = selectedScreen is Screen.Settings

                        M3NavItem(Icons.Default.Home,     "Home",     homeSelected,     NavPosition.START,  nextSelected = searchSelected)   { selectedScreen = Screen.Home }
                        M3NavItem(Icons.Default.Search,   "Search",   searchSelected,   NavPosition.MIDDLE, prevSelected = homeSelected,     nextSelected = settingsSelected) { selectedScreen = Screen.Search }
                        M3NavItem(Icons.Default.Settings, "Settings", settingsSelected, NavPosition.END,    prevSelected = searchSelected)   { selectedScreen = Screen.Settings }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(navBarColor)
                        // Block touches from passing through the Legacy NavBar
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegacyNavItem(Icons.Default.Home, "Home", selectedScreen is Screen.Home) { selectedScreen = Screen.Home }
                        LegacyNavItem(Icons.Default.Search, "Search", selectedScreen is Screen.Search) { selectedScreen = Screen.Search }
                        LegacyNavItem(Icons.Default.Settings, "Settings", selectedScreen is Screen.Settings) { selectedScreen = Screen.Settings }
                    }
                }
            }
        }
    }
}

private enum class NavPosition { START, MIDDLE, END }

@Composable
private fun RowScope.M3NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    position: NavPosition,
    prevSelected: Boolean = false,
    nextSelected: Boolean = false,
    onClick: () -> Unit
) {
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(200),
        label = "m3BgAlpha"
    )

    val outerR = 40.dp
    val innerR = 4.dp
    val shape = if (selected) {
        RoundedCornerShape(outerR)
    } else {
        val startR = if (position == NavPosition.START || prevSelected) outerR else innerR
        val endR   = if (position == NavPosition.END   || nextSelected) outerR else innerR
        RoundedCornerShape(topStart = startR, bottomStart = startR, topEnd = endR, bottomEnd = endR)
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.15f + bgAlpha * 0.15f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = if (selected) 1f else 0.5f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = if (selected) 1f else 0.5f),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun LegacyNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.5f)
        )
    }
}