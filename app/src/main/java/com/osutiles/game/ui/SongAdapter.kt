package com.osutiles.game.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.osutiles.game.databinding.ItemSongBinding
import com.osutiles.game.model.Song
import com.osutiles.game.model.SongSource

/** Simple list adapter for the song library. */
class SongAdapter(
    private val onPlay: (Song) -> Unit,
    private val onDelete: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private val items = mutableListOf<Song>()

    fun submit(songs: List<Song>) {
        items.clear()
        items.addAll(songs)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.title.text = song.title
            binding.artist.text = song.artist
            binding.source.text = when (song.source) {
                SongSource.BUILT_IN -> "Built-in"
                SongSource.IMPORTED -> "Imported"
                SongSource.REMOTE -> "Community"
            }
            binding.root.setOnClickListener { onPlay(song) }
            // Only user-imported songs can be deleted.
            binding.deleteButton.visibility =
                if (song.source == SongSource.IMPORTED) android.view.View.VISIBLE
                else android.view.View.GONE
            binding.deleteButton.setOnClickListener { onDelete(song) }
        }
    }
}
