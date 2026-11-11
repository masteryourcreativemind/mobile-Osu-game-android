package com.osutiles.game.backend

import com.osutiles.game.model.Song

/**
 * Pluggable provider of songs/maps. This is the seam that makes the app
 * community-ready while staying local-only today.
 *
 * The app talks only to this interface, so joining the peer-to-peer swarm later is
 * a matter of shipping a [MapSource] implementation (see
 * [com.osutiles.game.backend.PeerMapSource]) and registering it in
 * [MapSourceRegistry] — no gameplay or UI code changes.
 */
interface MapSource {
    /** Stable identifier, e.g. "local" or "p2p-swarm". */
    val id: String

    /** Human-readable name shown in UI/source pickers. */
    val displayName: String

    /** Whether this source can currently serve songs (e.g. peers reachable). */
    suspend fun isAvailable(): Boolean

    /** List songs this source offers. Local sources return the on-device library. */
    suspend fun listSongs(): List<Song>

    /**
     * Publish a song/map to this source. Local sources persist it on-device; the
     * peer-to-peer source starts seeding it to the swarm. Returns the stored song.
     *
     * The community requirement is explicit: any user may contribute a song with no
     * size/type restrictions. For P2P, how much a device shares back is governed by
     * the user's [com.osutiles.game.backend.SeedingPolicy].
     */
    suspend fun publish(song: Song): Song
}
