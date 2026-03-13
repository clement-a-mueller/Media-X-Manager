package com.example.mediaxmanager.media

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ─── Model ────────────────────────────────────────────────────────────────────

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackUris: List<String> = emptyList(), // uri.toString(), fallback to path
    val createdAt: Long = System.currentTimeMillis(),
    /** True for built-in playlists the user cannot delete or rename. */
    val isSystem: Boolean = false
)

// ─── Repository ───────────────────────────────────────────────────────────────

class PlaylistRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("playlists_v1", Context.MODE_PRIVATE)

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        val loaded = loadAll()
        // Ensure Liked Songs always exists; inject at front if missing
        val hasLiked = loaded.any { it.id == LIKED_ID }
        _playlists.value = if (hasLiked) loaded else listOf(likedSongsBase()) + loaded
    }

    // ── Liked Songs ───────────────────────────────────────────────────────────

    private fun likedSongsBase() = Playlist(
        id        = LIKED_ID,
        name      = "Liked Songs",
        isSystem  = true,
        createdAt = Long.MAX_VALUE   // always sorts to the top
    )

    val likedPlaylist: Playlist
        get() = _playlists.value.first { it.id == LIKED_ID }

    fun isLiked(trackUri: String): Boolean = trackUri in likedPlaylist.trackUris

    fun toggleLike(trackUri: String) {
        if (isLiked(trackUri)) removeTrack(LIKED_ID, trackUri)
        else                   addTrack(LIKED_ID, trackUri)
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    private fun loadAll(): List<Playlist> {
        val json = prefs.getString(KEY_ALL, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length())
                .map { arr.getJSONObject(it).toPlaylist() }
                .sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getPlaylist(id: String): Playlist? =
        _playlists.value.firstOrNull { it.id == id }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun createPlaylist(name: String): Playlist {
        val pl = Playlist(name = name.trim())
        persist(_playlists.value + pl)
        return pl
    }

    fun renamePlaylist(id: String, newName: String) {
        if (id == LIKED_ID) return   // system playlists cannot be renamed
        persist(_playlists.value.map { if (it.id == id) it.copy(name = newName.trim()) else it })
    }

    fun deletePlaylist(id: String) {
        if (id == LIKED_ID) return   // system playlists cannot be deleted
        persist(_playlists.value.filter { it.id != id })
    }

    /** Appends a single track URI; no-op if already present. */
    fun addTrack(playlistId: String, trackUri: String) {
        persist(_playlists.value.map { pl ->
            if (pl.id == playlistId && trackUri !in pl.trackUris)
                pl.copy(trackUris = pl.trackUris + trackUri)
            else pl
        })
    }

    /** Appends multiple track URIs at once (e.g. whole album or folder). */
    fun addTracks(playlistId: String, trackUris: List<String>) {
        persist(_playlists.value.map { pl ->
            if (pl.id == playlistId) {
                val existing = pl.trackUris.toSet()
                pl.copy(trackUris = pl.trackUris + trackUris.filter { it !in existing })
            } else pl
        })
    }

    /** Removes a track URI from a playlist. */
    fun removeTrack(playlistId: String, trackUri: String) {
        persist(_playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(trackUris = pl.trackUris - trackUri)
            else pl
        })
    }

    /** Replaces the ordered URI list after a drag-reorder. */
    fun reorderTracks(playlistId: String, orderedUris: List<String>) {
        persist(_playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(trackUris = orderedUris) else pl
        })
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun persist(list: List<Playlist>) {
        _playlists.value = list
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_ALL, arr.toString()).apply()
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun Playlist.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("isSystem", isSystem)
        put("trackUris", JSONArray(trackUris))
    }

    private fun JSONObject.toPlaylist(): Playlist {
        val arr = optJSONArray("trackUris") ?: JSONArray()
        return Playlist(
            id        = getString("id"),
            name      = getString("name"),
            createdAt = optLong("createdAt", 0L),
            isSystem  = optBoolean("isSystem", false),
            trackUris = (0 until arr.length()).map { arr.getString(it) }
        )
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    companion object {
        const val LIKED_ID = "system_liked_songs"
        private const val KEY_ALL = "all_playlists"

        @Volatile private var INSTANCE: PlaylistRepository? = null

        fun get(context: Context): PlaylistRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaylistRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}