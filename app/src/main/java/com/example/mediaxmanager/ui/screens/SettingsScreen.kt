package com.example.mediaxmanager.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.mediaxmanager.media.JellyfinRepository
import com.example.mediaxmanager.media.JellyfinResult
import com.example.mediaxmanager.media.MediaStreamService
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.theme.AppStyle
import kotlinx.coroutines.launch

// ─── Adaptive card ────────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(
    m3Enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (m3Enabled) {
        Column(
            modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            content = content
        )
    } else {
        Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp), content = content)
    }
}

@Composable
private fun SectionLabel(text: String, m3Enabled: Boolean) {
    if (m3Enabled) Text(text, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.55f))
    else           Text(text, style = MaterialTheme.typography.bodyLarge,  color = Color.White)
}

// ─── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    currentStyle:  AppStyle,
    onStyleChange: (AppStyle) -> Unit,
    viewModel:     MediaViewModel,
    appStyle:      AppStyle = AppStyle.DYNAMIC,
    dominantColor: Color    = Color.Black,
    m3Enabled:     Boolean  = true,
    onM3Change:    (Boolean) -> Unit = {}
) {
    var showDesignScreen by remember { mutableStateOf(false) }
    if (showDesignScreen) {
        BackHandler { showDesignScreen = false }
        DesignSettingsScreen(currentStyle = currentStyle, onStyleChange = onStyleChange,
            onBack = { showDesignScreen = false }, appStyle = appStyle, dominantColor = dominantColor,
            m3Enabled = m3Enabled, onM3Change = onM3Change)
    } else {
        MainSettingsScreen(viewModel = viewModel, onOpenDesign = { showDesignScreen = true },
            appStyle = appStyle, dominantColor = dominantColor, m3Enabled = m3Enabled)
    }
}

