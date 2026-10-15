package com.overdrive.player.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.overdrive.player.R
import com.overdrive.player.data.Album
import com.overdrive.player.databinding.ItemAlbumBinding
import com.overdrive.player.util.ArtworkLoader

class AlbumAdapter(
    private val onClick: (Album) -> Unit
) : ListAdapter<Album, AlbumAdapter.AlbumViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlbumViewHolder(
        private val binding: ItemAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onClick(getItem(position))
            }
        }

        fun bind(album: Album) {
            val context = binding.root.context
            binding.albumName.text = album.title.ifBlank { context.getString(R.string.unknown_album) }
            binding.albumSubtitle.text = buildString {
                append(album.artist.ifBlank { context.getString(R.string.unknown_artist) })
                if (album.year > 0) append(" · ").append(album.year)
            }
            ArtworkLoader.load(
                context,
                binding.albumArt,
                album.albumArtUri,
                null,
                400,
                placeholder = R.drawable.placeholder_art
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Album>() {
            override fun areItemsTheSame(oldItem: Album, newItem: Album) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: Album, newItem: Album) = oldItem == newItem
        }
    }
}
