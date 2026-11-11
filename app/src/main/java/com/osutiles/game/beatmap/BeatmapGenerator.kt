package com.osutiles.game.beatmap

import com.osutiles.game.model.Beatmap
import com.osutiles.game.model.Difficulty
import com.osutiles.game.model.HitObject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Turns detected [Onset]s into a playable [Beatmap] for a given [Difficulty].
 *
 * Design goals:
 *  - "Feel like you play the song": notes land on real musical onsets.
 *  - Difficulty scaling straight out of the osu! playbook: harder difficulties
 *    keep more onsets, enforce smaller gaps and (via [Difficulty]) smaller/faster
 *    circles and tighter hit windows.
 *  - Deterministic layout: the same song + difficulty always produces the same
 *    map (seeded by song id) so players can learn and improve, like osu! maps.
 *  - Robust fallback: if a track can't be analysed (odd codec, silence, etc.) we
 *    still generate a steady rhythmic map so every imported file is playable,
 *    honouring the "no restrictions on type of audio" requirement.
 *
 * Pure Kotlin, no Android dependencies, so it is unit-testable on the JVM.
 */
object BeatmapGenerator {

    private const val PLAYFIELD_MARGIN = 0.12f

    fun generate(
        songId: String,
        difficulty: Difficulty,
        durationMs: Long,
        onsets: List<Onset>
    ): Beatmap {
        val times = selectTimes(difficulty, durationMs, onsets)
        val objects = layout(songId, difficulty, times)
        return Beatmap(songId, difficulty, durationMs, objects)
    }

    /** Pick which onset times become notes, applying keep-ratio and min-gap. */
    private fun selectTimes(
        difficulty: Difficulty,
        durationMs: Long,
        onsets: List<Onset>
    ): List<Long> {
        val usable = onsets.filter { it.timeMs in 0..durationMs }
        if (usable.size < 4) return fallbackTimes(difficulty, durationMs)

        // Keep the strongest onsets according to the difficulty's keep ratio.
        val keepCount = maxOf(4, (usable.size * difficulty.onsetKeepRatio).toInt())
        val kept = usable.sortedByDescending { it.strength }
            .take(keepCount)
            .sortedBy { it.timeMs }

        // Enforce the minimum gap so notes stay humanly tappable for the level.
        // The first onset is accepted unconditionally: subtracting Long.MIN_VALUE
        // would overflow and reject everything.
        val result = ArrayList<Long>(kept.size)
        var last: Long? = null
        for (o in kept) {
            val prev = last
            if (prev == null || o.timeMs - prev >= difficulty.minGapMs) {
                result.add(o.timeMs)
                last = o.timeMs
            }
        }
        return if (result.size < 4) fallbackTimes(difficulty, durationMs) else result
    }

    /** Steady metronome-style map used when audio analysis yields too little. */
    private fun fallbackTimes(difficulty: Difficulty, durationMs: Long): List<Long> {
        if (durationMs <= 0) return emptyList()
        // Space notes a bit wider than the raw min gap so the fallback stays fair.
        val step = (difficulty.minGapMs * 1.8).toLong().coerceAtLeast(250L)
        val times = ArrayList<Long>()
        var t = 800L // small lead-in before the first note
        while (t < durationMs - 200L) {
            times.add(t)
            t += step
        }
        return times
    }

    /**
     * Place circles with a seeded "flowing" random walk: each note nudges in a
     * new direction from the previous one, giving osu!-like patterns that are
     * stable per song+difficulty.
     */
    private fun layout(
        songId: String,
        difficulty: Difficulty,
        times: List<Long>
    ): List<HitObject> {
        val rng = Random(seedFor(songId, difficulty))
        val lo = PLAYFIELD_MARGIN
        val hi = 1f - PLAYFIELD_MARGIN

        var x = 0.5f
        var y = 0.5f
        var angle = rng.nextFloat() * (2f * Math.PI.toFloat())
        val objects = ArrayList<HitObject>(times.size)

        times.forEachIndexed { index, t ->
            // Turn by a random amount and step a random distance to the next note.
            angle += (rng.nextFloat() - 0.5f) * 2.4f
            val stepLen = 0.18f + rng.nextFloat() * 0.22f
            x += cos(angle) * stepLen
            y += sin(angle) * stepLen

            // Reflect off the playfield edges so notes stay fully on screen.
            if (x < lo) { x = lo + (lo - x); angle = (Math.PI.toFloat() - angle) }
            if (x > hi) { x = hi - (x - hi); angle = (Math.PI.toFloat() - angle) }
            if (y < lo) { y = lo + (lo - y); angle = -angle }
            if (y > hi) { y = hi - (y - hi); angle = -angle }
            x = x.coerceIn(lo, hi)
            y = y.coerceIn(lo, hi)

            objects.add(HitObject(timeMs = t, x = x, y = y, comboIndex = index + 1))
        }
        return objects
    }

    private fun seedFor(songId: String, difficulty: Difficulty): Long {
        return songId.hashCode().toLong() * 31 + difficulty.ordinal
    }
}
