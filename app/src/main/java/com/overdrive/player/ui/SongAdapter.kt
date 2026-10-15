package com.overdrive.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.overdrive.player.R
import com.overdrive.player.data.Song
import com.overdrive.player.databinding.ItemSongBinding
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.ThemeHelper
import com.overdrive.player.util.formatDuration

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

        fun bind(song: Song) {
            val context = binding.root.context
            binding.songTitle.text = song.title
            binding.songSubtitle.text = buildString {
                append(song.artist.ifBlank { context.getString(R.string.unknown_artist) })
                if (!showTrackNumbers && song.album.isNotBlank()) {
                    append(" • ")
                    append(song.album)
                }
            }
            binding.songDuration.text = formatDuration(song.duration)

            if (showTrackNumbers) {
                binding.songArt.visibility = View.INVISIBLE
                binding.trackNumber.visibility = View.VISIBLE
                binding.trackNumber.text =
                    if (song.track > 0) (song.track % 1000).toString() else "–"
            } else {
                binding.songArt.visibility = View.VISIBLE
                binding.trackNumber.visibility = View.GONE
                ArtworkLoader.load(context, binding.songArt, song.albumArtUri, song.uri, 160)
            }
            bindPlaying(song)
        }

        fun bindPlaying(song: Song) {
            val context = binding.root.context
            val playing = song.id == playingId
            binding.playingIndicator.visibility = if (playing) View.VISIBLE else View.GONE
            binding.songArt.alpha = if (playing) 0.45f else 1f
            binding.trackNumber.alpha = if (playing) 0f else 1f
            binding.songTitle.setTextColor(
                if (playing) ThemeHelper.accentColor(context)
                else ContextCompat.getColor(context, R.color.text_primary)
            )
            if (showTrackNumbers && playing) {
                binding.playingIndicator.setColorFilter(ThemeHelper.accentColor(context))
            } else {
                binding.playingIndicator.clearColorFilter()
            }
        }
    }

    companion object {
        private const val PAYLOAD_PLAYING = "playing"

        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
        }
    }
}
