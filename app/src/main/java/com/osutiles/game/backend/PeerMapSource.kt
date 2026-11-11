package com.osutiles.game.backend

import com.osutiles.game.model.Song

/**
 * [MapSource] backed by the peer-to-peer swarm instead of a central server.
 *
 * It aggregates the catalogs advertised by discovered [Peer]s and lets the player
 * download maps directly from whoever is seeding them. Publishing a map here means
 * "start seeding it to the swarm" rather than "upload it to a server".
 *
 * This is intentionally inert until [P2PConfig.enabled] is true and a real
 * [PeerDiscovery] / [PeerTransport] pair is supplied, keeping the app fully
 * local-only for now while the networking seam is ready to drop in.
 */
class PeerMapSource(
    private val discovery: PeerDiscovery? = null,
    private val transport: PeerTransport? = null
) : MapSource {

    override val id: String = "p2p-swarm"
    override val displayName: String = "Peer-to-peer swarm"

    override suspend fun isAvailable(): Boolean {
        if (!P2PConfig.enabled) return false
        val d = discovery ?: return false
        d.start()
        return d.knownPeers().isNotEmpty()
    }

    override suspend fun listSongs(): List<Song> {
        val d = discovery ?: return emptyList()
        val t = transport ?: return emptyList()
        if (!P2PConfig.enabled) return emptyList()
        // Aggregate every peer's catalog, de-duplicating by content hash so the
        // same map seeded by many peers appears once (with combined seeder counts).
        val byHash = LinkedHashMap<String, SharedMap>()
        for (peer in d.knownPeers()) {
            for (shared in runCatching { t.fetchCatalog(peer) }.getOrDefault(emptyList())) {
                val existing = byHash[shared.contentHash]
                byHash[shared.contentHash] = existing?.copy(
                    seeders = existing.seeders + shared.seeders,
                    demand = maxOf(existing.demand, shared.demand)
                ) ?: shared
            }
        }
        return byHash.values.map { it.song }
    }

    /**
     * Publish = start seeding. Honours the user's [SeedingPolicy] via [SeedingManager];
     * a locally created map is seeded when the policy allows it.
     */
    override suspend fun publish(song: Song): Song {
        val t = transport ?: return song
        if (!P2PConfig.enabled) return song
        val shared = SharedMap(
            contentHash = contentHashFor(song),
            song = song,
            sizeBytes = song.durationMs // placeholder until the file is hashed/sized
        )
        if (SeedingManager.shouldSeed(shared, locallyCreated = true, onWifi = true)) {
            t.seed(shared)
        }
        return song
    }

    private fun contentHashFor(song: Song): String {
        // TODO: hash the actual audio bytes (SHA-256) for real content addressing.
        return song.id
    }
}
