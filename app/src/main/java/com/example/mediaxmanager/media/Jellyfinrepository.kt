package com.example.mediaxmanager.media

import android.net.Uri
import com.example.mediaxmanager.ui.screens.LocalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ─── Jellyfin auth state ──────────────────────────────────────────────────────

data class JellyfinSession(
    val serverUrl: String,   // e.g. "http://192.168.1.10:8096"
    val userId:    String,
    val token:     String
)

sealed class JellyfinResult<out T> {
    data class Success<T>(val data: T) : JellyfinResult<T>()
    data class Error(val message: String) : JellyfinResult<Nothing>()
}

// ─── Repository ───────────────────────────────────────────────────────────────

object JellyfinRepository {

    private const val CLIENT      = "MediaXManager"
    private const val DEVICE      = "AndroidPhone"
    private const val DEVICE_ID   = "mediaxmanager-device-001"
    private const val APP_VERSION = "1.0.0"

    // In-memory session — survives the process, not across reboots (fine for streaming)
    var session: JellyfinSession? = null

    // ── Auth ──────────────────────────────────────────────────────────────────

    suspend fun login(
        serverUrl: String,
        username:  String,
        password:  String
    ): JellyfinResult<JellyfinSession> = withContext(Dispatchers.IO) {
        try {
            val base = serverUrl.trimEnd('/')
            val url  = URL("$base/Users/AuthenticateByName")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Emby-Authorization", authHeader())
                doOutput = true
                connectTimeout = 10_000
                readTimeout    = 10_000
            }
            val body = JSONObject().apply {
                put("Username", username)
                put("Pw", password)
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode != 200) {
                return@withContext JellyfinResult.Error("Login failed: HTTP ${conn.responseCode}")
            }

            val response = conn.inputStream.bufferedReader().readText()
            val json     = JSONObject(response)
            val userId   = json.getJSONObject("User").getString("Id")
            val token    = json.getString("AccessToken")
            val newSession = JellyfinSession(base, userId, token)
            session = newSession
            JellyfinResult.Success(newSession)
        } catch (e: Exception) {
            JellyfinResult.Error(e.message ?: "Unknown error")
        }
    }

    fun logout() { session = null }

    // ── Fetch all music tracks ────────────────────────────────────────────────

    suspend fun fetchAllTracks(): JellyfinResult<List<LocalTrack>> = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext JellyfinResult.Error("Not logged in")
        try {
            val url = URL(
                "${s.serverUrl}/Users/${s.userId}/Items" +
                "?IncludeItemTypes=Audio" +
                "&Recursive=true" +
                "&Fields=Path,MediaSources,RunTimeTicks,AlbumId,AlbumArtist" +
                "&SortBy=SortName" +
                "&SortOrder=Ascending" +
                "&Limit=5000"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("X-Emby-Authorization", authHeader(s.token))
                connectTimeout = 15_000
                readTimeout    = 15_000
            }
            if (conn.responseCode != 200) {
                return@withContext JellyfinResult.Error("Failed to fetch tracks: HTTP ${conn.responseCode}")
            }
            val json   = JSONObject(conn.inputStream.bufferedReader().readText())
            val items  = json.getJSONArray("Items")
            val tracks = mutableListOf<LocalTrack>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id   = item.getString("Id")
                tracks.add(
                    LocalTrack(
                        id       = id.hashCode().toLong(),
                        title    = item.optString("Name", "Unknown"),
                        artist   = item.optString("AlbumArtist", item.optString("Artists", "Unknown")),
                        album    = item.optString("Album", ""),
                        duration = item.optLong("RunTimeTicks", 0L) / 10_000, // ticks → ms
                        uri      = streamUri(s, id),
                        path     = "jellyfin://$id"   // sentinel prefix — never a real file path
                    )
                )
            }
            JellyfinResult.Success(tracks)
        } catch (e: Exception) {
            JellyfinResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── Fetch tracks for a virtual folder / album ─────────────────────────────

    suspend fun fetchAlbums(): JellyfinResult<List<JellyfinAlbum>> = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext JellyfinResult.Error("Not logged in")
        try {
            val url = URL(
                "${s.serverUrl}/Users/${s.userId}/Items" +
                "?IncludeItemTypes=MusicAlbum" +
                "&Recursive=true" +
                "&SortBy=SortName" +
                "&SortOrder=Ascending" +
                "&Limit=2000"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("X-Emby-Authorization", authHeader(s.token))
                connectTimeout = 15_000
                readTimeout    = 15_000
            }
            if (conn.responseCode != 200) {
                return@withContext JellyfinResult.Error("HTTP ${conn.responseCode}")
            }
            val json   = JSONObject(conn.inputStream.bufferedReader().readText())
            val items  = json.getJSONArray("Items")
            val albums = mutableListOf<JellyfinAlbum>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                albums.add(
                    JellyfinAlbum(
                        id         = item.getString("Id"),
                        name       = item.optString("Name", "Unknown"),
                        artist     = item.optString("AlbumArtist", ""),
                        trackCount = item.optInt("ChildCount", 0),
                        artUrl     = artUrl(s, item.getString("Id"))
                    )
                )
            }
            JellyfinResult.Success(albums)
        } catch (e: Exception) {
            JellyfinResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun fetchTracksForAlbum(albumId: String): JellyfinResult<List<LocalTrack>> =
        withContext(Dispatchers.IO) {
            val s = session ?: return@withContext JellyfinResult.Error("Not logged in")
            try {
                val url = URL(
                    "${s.serverUrl}/Users/${s.userId}/Items" +
                    "?ParentId=$albumId" +
                    "&IncludeItemTypes=Audio" +
                    "&Fields=RunTimeTicks,AlbumArtist" +
                    "&SortBy=IndexNumber"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("X-Emby-Authorization", authHeader(s.token))
                    connectTimeout = 10_000
                    readTimeout    = 10_000
                }
                if (conn.responseCode != 200) {
                    return@withContext JellyfinResult.Error("HTTP ${conn.responseCode}")
                }
                val json   = JSONObject(conn.inputStream.bufferedReader().readText())
                val items  = json.getJSONArray("Items")
                val tracks = mutableListOf<LocalTrack>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id   = item.getString("Id")
                    tracks.add(
                        LocalTrack(
                            id       = id.hashCode().toLong(),
                            title    = item.optString("Name", "Unknown"),
                            artist   = item.optString("AlbumArtist", "Unknown"),
                            album    = item.optString("Album", ""),
                            duration = item.optLong("RunTimeTicks", 0L) / 10_000,
                            uri      = streamUri(s, id),
                            path     = "jellyfin://$id"
                        )
                    )
                }
                JellyfinResult.Success(tracks)
            } catch (e: Exception) {
                JellyfinResult.Error(e.message ?: "Unknown error")
            }
        }

    // ── URL helpers ───────────────────────────────────────────────────────────

    fun streamUri(s: JellyfinSession, itemId: String): Uri =
        Uri.parse("${s.serverUrl}/Audio/$itemId/stream?static=true&api_key=${s.token}")

    fun artUrl(s: JellyfinSession, itemId: String): String =
        "${s.serverUrl}/Items/$itemId/Images/Primary?fillWidth=200&quality=80&api_key=${s.token}"

    // ── Header helpers ────────────────────────────────────────────────────────

    private fun authHeader(token: String? = null): String {
        val base = "MediaBrowser Client=\"$CLIENT\", Device=\"$DEVICE\", " +
                   "DeviceId=\"$DEVICE_ID\", Version=\"$APP_VERSION\""
        return if (token != null) "$base, Token=\"$token\"" else base
    }
}

// ─── Album model ──────────────────────────────────────────────────────────────

data class JellyfinAlbum(
    val id:         String,
    val name:       String,
    val artist:     String,
    val trackCount: Int,
    val artUrl:     String
)

// ─── Helpers usable from UI ───────────────────────────────────────────────────

fun isJellyfinTrack(track: LocalTrack): Boolean = track.path.startsWith("jellyfin://")