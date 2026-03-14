package com.example.mediaxmanager.ui.screens

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import com.example.mediaxmanager.media.JellyfinAlbum
import com.example.mediaxmanager.media.JellyfinRepository
import com.example.mediaxmanager.media.JellyfinResult
import com.example.mediaxmanager.media.MediaViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediaxmanager.ui.theme.AppStyle
import com.example.mediaxmanager.media.PlaylistRepository
import com.example.mediaxmanager.media.Playlist
import kotlinx.coroutines.delay

val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "m4a", "wav", "aac", "opus", "wma", "aiff")
private const val MUSIC_ROOT = "/storage/emulated/0/Music"

// ─── Source enum ──────────────────────────────────────────────────────────────

enum class MediaSource { LOCAL, JELLYFIN }

// ─── Track model ──────────────────────────────────────────────────────────────

data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val path: String = ""
)

fun isJellyfinTrack(track: LocalTrack) = track.path.startsWith("jellyfin://")

// ─── Track cache ──────────────────────────────────────────────────────────────

object TrackCache {
    var tracks: List<LocalTrack> = emptyList()
    var lastLoadTime: Long = 0L
    var metadataLoaded: Boolean = false
    var lastMetadataLoadTime: Long = 0L
    val artCache    = mutableMapOf<String, Bitmap?>()
    val artistCache = mutableMapOf<String, String?>()

    // Jellyfin tracks cached separately — cleared on logout
    var jellyfinTracks: List<LocalTrack> = emptyList()
    var jellyfinAlbums: List<JellyfinAlbum> = emptyList()
    var jellyfinLastLoadTime: Long = 0L
}

// ─── Suspend helpers ──────────────────────────────────────────────────────────

suspend fun preloadAllMetadata(context: Context, tracks: List<LocalTrack>) = withContext(Dispatchers.IO) {
    tracks.map { track ->
        async {
            val key = track.uri.toString().ifBlank { track.path }
            if (!TrackCache.artCache.containsKey(key)) getMetadataForTrack(context, track)
        }
    }.awaitAll()
    TrackCache.metadataLoaded       = true
    TrackCache.lastMetadataLoadTime = System.currentTimeMillis()
}

suspend fun preloadAllLyrics(tracks: List<LocalTrack>, viewModel: MediaViewModel) {
    tracks.forEach { track ->
        viewModel.prefetchLyrics(track.title, track.artist)
        delay(50) // small delay to avoid hammering lrclib.net
    }
}
suspend fun queryLocalTracks(context: Context): List<LocalTrack> = withContext(Dispatchers.IO) {
    val tracks = mutableListOf<LocalTrack>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA
    )
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
        "${MediaStore.Audio.Media.TITLE} ASC"
    )?.use { cursor ->
        val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            tracks.add(LocalTrack(
                id       = id,
                title    = cursor.getString(titleCol)  ?: "Unknown",
                artist   = cursor.getString(artistCol) ?: "Unknown",
                album    = cursor.getString(albumCol)  ?: "",
                duration = cursor.getLong(durationCol),
                uri      = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                path     = cursor.getString(dataCol) ?: ""
            ))
        }
    }
    tracks
}

suspend fun buildTrackFromFile(context: Context, file: File): LocalTrack = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    return@withContext try {
        retriever.setDataSource(file.absolutePath)
        LocalTrack(
            id       = file.hashCode().toLong(),
            title    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            artist   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: "Unknown",
            album    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: "",
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            uri      = Uri.fromFile(file),
            path     = file.absolutePath
        )
    } catch (e: Exception) {
        LocalTrack(id = file.hashCode().toLong(), title = file.nameWithoutExtension,
            artist = "Unknown", album = "", duration = 0L, uri = Uri.fromFile(file), path = file.absolutePath)
    } finally {
        retriever.release()
    }
}

suspend fun resolveTracksForFolder(
    context: Context,
    folder: File,
    tracksByPath: Map<String, LocalTrack>
): List<LocalTrack> = withContext(Dispatchers.IO) {
    folder.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
        .sortedBy { it.name.lowercase() }
        .toList()
        .map { file ->
            async {
                tracksByPath[file.absolutePath]
                    ?: tracksByPath[file.canonicalPath]
                    ?: buildTrackFromFile(context, file)
            }
        }.awaitAll()
}

suspend fun getMetadataForTrack(context: Context, track: LocalTrack): Pair<Bitmap?, String?> =
    withContext(Dispatchers.IO) {
        val key = track.uri.toString().ifBlank { track.path }
        if (key.isBlank()) return@withContext Pair(null, null)
        if (TrackCache.artCache.containsKey(key) && TrackCache.artistCache.containsKey(key))
            return@withContext Pair(TrackCache.artCache[key], TrackCache.artistCache[key])

        var bitmap: Bitmap? = null
        var artist: String? = null

        // Jellyfin: fetch art from API URL stored in cache key
        if (track.path.startsWith("jellyfin://")) {
            val session = JellyfinRepository.session
            if (session != null) {
                val itemId = track.path.removePrefix("jellyfin://")
                val artUrl = JellyfinRepository.artUrl(session, itemId)
                try {
                    bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeStream(URL(artUrl).openStream())
                    }
                } catch (_: Exception) {}
            }
            artist = track.artist
            TrackCache.artCache[key]    = bitmap
            TrackCache.artistCache[key] = artist
            return@withContext Pair(bitmap, artist)
        }

        try {
            val albumId = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(track.id.toString()), null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            if (albumId != null) {
                val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                bitmap = context.contentResolver.openInputStream(artUri)?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Exception) {}

        if (bitmap == null || artist == null) {
            val retriever = MediaMetadataRetriever()
            try {
                var opened = false
                try { context.contentResolver.openFileDescriptor(track.uri, "r")?.use { pfd -> retriever.setDataSource(pfd.fileDescriptor); opened = true } } catch (_: Exception) {}
                if (!opened) try { retriever.setDataSource(context, track.uri); opened = true } catch (_: Exception) {}
                if (!opened && track.path.isNotBlank()) try { retriever.setDataSource(track.path); opened = true } catch (_: Exception) {}
                if (opened) {
                    if (bitmap == null) bitmap = retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                }
            } finally { retriever.release() }
        }

        TrackCache.artCache[key]    = bitmap
        TrackCache.artistCache[key] = artist
        Pair(bitmap, artist)
    }

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

