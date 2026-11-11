package com.osutiles.game.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.osutiles.game.backend.MapSourceRegistry
import com.osutiles.game.databinding.ActivityDifficultyBinding
import com.osutiles.game.game.GameActivity
import com.osutiles.game.model.Difficulty

/** Lets the player choose an osu!-style difficulty for the selected song. */
class DifficultyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDifficultyBinding
    private var songId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDifficultyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        songId = intent.getStringExtra(EXTRA_SONG_ID)
        val song = songId?.let { MapSourceRegistry.repository(this).getSong(it) }
        binding.songTitle.text = song?.title ?: "Song"
        binding.songArtist.text = song?.artist ?: ""

        bind(binding.easyButton, Difficulty.EASY)
        bind(binding.normalButton, Difficulty.NORMAL)
        bind(binding.hardButton, Difficulty.HARD)
        bind(binding.insaneButton, Difficulty.INSANE)
    }

    private fun bind(button: Button, difficulty: Difficulty) {
        button.text = difficulty.displayName
        button.setOnClickListener { play(difficulty) }
    }

    private fun play(difficulty: Difficulty) {
        val id = songId ?: return
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_SONG_ID, id)
                .putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name)
        )
    }

    companion object {
        const val EXTRA_SONG_ID = "song_id"
    }
}
