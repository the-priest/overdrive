package com.overdrive.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.overdrive.player.R
import com.overdrive.player.databinding.FragmentSongListBinding

class AlbumsFragment : Fragment() {

    private var _binding: FragmentSongListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: AlbumAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AlbumAdapter { album ->
            SongMenu.openAlbum(requireContext(), album.key, album.title, album.id)
        }
        // Two columns on phones, more on wide screens / landscape.
        val columns = (resources.configuration.screenWidthDp / 180).coerceIn(2, 6)
        binding.recycler.layoutManager = GridLayoutManager(requireContext(), columns)
        binding.recycler.setPadding(6, binding.recycler.paddingTop, 6, binding.recycler.paddingBottom)
        binding.recycler.adapter = adapter
        binding.emptyText.setText(R.string.empty_albums)
        binding.emptyIcon.setImageResource(R.drawable.ic_album)
        binding.emptyAction.visibility = View.GONE

        viewModel.visibleAlbums.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility =
                if (list.isEmpty() && viewModel.loading.value != true) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
