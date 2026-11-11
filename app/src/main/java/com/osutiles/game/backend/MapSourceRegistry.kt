package com.osutiles.game.backend

import android.content.Context
import com.osutiles.game.repository.LocalSongRepository
import com.osutiles.game.repository.SongRepository

/**
 * Central registry of available [MapSource]s and the shared [SongRepository].
 *
 * Today it exposes only the local source. When [P2PConfig.enabled] is set (and a
 * [PeerDiscovery]/[PeerTransport] implementation is wired up), the peer-to-peer
 * swarm source is added automatically and the rest of the app (which just iterates
 * [sources]) starts offering community maps — no gameplay/UI changes required.
 */
object MapSourceRegistry {

    @Volatile
    private var repository: SongRepository? = null

    @Volatile
    private var peerDiscovery: PeerDiscovery? = null

    @Volatile
    private var peerTransport: PeerTransport? = null

    /** Supply real P2P implementations to bring the swarm online. */
    fun installP2P(discovery: PeerDiscovery, transport: PeerTransport) {
        peerDiscovery = discovery
        peerTransport = transport
    }

    fun repository(context: Context): SongRepository {
        return repository ?: synchronized(this) {
            repository ?: LocalSongRepository(context.applicationContext).also { repository = it }
        }
    }

    fun sources(context: Context): List<MapSource> {
        val local = LocalMapSource(repository(context))
        return if (P2PConfig.enabled) {
            listOf(local, PeerMapSource(peerDiscovery, peerTransport))
        } else {
            listOf(local)
        }
    }
}
