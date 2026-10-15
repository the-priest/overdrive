package com.overdrive.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.overdrive.player.R
import com.overdrive.player.data.Song
import com.overdrive.player.databinding.FragmentSongListBinding

class SongsFragment : Fragment() {

    private var _binding: FragmentSongListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: SongAdapter

    /** Mirrors what was last handed to the adapter; [adapter.currentList] lags
     *  behind because ListAdapter diffs on a background thread. */
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
        val activity = requireActivity() as MainActivity
        adapter = SongAdapter(
            onPlay = { list, index -> activity.playList(list, index) },
            onMenu = { song, anchor -> activity.showSongMenu(song, anchor) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.emptyText.setText(R.string.empty_songs)
        binding.emptyAction.setText(R.string.rescan)
        binding.emptyAction.setOnClickListener { activity.onGrantOrRescan() }

        viewModel.filteredSongs.observe(viewLifecycleOwner) { list ->
            lastSongs = list
            adapter.submitList(list)
            updateEmptyState()
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.loading.visibility = if (loading) View.VISIBLE else View.GONE
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        val show = lastSongs.isEmpty() && viewModel.loading.value != true
        binding.emptyView.visibility = if (show) View.VISIBLE else View.GONE
        binding.emptyAction.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