// ─── Main settings ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainSettingsScreen(
    viewModel:     MediaViewModel,
    onOpenDesign:  () -> Unit,
    appStyle:      AppStyle = AppStyle.DYNAMIC,
    dominantColor: Color    = Color.Black,
    m3Enabled:     Boolean  = true
) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val scope   = rememberCoroutineScope()

    var sleepTimerMinutes    by remember { mutableStateOf(prefs.getInt("sleep_timer", 0)) }
    var volumeSyncEnabled    by remember { mutableStateOf(prefs.getBoolean("pc_volume_sync", true)) }
    var manualVolume         by remember { mutableStateOf(prefs.getInt("pc_manual_volume", 80)) }
    var aodEnabled           by remember { mutableStateOf(prefs.getBoolean("aod_enabled", false)) }
    var gesturesEnabled      by remember { mutableStateOf(prefs.getBoolean("gestures_enabled", false)) }
    var searchRefreshMinutes by remember { mutableStateOf(prefs.getInt("search_refresh_minutes", -1)) }

    val backgroundColor = appBgColor(appStyle, dominantColor, prefs)
    val hPad        = if (m3Enabled) 20.dp else 24.dp
    val cardSpacing = if (m3Enabled) 12.dp else 0.dp

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor).statusBarsPadding()) {
        if (appStyle == AppStyle.GLASS) {
            val localArtwork  by viewModel.localArtwork.collectAsStateWithLifecycle()
            val combinedState by viewModel.combinedMediaState.collectAsStateWithLifecycle()
            val artwork = localArtwork ?: combinedState.artwork
            if (artwork != null) {
                Image(bitmap = artwork.asImageBitmap(), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.4f)
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            }
        }

        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(horizontal = hPad),
            contentPadding  = PaddingValues(top = 28.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Text("Settings",
                    style = if (m3Enabled) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                    color = Color.White)
                Spacer(Modifier.height(24.dp))
            }

            item {
                SettingsGroupRow(title = "Design", subtitle = "App style, colors, fullscreen, lyrics & karaoke",
                    m3Enabled = m3Enabled, onClick = onOpenDesign)
                Spacer(Modifier.height(if (m3Enabled) 24.dp else 0.dp))
            }

            // ── Linux Client Volume ──────────────────────────────────────────
            item {
                if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SectionLabel("Linux Client Volume", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))
                SettingsCard(m3Enabled) {
                    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
                    SettingsToggle(
                        title    = "Sync with phone volume",
                        subtitle = "Linux client volume matches your media volume buttons",
                        checked  = volumeSyncEnabled,
                        onCheckedChange = {
                            volumeSyncEnabled = it
                            prefs.edit().putBoolean("pc_volume_sync", it).apply()
                        }
                    )
                    AnimatedVisibility(visible = !volumeSyncEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = if (m3Enabled) 0.07f else 0.1f))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.VolumeDown, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                Slider(
                                    value         = manualVolume.toFloat(),
                                    onValueChange = {
                                        manualVolume = it.toInt()
                                        prefs.edit().putInt("pc_manual_volume", it.toInt()).apply()
                                    },
                                    valueRange = 0f..100f,
                                    modifier   = Modifier.weight(1f),
                                    colors     = SliderDefaults.colors(
                                        thumbColor            = Color.White,
                                        activeTrackColor      = Color.White.copy(alpha = 0.8f),
                                        inactiveTrackColor    = Color.White.copy(alpha = 0.2f)
                                    )
                                )
                                Icon(Icons.Default.VolumeUp, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                Text("${manualVolume}%", style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(36.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(cardSpacing))
            }

            // ── Sleep Timer ──────────────────────────────────────────────────
            item {
                if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SectionLabel("Sleep Timer", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))
                SettingsCard(m3Enabled) {
                    if (!m3Enabled) { Text("Stop playback after a set time", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f)); Spacer(Modifier.height(8.dp)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1, 15, 30, 45, 60).forEach { minutes ->
                            FilterChip(selected = sleepTimerMinutes == minutes,
                                onClick = { sleepTimerMinutes = minutes; viewModel.setSleepTimer(minutes) },
                                label = { Text(if (minutes == 0) "Off" else "${minutes}m") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White.copy(alpha = 0.28f),
                                    containerColor = Color.White.copy(alpha = 0.08f), labelColor = Color.White, selectedLabelColor = Color.White),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false,
                                    borderColor = Color.White.copy(alpha = 0.12f), selectedBorderColor = Color.Transparent))
                        }
                    }
                }
                Spacer(Modifier.height(cardSpacing))
            }

            // ── Library ──────────────────────────────────────────────────────
            item {
                if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SectionLabel("Library", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))

                var likedFolderPath by remember { mutableStateOf(prefs.getString("liked_folder_path", null)) }
                val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val docId = DocumentsContract.getTreeDocumentId(uri)
                        val parts = docId.split(":")
                        val absPath = if (parts.size == 2 && parts[0].equals("primary", ignoreCase = true))
                            "/storage/emulated/0/${parts[1]}"
                        else uri.path?.removePrefix("/tree/primary:")?.let { "/storage/emulated/0/$it" } ?: "/storage/emulated/0/Music"
                        prefs.edit().putString("liked_folder_path", absPath).apply()
                        likedFolderPath = absPath
                    }
                }

                SettingsCard(m3Enabled) {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { folderPickerLauncher.launch(null) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Folder, null, tint = Color(0xFFFFD700).copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Music folder", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                            Text(likedFolderPath?.removePrefix("/storage/emulated/0/") ?: "Not set — tap to choose",
                                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = if (m3Enabled) 0.07f else 0.1f))
                    Text("Local music rescan interval", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("How often to rescan your local music library", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-1 to "Never", 2 to "2 min", 5 to "5 min", 30 to "30 min").forEach { (minutes, label) ->
                            FilterChip(selected = searchRefreshMinutes == minutes,
                                onClick = { searchRefreshMinutes = minutes; prefs.edit().putInt("search_refresh_minutes", minutes).apply() },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White.copy(alpha = 0.28f),
                                    containerColor = Color.White.copy(alpha = 0.08f), labelColor = Color.White, selectedLabelColor = Color.White),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false,
                                    borderColor = Color.White.copy(alpha = 0.12f), selectedBorderColor = Color.Transparent))
                        }
                    }
                }
                Spacer(Modifier.height(cardSpacing))
            }

            // ── Jellyfin ─────────────────────────────────────────────────────
            item {
                if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SectionLabel("Jellyfin", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))

                var jellyfinEnabled by remember { mutableStateOf(prefs.getBoolean("jellyfin_enabled", false)) }
                var serverUrl       by remember { mutableStateOf(prefs.getString("jellyfin_server_url", "") ?: "") }
                var username        by remember { mutableStateOf(prefs.getString("jellyfin_username", "") ?: "") }
                var password        by remember { mutableStateOf("") }
                var isConnecting    by remember { mutableStateOf(false) }
                var connectError    by remember { mutableStateOf<String?>(null) }
                var isConnected     by remember { mutableStateOf(JellyfinRepository.session != null) }

                SettingsCard(m3Enabled) {
                    SettingsToggle(
                        title    = "Enable Jellyfin",
                        subtitle = "Stream music from your Jellyfin server",
                        checked  = jellyfinEnabled,
                        onCheckedChange = {
                            jellyfinEnabled = it
                            prefs.edit().putBoolean("jellyfin_enabled", it).apply()
                            if (!it) {
                                JellyfinRepository.logout()
                                isConnected = false
                                TrackCache.jellyfinTracks = emptyList()
                                TrackCache.jellyfinAlbums = emptyList()
                                prefs.edit().putString("active_source", "LOCAL").apply()
                            }
                        }
                    )

                    AnimatedVisibility(visible = jellyfinEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.07f))

                            // Status indicator
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                                    .background(if (isConnected) Color(0xFF00A4DC) else Color.White.copy(alpha = 0.2f)))
                                Text(
                                    if (isConnected) "Connected · ${serverUrl.removePrefix("http://").removePrefix("https://").trimEnd('/')}"
                                    else "Not connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isConnected) Color(0xFF00A4DC) else Color.White.copy(alpha = 0.45f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!isConnected) {
                                // Server URL
                                OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it; prefs.edit().putString("jellyfin_server_url", it).apply() },
                                    modifier = Modifier.fillMaxWidth(), label = { Text("Server URL", color = Color.White.copy(alpha = 0.5f)) },
                                    placeholder = { Text("http://192.168.1.10:8096", color = Color.White.copy(alpha = 0.3f)) },
                                    singleLine = true, shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF00A4DC).copy(alpha = 0.7f), unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        cursorColor = Color.White, focusedLabelColor = Color(0xFF00A4DC), unfocusedLabelColor = Color.White.copy(alpha = 0.5f)))
                                Spacer(Modifier.height(8.dp))

                                // Username
                                OutlinedTextField(value = username, onValueChange = { username = it; prefs.edit().putString("jellyfin_username", it).apply() },
                                    modifier = Modifier.fillMaxWidth(), label = { Text("Username", color = Color.White.copy(alpha = 0.5f)) },
                                    singleLine = true, shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF00A4DC).copy(alpha = 0.7f), unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        cursorColor = Color.White, focusedLabelColor = Color(0xFF00A4DC), unfocusedLabelColor = Color.White.copy(alpha = 0.5f)))
                                Spacer(Modifier.height(8.dp))

                                // Password
                                OutlinedTextField(value = password, onValueChange = { password = it },
                                    modifier = Modifier.fillMaxWidth(), label = { Text("Password", color = Color.White.copy(alpha = 0.5f)) },
                                    singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF00A4DC).copy(alpha = 0.7f), unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        cursorColor = Color.White, focusedLabelColor = Color(0xFF00A4DC), unfocusedLabelColor = Color.White.copy(alpha = 0.5f)))
                                Spacer(Modifier.height(12.dp))

                                if (connectError != null) {
                                    Text(connectError!!, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(bottom = 8.dp))
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isConnecting = true; connectError = null
                                            when (val r = JellyfinRepository.login(serverUrl.trim(), username.trim(), password)) {
                                                is JellyfinResult.Success -> { isConnected = true; password = ""; TrackCache.jellyfinTracks = emptyList(); TrackCache.jellyfinAlbums = emptyList() }
                                                is JellyfinResult.Error   -> connectError = r.message
                                            }
                                            isConnecting = false
                                        }
                                    },
                                    enabled  = !isConnecting && serverUrl.isNotBlank() && username.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A4DC).copy(alpha = 0.3f),
                                        disabledContainerColor = Color.White.copy(alpha = 0.06f))
                                ) {
                                    if (isConnecting) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Connecting…", color = Color.White)
                                    } else {
                                        Icon(Icons.Default.Cloud, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Connect", color = Color.White)
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        JellyfinRepository.logout(); isConnected = false
                                        TrackCache.jellyfinTracks = emptyList(); TrackCache.jellyfinAlbums = emptyList()
                                        prefs.edit().putString("active_source", "LOCAL").apply()
                                    },
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                                    border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.4f))
                                ) {
                                    Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Disconnect")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(cardSpacing))
            }

            // ── Stream Server ─────────────────────────────────────────────────
            item {
                if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SectionLabel("Stream Server", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))

                var serverRunning by remember { mutableStateOf(prefs.getBoolean("stream_server_enabled", false)) }
                val localIp = remember { MediaStreamService.getLocalIp() }

                SettingsCard(m3Enabled) {
                    SettingsToggle(
                        title    = "Enable Stream Server",
                        subtitle = "Let your Linux PC stream music from this device",
                        checked  = serverRunning,
                        onCheckedChange = { enabled ->
                            serverRunning = enabled
                            prefs.edit().putBoolean("stream_server_enabled", enabled).apply()
                            if (enabled) {
                                MediaStreamService.viewModel = viewModel
                                MediaStreamService.start(context)
                            } else {
                                MediaStreamService.stop(context)
                            }
                        }
                    )

                    AnimatedVisibility(visible = serverRunning, enter = expandVertically(), exit = shrinkVertically()) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color    = Color.White.copy(alpha = if (m3Enabled) 0.07f else 0.1f)
                            )

                            // Status row with IP + port
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                                Column {
                                    Text(
                                        text  = if (localIp != null) "$localIp:${com.example.mediaxmanager.media.MediaHttpServer.PORT}"
                                        else "IP unavailable — check Wi-Fi",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (localIp != null) Color(0xFF4CAF50) else Color(0xFFFF6B6B)
                                    )
                                    Text(
                                        text  = "Run  python3 mediax_client.py --host ${localIp ?: "<ip>"}  on your PC",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.45f)
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color    = Color.White.copy(alpha = if (m3Enabled) 0.07f else 0.1f)
                            )

                            var playOnPc by remember { mutableStateOf(prefs.getBoolean("play_on_pc", false)) }
                            SettingsToggle(
                                title    = "Play on PC",
                                subtitle = "Music plays through your Linux PC instead of this phone",
                                checked  = playOnPc,
                                onCheckedChange = { enabled ->
                                    playOnPc = enabled
                                    prefs.edit().putBoolean("play_on_pc", enabled).apply()
                                    if (enabled) {
                                        viewModel.switchToPcStreamingMode()
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(cardSpacing))
            }

            // ── Behaviour toggles ────────────────────────────────────────────
            item {
                if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SectionLabel("Behaviour", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))
                SettingsCard(m3Enabled) {
                    SettingsToggle(title = "Always-On Display", subtitle = "Show album art & track info when charging",
                        checked = aodEnabled,
                        onCheckedChange = { enabled ->
                            aodEnabled = enabled; prefs.edit().putBoolean("aod_enabled", enabled).apply()
                            if (enabled) {
                                val cur = android.provider.Settings.Secure.getString(context.contentResolver, "screensaver_component")
                                if (cur?.contains(context.packageName) != true)
                                    context.startActivity(android.content.Intent("com.android.settings.ACTION_DREAM_SETTINGS"))
                            }
                        })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = if (m3Enabled) 0.07f else 0.1f))
                    SettingsToggle(title = "Gestures", subtitle = "Swipe left/right to switch source, up/down to skip",
                        checked = gesturesEnabled,
                        onCheckedChange = { gesturesEnabled = it; prefs.edit().putBoolean("gestures_enabled", it).apply() })
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─── Design sub-screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignSettingsScreen(
    currentStyle:  AppStyle,
    onStyleChange: (AppStyle) -> Unit,
    onBack:        () -> Unit,
    appStyle:      AppStyle = AppStyle.DYNAMIC,
    dominantColor: Color    = Color.Black,
    m3Enabled:     Boolean  = true,
    onM3Change:    (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var fullscreenEnabled    by remember { mutableStateOf(prefs.getBoolean("fullscreen", true)) }
    var lyricsEnabled        by remember { mutableStateOf(prefs.getBoolean("lyrics_enabled", false)) }
    var karaokeEnabled       by remember { mutableStateOf(prefs.getBoolean("karaoke_enabled", false)) }
    var showTrackInfo        by remember { mutableStateOf(prefs.getBoolean("show_track_info", true)) }
    var showPlaybackControls by remember { mutableStateOf(prefs.getBoolean("show_playback_controls", true)) }
    var showUpNext           by remember { mutableStateOf(prefs.getBoolean("show_up_next", true)) }
    var sliderStyle          by remember { mutableStateOf(prefs.getString("player_slider_style", "wave") ?: "wave") }
    var selectedColor        by remember { mutableStateOf(Color(prefs.getInt("minimal_color", 0xFF2C2C2C.toInt()))) }

    val backgroundColor = appBgColor(appStyle, dominantColor, prefs)
    val hPad        = if (m3Enabled) 20.dp else 24.dp
    val cardSpacing = if (m3Enabled) 12.dp else 0.dp

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor).statusBarsPadding()) {
        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(horizontal = hPad),
            contentPadding  = PaddingValues(0.dp, 28.dp, 0.dp, 104.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m3Enabled) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.1f)).clickable { onBack() },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Design",
                        style = if (m3Enabled) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                        color = Color.White)
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                SectionLabel("Interface", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))
                SettingsCard(m3Enabled) {
                    SettingsToggle(title = "Material 3 Design", subtitle = "Pill nav bar, tonal surfaces and rounded shapes",
                        checked = m3Enabled, onCheckedChange = onM3Change)
                }
                Spacer(Modifier.height(if (m3Enabled) 24.dp else 0.dp))
            }

            item { if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f)) }

            item {
                SectionLabel("Appearance", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleOption("Dynamic", "Background adapts to album art colors",       currentStyle == AppStyle.DYNAMIC, m3Enabled) { onStyleChange(AppStyle.DYNAMIC) }
                    StyleOption("AMOLED",  "Pure black background, easy on OLED screens", currentStyle == AppStyle.AMOLED,  m3Enabled) { onStyleChange(AppStyle.AMOLED) }
                    StyleOption("Minimal", "Clean dark background, no color",              currentStyle == AppStyle.MINIMAL, m3Enabled) { onStyleChange(AppStyle.MINIMAL) }
                    StyleOption("Glass",   "Frosted glass effect over album art",          currentStyle == AppStyle.GLASS,   m3Enabled) { onStyleChange(AppStyle.GLASS) }
                }
                Spacer(Modifier.height(if (m3Enabled) 24.dp else 0.dp))
            }

            item {
                if (currentStyle == AppStyle.MINIMAL) {
                    if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                    SectionLabel("Background Color", m3Enabled)
                    Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))
                    SettingsCard(m3Enabled) {
                        val palette = listOf(
                            Color(0xFF1C1B1F) to "Default", Color(0xFF1A1A2E) to "Navy",
                            Color(0xFF1B2A1B) to "Forest",  Color(0xFF2A1B1B) to "Crimson",
                            Color(0xFF1B1B2A) to "Indigo",  Color(0xFF2A2A1B) to "Olive",
                            Color(0xFF2A1B2A) to "Plum",    Color(0xFF1B2A2A) to "Teal",
                            Color(0xFF2A2010) to "Amber",   Color(0xFF101020) to "Midnight",
                            Color(0xFF8B4513) to "Rust",    Color(0xFF2E8B57) to "Emerald",
                            Color(0xFF6A0DAD) to "Violet",  Color(0xFFB8860B) to "Gold",
                        )
                        val cornerDp = if (m3Enabled) 14.dp else 12.dp
                        val sizeDp   = if (m3Enabled) 52.dp else 48.dp
                        FlowRow(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (m3Enabled) 10.dp else 8.dp),
                            verticalArrangement   = Arrangement.spacedBy(if (m3Enabled) 10.dp else 8.dp)) {
                            palette.forEach { (color, name) ->
                                val isSelected = selectedColor == color
                                Box(modifier = Modifier.size(sizeDp).clip(RoundedCornerShape(cornerDp)).background(color)
                                    .border(width = if (isSelected) 2.dp else 0.dp, color = Color.White, shape = RoundedCornerShape(cornerDp))
                                    .clickable { selectedColor = color; prefs.edit().putInt("minimal_color", color.toArgb()).apply() },
                                    contentAlignment = Alignment.Center) {
                                    if (isSelected) Icon(Icons.Default.Check, name, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(if (m3Enabled) 24.dp else 0.dp))
                }
            }

            item {
                if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SectionLabel("Display", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))
                SettingsCard(m3Enabled) {
                    SettingsToggle("Fullscreen mode", "Hide status and navigation bar", fullscreenEnabled,
                        { fullscreenEnabled = it; prefs.edit().putBoolean("fullscreen", it).apply() })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = if (m3Enabled) 0.07f else 0.1f))
                    SettingsToggle("Track info", "Album art, title and artist", showTrackInfo,
                        { showTrackInfo = it; prefs.edit().putBoolean("show_track_info", it).apply() })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = if (m3Enabled) 0.07f else 0.1f))
                    SettingsToggle("Playback controls", "Play/pause, skip, shuffle and repeat", showPlaybackControls,
                        { showPlaybackControls = it; prefs.edit().putBoolean("show_playback_controls", it).apply() })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = if (m3Enabled) 0.07f else 0.1f))
                    SettingsToggle("Up Next", "Button to view the queue", showUpNext,
                        { showUpNext = it; prefs.edit().putBoolean("show_up_next", it).apply() })
                }
                Spacer(Modifier.height(cardSpacing))
            }

            item {
                if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SectionLabel("Progress Bar", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleOption("Wave",    "Animated wave line with timestamps",                sliderStyle == "wave",    m3Enabled) { sliderStyle = "wave";    prefs.edit().putString("player_slider_style", "wave").apply() }
                    StyleOption("Minimal", "Simple flat bar with timestamps",                   sliderStyle == "minimal", m3Enabled) { sliderStyle = "minimal"; prefs.edit().putString("player_slider_style", "minimal").apply() }
                    StyleOption("EQ",      "Full-width animated equaliser bars, no timestamps", sliderStyle == "eq",      m3Enabled) { sliderStyle = "eq";      prefs.edit().putString("player_slider_style", "eq").apply() }
                }
                Spacer(Modifier.height(if (m3Enabled) 24.dp else 0.dp))
            }

            item {
                if (!m3Enabled) HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                SectionLabel("Lyrics", m3Enabled)
                Spacer(Modifier.height(if (m3Enabled) 12.dp else 8.dp))
                SettingsCard(m3Enabled) {
                    SettingsToggle(title = "Lyrics", subtitle = "Fetch and show synced lyrics below the player",
                        checked = lyricsEnabled,
                        onCheckedChange = {
                            lyricsEnabled = it; prefs.edit().putBoolean("lyrics_enabled", it).apply()
                            if (!it && karaokeEnabled) { karaokeEnabled = false; prefs.edit().putBoolean("karaoke_enabled", false).apply() }
                        })
                    AnimatedVisibility(visible = lyricsEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = if (m3Enabled) 0.07f else 0.1f))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Lyrics, null,
                                    tint = if (karaokeEnabled) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Karaoke mode", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                    Text("Full-screen lyrics view on the player", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                                }
                                Switch(checked = karaokeEnabled,
                                    onCheckedChange = { karaokeEnabled = it; prefs.edit().putBoolean("karaoke_enabled", it).apply() },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.White.copy(alpha = 0.4f),
                                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f), uncheckedTrackColor = Color.White.copy(alpha = 0.2f)))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─── Shared components ────────────────────────────────────────────────────────