// ─── Background color helper ──────────────────────────────────────────────────

fun appBgColor(
    appStyle:      AppStyle,
    dominantColor: Color,
    prefs:         android.content.SharedPreferences
): Color = when (appStyle) {
    AppStyle.DYNAMIC -> Color(
        red   = dominantColor.red   * 0.65f,
        green = dominantColor.green * 0.65f,
        blue  = dominantColor.blue  * 0.65f,
        alpha = 1f
    )
    AppStyle.AMOLED -> Color.Black
    AppStyle.GLASS  -> Color.Black
    else            -> Color(prefs.getInt("minimal_color", 0xFF2C2C2C.toInt()))
}

// ─── Root screen ──────────────────────────────────────────────────────────────

@Composable
fun SearchScreen(
    viewModel:     MediaViewModel,
    appStyle:      AppStyle = AppStyle.DYNAMIC,
    dominantColor: Color    = Color(0xFF1C1B1F),
    onBack:        () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val scope   = rememberCoroutineScope()

    // ── Source state ──────────────────────────────────────────────────────────
    val jellyfinEnabled = remember { prefs.getBoolean("jellyfin_enabled", false) }
    var activeSource by remember {
        val saved = prefs.getString("active_source", "LOCAL")
        mutableStateOf(if (saved == "JELLYFIN" && jellyfinEnabled) MediaSource.JELLYFIN else MediaSource.LOCAL)
    }

    // ── Local state ───────────────────────────────────────────────────────────
    var query     by remember { mutableStateOf("") }
    var allTracks by remember { mutableStateOf(TrackCache.tracks) }
    var isLoading by remember { mutableStateOf(true) }

    var likedFolderPath  by remember { mutableStateOf(prefs.getString("liked_folder_path", null)) }
    var showFolderPicker by remember { mutableStateOf(likedFolderPath == null && activeSource == MediaSource.LOCAL) }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val docId   = DocumentsContract.getTreeDocumentId(uri)
            val parts   = docId.split(":")
            val absPath = if (parts.size == 2 && parts[0].equals("primary", ignoreCase = true))
                "/storage/emulated/0/${parts[1]}"
            else uri.path?.removePrefix("/tree/primary:")?.let { "/storage/emulated/0/$it" } ?: "/storage/emulated/0/Music"
            prefs.edit().putString("liked_folder_path", absPath).apply()
            likedFolderPath = absPath
        }
        showFolderPicker = false
    }

    val repo           = remember { PlaylistRepository.get(context) }
    val playlists      by repo.playlists.collectAsStateWithLifecycle()
    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreate     by remember { mutableStateOf(false) }
    var openFolderPath by remember { mutableStateOf<String?>(null) }
    var browserPath    by remember { mutableStateOf<String?>(null) }

    val musicFolders = remember(allTracks, likedFolderPath) {
        val root = likedFolderPath?.let { File(it) } ?: File(MUSIC_ROOT)
        if (root.exists() && root.isDirectory)
            root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
        else emptyList()
    }

    // Singles = tracks sitting directly in the root folder (not inside any subfolder)
    val singles = remember(allTracks, likedFolderPath) {
        val root = (likedFolderPath?.let { File(it) } ?: File(MUSIC_ROOT)).absolutePath
        allTracks.filter { track ->
            val parent = File(track.path).parent
            parent == root
        }.sortedBy { it.title.lowercase() }
    }
    // ── Jellyfin state ────────────────────────────────────────────────────────
    var jellyfinTracks   by remember { mutableStateOf(TrackCache.jellyfinTracks) }
    var jellyfinAlbums   by remember { mutableStateOf(TrackCache.jellyfinAlbums) }
    var jellyfinLoading  by remember { mutableStateOf(false) }
    var jellyfinError    by remember { mutableStateOf<String?>(null) }
    var openJfAlbumId    by remember { mutableStateOf<String?>(null) }

    // Only go back to Home when no overlay is open
    BackHandler(
        enabled = openPlaylistId == null && openFolderPath == null &&
                openJfAlbumId == null && browserPath == null
    ) { onBack() }

    // ── Load local tracks ─────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        val refreshMinutes = prefs.getInt("search_refresh_minutes", -1)
        val now = System.currentTimeMillis()
        val shouldRefresh = TrackCache.tracks.isEmpty() ||
                (refreshMinutes > 0 && now - TrackCache.lastLoadTime > refreshMinutes * 60_000L)
        if (shouldRefresh) {
            isLoading = true
            TrackCache.tracks       = queryLocalTracks(context)
            TrackCache.lastLoadTime = now
            TrackCache.metadataLoaded = false
        }
        val shouldRefreshMeta = !TrackCache.metadataLoaded ||
                (refreshMinutes > 0 && now - TrackCache.lastMetadataLoadTime > refreshMinutes * 60_000L)
        if (shouldRefreshMeta) preloadAllMetadata(context, TrackCache.tracks)

        // Now reveal tracks to UI — art is already in cache
        allTracks = TrackCache.tracks
        isLoading = false

        if (prefs.getBoolean("lyrics_enabled", false)) preloadAllLyrics(TrackCache.tracks, viewModel)

        val folderPath = prefs.getString("liked_folder_path", null)
        if (folderPath != null) {
            val folderTracks = allTracks.filter { it.path.startsWith("$folderPath/") }
            val uris = folderTracks.map { it.uri.toString().ifBlank { it.path } }
            if (uris.isNotEmpty()) repo.addTracks(PlaylistRepository.LIKED_ID, uris)
        }
    }

    LaunchedEffect(likedFolderPath) {
        val folderPath = likedFolderPath ?: return@LaunchedEffect
        val uris = allTracks.filter { it.path.startsWith("$folderPath/") }
            .map { it.uri.toString().ifBlank { it.path } }
        if (uris.isNotEmpty()) repo.addTracks(PlaylistRepository.LIKED_ID, uris)
    }

    // ── Load Jellyfin when switching to it ────────────────────────────────────
    LaunchedEffect(activeSource) {
        if (activeSource == MediaSource.JELLYFIN && JellyfinRepository.session != null) {
            val now = System.currentTimeMillis()
            if (TrackCache.jellyfinTracks.isEmpty() || now - TrackCache.jellyfinLastLoadTime > 5 * 60_000L) {
                jellyfinLoading = true
                jellyfinError   = null
                when (val result = JellyfinRepository.fetchAllTracks()) {
                    is JellyfinResult.Success -> {
                        TrackCache.jellyfinTracks = result.data
                        jellyfinTracks = result.data
                    }
                    is JellyfinResult.Error -> jellyfinError = result.message
                }
                when (val result = JellyfinRepository.fetchAlbums()) {
                    is JellyfinResult.Success -> {
                        TrackCache.jellyfinAlbums = result.data
                        jellyfinAlbums = result.data
                    }
                    is JellyfinResult.Error -> { /* albums are optional */ }
                }
                TrackCache.jellyfinLastLoadTime = now
                jellyfinLoading = false
            } else {
                jellyfinTracks = TrackCache.jellyfinTracks
                jellyfinAlbums = TrackCache.jellyfinAlbums
            }
        }
    }

    val activeTracks = if (activeSource == MediaSource.JELLYFIN) jellyfinTracks else allTracks

    var filteredTracks by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    LaunchedEffect(query, activeTracks) {
        if (query.isBlank()) {
            filteredTracks = emptyList()
        } else {
            filteredTracks = withContext(Dispatchers.Default) {
                activeTracks.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true) ||
                            it.album.contains(query, ignoreCase = true)
                }
            }
        }
    }

    val m3Enabled   = remember { prefs.getBoolean("m3_design", true) }
    val hPad        = if (m3Enabled) 20.dp else 16.dp
    val btnRadius   = if (m3Enabled) 28.dp else 12.dp
    val fieldRadius = if (m3Enabled) 28.dp else 12.dp

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Glass layer
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

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = hPad)) {
            Spacer(Modifier.height(if (m3Enabled) 28.dp else 24.dp))

            // ── Title + source toggle ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Search",
                    style = if (m3Enabled) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // Source toggle — only shown when Jellyfin is enabled in settings
                if (jellyfinEnabled) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Local pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (activeSource == MediaSource.LOCAL)
                                        Color.White.copy(alpha = 0.18f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    activeSource = MediaSource.LOCAL
                                    prefs.edit().putString("active_source", "LOCAL").apply()
                                    // Stop Jellyfin playback if something was playing
                                    viewModel.suspendLocalMedia()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.PhoneAndroid, null,
                                    tint = Color.White.copy(alpha = if (activeSource == MediaSource.LOCAL) 1f else 0.4f),
                                    modifier = Modifier.size(13.dp))
                                Text("Local",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = if (activeSource == MediaSource.LOCAL) 1f else 0.4f))
                            }
                        }
                        // Jellyfin pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (activeSource == MediaSource.JELLYFIN)
                                        Color(0xFF00A4DC).copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (JellyfinRepository.session == null) {
                                        // Not logged in — do nothing, settings will guide them
                                        return@clickable
                                    }
                                    activeSource = MediaSource.JELLYFIN
                                    prefs.edit().putString("active_source", "JELLYFIN").apply()
                                    // Stop local playback when switching to Jellyfin
                                    viewModel.suspendLocalMedia()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Cloud, null,
                                    tint = if (activeSource == MediaSource.JELLYFIN) Color(0xFF00A4DC)
                                    else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(13.dp))
                                Text("Jellyfin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (activeSource == MediaSource.JELLYFIN) Color(0xFF00A4DC)
                                    else Color.White.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (m3Enabled) 20.dp else 16.dp))

            // ── Quick-action buttons (Local only) ─────────────────────────────
            if (activeSource == MediaSource.LOCAL) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(btnRadius))
                            .background(Color(0xFF1DB954))
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:")).apply {
                                    setPackage("com.spotify.music"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try { context.startActivity(intent) }
                                catch (_: Exception) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))) }
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) { Text("Spotify", style = MaterialTheme.typography.bodyMedium, color = Color.White) }

                    Row(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(btnRadius))
                            .background(Color.White.copy(alpha = if (m3Enabled) 0.1f else 0.08f))
                            .clickable { browserPath = "/storage/emulated/0" }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Folder, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Browse", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Search field ──────────────────────────────────────────────────
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        if (activeSource == MediaSource.JELLYFIN) "Search Jellyfin library..."
                        else "Search local music...",
                        color = Color.White.copy(alpha = 0.4f)
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.6f)) },
                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear", tint = Color.White.copy(alpha = 0.5f)) } },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor     = Color.White, unfocusedTextColor   = Color.White,
                    focusedBorderColor   = if (activeSource == MediaSource.JELLYFIN) Color(0xFF00A4DC).copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor          = Color.White
                ),
                shape = RoundedCornerShape(fieldRadius)
            )

            Spacer(Modifier.height(12.dp))

            // ── Main content ──────────────────────────────────────────────────
            when {
                isLoading && activeSource == MediaSource.LOCAL -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }

                jellyfinLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Color(0xFF00A4DC))
                        Text("Loading Jellyfin library…", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }

                activeSource == MediaSource.JELLYFIN && JellyfinRepository.session == null -> {
                    JellyfinNotConfiguredBanner()
                }

                activeSource == MediaSource.JELLYFIN && jellyfinError != null -> {
                    JellyfinErrorBanner(jellyfinError!!) {
                        scope.launch {
                            jellyfinLoading = true
                            jellyfinError   = null
                            when (val r = JellyfinRepository.fetchAllTracks()) {
                                is JellyfinResult.Success -> { TrackCache.jellyfinTracks = r.data; jellyfinTracks = r.data }
                                is JellyfinResult.Error   -> jellyfinError = r.message
                            }
                            jellyfinLoading = false
                        }
                    }
                }

                query.isNotBlank() -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items = filteredTracks, key = { it.id }) { track ->
                        TrackRow(track = track, context = context, queue = filteredTracks, viewModel = viewModel, repo = repo)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                    if (filteredTracks.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No tracks found", color = Color.White.copy(alpha = 0.4f))
                            }
                        }
                    }
                }

                activeSource == MediaSource.JELLYFIN -> {
                    // Jellyfin idle: album grid
                    JellyfinBrowseContent(
                        albums    = jellyfinAlbums,
                        tracks    = jellyfinTracks,
                        viewModel = viewModel,
                        context   = context,
                        repo      = repo,
                        onOpenAlbum = { openJfAlbumId = it }
                    )
                }

                else -> {
                    // Local idle: playlists + folders
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "pl_header") {
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Playlists", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
                                Row(
                                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                        .clickable { showCreate = true }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("New", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }

                        item(key = "pl_content") {
                            val userPlaylists = playlists.filter { !it.isSystem }
                            val likedPlaylist = playlists.firstOrNull { it.id == PlaylistRepository.LIKED_ID }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (likedPlaylist != null) {
                                    LikedSongsCard(playlist = likedPlaylist, allTracks = allTracks, onClick = { openPlaylistId = likedPlaylist.id })
                                }
                                if (userPlaylists.isEmpty() && likedPlaylist == null) {
                                    Box(modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.04f)).clickable { showCreate = true },
                                        contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Add, null, tint = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(26.dp))
                                            Spacer(Modifier.height(5.dp))
                                            Text("Create a playlist", color = Color.White.copy(alpha = 0.22f), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                } else if (userPlaylists.isNotEmpty()) {
                                    userPlaylists.chunked(3).forEach { rowPls ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            rowPls.forEach { pl ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    PlaylistSquareCard(playlist = pl, allTracks = allTracks,
                                                        onClick = { openPlaylistId = pl.id }, onDelete = { repo.deletePlaylist(pl.id) })
                                                }
                                            }
                                            repeat(3 - rowPls.size) { Spacer(modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        if (musicFolders.isNotEmpty()) {
                            item(key = "sf_header") {
                                Text("Folders", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 12.dp))
                            }
                            item(key = "sf_content") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    musicFolders.chunked(3).forEach { rowFolders ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            rowFolders.forEach { folder ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    FolderSquareCard(folder = folder, allTracks = allTracks,
                                                        context = context, repo = repo, onClick = { openFolderPath = folder.absolutePath })
                                                }
                                            }
                                            repeat(3 - rowFolders.size) { Spacer(modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }

                        if (singles.isNotEmpty()) {
                            item(key = "singles_header") {
                                Text("Singles", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 12.dp))
                            }
                            item(key = "singles_content") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    singles.chunked(3).forEach { rowTracks ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            rowTracks.forEach { track ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    SingleSquareCard(track = track, context = context,
                                                        queue = singles, viewModel = viewModel, repo = repo)
                                                }
                                            }
                                            repeat(3 - rowTracks.size) { Spacer(modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }

                        item { Spacer(Modifier.height(100.dp)) }
                    }
                }
            }
        }

        // ── First-use folder picker ───────────────────────────────────────────
        if (showFolderPicker) {
            AlertDialog(
                onDismissRequest = {},
                containerColor   = Color(0xFF1C1C1E),
                title  = { Text("Choose your music folder", color = Color.White, style = MaterialTheme.typography.titleLarge) },
                text   = { Text("Pick the folder that contains your music. Every track in it will be added to Liked Songs automatically.", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    Button(onClick = { folderPickerLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f))) {
                        Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Choose folder", color = Color.White)
                    }
                },
                dismissButton = { TextButton(onClick = { showFolderPicker = false }) { Text("Skip", color = Color.White.copy(alpha = 0.45f)) } }
            )
        }

        // ── Playlist detail overlay ───────────────────────────────────────────
        val openId = openPlaylistId
        if (openId != null) {
            BackHandler { openPlaylistId = null }
            val playlist = playlists.firstOrNull { it.id == openId }
            if (playlist != null) {
                PlaylistDetailScreen(playlist = playlist, repo = repo, viewModel = viewModel,
                    onBack = { openPlaylistId = null }, appStyle = appStyle, dominantColor = dominantColor)
            }
        }

        // ── Jellyfin album detail overlay ─────────────────────────────────────
        if (openJfAlbumId != null) {
            BackHandler { openJfAlbumId = null }
            JellyfinAlbumDetailScreen(
                albumId       = openJfAlbumId!!,
                albums        = jellyfinAlbums,
                viewModel     = viewModel,
                context       = context,
                repo          = repo,
                appStyle      = appStyle,
                dominantColor = dominantColor,
                onBack        = { openJfAlbumId = null }
            )
        }

        // ── Folder detail overlay ─────────────────────────────────────────────
        if (openFolderPath != null) {
            BackHandler { openFolderPath = null }
            FolderDetailScreen(folderPath = openFolderPath!!, allTracks = allTracks, context = context,
                viewModel = viewModel, repo = repo, appStyle = appStyle, dominantColor = dominantColor,
                onBack = { openFolderPath = null })
        }

        if (showCreate) {
            PlaylistNameDialog(title = "New playlist", initialName = "",
                onConfirm = { name -> repo.createPlaylist(name); showCreate = false },
                onDismiss = { showCreate = false })
        }

        if (browserPath != null) {
            val browserParent = File(browserPath!!).parent
            BackHandler {
                browserPath = if (browserParent == null || browserParent == "/storage/emulated") null else browserParent
            }
            FolderBrowserScreen(currentPath = browserPath!!, allTracks = allTracks, context = context,
                repo = repo, viewModel = viewModel, appStyle = appStyle, dominantColor = dominantColor,
                onNavigate = { browserPath = it },
                onBack = {
                    val parent = File(browserPath!!).parent
                    browserPath = if (parent == null || parent == "/storage/emulated") null else parent
                },
                onClose = { browserPath = null })
        }
    }
}

// ─── Jellyfin browse content ──────────────────────────────────────────────────

@Composable
fun JellyfinBrowseContent(
    albums:     List<JellyfinAlbum>,
    tracks:     List<LocalTrack>,
    viewModel:  MediaViewModel,
    context:    Context,
    repo:       PlaylistRepository,
    onOpenAlbum: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (albums.isNotEmpty()) {
            item(key = "jf_albums_header") {
                Text("Albums", style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 12.dp))
            }
            item(key = "jf_albums_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    albums.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { album ->
                                Box(modifier = Modifier.weight(1f)) {
                                    JellyfinAlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                                }
                            }
                            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (tracks.isNotEmpty()) {
            item(key = "jf_tracks_header") {
                Text("All tracks", style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 8.dp))
            }
            items(items = tracks, key = { "jft_${it.id}" }) { track ->
                TrackRow(track = track, context = context, queue = tracks, viewModel = viewModel, repo = repo)
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            }
        }

        if (albums.isEmpty() && tracks.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Cloud, null, tint = Color.White.copy(alpha = 0.13f), modifier = Modifier.size(46.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No tracks found on server", color = Color.White.copy(alpha = 0.3f))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

// ─── Jellyfin album square card ───────────────────────────────────────────────

@Composable
fun JellyfinAlbumCard(
    album:   JellyfinAlbum,
    onClick: () -> Unit
) {
    var art by remember(album.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(album.id) {
        if (art == null) {
            art = withContext(Dispatchers.IO) {
                try { BitmapFactory.decodeStream(URL(album.artUrl).openStream()) } catch (_: Exception) { null }
            }
        }
    }
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF00A4DC).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (art != null) {
                Image(bitmap = art!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.Album, null, tint = Color(0xFF00A4DC).copy(alpha = 0.4f), modifier = Modifier.fillMaxSize(0.35f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(album.name, color = Color.White, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp))
        Text(album.artist, color = Color.White.copy(alpha = 0.38f), style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

// ─── Jellyfin album detail screen ────────────────────────────────────────────

@Composable
fun JellyfinAlbumDetailScreen(
    albumId:       String,
    albums:        List<JellyfinAlbum>,
    viewModel:     MediaViewModel,
    context:       Context,
    repo:          PlaylistRepository,
    appStyle:      AppStyle,
    dominantColor: Color,
    onBack:        () -> Unit
) {
    val prefs   = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val bgColor = appBgColor(appStyle, dominantColor, prefs)
    val album   = albums.firstOrNull { it.id == albumId }

    BackHandler { onBack() }

    var tracks     by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    var isLoading  by remember { mutableStateOf(true) }
    var headerArt  by remember { mutableStateOf<Bitmap?>(null) }
    var showSheet  by remember { mutableStateOf(false) }

    LaunchedEffect(albumId) {
        isLoading = true
        headerArt = withContext(Dispatchers.IO) {
            try { BitmapFactory.decodeStream(URL(album?.artUrl ?: "").openStream()) } catch (_: Exception) { null }
        }
        when (val r = JellyfinRepository.fetchTracksForAlbum(albumId)) {
            is JellyfinResult.Success -> tracks = r.data
            is JellyfinResult.Error   -> {}
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize().clickable(enabled = false, onClick = {}).background(bgColor)) {
        Column {
            // Hero
            Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                if (headerArt != null) {
                    Image(bitmap = headerArt!!.asImageBitmap(), null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.3f)
                }
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, bgColor), startY = 60f)))
                IconButton(onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                if (tracks.isNotEmpty()) {
                    IconButton(onClick = { showSheet = true },
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
                            .clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))) {
                        Icon(Icons.Default.PlaylistAdd, null, tint = Color.White)
                    }
                }
                Row(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF00A4DC).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        if (headerArt != null) Image(bitmap = headerArt!!.asImageBitmap(), null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Default.Album, null, tint = Color(0xFF00A4DC).copy(alpha = 0.6f), modifier = Modifier.size(32.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(album?.name ?: "Album", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (album?.artist?.isNotBlank() == true) {
                            Text(album.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                        }
                        Text("${tracks.size} track${if (tracks.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
            // Action bar
            if (tracks.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f).clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF00A4DC).copy(alpha = 0.2f))
                        .clickable { viewModel.playLocalTrack(context, tracks.first(), tracks) }
                        .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play all", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { val s = tracks.shuffled(); viewModel.playLocalTrack(context, s.first(), s) },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shuffle, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(19.dp))
                    }
                }
            }
            // Track list
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00A4DC), modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
                tracks.isEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                    Text("No tracks found", color = Color.White.copy(alpha = 0.32f))
                }
                else -> LazyColumn(modifier = Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(items = tracks, key = { it.id }) { track ->
                        TrackRow(track = track, context = context, queue = tracks, viewModel = viewModel, repo = repo)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.04f))
                    }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }

    if (showSheet && tracks.isNotEmpty()) {
        AddFolderToPlaylistSheet(tracks = tracks, repo = repo, onDismiss = { showSheet = false })
    }
}

// ─── Jellyfin status banners ──────────────────────────────────────────────────

@Composable
fun JellyfinNotConfiguredBanner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Cloud, null, tint = Color(0xFF00A4DC).copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
            Text("Jellyfin not connected", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Enter your server address and credentials in Settings → Jellyfin",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun JellyfinErrorBanner(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.CloudOff, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
            Text("Connection error", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(error, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3)
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A4DC).copy(alpha = 0.3f))) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry", color = Color.White)
            }
        }
    }
}

