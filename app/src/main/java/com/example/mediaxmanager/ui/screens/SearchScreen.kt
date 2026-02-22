package com.example.mediaxmanager.ui.screens

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import com.example.mediaxmanager.media.MediaViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediaxmanager.ui.theme.AppStyle

val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "m4a", "wav", "aac", "opus", "wma", "aiff")

data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val path: String = ""
)

object TrackCache {
    var tracks: List<LocalTrack> = emptyList()
    var lastLoadTime: Long = 0L
    var metadataLoaded: Boolean = false
    var lastMetadataLoadTime: Long = 0L
    val artCache = mutableMapOf<String, Bitmap?>()
    val artistCache = mutableMapOf<String, String?>()
}

suspend fun preloadAllMetadata(context: Context, tracks: List<LocalTrack>) = withContext(Dispatchers.IO) {
    tracks.map { track ->
        async {
            val key = track.uri.toString().ifBlank { track.path }
            if (!TrackCache.artCache.containsKey(key)) {
                getMetadataForTrack(context, track)
            }
        }
    }.awaitAll()
    TrackCache.metadataLoaded = true
    TrackCache.lastMetadataLoadTime = System.currentTimeMillis()
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
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, selection, null, sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val path = cursor.getString(dataCol) ?: ""
            tracks.add(
                LocalTrack(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol) ?: "Unknown",
                    album = cursor.getString(albumCol) ?: "",
                    duration = cursor.getLong(durationCol),
                    uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ),
                    path = path
                )
            )
        }
    }
    tracks
}

suspend fun buildTrackFromFile(context: Context, file: File): LocalTrack =
    withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        return@withContext try {
            retriever.setDataSource(file.absolutePath)
            LocalTrack(
                id = file.hashCode().toLong(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() } ?: "Unknown",
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() } ?: "",
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                uri = Uri.fromFile(file),
                path = file.absolutePath
            )
        } catch (e: Exception) {
            LocalTrack(
                id = file.hashCode().toLong(),
                title = file.nameWithoutExtension,
                artist = "Unknown",
                album = "",
                duration = 0L,
                uri = Uri.fromFile(file),
                path = file.absolutePath
            )
        } finally {
            retriever.release()
        }
    }

