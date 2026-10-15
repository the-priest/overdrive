package com.overdrive.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.overdrive.player.data.Song
import com.overdrive.player.databinding.ItemSongBinding
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.formatDuration

class SongAdapter(
    private val onPlay: (List<Song>, Int) -> Unit,
    private val onMenu: (Song, View) -> Unit
) : ListAdapter<Song, SongAdapter.SongViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SongViewHolder(
        private val binding: ItemSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.songTitle.text = song.title
            binding.songSubtitle.text = buildString {
                append(song.artist.ifBlank { "Unknown artist" })
                if (song.album.isNotBlank()) {
                    append(" • ")
                    append(song.album)
                }
            }
            binding.songDuration.text = formatDuration(song.duration)

            ArtworkLoader.load(
                binding.root.context,
                binding.songArt,
                com.overdrive.player.data.MusicRepository.albumArtUri(song.albumId),
                song.uri,
                160
            )

            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onPlay(currentList, position)
            }

            binding.songMenu.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onMenu(getItem(position), it)
            }

            binding.root.setOnLongClickListener {
                onMenu(song, it)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
        }
    }
}