// ─── Liked Songs card ─────────────────────────────────────────────────────────

@Composable
fun LikedSongsCard(playlist: Playlist, allTracks: List<LocalTrack>, onClick: () -> Unit) {
    val trackCount = playlist.trackUris.size
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xFF6A0DAD).copy(alpha = 0.8f), Color(0xFF3D0080).copy(alpha = 0.6f))))
        .clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF6B9D), modifier = Modifier.size(28.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Liked Songs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text("$trackCount track${if (trackCount != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.5f))
    }
}

// ─── Folder square card ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderSquareCard(folder: File, allTracks: List<LocalTrack>, context: Context, repo: PlaylistRepository, onClick: () -> Unit) {
    var art        by remember(folder.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var artistName by remember(folder.absolutePath) { mutableStateOf<String?>(null) }
    var showMenu   by remember { mutableStateOf(false) }
    var showSheet  by remember { mutableStateOf(false) }
    val trackCount = remember(folder.absolutePath, allTracks) { allTracks.count { it.path.startsWith(folder.absolutePath + "/") } }
    val folderTracks = remember(folder.absolutePath, allTracks) { allTracks.filter { it.path.startsWith(folder.absolutePath + "/") } }
    LaunchedEffect(folder.absolutePath) {
        allTracks.firstOrNull { it.path.startsWith(folder.absolutePath + "/") }?.let { t ->
            val (bmp, art2) = getMetadataForTrack(context, t)
            art = bmp; artistName = art2 ?: t.artist.takeIf { it != "Unknown" }
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onClick() }, onLongClick = { showMenu = true })) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                if (art != null) {
                    Image(bitmap = art!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), startY = 60f)))
                } else Icon(Icons.Default.Folder, null, tint = Color(0xFFFFD700).copy(alpha = 0.5f), modifier = Modifier.fillMaxSize(0.35f))
            }
            Spacer(Modifier.height(6.dp))
            Text(folder.name, color = Color.White, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
            Text(buildString { append("$trackCount track${if (trackCount != 1) "s" else ""}"); if (!artistName.isNullOrBlank()) append("  ·  $artistName") },
                color = Color.White.copy(alpha = 0.38f), style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(Color(0xFF1E1E1E))) {
            DropdownMenuItem(text = { Text("Add to playlist", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = Color.White) },
                onClick = { showMenu = false; showSheet = true })
        }
        if (showSheet && folderTracks.isNotEmpty()) {
            AddFolderToPlaylistSheet(tracks = folderTracks, repo = repo, onDismiss = { showSheet = false })
        }
    }
}