suspend fun resolveTracksForFolder(
    context: Context,
    folder: File,
    tracksByPath: Map<String, LocalTrack>
): List<LocalTrack> = withContext(Dispatchers.IO) {
    val audioFiles = folder.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
        .sortedBy { it.name.lowercase() }
        .toList()
    audioFiles.map { file ->
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
        if (TrackCache.artCache.containsKey(key) && TrackCache.artistCache.containsKey(key)) {
            return@withContext Pair(TrackCache.artCache[key], TrackCache.artistCache[key])
        }
        var bitmap: Bitmap? = null
        var artist: String? = null
        try {
            val albumId = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(track.id.toString()),
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
            if (albumId != null) {
                val artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                bitmap = context.contentResolver.openInputStream(artUri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) { }

        if (bitmap == null || artist == null) {
            val retriever = MediaMetadataRetriever()
            try {
                var opened = false
                try {
                    context.contentResolver.openFileDescriptor(track.uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        opened = true
                    }
                } catch (e: Exception) { }
                if (!opened) {
                    try {
                        retriever.setDataSource(context, track.uri)
                        opened = true
                    } catch (e: Exception) { }
                }
                if (!opened && track.path.isNotBlank()) {
                    try {
                        retriever.setDataSource(track.path)
                        opened = true
                    } catch (e: Exception) { }
                }
                if (opened) {
                    if (bitmap == null) {
                        val bytes = retriever.embeddedPicture
                        bitmap = bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.takeIf { it.isNotBlank() }
                }
            } finally {
                retriever.release()
            }
        }
        TrackCache.artCache[key] = bitmap
        TrackCache.artistCache[key] = artist
        Pair(bitmap, artist)
    }

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// ─── Root screen ──────────────────────────────────────────────────────────────
@Composable
fun SearchScreen(
    viewModel: MediaViewModel,
    appStyle: AppStyle = AppStyle.DYNAMIC,
    dominantColor: Color = Color(0xFF1C1B1F)
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var allTracks by remember { mutableStateOf(TrackCache.tracks) }
    var isLoading by remember { mutableStateOf(false) }
    var browserPath by remember { mutableStateOf<String?>(null) }
    var starredFolders by remember {
        mutableStateOf(
            prefs.getString("starred_folders", "")
                ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        )
    }

    fun saveStarred(folders: Set<String>) {
        val clean = folders.filter { it.isNotBlank() }.toSet()
        prefs.edit().putString("starred_folders", clean.joinToString(",")).apply()
        starredFolders = clean
    }

    LaunchedEffect(Unit) {
        val refreshMinutes = prefs.getInt("search_refresh_minutes", -1)
        val now = System.currentTimeMillis()
        val shouldRefreshTracks = TrackCache.tracks.isEmpty() ||
                (refreshMinutes > 0 && now - TrackCache.lastLoadTime > refreshMinutes * 60 * 1000L)
        if (shouldRefreshTracks) {
            isLoading = true
            TrackCache.tracks = queryLocalTracks(context)
            TrackCache.lastLoadTime = now
            TrackCache.metadataLoaded = false
            isLoading = false
        }
        allTracks = TrackCache.tracks
        val shouldRefreshMetadata = !TrackCache.metadataLoaded ||
                (refreshMinutes > 0 && now - TrackCache.lastMetadataLoadTime > refreshMinutes * 60 * 1000L)
        if (shouldRefreshMetadata) {
            preloadAllMetadata(context, TrackCache.tracks)
        }
    }

    val filteredTracks = remember(query, allTracks) {
        if (query.isBlank()) emptyList()
        else allTracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
        }
    }

    // Background color matching HomeScreen logic
    val minimalColor = remember { Color(prefs.getInt("minimal_color", 0xFF2C2C2C.toInt())) }
    val backgroundColor = when (appStyle) {
        AppStyle.DYNAMIC -> dominantColor
        AppStyle.AMOLED  -> Color.Black
        AppStyle.GLASS   -> Color.Black
        AppStyle.MINIMAL -> resolveSecondaryBackground(appStyle, minimalColor)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Glass background image layer
        if (appStyle == AppStyle.GLASS) {
            val localArtwork by viewModel.localArtwork.collectAsStateWithLifecycle()
            val combinedState by viewModel.combinedMediaState.collectAsStateWithLifecycle()
            val artwork = localArtwork ?: combinedState.artwork
            if (artwork != null) {
                androidx.compose.foundation.Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.4f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }
        }

        Column {
            Spacer(Modifier.height(24.dp))
            Text("Search", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(Modifier.height(16.dp))
            // Spotify button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1DB954))
                    .clickable {
                        val intent = context.packageManager
                            .getLaunchIntentForPackage("com.spotify.music")
                            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
                        context.startActivity(intent)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Open Spotify", style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            // Browse Folders button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { browserPath = "/storage/emulated/0" }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Browse Folders", style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search local music...", color = Color.White.copy(alpha = 0.4f)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (query.isNotBlank()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items = filteredTracks, key = { it.id }) { track ->
                        TrackRow(track = track, context = context, queue = filteredTracks, viewModel = viewModel)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                    if (filteredTracks.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No tracks found", color = Color.White.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            } else {
                if (starredFolders.isNotEmpty()) {
                    Text(
                        "Starred",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = starredFolders.toList().sorted(),
                            key = { it }
                        ) { folderPath ->
                            StarredFolderRow(
                                folder = File(folderPath),
                                allTracks = allTracks,
                                context = context,
                                viewModel = viewModel,
                                onUnstar = { saveStarred(starredFolders - folderPath) }
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Browse folders or search above",
                                color = Color.White.copy(alpha = 0.3f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        // Folder browser overlay
        if (browserPath != null) {
            FolderBrowserScreen(
                currentPath = browserPath!!,
                allTracks = allTracks,
                context = context,
                starredFolders = starredFolders,
                viewModel = viewModel,
                onNavigate = { newPath -> browserPath = newPath },
                onBack = {
                    val parent = File(browserPath!!).parent
                    if (parent == null || parent == "/storage/emulated") {
                        browserPath = null
                    } else {
                        browserPath = parent
                    }
                },
                onClose = { browserPath = null },
                onStar = { path -> saveStarred(starredFolders + path) },
                onUnstar = { path -> saveStarred(starredFolders - path) }
            )
        }
    }
}

// ─── Full-screen folder browser ───────────────────────────────────────────────
@Composable
fun FolderBrowserScreen(
    currentPath: String,
    allTracks: List<LocalTrack>,
    context: Context,
    starredFolders: Set<String>,
    viewModel: MediaViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onStar: (String) -> Unit,
    onUnstar: (String) -> Unit
) {
    val currentFolder = File(currentPath)
    val isStarred = starredFolders.contains(currentPath)

    val subDirs = remember(currentPath) {
        currentFolder.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    val directAudioFiles = remember(currentPath) {
        currentFolder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    var directTracks by remember(currentPath) { mutableStateOf<List<LocalTrack>?>(null) }

    LaunchedEffect(currentPath) {
        if (directAudioFiles.isNotEmpty()) {
            val tracksByPath = allTracks.associateBy { it.path }
            directTracks = directAudioFiles.map { file ->
                tracksByPath[file.absolutePath]
                    ?: tracksByPath[file.canonicalPath]
                    ?: buildTrackFromFile(context, file)
            }
        } else {
            directTracks = emptyList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Column {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = currentFolder.name.ifBlank { "Storage" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = {
                    if (isStarred) onUnstar(currentPath) else onStar(currentPath)
                }) {
                    Icon(
                        if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isStarred) "Unstar" else "Star",
                        tint = if (isStarred) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val isEmpty = subDirs.isEmpty() && directTracks?.isEmpty() == true
            if (isEmpty && directTracks != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No folders or music files found", color = Color.White.copy(alpha = 0.4f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = subDirs, key = { "dir_${it.absolutePath}" }) { dir ->
                        FolderNavigateRow(
                            folder = dir,
                            allTracks = allTracks,
                            isStarred = starredFolders.contains(dir.absolutePath),
                            context = context,
                            onNavigate = { onNavigate(dir.absolutePath) },
                            onStar = {
                                if (starredFolders.contains(dir.absolutePath)) onUnstar(dir.absolutePath)
                                else onStar(dir.absolutePath)
                            }
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }

                    if (directTracks?.isNotEmpty() == true) {
                        items(items = directTracks!!, key = { "track_${it.path}" }) { track ->
                            TrackRow(
                                track = track,
                                context = context,
                                queue = directTracks!!,
                                viewModel = viewModel
                            )
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
fun FolderNavigateRow(
    folder: File,
    allTracks: List<LocalTrack>,
    isStarred: Boolean,
    context: Context,
    onNavigate: () -> Unit,
    onStar: () -> Unit
) {
    var art by remember(folder.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var artistName by remember(folder.absolutePath) { mutableStateOf<String?>(null) }
    val firstTrack = remember(folder.absolutePath, allTracks) {
        allTracks.firstOrNull { it.path.startsWith(folder.absolutePath + "/") }
    }

    LaunchedEffect(folder.absolutePath) {
        if (firstTrack != null) {
            val (bitmap, artist) = getMetadataForTrack(context, firstTrack)
            art = bitmap
            artistName = artist ?: firstTrack.artist.takeIf { it != "Unknown" }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onNavigate() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(
                    bitmap = art!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!artistName.isNullOrBlank()) {
                Text(
                    artistName!!,
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onStar) {
            Icon(
                if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Star",
                tint = if (isStarred) Color(0xFFFFD700) else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Open",
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─── Starred folder row ───────────────────────────────────────────────────────
@Composable
fun StarredFolderRow(
    folder: File,
    allTracks: List<LocalTrack>,
    context: Context,
    viewModel: MediaViewModel,
    onUnstar: () -> Unit
) {
    var art by remember(folder.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var artistName by remember(folder.absolutePath) { mutableStateOf<String?>(null) }
    var isExpanded by remember(folder.absolutePath) { mutableStateOf(false) }
    var expandedTracks by remember(folder.absolutePath) { mutableStateOf<List<LocalTrack>?>(null) }
    var isLoadingTracks by remember(folder.absolutePath) { mutableStateOf(false) }

    val trackCount = remember(folder.absolutePath, allTracks) {
        allTracks.count { it.path.startsWith(folder.absolutePath + "/") }
    }

    LaunchedEffect(folder.absolutePath) {
        val firstTrack = allTracks.firstOrNull { it.path.startsWith(folder.absolutePath + "/") }
            ?: run {
                val firstFile = withContext(Dispatchers.IO) {
                    folder.walkTopDown()
                        .firstOrNull { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                }
                firstFile?.let { buildTrackFromFile(context, it) }
            }
        if (firstTrack != null) {
            val (bitmap, artist) = getMetadataForTrack(context, firstTrack)
            art = bitmap
            artistName = artist ?: firstTrack.artist.takeIf { it != "Unknown" }
        }
    }

    LaunchedEffect(folder.absolutePath, isExpanded) {
        if (isExpanded && expandedTracks == null && !isLoadingTracks) {
            isLoadingTracks = true
            val tracksByPath = allTracks.associateBy { it.path }
            expandedTracks = resolveTracksForFolder(context, folder, tracksByPath)
            isLoadingTracks = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    Image(
                        bitmap = art!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFFFD700).copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!artistName.isNullOrBlank()) {
                    Text(
                        artistName!!,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "$trackCount track${if (trackCount != 1) "s" else ""}",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onUnstar) {
                Icon(Icons.Default.Star, contentDescription = "Unstar", tint = Color(0xFFFFD700))
            }
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            when {
                isLoadingTracks -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                expandedTracks?.isEmpty() == true -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            "No music files found",
                            color = Color.White.copy(alpha = 0.3f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                expandedTracks != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f))
                            .padding(start = 8.dp)
                    ) {
                        expandedTracks!!.forEach { track ->
                            TrackRow(
                                track = track,
                                context = context,
                                queue = expandedTracks!!,
                                viewModel = viewModel
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.04f))
                        }
                    }
                }
            }
        }
    }
}

// ─── Track row ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: LocalTrack,
    context: Context,
    queue: List<LocalTrack> = emptyList(),
    viewModel: MediaViewModel? = null
) {
    val trackKey = track.uri.toString().ifBlank { track.path }
    var art by remember(trackKey) { mutableStateOf<Bitmap?>(null) }
    var artistName by remember(trackKey) { mutableStateOf(track.artist) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(trackKey) {
        val key = track.uri.toString().ifBlank { track.path }
        if (TrackCache.artCache.containsKey(key) && TrackCache.artistCache.containsKey(key)) {
            // Already cached — read directly without launching coroutine work
            art = TrackCache.artCache[key]
            val cachedArtist = TrackCache.artistCache[key]
            if (!cachedArtist.isNullOrBlank()) artistName = cachedArtist
        } else {
            val (bitmap, artist) = getMetadataForTrack(context, track)
            art = bitmap
            if (!artist.isNullOrBlank()) artistName = artist
        }
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = {
                        if (viewModel != null) {
                            viewModel.playLocalTrack(context, track, queue.ifEmpty { listOf(track) })
                        } else {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(track.uri, "audio/*")
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                            )
                        }
                    },
                    onLongClick = { if (viewModel != null) showMenu = true }
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    Image(
                        bitmap = art!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(artistName)
                        if (track.album.isNotBlank()) append(" • ${track.album}")
                    },
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                formatDuration(track.duration),
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color(0xFF1A1A1A))
        ) {
            DropdownMenuItem(
                text = { Text("Play", color = Color.White) },
                leadingIcon = {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                },
                onClick = {
                    showMenu = false
                    viewModel?.playLocalTrack(context, track, queue.ifEmpty { listOf(track) })
                }
            )
            DropdownMenuItem(
                text = { Text("Add to queue", color = Color.White) },
                leadingIcon = {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White)
                },
                onClick = {
                    showMenu = false
                    viewModel?.addToLocalQueue(track)
                }
            )
        }
    }
}