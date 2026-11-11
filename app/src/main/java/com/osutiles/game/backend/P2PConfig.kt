package com.osutiles.game.backend

/**
 * User-controlled seeding preferences for the peer-to-peer swarm.
 *
 * The player decides how much they contribute back to the community:
 *  - [seedCreatedMaps]: share maps you created/imported so others can download them.
 *  - [seedDownloadedMaps]: keep re-sharing maps you downloaded (BitTorrent-style),
 *    so popular content stays available even if the original creator goes offline.
 *  - [seedHighDemandOnly]: when re-seeding downloads, only bother with the maps the
 *    swarm actually needs (demand >= [highDemandThreshold]), to spend bandwidth and
 *    storage where it helps most.
 */
data class SeedingPolicy(
    val seedCreatedMaps: Boolean = true,
    val seedDownloadedMaps: Boolean = false,
    val seedHighDemandOnly: Boolean = true,
    val highDemandThreshold: Int = 10,
    val maxSeedStorageMb: Int = 512,
    /** Only seed while on un-metered (Wi-Fi) connections. */
    val wifiOnly: Boolean = true
)

/**
 * Master configuration for the (currently local-only) peer-to-peer backend.
 *
 * Everything is inert until [enabled] is set true. When enabled, the app discovers
 * peers (LAN first, then DHT/tracker) and shares/downloads maps directly between
 * devices with no central server. This is the single switch that turns the
 * community swarm on.
 */
object P2PConfig {
    /** Master switch for all peer-to-peer features. Local-only until true. */
    @Volatile
    var enabled: Boolean = false

    /** Current seeding preferences. */
    @Volatile
    var seeding: SeedingPolicy = SeedingPolicy()

    /**
     * Optional bootstrap peers/trackers used to join the swarm beyond the LAN,
     * e.g. "dht://bootstrap.example.com:6881". Empty = LAN discovery only.
     */
    @Volatile
    var bootstrapNodes: List<String> = emptyList()
}