// ─── Single square card ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleSquareCard(track: LocalTrack, context: Context, queue: List<LocalTrack>, viewModel: MediaViewModel?, repo: PlaylistRepository) {
    val trackKey   = track.uri.toString().ifBlank { track.path }
    var art        by remember(trackKey) { mutableStateOf<Bitmap?>(null) }
    var showMenu   by remember { mutableStateOf(false) }
    var showSheet  by remember { mutableStateOf(false) }
    val resolvedRepo = repo
    val playlists  by resolvedRepo.playlists.collectAsStateWithLifecycle()
    val isLiked = remember(playlists, trackKey) {
        playlists.firstOrNull { it.id == PlaylistRepository.LIKED_ID }?.trackUris?.contains(trackKey) == true
    }
    LaunchedEffect(trackKey) {
        if (TrackCache.artCache.containsKey(trackKey)) {
            art = TrackCache.artCache[trackKey]
        } else {
            val (bitmap, _) = getMetadataForTrack(context, track)
            art = bitmap
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {
                if (viewModel != null) {
                    if (viewModel.mediaState.value.isPlaying) { viewModel.playPause(); viewModel.spotifyWasPausedByUs = true }
                    viewModel.resumeLocalMedia()
                    viewModel.playLocalTrack(context, track, queue.ifEmpty { listOf(track) })
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(track.uri, "audio/*"); flags = Intent.FLAG_GRANT_READ_URI_PERMISSION })
                }
            },
            onLongClick = { showMenu = true }
        )) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                if (art != null) {
                    Image(bitmap = art!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), startY = 60f)))
                } else Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.fillMaxSize(0.35f))
                // Like button overlay
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    .clickable { resolvedRepo.toggleLike(trackKey) },
                    contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isLiked) Color(0xFFFF6B9D) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(track.title, color = Color.White, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
            Text(track.artist.takeIf { it != "Unknown" } ?: "", color = Color.White.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(Color(0xFF1E1E1E))) {
            DropdownMenuItem(text = { Text("Play", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = Color.White) },
                onClick = { showMenu = false; viewModel?.playLocalTrack(context, track, queue.ifEmpty { listOf(track) }) })
            DropdownMenuItem(text = { Text("Add to queue", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.QueueMusic, null, tint = Color.White) },
                onClick = { showMenu = false; viewModel?.addToLocalQueue(track) })
            DropdownMenuItem(text = { Text("Add to playlist", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = Color.White) },
                onClick = { showMenu = false; showSheet = true })
        }
        if (showSheet) {
            AddToPlaylistSheet(track = track, repo = resolvedRepo, onDismiss = { showSheet = false })
        }
    }
}

