package com.osutiles.game.repository

import com.osutiles.game.model.Song

/**
 * Storage of the player's local song library (built-in + imported songs).
 *
 * This is intentionally separate from the networked [com.osutiles.game.backend.MapSource]
 * abstraction: the repository is the on-device source of truth, while map sources
 * are pluggable providers that can feed songs into it (e.g. a future community server).
 */
interface SongRepository {
    fun getSongs(): List<Song>
    fun getSong(id: String): Song?
    fun addSong(song: Song)
    fun removeSong(id: String)
}
