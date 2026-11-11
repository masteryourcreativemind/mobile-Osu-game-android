package com.osutiles.game.model

/**
 * A single osu!-style hit circle.
 *
 * @param timeMs   the moment in the song (ms) the circle should be tapped.
 * @param x        target centre, normalised 0..1 across the playfield width.
 * @param y        target centre, normalised 0..1 across the playfield height.
 * @param comboIndex 1-based number drawn inside the circle (osu! combo numbering).
 */
data class HitObject(
    val timeMs: Long,
    val x: Float,
    val y: Float,
    val comboIndex: Int
)
