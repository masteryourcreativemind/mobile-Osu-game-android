package com.osutiles.game.backend

import com.osutiles.game.model.Song
import com.osutiles.game.repository.SongRepository

/** [MapSource] backed by the on-device [SongRepository]. The default, always-on source. */
class LocalMapSource(private val repository: SongRepository) : MapSource {
    override val id: String = "local"
    override val displayName: String = "On this device"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun listSongs(): List<Song> = repository.getSongs()

    override suspend fun publish(song: Song): Song {
        repository.addSong(song)
        return song
    }
}
