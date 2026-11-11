package com.osutiles.game

import com.osutiles.game.backend.P2PConfig
import com.osutiles.game.backend.SeedDecision
import com.osutiles.game.backend.SeedingManager
import com.osutiles.game.backend.SeedingPolicy
import com.osutiles.game.backend.SharedMap
import com.osutiles.game.model.Song
import com.osutiles.game.model.SongSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedingManagerTest {

    private fun map(demand: Int) = SharedMap(
        contentHash = "h$demand",
        song = Song("s", "t", "a", "u", SongSource.IMPORTED),
        sizeBytes = 1_000,
        demand = demand
    )

    @After
    fun reset() {
        P2PConfig.enabled = false
        P2PConfig.seeding = SeedingPolicy()
    }

    @Test
    fun nothingSeedsWhenDisabled() {
        P2PConfig.enabled = false
        assertEquals(SeedDecision.DISABLED, SeedingManager.decide(map(100), locallyCreated = true, onWifi = true))
        assertFalse(SeedingManager.shouldSeed(map(100), locallyCreated = true, onWifi = true))
    }

    @Test
    fun wifiOnlyBlocksMeteredSeeding() {
        P2PConfig.enabled = true
        P2PConfig.seeding = SeedingPolicy(wifiOnly = true)
        assertEquals(
            SeedDecision.METERED_NETWORK,
            SeedingManager.decide(map(100), locallyCreated = true, onWifi = false)
        )
    }

    @Test
    fun createdMapsSeedWhenEnabled() {
        P2PConfig.enabled = true
        P2PConfig.seeding = SeedingPolicy(seedCreatedMaps = true)
        assertEquals(SeedDecision.CREATED, SeedingManager.decide(map(0), locallyCreated = true, onWifi = true))
    }

    @Test
    fun createdMapsStayLocalWhenCreatedSeedingOff() {
        P2PConfig.enabled = true
        P2PConfig.seeding = SeedingPolicy(seedCreatedMaps = false)
        assertFalse(SeedingManager.shouldSeed(map(0), locallyCreated = true, onWifi = true))
    }

    @Test
    fun downloadsNotReseededByDefault() {
        P2PConfig.enabled = true
        P2PConfig.seeding = SeedingPolicy(seedDownloadedMaps = false)
        assertFalse(SeedingManager.shouldSeed(map(999), locallyCreated = false, onWifi = true))
    }

    @Test
    fun highDemandDownloadsReseeded() {
        P2PConfig.enabled = true
        P2PConfig.seeding = SeedingPolicy(
            seedDownloadedMaps = true,
            seedHighDemandOnly = true,
            highDemandThreshold = 10
        )
        assertFalse(SeedingManager.shouldSeed(map(5), locallyCreated = false, onWifi = true))
        assertEquals(
            SeedDecision.HIGH_DEMAND_DOWNLOAD,
            SeedingManager.decide(map(25), locallyCreated = false, onWifi = true)
        )
    }

    @Test
    fun seedAllDownloadsWhenHighDemandOnlyOff() {
        P2PConfig.enabled = true
        P2PConfig.seeding = SeedingPolicy(seedDownloadedMaps = true, seedHighDemandOnly = false)
        assertTrue(SeedingManager.shouldSeed(map(0), locallyCreated = false, onWifi = true))
    }
}
