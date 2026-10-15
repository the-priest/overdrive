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
import com.overdrive.player.player.Playback

class FavoritesFragment : Fragment() {

    private var _binding: FragmentSongListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: SongAdapter
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
        binding.emptyText.setText(R.string.empty_favorites)
        binding.emptyIcon.setImageResource(R.drawable.ic_favorite_border)
        binding.emptyAction.visibility = View.GONE
        binding.sortChip.visibility = View.GONE
        binding.shuffleButton.setOnClickListener {
            Playback.shuffle(requireContext(), lastSongs)
        }

        viewModel.favorites.observe(viewLifecycleOwner) { list ->
            lastSongs = list
            adapter.submitList(list)
            binding.headerRow.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            binding.countText.text =
                resources.getQuantityString(R.plurals.songs, list.size, list.size)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.nowPlayingId.observe(viewLifecycleOwner) { adapter.setPlayingId(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
