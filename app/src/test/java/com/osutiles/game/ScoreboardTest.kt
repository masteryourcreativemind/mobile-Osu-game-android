package com.osutiles.game

import com.osutiles.game.game.Judgement
import com.osutiles.game.game.Scoreboard
import com.osutiles.game.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardTest {

    @Test
    fun judgesByTimingWindows() {
        val sb = Scoreboard(Difficulty.NORMAL)
        assertEquals(Judgement.PERFECT, sb.judge(0))
        assertEquals(Judgement.PERFECT, sb.judge(-Difficulty.NORMAL.windowPerfectMs))
        assertEquals(Judgement.GOOD, sb.judge(Difficulty.NORMAL.windowPerfectMs + 1))
        assertEquals(Judgement.MEH, sb.judge(Difficulty.NORMAL.windowGoodMs + 1))
        assertEquals(Judgement.MISS, sb.judge(Difficulty.NORMAL.windowMehMs + 1))
    }

    @Test
    fun missResetsComboAndTracksMax() {
        val sb = Scoreboard(Difficulty.NORMAL)
        sb.apply(Judgement.PERFECT)
        sb.apply(Judgement.GOOD)
        assertEquals(2, sb.combo)
        sb.apply(Judgement.MISS)
        assertEquals(0, sb.combo)
        assertEquals(2, sb.maxCombo)
    }

    @Test
    fun accuracyIsWeighted() {
        val allPerfect = Scoreboard(Difficulty.HARD).apply { repeat(5) { apply(Judgement.PERFECT) } }
        assertEquals(1.0, allPerfect.accuracy(), 1e-9)

        val mixed = Scoreboard(Difficulty.HARD).apply {
            apply(Judgement.PERFECT) // 300
            apply(Judgement.MISS)    // 0 of 300
        }
        assertEquals(0.5, mixed.accuracy(), 1e-9)
    }

    @Test
    fun comboIncreasesScore() {
        val a = Scoreboard(Difficulty.NORMAL).apply { apply(Judgement.PERFECT) }.score
        val b = Scoreboard(Difficulty.NORMAL).apply {
            repeat(50) { apply(Judgement.PERFECT) }
        }.score
        // With the combo multiplier, 50 perfects should exceed 50x a single perfect's base.
        assertTrue(b > a * 50)
    }

    @Test
    fun gradeReflectsAccuracy() {
        val ss = Scoreboard(Difficulty.NORMAL).apply { repeat(10) { apply(Judgement.PERFECT) } }
        assertEquals("SS", ss.grade())
    }
}
