package com.osutiles.game.beatmap

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A detected rhythmic event in the audio. */
data class Onset(val timeMs: Long, val strength: Float)

/**
 * Energy-based onset detector.
 *
 * It slices mono PCM into short overlapping frames, measures how much the
 * short-time energy rises from one frame to the next (a "spectral-flux"-style
 * positive difference) and keeps the local peaks that stand out above a moving
 * average. Those peaks are the beats/hits a human hears in the music, which is
 * exactly what we want to turn into tappable circles so the map "plays the song".
 *
 * Pure Kotlin, no Android dependencies, so it is unit-testable on the JVM.
 */
object OnsetDetector {

    private const val FRAME_SIZE = 1024
    private const val HOP_SIZE = 512

    /**
     * @param samples    mono audio normalised to roughly [-1, 1].
     * @param sampleRate samples per second.
     * @return onsets ordered by time.
     */
    fun detect(samples: FloatArray, sampleRate: Int): List<Onset> {
        if (samples.isEmpty() || sampleRate <= 0) return emptyList()
        if (samples.size < FRAME_SIZE) return emptyList()

        val frameCount = 1 + (samples.size - FRAME_SIZE) / HOP_SIZE
        if (frameCount <= 2) return emptyList()

        // 1) Short-time RMS energy per frame.
        val energy = FloatArray(frameCount)
        for (f in 0 until frameCount) {
            val start = f * HOP_SIZE
            var sum = 0.0
            for (i in 0 until FRAME_SIZE) {
                val s = samples[start + i]
                sum += s.toDouble() * s.toDouble()
            }
            energy[f] = sqrt(sum / FRAME_SIZE).toFloat()
        }

        // 2) Positive energy difference (onset detection function).
        val flux = FloatArray(frameCount)
        for (f in 1 until frameCount) {
            flux[f] = max(0f, energy[f] - energy[f - 1])
        }

        // 3) Adaptive threshold = moving average of flux * sensitivity.
        val window = 20
        val sensitivity = 1.5f
        val msPerFrame = HOP_SIZE.toDouble() * 1000.0 / sampleRate.toDouble()
        val onsets = ArrayList<Onset>()

        for (f in 1 until frameCount - 1) {
            val lo = max(0, f - window)
            val hi = min(frameCount - 1, f + window)
            var sum = 0f
            for (i in lo..hi) sum += flux[i]
            val avg = sum / (hi - lo + 1)
            val threshold = avg * sensitivity

            val isPeak = flux[f] > threshold &&
                flux[f] >= flux[f - 1] &&
                flux[f] >= flux[f + 1] &&
                flux[f] > 0f
            if (isPeak) {
                val timeMs = (f * msPerFrame).toLong()
                onsets.add(Onset(timeMs, flux[f]))
            }
        }
        return onsets
    }
}
