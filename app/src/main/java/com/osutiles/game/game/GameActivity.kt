package com.osutiles.game.game

import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.osutiles.game.audio.PcmDecoder
import com.osutiles.game.beatmap.BeatmapGenerator
import com.osutiles.game.beatmap.OnsetDetector
import com.osutiles.game.model.Beatmap
import com.osutiles.game.model.Difficulty
import com.osutiles.game.model.Song
import com.osutiles.game.backend.MapSourceRegistry
import com.osutiles.game.ui.ResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameActivity : ComponentActivity(), GameView.Listener {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var gameView: GameView
    private lateinit var loadingText: TextView
    private lateinit var container: FrameLayout

    private var difficulty: Difficulty = Difficulty.NORMAL
    private var songTitle: String = ""

    // Smoothed song clock.
    private var anchorPositionMs = 0L
    private var anchorNanos = 0L
    private var playing = false
    private var pausedBySystem = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureHighRefreshRate()

        container = FrameLayout(this)
        gameView = GameView(this)
        gameView.setTargetFps(highestRefreshRateHz())
        loadingText = TextView(this).apply {
            text = "Analyzing song and building your map…"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(15, 17, 21))
        }
        container.addView(
            gameView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(
            loadingText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(container)

        val songId = intent.getStringExtra(EXTRA_SONG_ID)
        difficulty = Difficulty.fromName(intent.getStringExtra(EXTRA_DIFFICULTY))

        val song = songId?.let { MapSourceRegistry.repository(this).getSong(it) }
        if (song == null) {
            finish()
            return
        }
        songTitle = song.title
        prepare(song)
    }

    private fun prepare(song: Song) {
        val uri = Uri.parse(song.uri)
        lifecycleScope.launch {
            val durationMs = probeDuration(uri)
            val beatmap = withContext(Dispatchers.Default) {
                buildBeatmap(song.id, uri, durationMs)
            }
            startPlayback(uri, beatmap)
        }
    }

    private suspend fun probeDuration(uri: Uri): Long = withContext(Dispatchers.IO) {
        val probe = MediaPlayer()
        try {
            probe.setDataSource(this@GameActivity, uri)
            probe.prepare()
            probe.duration.toLong()
        } catch (_: Throwable) {
            0L
        } finally {
            probe.release()
        }
    }

    private fun buildBeatmap(songId: String, uri: Uri, durationMs: Long): Beatmap {
        val decoded = PcmDecoder.decode(this, uri)
        val onsets = if (decoded != null) {
            OnsetDetector.detect(decoded.samples, decoded.sampleRate)
        } else {
            emptyList()
        }
        // If the duration probe failed, fall back to the decoded audio length.
        val effectiveDurationMs = if (durationMs > 0 || decoded == null) {
            durationMs
        } else {
            decoded.samples.size * 1000L / decoded.sampleRate
        }
        return BeatmapGenerator.generate(songId, difficulty, effectiveDurationMs, onsets)
    }

    private fun startPlayback(uri: Uri, beatmap: Beatmap) {
        val mp = MediaPlayer()
        try {
            mp.setDataSource(this, uri)
            mp.prepare()
        } catch (_: Throwable) {
            finish()
            return
        }
        mediaPlayer = mp
        gameView.setup(beatmap, this)
        loadingText.visibility = android.view.View.GONE

        anchorPositionMs = 0L
        anchorNanos = System.nanoTime()
        mp.setOnCompletionListener { playing = false }
        mp.start()
        playing = true
    }

    // --- GameView.Listener ---

    override fun currentSongTimeMs(): Long {
        val mp = mediaPlayer ?: return 0L
        return try {
            if (playing && mp.isPlaying) {
                val pos = mp.currentPosition.toLong()
                if (pos != anchorPositionMs) {
                    anchorPositionMs = pos
                    anchorNanos = System.nanoTime()
                    pos
                } else {
                    // Interpolate between coarse position updates for smoothness.
                    val elapsed = (System.nanoTime() - anchorNanos) / 1_000_000L
                    anchorPositionMs + elapsed
                }
            } else {
                anchorPositionMs
            }
        } catch (_: Throwable) {
            anchorPositionMs
        }
    }

    override fun onFinished(scoreboard: Scoreboard) {
        runOnUiThread {
            val counts = scoreboard.counts()
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_TITLE, songTitle)
                putExtra(ResultActivity.EXTRA_DIFFICULTY, difficulty.displayName)
                putExtra(ResultActivity.EXTRA_SCORE, scoreboard.score)
                putExtra(ResultActivity.EXTRA_MAX_COMBO, scoreboard.maxCombo)
                putExtra(ResultActivity.EXTRA_ACCURACY, scoreboard.accuracy())
                putExtra(ResultActivity.EXTRA_GRADE, scoreboard.grade())
                putExtra(ResultActivity.EXTRA_PERFECT, counts[0])
                putExtra(ResultActivity.EXTRA_GOOD, counts[1])
                putExtra(ResultActivity.EXTRA_MEH, counts[2])
                putExtra(ResultActivity.EXTRA_MISS, counts[3])
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        val mp = mediaPlayer
        if (mp != null && playing) {
            try {
                // Freeze the clock exactly where playback stops.
                anchorPositionMs = mp.currentPosition.toLong()
                mp.pause()
                pausedBySystem = true
            } catch (_: Throwable) {}
        }
        playing = false
    }

    override fun onResume() {
        super.onResume()
        if (!pausedBySystem) return
        pausedBySystem = false
        val mp = mediaPlayer ?: return
        try {
            // Re-anchor so the interpolated clock continues from the pause point.
            anchorNanos = System.nanoTime()
            mp.start()
            playing = true
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.stop()
        try { mediaPlayer?.release() } catch (_: Throwable) {}
        mediaPlayer = null
    }

    companion object {
        const val EXTRA_SONG_ID = "song_id"
        const val EXTRA_DIFFICULTY = "difficulty"

        /** We pace gameplay up to this ceiling even on faster panels. */
        const val MAX_FPS = 144
    }

    /** Pick this device's highest supported refresh rate (capped at [MAX_FPS]). */
    private fun highestRefreshRateHz(): Int {
        val disp = activeDisplay() ?: return 60
        val best = bestMode(disp)?.refreshRate ?: disp.refreshRate
        return best.toInt().coerceIn(60, MAX_FPS)
    }

    /** Ask the OS to drive the panel at its highest refresh mode while we play. */
    private fun configureHighRefreshRate() {
        val disp = activeDisplay() ?: return
        val best = bestMode(disp) ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = best.modeId
            preferredRefreshRate = best.refreshRate
        }
    }

    /**
     * Highest-refresh mode at the panel's current resolution. Some devices only
     * expose their top refresh rate at a lower resolution; switching to it would
     * shrink the playfield, so those modes are avoided unless nothing else fits.
     */
    private fun bestMode(disp: android.view.Display): android.view.Display.Mode? {
        val current = disp.mode
        val sameResolution = disp.supportedModes.filter {
            it.physicalWidth == current.physicalWidth &&
                it.physicalHeight == current.physicalHeight
        }
        val candidates = if (sameResolution.isNotEmpty()) sameResolution
        else disp.supportedModes.toList()
        return candidates.maxByOrNull { it.refreshRate }
    }

    private fun activeDisplay(): android.view.Display? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
    }
}
