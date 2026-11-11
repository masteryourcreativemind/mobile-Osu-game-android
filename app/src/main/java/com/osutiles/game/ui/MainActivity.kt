package com.osutiles.game.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.osutiles.game.backend.MapSourceRegistry
import com.osutiles.game.databinding.ActivityMainBinding
import com.osutiles.game.model.Song
import com.osutiles.game.repository.SongImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Song library: pick a song to play or import a new one from the device. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SongAdapter

    private val pickAudio = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importSong(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SongAdapter(
            onPlay = { song -> openDifficulty(song) },
            onDelete = { song -> deleteSong(song) }
        )
        binding.songList.layoutManager = LinearLayoutManager(this)
        binding.songList.adapter = adapter

        // No restriction on type: accept any file the picker can return.
        binding.importButton.setOnClickListener { pickAudio.launch(arrayOf("*/*")) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val songs = MapSourceRegistry.repository(this).getSongs()
        adapter.submit(songs)
        binding.emptyHint.visibility =
            if (songs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun importSong(uri: android.net.Uri) {
        Toast.makeText(this, "Importing…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val song = withContext(Dispatchers.IO) { SongImporter.import(this@MainActivity, uri) }
            if (song != null) {
                MapSourceRegistry.repository(this@MainActivity).addSong(song)
                refresh()
                Toast.makeText(this@MainActivity, "Added ${song.title}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Could not import that file", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteSong(song: Song) {
        MapSourceRegistry.repository(this).removeSong(song.id)
        refresh()
    }

    private fun openDifficulty(song: Song) {
        startActivity(
            Intent(this, DifficultyActivity::class.java)
                .putExtra(DifficultyActivity.EXTRA_SONG_ID, song.id)
        )
    }
}
