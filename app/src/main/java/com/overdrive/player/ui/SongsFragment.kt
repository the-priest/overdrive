package com.overdrive.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.overdrive.player.R
import com.overdrive.player.data.Song
import com.overdrive.player.data.SongSort
import com.overdrive.player.databinding.FragmentSongListBinding
import com.overdrive.player.player.Playback

class SongsFragment : Fragment() {

    private var _binding: FragmentSongListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: SongAdapter

    /** Mirrors what was last handed to the adapter; [SongAdapter.getCurrentList]
     *  lags behind because ListAdapter diffs on a background thread. */
    private var lastSongs: List<Song> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SongAdapter(
            onPlay = { list, index -> Playback.start(requireContext(), list, index) },
            onMenu = { song, anchor -> SongMenu.show(requireActivity(), song, anchor) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.headerRow.visibility = View.VISIBLE
        binding.shuffleButton.setOnClickListener {
            Playback.shuffle(requireContext(), lastSongs)
        }
        binding.sortChip.setOnClickListener { showSortMenu(it) }
        binding.emptyAction.setOnClickListener {
            (activity as? MainActivity)?.onGrantOrRescan()
        }

        viewModel.visibleSongs.observe(viewLifecycleOwner) { list ->
            lastSongs = list
            adapter.submitList(list)
            binding.countText.text =
                resources.getQuantityString(R.plurals.songs, list.size, list.size)
            binding.shuffleButton.isEnabled = list.isNotEmpty()
            updateEmptyState()
        }
        viewModel.sort.observe(viewLifecycleOwner) { sort ->
            binding.sortChip.text = getString(sortLabel(sort))
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.loading.visibility = if (loading) View.VISIBLE else View.GONE
            updateEmptyState()
        }
        viewModel.hasPermission.observe(viewLifecycleOwner) { updateEmptyState() }
        viewModel.nowPlayingId.observe(viewLifecycleOwner) { adapter.setPlayingId(it) }
    }

    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.inflate(R.menu.menu_sort)
        val current = viewModel.sort.value ?: SongSort.TITLE
        popup.menu.findItem(menuId(current))?.isChecked = true
        popup.setOnMenuItemClickListener { item ->
            val sort = SongSort.entries.firstOrNull { menuId(it) == item.itemId }
                ?: return@setOnMenuItemClickListener false
            viewModel.setSort(sort)
            binding.recycler.scrollToPosition(0)
            true
        }
        popup.show()
    }

    private fun updateEmptyState() {
        val b = _binding ?: return
        val permission = viewModel.hasPermission.value
        val show = lastSongs.isEmpty() && viewModel.loading.value != true && permission != null
        b.emptyView.visibility = if (show) View.VISIBLE else View.GONE
        when {
            permission == false -> {
                b.emptyText.setText(R.string.permission_needed)
                b.emptyAction.setText(R.string.grant_access)
                b.emptyAction.visibility = View.VISIBLE
            }
            viewModel.isSearching -> {
                b.emptyText.setText(R.string.empty_search)
                b.emptyAction.visibility = View.GONE
            }
            else -> {
                b.emptyText.setText(R.string.empty_songs)
                b.emptyAction.setText(R.string.rescan)
                b.emptyAction.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun sortLabel(sort: SongSort): Int = when (sort) {
            SongSort.TITLE -> R.string.sort_title
            SongSort.ARTIST -> R.string.sort_artist
            SongSort.ALBUM -> R.string.sort_album
            SongSort.RECENTLY_ADDED -> R.string.sort_recent
            SongSort.DURATION -> R.string.sort_duration
        }

        private fun menuId(sort: SongSort): Int = when (sort) {
            SongSort.TITLE -> R.id.sort_title
            SongSort.ARTIST -> R.id.sort_artist
            SongSort.ALBUM -> R.id.sort_album
            SongSort.RECENTLY_ADDED -> R.id.sort_recent
            SongSort.DURATION -> R.id.sort_duration
        }
    }
}
