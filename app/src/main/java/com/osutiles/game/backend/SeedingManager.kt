package com.osutiles.game.backend

/** Why a map is (or isn't) being seeded — handy for UI and tests. */
enum class SeedDecision(val seeding: Boolean) {
    CREATED(true),
    HIGH_DEMAND_DOWNLOAD(true),
    DOWNLOAD(true),
    DISABLED(false),
    METERED_NETWORK(false),
    NOT_CREATED_SEEDING_OFF(false),
    DEMAND_TOO_LOW(false)
}

/**
 * Decides which maps this device should seed, based on the user's [SeedingPolicy].
 *
 * The decision logic is pure and JVM-testable; actually serving bytes to peers is
 * delegated to a [PeerTransport]. This keeps the "what should I share" rules (which
 * users care about and tweak) independent of the networking.
 */
object SeedingManager {

    /**
     * @param map          the map in question (carries swarm [SharedMap.demand]).
     * @param locallyCreated true if this device created/imported the map (vs. downloaded it).
     * @param onWifi       whether the device is currently on an un-metered connection.
     * @param policy       current seeding preferences.
     */
    fun decide(
        map: SharedMap,
        locallyCreated: Boolean,
        onWifi: Boolean,
        policy: SeedingPolicy = P2PConfig.seeding
    ): SeedDecision {
        if (!P2PConfig.enabled) return SeedDecision.DISABLED
        if (policy.wifiOnly && !onWifi) return SeedDecision.METERED_NETWORK

        if (locallyCreated) {
            return if (policy.seedCreatedMaps) SeedDecision.CREATED
            else SeedDecision.NOT_CREATED_SEEDING_OFF
        }

        // Re-seeding content we downloaded.
        if (!policy.seedDownloadedMaps) return SeedDecision.NOT_CREATED_SEEDING_OFF
        if (policy.seedHighDemandOnly && map.demand < policy.highDemandThreshold) {
            return SeedDecision.DEMAND_TOO_LOW
        }
        return if (policy.seedHighDemandOnly) SeedDecision.HIGH_DEMAND_DOWNLOAD
        else SeedDecision.DOWNLOAD
    }

    fun shouldSeed(
        map: SharedMap,
        locallyCreated: Boolean,
        onWifi: Boolean,
        policy: SeedingPolicy = P2PConfig.seeding
    ): Boolean = decide(map, locallyCreated, onWifi, policy).seeding
}
