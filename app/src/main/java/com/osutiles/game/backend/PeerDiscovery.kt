package com.osutiles.game.backend

/**
 * Finds other devices sharing maps. Implementations layer discovery channels:
 * LAN first (zero-config, e.g. Android NSD / mDNS), then wide-area via DHT or a
 * lightweight tracker seeded from [P2PConfig.bootstrapNodes].
 *
 * Deliberately an interface so the P2P transport can evolve without touching the
 * rest of the app.
 */
interface PeerDiscovery {
    /** Begin advertising this device and listening for peers. */
    suspend fun start()

    /** Snapshot of currently known peers. */
    suspend fun knownPeers(): List<Peer>

    /** Stop advertising/listening and release resources. */
    suspend fun stop()
}

/**
 * Moves map data between peers: fetches a peer's catalog, downloads a map's bytes,
 * and serves (seeds) local maps back to the swarm.
 */
interface PeerTransport {
    /** List the maps a peer is offering. */
    suspend fun fetchCatalog(peer: Peer): List<SharedMap>

    /**
     * Download [map] from the swarm into [destinationPath].
     * @return true on success (full, hash-verified file).
     */
    suspend fun download(map: SharedMap, destinationPath: String): Boolean

    /** Start serving [map] to peers that request it (seeding). */
    suspend fun seed(map: SharedMap)

    /** Stop serving [map]. */
    suspend fun stopSeeding(contentHash: String)
}