// ─── Playlist square card ─────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistSquareCard(playlist: Playlist, allTracks: List<LocalTrack>, onClick: () -> Unit, onDelete: () -> Unit) {
    val art: Bitmap? = remember(playlist.trackUris, allTracks) {
        val trackMap = allTracks.associateBy { it.uri.toString() } + allTracks.associateBy { it.path }
        playlist.trackUris.firstNotNullOfOrNull { uri ->
            TrackCache.artCache[trackMap[uri]?.let { t -> t.uri.toString().ifBlank { t.path } } ?: uri]
        }
    }
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onClick() }, onLongClick = { showMenu = true })) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                if (art != null) {
                    Image(bitmap = art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), startY = 60f)))
                } else Icon(Icons.Default.QueueMusic, null, tint = Color.White.copy(alpha = 0.28f), modifier = Modifier.fillMaxSize(0.35f))
            }
            Spacer(Modifier.height(6.dp))
            Text(playlist.name, color = Color.White, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
            Text("${playlist.trackUris.size} track${if (playlist.trackUris.size != 1) "s" else ""}",
                color = Color.White.copy(alpha = 0.38f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 2.dp))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(Color(0xFF1E1E1E))) {
            DropdownMenuItem(text = { Text("Delete", color = Color(0xFFFF6B6B)) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B)) },
                onClick = { showMenu = false; onDelete() })
        }
    }
}

