package com.overdrive.player.ui

import android.view.View
import androidx.core.content.ContextCompat
import com.overdrive.player.R
import com.overdrive.player.data.Song
import com.overdrive.player.databinding.ItemSongBinding
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.ThemeHelper
import com.overdrive.player.util.formatDuration

/** Renders one song row; shared by every song list in the app. */
object SongRows {

    fun bind(binding: ItemSongBinding, song: Song, playing: Boolean, showTrackNumbers: Boolean) {
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
            binding.trackNumber.text = if (song.track > 0) (song.track % 1000).toString() else "–"
        } else {
            binding.songArt.visibility = View.VISIBLE
            binding.trackNumber.visibility = View.GONE
            ArtworkLoader.load(context, binding.songArt, song.albumArtUri, song.uri, 160)
        }
        bindPlaying(binding, playing, showTrackNumbers)
    }

    fun bindPlaying(binding: ItemSongBinding, playing: Boolean, showTrackNumbers: Boolean) {
        val context = binding.root.context
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
