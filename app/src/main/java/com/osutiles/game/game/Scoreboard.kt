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
 * Tracks score, combo and accuracy during play. Pure Kotlin / JVM-testable.
 *
 * Scoring mirrors osu!'s spirit: base points per judgement, a combo multiplier
 * that rewards unbroken chains, and accuracy as the weighted ratio of achieved
 * to maximum points.
 */
class Scoreboard(private val difficulty: Difficulty) {

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

    /** Apply a judgement, updating combo and score. */
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
                combo++
                if (combo > maxCombo) maxCombo = combo
                // Base points scaled by a gentle combo multiplier (osu!-like).
                val multiplier = 1.0 + (combo - 1) * 0.02
                score += (j.points * multiplier).toLong()
            }
        }
    }

    /** Accuracy in 0..1, weighted by judgement value (osu! formula). */
    fun accuracy(): Double {
        val judged = perfect + good + meh + miss
        if (judged == 0) return 1.0
        val achieved = perfect * 300.0 + good * 100.0 + meh * 50.0
        val maxPossible = judged * 300.0
        return achieved / maxPossible
    }

    fun counts(): IntArray = intArrayOf(perfect, good, meh, miss)

    /** Letter grade from accuracy, loosely following osu!'s grade bands. */
    fun grade(): String {
        val acc = accuracy()
        return when {
            miss == 0 && acc >= 0.99 -> "SS"
            acc >= 0.95 -> "S"
            acc >= 0.90 -> "A"
            acc >= 0.80 -> "B"
            acc >= 0.70 -> "C"
            else -> "D"
        }
    }
}
