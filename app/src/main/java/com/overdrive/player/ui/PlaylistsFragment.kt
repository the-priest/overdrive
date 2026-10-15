package com.overdrive.player.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.data.PlaylistSummary
import com.overdrive.player.databinding.FragmentSongListBinding
import com.overdrive.player.databinding.ItemPlaylistBinding
import com.overdrive.player.player.Playback
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.formatTotalDuration

class PlaylistsFragment : Fragment() {

    private var _binding: FragmentSongListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private val store by lazy { OverdriveApp.from(requireContext()).playlists }
    private lateinit var adapter: PlaylistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PlaylistAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.headerRow.visibility = View.VISIBLE
        binding.sortChip.visibility = View.GONE
        binding.shuffleButton.setText(R.string.playlist_new_short)
        binding.shuffleButton.setIconResource(R.drawable.ic_playlist_add)
        binding.shuffleButton.setOnClickListener { createPlaylist() }

        binding.emptyText.setText(R.string.empty_playlists)
        binding.emptyIcon.setImageResource(R.drawable.ic_queue_music)
        binding.emptyAction.setText(R.string.playlist_new)
        binding.emptyAction.setOnClickListener { createPlaylist() }

        viewModel.playlists.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.countText.text =
                resources.getQuantityString(R.plurals.playlists, list.size, list.size)
            val empty = list.isEmpty()
            binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
            binding.emptyAction.visibility = if (empty) View.VISIBLE else View.GONE
        }
    }

    private fun createPlaylist() {
        val count = viewModel.playlists.value?.size ?: 0
        PlaylistDialogs.promptName(
            requireContext(),
            R.string.playlist_new,
            getString(R.string.playlist_default_name, count + 1)
        ) { name ->
            val playlist = store.create(name)
            open(playlist.id)
        }
    }

    private fun open(id: Long) {
        startActivity(
            Intent(requireContext(), PlaylistActivity::class.java)
                .putExtra(PlaylistActivity.EXTRA_PLAYLIST_ID, id)
        )
    }

    private fun showMenu(summary: PlaylistSummary, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.inflate(R.menu.menu_playlist_item)
        val hasSongs = summary.songs.isNotEmpty()
        listOf(R.id.action_play, R.id.action_shuffle, R.id.action_play_next, R.id.action_add_queue)
            .forEach { popup.menu.findItem(it).isEnabled = hasSongs }
        popup.setOnMenuItemClickListener { item ->
            val context = requireContext()
            when (item.itemId) {
                R.id.action_play -> Playback.start(context, summary.songs, 0, shuffle = false)
                R.id.action_shuffle -> Playback.shuffle(context, summary.songs)
                R.id.action_play_next -> Playback.playNext(context, summary.songs)
                R.id.action_add_queue -> Playback.enqueue(context, summary.songs)
                R.id.action_rename -> PlaylistDialogs.promptName(
                    context, R.string.playlist_rename, summary.playlist.name, R.string.playlist_save
                ) { store.rename(summary.playlist.id, it) }
                R.id.action_delete -> PlaylistDialogs.confirmDelete(context, summary.playlist.name) {
                    store.delete(summary.playlist.id)
                }
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class PlaylistAdapter :
        ListAdapter<PlaylistSummary, PlaylistAdapter.Holder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

        inner class Holder(private val b: ItemPlaylistBinding) : RecyclerView.ViewHolder(b.root) {

            init {
                b.root.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) open(getItem(position).playlist.id)
                }
                b.playlistMenu.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) showMenu(getItem(position), it)
                }
            }

            fun bind(summary: PlaylistSummary) {
                val context = b.root.context
                b.playlistName.text = summary.playlist.name
                val count = summary.songs.size
                b.playlistSubtitle.text = buildString {
                    append(context.resources.getQuantityString(R.plurals.songs, count, count))
                    if (summary.duration > 0) append(" · ").append(formatTotalDuration(summary.duration))
                }
                val cover = summary.songs.firstOrNull { it.albumId != 0L } ?: summary.songs.firstOrNull()
                if (cover != null) {
                    ArtworkLoader.load(context, b.playlistArt, cover.albumArtUri, cover.uri, 200)
                } else {
                    b.playlistArt.tag = null
                    b.playlistArt.setImageResource(R.drawable.placeholder_art)
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PlaylistSummary>() {
            override fun areItemsTheSame(oldItem: PlaylistSummary, newItem: PlaylistSummary) =
                oldItem.playlist.id == newItem.playlist.id

            override fun areContentsTheSame(oldItem: PlaylistSummary, newItem: PlaylistSummary) =
                oldItem == newItem
        }
    }
}