@Composable
fun SettingsGroupRow(title: String, subtitle: String, m3Enabled: Boolean = true, onClick: () -> Unit) {
    val cornerRadius = if (m3Enabled) 16.dp else 12.dp
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(cornerRadius))
        .background(Color.White.copy(alpha = if (m3Enabled) 0.07f else 0.05f))
        .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = if (m3Enabled) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f))
    }
}

@Composable
fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.White.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f), uncheckedTrackColor = Color.White.copy(alpha = 0.18f)))
    }
}

@Composable
fun StyleOption(label: String, description: String, selected: Boolean, m3Enabled: Boolean = true, onClick: () -> Unit) {
    val cornerRadius = if (m3Enabled) 16.dp else 12.dp
    val borderWidth  = if (m3Enabled) 1.5.dp else 1.dp
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(cornerRadius))
        .background(if (selected) Color.White.copy(alpha = if (m3Enabled) 0.14f else 0.15f) else Color.White.copy(alpha = if (m3Enabled) 0.06f else 0.05f))
        .border(width = if (selected) borderWidth else 0.dp,
            color = if (selected) Color.White.copy(alpha = 0.4f) else Color.Transparent,
            shape = RoundedCornerShape(cornerRadius))
        .clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
        }
        if (selected) {
            if (m3Enabled) {
                Box(modifier = Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                }
            } else {
                Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(50)).background(Color.White), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color.Black))
                }
            }
        }
    }
}