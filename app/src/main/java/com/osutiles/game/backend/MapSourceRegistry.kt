package com.osutiles.game.backend

import android.content.Context
import com.osutiles.game.repository.LocalSongRepository
import com.osutiles.game.repository.SongRepository

/**
 * Central registry of available [MapSource]s and the shared [SongRepository].
 *
 * Today it exposes only the local source. When [P2PConfig.enabled] is set (and a
 * [PeerDiscovery]/[PeerTransport] implementation is wired up), the peer-to-peer
 * swarm source is added automatically to [sources].
 *
 * NOTE: [sources] is not yet consumed by the app — the library UI, imports, and
 * gameplay currently use only [repository]. Enabling P2P still requires wiring
 * source enumeration, publication, and peer-audio download into the app before
 * community maps become visible (see README "Peer-to-peer map sharing").
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
            listOf(
                local,
                PeerMapSource(peerDiscovery, peerTransport) {
                    isOnUnmeteredNetwork(context)
                }
            )
        } else {
            listOf(local)
        }
    }

    /** Actual metering state from the OS; unknown means "metered" (safe default). */
    private fun isOnUnmeteredNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return false
        return !cm.isActiveNetworkMetered
    }
}
