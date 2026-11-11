package com.osutiles.game.model

/**
 * Gameplay difficulties, directly inspired by osu!'s difficulty attributes
 * (Approach Rate, Circle Size, hit windows and object density).
 *
 * Every parameter here is tuned so the same song feels progressively harder
 * without changing the underlying music: more of the song's onsets become
 * notes, the approach time shrinks, circles get smaller and timing windows
 * get tighter.
 */
enum class Difficulty(
    val displayName: String,
    /** How long (ms) a circle is visible/approaching before it must be hit. Lower = harder (osu! AR). */
    val approachMs: Long,
    /** Circle radius as a fraction of the playfield's smaller side. Lower = harder (osu! CS). */
    val circleRadiusFraction: Float,
    /** Perfect ("300") timing window in ms around the target time. */
    val windowPerfectMs: Long,
    /** Good ("100") timing window in ms. */
    val windowGoodMs: Long,
    /** Meh ("50") timing window in ms. Outside this window a tap misses. */
    val windowMehMs: Long,
    /** Fraction of detected onsets that become notes (0..1). Higher = denser = harder. */
    val onsetKeepRatio: Float,
    /** Minimum time (ms) allowed between two consecutive notes. Lower = harder. */
    val minGapMs: Long,
    /**
     * osu!'s per-map "difficulty multiplier" used in the ScoreV1 formula. In osu!
     * it is derived from HP/CS/OD/object-count; here it is fixed per difficulty so
     * harder maps award more score for the same combo, exactly like osu!.
     */
    val scoreDifficultyMultiplier: Int
) {
    EASY("Easy", 1200, 0.11f, 160, 240, 320, 0.35f, 420, 2),
    NORMAL("Normal", 900, 0.09f, 120, 180, 260, 0.55f, 300, 3),
    HARD("Hard", 650, 0.075f, 90, 140, 200, 0.80f, 200, 5),
    INSANE("Insane", 450, 0.06f, 70, 110, 160, 1.0f, 120, 6);

    companion object {
        fun fromName(name: String?): Difficulty =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
