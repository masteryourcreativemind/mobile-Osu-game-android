package com.osutiles.game

import com.osutiles.game.beatmap.BeatmapGenerator
import com.osutiles.game.beatmap.Onset
import com.osutiles.game.beatmap.OnsetDetector
import com.osutiles.game.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class BeatmapLogicTest {

    /** A click every 400ms over 10s should surface as clear onsets. */
    private fun clickTrack(sampleRate: Int = 44100, seconds: Int = 10, periodMs: Int = 400): FloatArray {
        val n = sampleRate * seconds
        val out = FloatArray(n)
        val period = sampleRate * periodMs / 1000
        var i = 0
        while (i < n) {
            // short decaying 1kHz blip
            for (k in 0 until 1200) {
                val idx = i + k
                if (idx >= n) break
                val env = Math.exp(-k / 400.0)
                out[idx] = (sin(2 * Math.PI * 1000.0 * k / sampleRate) * env).toFloat()
            }
            i += period
        }
        return out
    }

    @Test
    fun onsetDetector_findsPeriodicHits() {
        val onsets = OnsetDetector.detect(clickTrack(), 44100)
        // ~25 clicks over 10s at 400ms spacing; allow detector slack.
        assertTrue("expected several onsets, got ${onsets.size}", onsets.size in 15..40)
    }

    @Test
    fun difficultyScalesNoteDensity() {
        val onsets = OnsetDetector.detect(clickTrack(seconds = 20, periodMs = 250), 44100)
        val durMs = 20_000L
        val counts = Difficulty.entries.map {
            BeatmapGenerator.generate("song", it, durMs, onsets).objects.size
        }
        // Harder difficulties keep at least as many notes as easier ones.
        for (i in 1 until counts.size) {
            assertTrue("density should not decrease with difficulty: $counts", counts[i] >= counts[i - 1])
        }
    }

    @Test
    fun generatedNotesRespectConstraints() {
        val onsets = (0 until 200).map { Onset(it * 150L, 1f + (it % 5)) }
        val d = Difficulty.NORMAL
        val bm = BeatmapGenerator.generate("song", d, 30_000L, onsets)
        assertTrue(bm.objects.isNotEmpty())
        bm.objects.forEach {
            assertTrue(it.x in 0f..1f)
            assertTrue(it.y in 0f..1f)
        }
        bm.objects.zipWithNext().forEach { (a, b) ->
            assertTrue("time order", b.timeMs >= a.timeMs)
            assertTrue("min gap", b.timeMs - a.timeMs >= d.minGapMs)
        }
    }

    @Test
    fun generationIsDeterministic() {
        val onsets = (0 until 100).map { Onset(it * 200L, 2f) }
        val a = BeatmapGenerator.generate("same", Difficulty.HARD, 25_000L, onsets)
        val b = BeatmapGenerator.generate("same", Difficulty.HARD, 25_000L, onsets)
        assertEquals(a.objects, b.objects)
    }

    @Test
    fun fallbackMapWhenNoOnsets() {
        val bm = BeatmapGenerator.generate("silent", Difficulty.EASY, 20_000L, emptyList())
        assertTrue("fallback should still produce playable notes", bm.objects.size > 5)
    }

    /** Regression: the min-gap check must not overflow and reject every onset. */
    @Test
    fun generatedMapUsesActualOnsetTimes() {
        // Off-grid onset times a metronome fallback would never produce.
        val times = listOf(812L, 1377L, 2041L, 2699L, 3303L, 4011L, 4717L, 5350L)
        val onsets = times.map { Onset(it, 3f) }
        val bm = BeatmapGenerator.generate("onsets", Difficulty.NORMAL, 6_000L, onsets)
        assertTrue("expected notes at onset times, got ${bm.objects.map { it.timeMs }}",
            bm.objects.any { it.timeMs in times })
    }
}
