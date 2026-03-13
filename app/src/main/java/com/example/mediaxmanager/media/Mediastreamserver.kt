package com.example.mediaxmanager.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mediaxmanager.ui.screens.TrackCache
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method
import org.json.JSONObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.net.NetworkInterface

// ─── Data classes ─────────────────────────────────────────────────────────────

@Serializable
data class TrackDto(
    val id:       String,
    val title:    String,
    val artist:   String,
    val album:    String,
    val duration: Long,
    val path:     String
)

// ─── HTTP Server ──────────────────────────────────────────────────────────────

class MediaHttpServer(
    port: Int,
    private val context: Context,
    private val viewModel: MediaViewModel
) : NanoHTTPD(port) {

    companion object {
        const val PORT = 8765
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return when {
            uri == "/tracks"              -> handleTracks()
            uri == "/now-playing"         -> handleNowPlaying()
            uri == "/volume"              -> handleVolume(session)
            uri.startsWith("/stream/")    -> handleStream(uri.removePrefix("/stream/"))
            uri.startsWith("/art/")       -> handleArt(uri.removePrefix("/art/"))
            uri.startsWith("/command/")   -> handleCommand(uri.removePrefix("/command/"), session)
            uri == "/ping"                -> newFixedLengthResponse("pong")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun handleTracks(): Response {
        val tracks = TrackCache.tracks.map { track ->
            TrackDto(
                id       = track.id.toString(),
                title    = track.title,
                artist   = track.artist,
                album    = track.album,
                duration = track.duration,
                path     = track.path
            )
        }
        val json = Json.encodeToString(tracks)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleNowPlaying(): Response {
        val prefs     = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val playOnPc  = prefs.getBoolean("play_on_pc", false)
        val track     = viewModel.localTrack.value
        val isPlaying = viewModel.isLocalPlaying.value
        val position  = viewModel.localPosition.value   // already in ms
        val duration  = viewModel.localDuration.value   // already in ms

        val json = if (track != null && playOnPc) {
            """{"id":"${track.id}","title":${Json.encodeToString(track.title)},"artist":${Json.encodeToString(track.artist)},"album":${Json.encodeToString(track.album)},"isPlaying":$isPlaying,"position":$position,"duration":$duration}"""
        } else {
            """{"id":null,"isPlaying":false,"position":0,"duration":0}"""
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleVolume(session: IHTTPSession): Response {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        // POST: Linux client pushes a new manual volume back to the phone
        if (session.method == Method.POST) {
            return try {
                val body = mutableMapOf<String, String>()
                session.parseBody(body)
                val raw    = body["postData"] ?: body.values.firstOrNull() ?: "{}"
                val parsed = org.json.JSONObject(raw)
                val vol    = parsed.optInt("volume", -1)
                if (vol in 0..100) {
                    prefs.edit()
                        .putInt("pc_manual_volume", vol)
                        .putBoolean("pc_volume_sync", false)
                        .apply()
                    newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
                } else {
                    newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad volume")
                }
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Parse error")
            }
        }

        // GET: return current effective volume (phone media stream or manual)
        val syncEnabled = prefs.getBoolean("pc_volume_sync", true)
        val volume: Int = if (syncEnabled) {
            val am      = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val current = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val max     = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            if (max > 0) (current * 100 / max) else 80
        } else {
            prefs.getInt("pc_manual_volume", 80)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"volume":$volume}""")
    }

    private fun handleStream(trackId: String): Response {
        val track = TrackCache.tracks.firstOrNull { it.id.toString() == trackId }
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Track not found")

        val file = File(track.path)
        if (!file.exists())
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")

        val mimeType = when (file.extension.lowercase()) {
            "mp3"  -> "audio/mpeg"
            "flac" -> "audio/flac"
            "ogg"  -> "audio/ogg"
            "m4a"  -> "audio/mp4"
            "wav"  -> "audio/wav"
            "aac"  -> "audio/aac"
            "opus" -> "audio/opus"
            else   -> "audio/mpeg"
        }

        return try {
            val fis = FileInputStream(file)
            newChunkedResponse(Response.Status.OK, mimeType, fis)
        } catch (e: Exception) {
            Log.e("MediaHttpServer", "Error streaming ${track.path}", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Stream error")
        }
    }

    private fun handleArt(trackId: String): Response {
        val track = TrackCache.tracks.firstOrNull { it.id.toString() == trackId }
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Track not found")

        val key    = track.uri.toString().ifBlank { track.path }
        val bitmap = TrackCache.artCache[key]
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No art")

        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg",
            java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
    }

    private fun handleCommand(command: String, session: IHTTPSession): Response {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("play_on_pc", false))
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Play on PC not enabled")

        return when {
            command.startsWith("play") -> {
                val trackId = session.parameters["id"]?.firstOrNull()
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing id")
                val track = TrackCache.tracks.firstOrNull { it.id.toString() == trackId }
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Track not found")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    viewModel.playLocalTrack(context, track, TrackCache.tracks)
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
            }
            command == "pause" -> {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    viewModel.pauseForPcStreaming()   // works with or without MediaPlayer
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
            }
            command == "resume" -> {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    viewModel.resumeForPcStreaming()
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
            }
            command == "next" -> {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    viewModel.localNext(context)
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
            }
            command == "ended" -> {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    viewModel.localNext(context)
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Unknown command")
        }
    }
}

// ─── Foreground Service ───────────────────────────────────────────────────────

class MediaStreamService : Service() {

    private var server: MediaHttpServer? = null

    companion object {
        const val CHANNEL_ID   = "media_stream_channel"
        const val NOTIF_ID     = 9001
        const val ACTION_START = "START"
        const val ACTION_STOP  = "STOP"

        var viewModel: MediaViewModel? = null

        fun start(context: Context) {
            val intent = Intent(context, MediaStreamService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaStreamService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun getLocalIp(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains('.') == true }
                    ?.hostAddress
            } catch (e: Exception) { null }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP  -> { stopServer(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        val vm = viewModel ?: return
        server = MediaHttpServer(MediaHttpServer.PORT, this, vm).also { it.start() }
        Log.i("MediaStreamService", "Server started on port ${MediaHttpServer.PORT}")
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun buildNotification(): Notification {
        val ip = getLocalIp() ?: "unknown"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Streaming Active")
            .setContentText("Streaming on $ip:${MediaHttpServer.PORT}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music Stream Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Keeps the music streaming server running" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}