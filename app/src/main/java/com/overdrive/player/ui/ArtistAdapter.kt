package com.overdrive.player.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.overdrive.player.R
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

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onClick(getItem(position))
            }
        }

        fun bind(artist: Artist) {
            val context = binding.root.context
            val res = context.resources
            binding.artistName.text = artist.name.ifBlank { context.getString(R.string.unknown_artist) }
            binding.artistSubtitle.text = context.getString(
                R.string.song_count_label,
                res.getQuantityString(R.plurals.albums, artist.albumCount, artist.albumCount),
                res.getQuantityString(R.plurals.songs, artist.songCount, artist.songCount)
            )
            ArtworkLoader.load(
                context,
                binding.artistArt,
                artist.albumArtUri,
                null,
                200,
                placeholder = R.drawable.placeholder_art,
                circle = true
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Artist>() {
            override fun areItemsTheSame(oldItem: Artist, newItem: Artist) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: Artist, newItem: Artist) = oldItem == newItem
        }
    }
}
