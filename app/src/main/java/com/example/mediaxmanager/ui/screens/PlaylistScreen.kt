package com.example.mediaxmanager.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.media.Playlist
import com.example.mediaxmanager.media.PlaylistRepository
import com.example.mediaxmanager.ui.theme.AppStyle

// ─── Inline playlists section (used inside SearchScreen's idle state) ─────────

@Composable
fun InlinePlaylistsSection(
    allTracks: List<LocalTrack>,
    viewModel: MediaViewModel
) {
    val context   = LocalContext.current
    val repo      = remember { PlaylistRepository.get(context) }
    val playlists by repo.playlists.collectAsStateWithLifecycle()

    // State hoisted outside the LazyRow so the overlay can render above everything
    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreate     by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header row
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Playlists",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showCreate = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add, null,
                        tint     = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "New",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .clickable { showCreate = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Add, null,
                            tint     = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Create a playlist",
                            color = Color.White.copy(alpha = 0.22f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding        = PaddingValues(end = 8.dp)
                ) {
                    items(items = playlists, key = { it.id }) { pl ->
                        PlaylistSquareCard(
                            playlist  = pl,
                            allTracks = allTracks,
                            onClick   = { openPlaylistId = pl.id },
                            onDelete  = { repo.deletePlaylist(pl.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        // Detail overlay — outside the LazyRow so it fills the screen
        val openId = openPlaylistId
        if (openId != null) {
            val playlist = playlists.firstOrNull { it.id == openId }
            if (playlist != null) {
                PlaylistDetailScreen(
                    playlist  = playlist,
                    repo      = repo,
                    viewModel = viewModel,
                    onBack    = { openPlaylistId = null }
                )
            }
        }

        if (showCreate) {
            PlaylistNameDialog(
                title       = "New playlist",
                initialName = "",
                onConfirm   = { name -> repo.createPlaylist(name); showCreate = false },
                onDismiss   = { showCreate = false }
            )
        }
    }
}

// ─── Detail screen ────────────────────────────────────────────────────────────

@Composable
fun PlaylistDetailScreen(
    playlist:      Playlist,
    repo:          PlaylistRepository,
    viewModel:     MediaViewModel,
    onBack:        () -> Unit,
    appStyle:      AppStyle = AppStyle.DYNAMIC,
    dominantColor: Color    = Color(0xFF1C1B1F)
) {
    BackHandler { onBack() }

    val context   = LocalContext.current
    val allTracks = TrackCache.tracks

    val trackMap = remember(allTracks) {
        allTracks.associateBy { it.uri.toString() } +
                allTracks.associateBy { it.path }
    }
    val resolved = remember(playlist.trackUris, allTracks) {
        playlist.trackUris.mapNotNull { trackMap[it] }
    }
    var orderedTracks by remember(resolved) { mutableStateOf(resolved) }
    var showRename    by remember { mutableStateOf(false) }

    val headerArt: Bitmap? = remember(playlist.trackUris, allTracks) {
        playlist.trackUris.firstNotNullOfOrNull { uri ->
            val key = trackMap[uri]?.let { t -> t.uri.toString().ifBlank { t.path } } ?: uri
            TrackCache.artCache[key]
        }
    }

    val bgColor = when (appStyle) {
        AppStyle.DYNAMIC -> Color(
            red   = dominantColor.red   * 0.65f,
            green = dominantColor.green * 0.65f,
            blue  = dominantColor.blue  * 0.65f,
            alpha = 1f
        )
        AppStyle.AMOLED -> Color.Black
        AppStyle.GLASS  -> Color.Black
        else -> {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            Color(prefs.getInt("minimal_color", 0xFF2C2C2C.toInt()))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = false, onClick = {})
            .background(bgColor)
    ) {
        Column {
            // ── Hero header ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                if (headerArt != null) {
                    Image(
                        bitmap             = headerArt.asImageBitmap(),
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                        alpha              = 0.3f
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF0A0A0A)),
                                startY = 60f
                            )
                        )
                )

                // Back button
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.28f))
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }

                // Info at bottom of hero
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (headerArt != null) {
                            Image(
                                bitmap             = headerArt.asImageBitmap(),
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.QueueMusic, null,
                                tint     = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            playlist.name,
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(
                            "${orderedTracks.size} track${if (orderedTracks.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                }
            }

            // ── Action bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (orderedTracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable {
                                viewModel.playLocalTrack(context, orderedTracks.first(), orderedTracks)
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play all", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                val shuffled = orderedTracks.shuffled()
                                viewModel.playLocalTrack(context, shuffled.first(), shuffled)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shuffle, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(19.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { showRename = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(17.dp))
                }
            }

            // ── Track list ────────────────────────────────────────────────────
            if (orderedTracks.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicOff, null,
                            tint     = Color.White.copy(alpha = 0.13f),
                            modifier = Modifier.size(50.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("No tracks yet", color = Color.White.copy(alpha = 0.32f), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Long-press a track → Add to playlist", color = Color.White.copy(alpha = 0.18f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier            = Modifier.padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = orderedTracks,
                        key   = { _, t -> t.uri.toString().ifBlank { t.path } }
                    ) { index, track ->
                        PlaylistTrackRow(
                            track      = track,
                            index      = index,
                            total      = orderedTracks.size,
                            context    = context,
                            queue      = orderedTracks,
                            viewModel  = viewModel,
                            onMoveUp   = {
                                val nl = orderedTracks.toMutableList().apply { add(index - 1, removeAt(index)) }
                                orderedTracks = nl
                                repo.reorderTracks(playlist.id, nl.map { it.uri.toString().ifBlank { it.path } })
                            },
                            onMoveDown = {
                                val nl = orderedTracks.toMutableList().apply { add(index + 1, removeAt(index)) }
                                orderedTracks = nl
                                repo.reorderTracks(playlist.id, nl.map { it.uri.toString().ifBlank { it.path } })
                            },
                            onRemove   = {
                                repo.removeTrack(playlist.id, track.uri.toString().ifBlank { track.path })
                                orderedTracks = orderedTracks.toMutableList().also { it.removeAt(index) }
                            }
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.04f))
                    }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }

        if (showRename) {
            PlaylistNameDialog(
                title       = "Rename playlist",
                initialName = playlist.name,
                onConfirm   = { name -> repo.renamePlaylist(playlist.id, name); showRename = false },
                onDismiss   = { showRename = false }
            )
        }
    }
}

