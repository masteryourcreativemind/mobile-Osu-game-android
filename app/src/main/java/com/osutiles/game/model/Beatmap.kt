package com.osutiles.game.model

/** A generated, playable map: a song + difficulty + the ordered notes to tap. */
data class Beatmap(
    val songId: String,
    val difficulty: Difficulty,
    val durationMs: Long,
    val objects: List<HitObject>
)
