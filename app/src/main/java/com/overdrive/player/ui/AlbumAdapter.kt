package com.overdrive.player.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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

        fun bind(album: Album) {
            binding.albumName.text = album.name
            binding.albumSubtitle.text = album.artist
            ArtworkLoader.load(
                binding.root.context,
                binding.albumArt,
                album.albumArtUri,
                null,
                400
            )
            binding.root.setOnClickListener { onClick(album) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Album>() {
            override fun areItemsTheSame(oldItem: Album, newItem: Album) = oldItem.sortKey == newItem.sortKey
            override fun areContentsTheSame(oldItem: Album, newItem: Album) = oldItem == newItem
        }
    }
}