// ─── Playlist track row ───────────────────────────────────────────────────────

@Composable
fun PlaylistTrackRow(
    track:      LocalTrack,
    index:      Int,
    total:      Int,
    context:    Context,
    queue:      List<LocalTrack>,
    viewModel:  MediaViewModel,
    onMoveUp:   () -> Unit,
    onMoveDown: () -> Unit,
    onRemove:   () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { viewModel.playLocalTrack(context, track, queue) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier            = Modifier.width(30.dp).padding(start = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowUp, null,
                    tint     = if (index > 0) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowDown, null,
                    tint     = if (index < total - 1) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            TrackRow(track = track, context = context, queue = queue, viewModel = viewModel)
        }

        IconButton(onClick = onRemove, modifier = Modifier.padding(end = 2.dp).size(30.dp)) {
            Icon(
                Icons.Default.RemoveCircleOutline, null,
                tint     = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// ─── Add to playlist bottom sheet ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track:     LocalTrack,
    repo:      PlaylistRepository,
    onDismiss: () -> Unit
) {
    val playlists  by repo.playlists.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val trackUri   = track.uri.toString().ifBlank { track.path }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1C1C1C),
        contentColor     = Color.White,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Add to playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(track.title, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.38f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable { showCreate = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier         = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
                Spacer(Modifier.width(12.dp))
                Text("New playlist", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(14.dp))

            playlists.forEach { pl ->
                val alreadyIn = trackUri in pl.trackUris
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !alreadyIn) { repo.addTrack(pl.id, trackUri); onDismiss() }
                        .padding(vertical = 11.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.QueueMusic, null,
                        tint     = if (alreadyIn) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        pl.name,
                        color    = if (alreadyIn) Color.White.copy(alpha = 0.28f) else Color.White,
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (alreadyIn) "Added" else "${pl.trackUris.size}",
                        color = Color.White.copy(alpha = 0.26f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            }
        }

        if (showCreate) {
            PlaylistNameDialog(
                title       = "New playlist",
                initialName = "",
                onConfirm   = { name ->
                    val newPl = repo.createPlaylist(name)
                    repo.addTrack(newPl.id, trackUri)
                    showCreate = false; onDismiss()
                },
                onDismiss = { showCreate = false }
            )
        }
    }
}

// ─── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
fun PlaylistNameDialog(
    title:       String,
    initialName: String,
    onConfirm:   (String) -> Unit,
    onDismiss:   () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor  = Color.White,
        title = { Text(title) },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                placeholder   = { Text("Playlist name", color = Color.White.copy(alpha = 0.3f)) },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    focusedBorderColor   = Color.White.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor          = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Save", color = if (name.isNotBlank()) Color.White else Color.White.copy(alpha = 0.3f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha = 0.42f)) }
        }
    )
}