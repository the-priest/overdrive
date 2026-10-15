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

class FavoritesFragment : Fragment() {

    private var _binding: FragmentSongListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: SongAdapter

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
        binding.emptyText.setText(R.string.empty_favorites)
        binding.emptyAction.visibility = View.GONE

        viewModel.favorites.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            updateEmptyState(list)
        }
    }

    private fun updateEmptyState(list: List<Song>) {
        binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
