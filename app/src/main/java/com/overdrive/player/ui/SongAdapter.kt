package com.overdrive.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.overdrive.player.data.Song
import com.overdrive.player.databinding.ItemSongBinding

class SongAdapter(
    private val onPlay: (List<Song>, Int) -> Unit,
    private val onMenu: (Song, View) -> Unit,
    /** Inside an album: show the track number instead of the artwork. */
    private val showTrackNumbers: Boolean = false
) : ListAdapter<Song, SongAdapter.SongViewHolder>(DIFF) {

    private var playingId: Long = -1L

    /** Highlights the row of the song that is currently loaded in the player. */
    fun setPlayingId(id: Long) {
        if (id == playingId) return
        val old = playingId
        playingId = id
        currentList.forEachIndexed { index, song ->
            if (song.id == old || song.id == id) notifyItemChanged(index, PAYLOAD_PLAYING)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PLAYING)) {
            holder.bindPlaying(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class SongViewHolder(
        private val binding: ItemSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onPlay(currentList, position)
            }
            binding.songMenu.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onMenu(getItem(position), it)
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onMenu(getItem(position), binding.songMenu)
                true
            }
        }

        fun bind(song: Song) =
            SongRows.bind(binding, song, song.id == playingId, showTrackNumbers)

        fun bindPlaying(song: Song) =
            SongRows.bindPlaying(binding, song.id == playingId, showTrackNumbers)
    }

    companion object {
        private const val PAYLOAD_PLAYING = "playing"

        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
        }
    }
}
