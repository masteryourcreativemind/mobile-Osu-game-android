package com.osutiles.game.backend

import com.osutiles.game.model.Song

/** How a peer was discovered. */
enum class PeerChannel { LAN, DHT, TRACKER }

/** A remote device participating in the map-sharing swarm. */
data class Peer(
    val id: String,
    val displayName: String,
    /** host:port or other transport address. */
    val address: String,
    val channel: PeerChannel
)

/**
 * A map/song being shared across the peer-to-peer swarm.
 *
 * @param contentHash content-addressed id (e.g. SHA-256 "infohash") of the audio,
 *                    so the same file shared by many peers is recognised as one map.
 * @param demand      recent download/request count; drives "seed high-demand" policy.
 * @param seeders     number of peers currently known to be seeding this map.
 */
data class SharedMap(
    val contentHash: String,
    val song: Song,
    val sizeBytes: Long,
    val demand: Int = 0,
    val seeders: Int = 0
)
