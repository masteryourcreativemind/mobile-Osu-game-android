package com.osutiles.game.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.osutiles.game.databinding.ActivityResultBinding

/** Post-play summary screen: grade, score, accuracy and judgement breakdown. */
class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.songTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: ""
        binding.difficulty.text = intent.getStringExtra(EXTRA_DIFFICULTY) ?: ""
        binding.grade.text = intent.getStringExtra(EXTRA_GRADE) ?: "D"
        binding.score.text = "Score: ${intent.getLongExtra(EXTRA_SCORE, 0)}"
        binding.maxCombo.text = "Max combo: ${intent.getIntExtra(EXTRA_MAX_COMBO, 0)}x"
        val acc = intent.getDoubleExtra(EXTRA_ACCURACY, 0.0) * 100.0
        binding.accuracy.text = String.format("Accuracy: %.2f%%", acc)
        binding.breakdown.text = buildString {
            append("300: ").append(intent.getIntExtra(EXTRA_PERFECT, 0)).append("\n")
            append("100: ").append(intent.getIntExtra(EXTRA_GOOD, 0)).append("\n")
            append("50: ").append(intent.getIntExtra(EXTRA_MEH, 0)).append("\n")
            append("Miss: ").append(intent.getIntExtra(EXTRA_MISS, 0))
        }

        binding.doneButton.setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_SCORE = "score"
        const val EXTRA_MAX_COMBO = "max_combo"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_GRADE = "grade"
        const val EXTRA_PERFECT = "perfect"
        const val EXTRA_GOOD = "good"
        const val EXTRA_MEH = "meh"
        const val EXTRA_MISS = "miss"
    }
}
