package com.osutiles.game.repository

import android.content.Context
import com.osutiles.game.R
import com.osutiles.game.model.Song
import com.osutiles.game.model.SongSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, offline song library backed by SharedPreferences (JSON).
 *
 * Ships with one royalty-free, procedurally-composed demo track (bundled in
 * res/raw, deliberately not from any particular regional music tradition) so the
 * game is playable out of the box, and stores any songs the player imports from
 * their device.
 */
class LocalSongRepository(private val context: Context) : SongRepository {

    private val prefs = context.getSharedPreferences("song_library", Context.MODE_PRIVATE)

    private val builtInSongs: List<Song> = listOf(
        Song(
            id = "builtin_neon_pulse",
            title = "Neon Pulse",
            artist = "OsuTiles",
            uri = "android.resource://${context.packageName}/${R.raw.demo_neon_pulse}",
            source = SongSource.BUILT_IN
        )
    )

    override fun getSongs(): List<Song> = builtInSongs + loadImported()

    override fun getSong(id: String): Song? = getSongs().firstOrNull { it.id == id }

    override fun addSong(song: Song) {
        val list = loadImported().filter { it.id != song.id } + song
        saveImported(list)
    }

    override fun removeSong(id: String) {
        saveImported(loadImported().filter { it.id != id })
    }

    private fun loadImported(): List<Song> {
        val raw = prefs.getString(KEY_IMPORTED, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Song(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    artist = o.optString("artist", "Unknown"),
                    uri = o.getString("uri"),
                    source = SongSource.valueOf(o.optString("source", SongSource.IMPORTED.name)),
                    durationMs = o.optLong("durationMs", 0L)
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun saveImported(songs: List<Song>) {
        val arr = JSONArray()
        songs.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("artist", s.artist)
                    .put("uri", s.uri)
                    .put("source", s.source.name)
                    .put("durationMs", s.durationMs)
            )
        }
        prefs.edit().putString(KEY_IMPORTED, arr.toString()).apply()
    }

    companion object {
        private const val KEY_IMPORTED = "imported_songs"
    }
}
