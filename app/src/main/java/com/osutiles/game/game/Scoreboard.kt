package com.osutiles.game.game

import com.osutiles.game.model.Difficulty
import kotlin.math.abs

/** osu!-style judgement for a tap. */
enum class Judgement(val points: Int, val label: String) {
    PERFECT(300, "300"),
    GOOD(100, "100"),
    MEH(50, "50"),
    MISS(0, "MISS")
}

/**
 * Tracks score, combo and accuracy during play using osu!'s standard rules.
 *
 * Score (osu! "ScoreV1"):
 *   points += hitValue + hitValue * (comboBeforeHit * difficultyMultiplier * modMultiplier) / 25
 * where hitValue is 300/100/50, comboBeforeHit is the combo count before this hit,
 * and modMultiplier is 1 (no mods). A miss adds nothing and resets combo.
 *
 * Accuracy and grades follow the osu!standard definitions.
 */
class Scoreboard(
    private val difficulty: Difficulty,
    private val modMultiplier: Double = 1.0
) {

    var score: Long = 0L
        private set
    var combo: Int = 0
        private set
    var maxCombo: Int = 0
        private set

    private var perfect = 0
    private var good = 0
    private var meh = 0
    private var miss = 0

    /** Classify how far (ms) a tap landed from the note's target time. */
    fun judge(deltaMs: Long): Judgement {
        val d = abs(deltaMs)
        return when {
            d <= difficulty.windowPerfectMs -> Judgement.PERFECT
            d <= difficulty.windowGoodMs -> Judgement.GOOD
            d <= difficulty.windowMehMs -> Judgement.MEH
            else -> Judgement.MISS
        }
    }

    /** Apply a judgement, updating combo and score (osu! ScoreV1). */
    fun apply(j: Judgement) {
        when (j) {
            Judgement.MISS -> {
                miss++
                combo = 0
            }
            else -> {
                when (j) {
                    Judgement.PERFECT -> perfect++
                    Judgement.GOOD -> good++
                    Judgement.MEH -> meh++
                    else -> {}
                }
                val comboBeforeHit = combo
                combo++
                if (combo > maxCombo) maxCombo = combo
                val bonus = j.points.toDouble() *
                    (comboBeforeHit * difficulty.scoreDifficultyMultiplier * modMultiplier) / 25.0
                score += j.points + bonus.toLong()
            }
        }
    }

    /** Accuracy in 0..1, weighted by judgement value (osu!standard formula). */
    fun accuracy(): Double {
        val judged = perfect + good + meh + miss
        if (judged == 0) return 1.0
        val achieved = perfect * 300.0 + good * 100.0 + meh * 50.0
        val maxPossible = judged * 300.0
        return achieved / maxPossible
    }

    fun counts(): IntArray = intArrayOf(perfect, good, meh, miss)

    /**
     * Letter grade following osu!standard rules:
     *  - SS: 100% accuracy (all 300s).
     *  - S : >90% 300s, no misses, and at most 1% 50s.
     *  - A : >80% 300s with no misses, or >90% 300s.
     *  - B : >70% 300s with no misses, or >80% 300s.
     *  - C : >60% 300s.
     *  - D : otherwise.
     */
    fun grade(): String {
        val total = perfect + good + meh + miss
        if (total == 0) return "D"
        val ratio300 = perfect.toDouble() / total
        val ratio50 = meh.toDouble() / total
        return when {
            perfect == total -> "SS"
            ratio300 > 0.90 && miss == 0 && ratio50 <= 0.01 -> "S"
            (ratio300 > 0.80 && miss == 0) || ratio300 > 0.90 -> "A"
            (ratio300 > 0.70 && miss == 0) || ratio300 > 0.80 -> "B"
            ratio300 > 0.60 -> "C"
            else -> "D"
        }
    }
}
