package com.overdrive.player.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.overdrive.player.data.Artist
import com.overdrive.player.databinding.ItemArtistBinding
import com.overdrive.player.util.ArtworkLoader

class ArtistAdapter(
    private val onClick: (Artist) -> Unit
) : ListAdapter<Artist, ArtistAdapter.ArtistViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val binding = ItemArtistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ArtistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ArtistViewHolder(
        private val binding: ItemArtistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(artist: Artist) {
            binding.artistName.text = artist.name
            binding.artistSubtitle.text = "${artist.songCount} ${if (artist.songCount == 1) "song" else "songs"}"
            ArtworkLoader.load(
                binding.root.context,
                binding.artistArt,
                artist.albumArtUri,
                null,
                200,
                circle = true
            )
            binding.root.setOnClickListener { onClick(artist) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Artist>() {
            override fun areItemsTheSame(oldItem: Artist, newItem: Artist) = oldItem.name == newItem.name
            override fun areContentsTheSame(oldItem: Artist, newItem: Artist) = oldItem == newItem
        }
    }
}