// ─── Add to playlist sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFolderToPlaylistSheet(tracks: List<LocalTrack>, repo: PlaylistRepository, onDismiss: () -> Unit) {
    val playlists by repo.playlists.collectAsStateWithLifecycle()
    val context   = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text("Add ${tracks.size} track${if (tracks.size != 1) "s" else ""} to playlist",
                style = MaterialTheme.typography.titleMedium, color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Row(modifier = Modifier.fillMaxWidth().clickable {
                val pl = repo.createPlaylist("Folder — ${tracks.firstOrNull()?.album?.ifBlank { null } ?: "New playlist"}")
                repo.addTracks(pl.id, tracks.map { it.uri.toString().ifBlank { it.path } }); onDismiss()
            }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Color.White.copy(alpha = 0.7f)) }
                Text("New playlist", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            playlists.forEach { pl ->
                val uriSet = tracks.map { it.uri.toString().ifBlank { it.path } }.toSet()
                val alreadyIn = pl.trackUris.any { it in uriSet }
                Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !alreadyIn) {
                    repo.addTracks(pl.id, tracks.map { it.uri.toString().ifBlank { it.path } }); onDismiss()
                }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.07f)),
                        contentAlignment = Alignment.Center) {
                        if (pl.id == PlaylistRepository.LIKED_ID) Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF6B9D), modifier = Modifier.size(20.dp))
                        else Icon(Icons.Default.QueueMusic, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pl.name, color = if (alreadyIn) Color.White.copy(alpha = 0.35f) else Color.White, style = MaterialTheme.typography.bodyMedium)
                        Text("${pl.trackUris.size} tracks", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                    }
                    if (alreadyIn) Text("Added", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ─── Folder detail screen ─────────────────────────────────────────────────────

@Composable
fun FolderDetailScreen(folderPath: String, allTracks: List<LocalTrack>, context: Context,
                       viewModel: MediaViewModel, repo: PlaylistRepository, appStyle: AppStyle, dominantColor: Color, onBack: () -> Unit) {
    BackHandler { onBack() }
    val folder  = remember(folderPath) { File(folderPath) }
    val prefs   = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val bgColor = appBgColor(appStyle, dominantColor, prefs)
    var tracks          by remember(folderPath) { mutableStateOf<List<LocalTrack>?>(null) }
    var isLoadingTracks by remember { mutableStateOf(true) }
    var showSheet       by remember { mutableStateOf(false) }
    LaunchedEffect(folderPath) {
        isLoadingTracks = true
        tracks = resolveTracksForFolder(context, folder, allTracks.associateBy { it.path })
        isLoadingTracks = false
    }
    val firstKnownTrack = remember(folderPath, allTracks) { allTracks.firstOrNull { it.path.startsWith("$folderPath/") } }
    val headerArt = remember(folderPath, allTracks) { firstKnownTrack?.let { TrackCache.artCache[it.uri.toString().ifBlank { it.path }] } }
    val artistName = remember(folderPath, allTracks) { firstKnownTrack?.let { TrackCache.artistCache[it.uri.toString().ifBlank { it.path }] } }
    Box(modifier = Modifier.fillMaxSize().clickable(enabled = false, onClick = {}).background(bgColor)) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                if (headerArt != null) Image(bitmap = headerArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.3f)
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, bgColor), startY = 60f)))
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                if (!tracks.isNullOrEmpty()) {
                    IconButton(onClick = { showSheet = true }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))) { Icon(Icons.Default.PlaylistAdd, null, tint = Color.White) }
                }
                Row(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        if (headerArt != null) Image(bitmap = headerArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Default.Folder, null, tint = Color(0xFFFFD700).copy(alpha = 0.6f), modifier = Modifier.size(32.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(folder.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!artistName.isNullOrBlank()) Text(artistName!!, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                        Text("${tracks?.size ?: "…"} track${if ((tracks?.size ?: 0) != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
            val resolvedTracks = tracks
            if (!resolvedTracks.isNullOrEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.1f))
                        .clickable { viewModel.playLocalTrack(context, resolvedTracks.first(), resolvedTracks) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play all", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                        .clickable { val s = resolvedTracks.shuffled(); viewModel.playLocalTrack(context, s.first(), s) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shuffle, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(19.dp))
                    }
                }
            }
            when {
                isLoadingTracks -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(28.dp), strokeWidth = 2.dp) }
                resolvedTracks.isNullOrEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicOff, null, tint = Color.White.copy(alpha = 0.13f), modifier = Modifier.size(50.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No tracks found", color = Color.White.copy(alpha = 0.32f))
                    }
                }
                else -> LazyColumn(modifier = Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(items = resolvedTracks, key = { it.uri.toString().ifBlank { it.path } }) { track ->
                        TrackRow(track = track, context = context, queue = resolvedTracks, viewModel = viewModel, repo = repo)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.04f))
                    }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }
    if (showSheet && !tracks.isNullOrEmpty()) {
        AddFolderToPlaylistSheet(tracks = tracks!!, repo = repo, onDismiss = { showSheet = false })
    }
}

// ─── Folder browser screen ────────────────────────────────────────────────────

@Composable
fun FolderBrowserScreen(currentPath: String, allTracks: List<LocalTrack>, context: Context,
                        repo: PlaylistRepository, viewModel: MediaViewModel, appStyle: AppStyle = AppStyle.DYNAMIC,
                        dominantColor: Color = Color(0xFF1C1B1F), onNavigate: (String) -> Unit, onBack: () -> Unit, onClose: () -> Unit) {
    val currentFolder = File(currentPath)
    val prefs   = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val bgColor = appBgColor(appStyle, dominantColor, prefs)
    val subDirs = remember(currentPath) {
        currentFolder.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    val directAudioFiles = remember(currentPath) {
        currentFolder.listFiles()?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    var directTracks by remember(currentPath) { mutableStateOf<List<LocalTrack>?>(null) }
    LaunchedEffect(currentPath) {
        directTracks = if (directAudioFiles.isNotEmpty()) {
            val byPath = allTracks.associateBy { it.path }
            directAudioFiles.map { f -> byPath[f.absolutePath] ?: byPath[f.canonicalPath] ?: buildTrackFromFile(context, f) }
        } else emptyList()
    }
    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column {
            Spacer(Modifier.statusBarsPadding())
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Text(currentFolder.name.ifBlank { "Storage" }, style = MaterialTheme.typography.titleMedium, color = Color.White,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            val isEmpty = subDirs.isEmpty() && directTracks?.isEmpty() == true
            if (isEmpty && directTracks != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No folders or music files found", color = Color.White.copy(alpha = 0.4f)) }
            } else {
                LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items = subDirs, key = { "dir_${it.absolutePath}" }) { dir ->
                        FolderNavigateRow(folder = dir, allTracks = allTracks, context = context, onNavigate = { onNavigate(dir.absolutePath) })
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                    if (directTracks?.isNotEmpty() == true) {
                        items(items = directTracks!!, key = { "track_${it.path}" }) { track ->
                            TrackRow(track = track, context = context, queue = directTracks!!, viewModel = viewModel, repo = repo)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }
    }
}

// ─── Folder navigate row ──────────────────────────────────────────────────────

@Composable
fun FolderNavigateRow(folder: File, allTracks: List<LocalTrack>, context: Context, onNavigate: () -> Unit) {
    var art        by remember(folder.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var artistName by remember(folder.absolutePath) { mutableStateOf<String?>(null) }
    val firstTrack = remember(folder.absolutePath, allTracks) { allTracks.firstOrNull { it.path.startsWith(folder.absolutePath + "/") } }
    LaunchedEffect(folder.absolutePath) {
        if (firstTrack != null) { val (b, a) = getMetadataForTrack(context, firstTrack); art = b; artistName = a ?: firstTrack.artist.takeIf { it != "Unknown" } }
    }
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onNavigate() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            if (art != null) Image(bitmap = art!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.Folder, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(folder.name, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!artistName.isNullOrBlank()) Text(artistName!!, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, "Open", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

// ─── Track row ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(track: LocalTrack, context: Context, queue: List<LocalTrack> = emptyList(),
             viewModel: MediaViewModel? = null, repo: PlaylistRepository? = null) {
    val trackKey    = track.uri.toString().ifBlank { track.path }
    var art         by remember(trackKey) { mutableStateOf<Bitmap?>(null) }
    var artistName  by remember(trackKey) { mutableStateOf(track.artist) }
    var showMenu    by remember { mutableStateOf(false) }
    var showSheet   by remember { mutableStateOf(false) }
    val resolvedRepo = repo ?: PlaylistRepository.get(context)
    val playlists   by resolvedRepo.playlists.collectAsStateWithLifecycle()
    val isLiked = remember(playlists, trackKey) {
        playlists.firstOrNull { it.id == PlaylistRepository.LIKED_ID }?.trackUris?.contains(trackKey) == true
    }
    LaunchedEffect(trackKey) {
        if (TrackCache.artCache.containsKey(trackKey) && TrackCache.artistCache.containsKey(trackKey)) {
            art = TrackCache.artCache[trackKey]
            val cached = TrackCache.artistCache[trackKey]
            if (!cached.isNullOrBlank()) artistName = cached
        } else {
            val (bitmap, artist) = getMetadataForTrack(context, track)
            art = bitmap
            if (!artist.isNullOrBlank()) artistName = artist
        }
    }
    Box {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {
                    if (viewModel != null) {
                        if (viewModel.mediaState.value.isPlaying) { viewModel.playPause(); viewModel.spotifyWasPausedByUs = true }
                        viewModel.resumeLocalMedia()
                        viewModel.playLocalTrack(context, track, queue.ifEmpty { listOf(track) })
                    } else {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(track.uri, "audio/*"); flags = Intent.FLAG_GRANT_READ_URI_PERMISSION })
                    }
                },
                onLongClick = { if (viewModel != null) showMenu = true }
            ).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                if (art != null) Image(bitmap = art!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(buildString { append(artistName); if (track.album.isNotBlank()) append(" • ${track.album}") },
                    color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { resolvedRepo.toggleLike(trackKey) }, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) Color(0xFFFF6B9D) else Color.White.copy(alpha = 0.28f),
                    modifier = Modifier.size(17.dp))
            }
            Text(formatDuration(track.duration), color = Color.White.copy(alpha = 0.35f), style = MaterialTheme.typography.labelSmall)
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(Color(0xFF1A1A1A))) {
            DropdownMenuItem(text = { Text("Play", color = Color.White) }, leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = Color.White) },
                onClick = { showMenu = false; viewModel?.playLocalTrack(context, track, queue.ifEmpty { listOf(track) }) })
            DropdownMenuItem(text = { Text("Add to queue", color = Color.White) }, leadingIcon = { Icon(Icons.Default.QueueMusic, null, tint = Color.White) },
                onClick = { showMenu = false; viewModel?.addToLocalQueue(track) })
            DropdownMenuItem(text = { Text("Add to playlist", color = Color.White) }, leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = Color.White) },
                onClick = { showMenu = false; showSheet = true })
        }
        if (showSheet) {
            AddToPlaylistSheet(track = track, repo = resolvedRepo, onDismiss = { showSheet = false })
        }
    }
}