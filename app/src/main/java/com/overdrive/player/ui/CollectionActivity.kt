package com.overdrive.player.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.data.MusicRepository
import com.overdrive.player.data.Song
import com.overdrive.player.databinding.ActivityCollectionBinding
import com.overdrive.player.player.Playback
import com.overdrive.player.util.ThemeHelper
import kotlinx.coroutines.launch

/** Shows every song that belongs to one album or one artist. */
class CollectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionBinding
    private lateinit var adapter: SongAdapter

    private val repository by lazy { MusicRepository(this) }
    private val prefs by lazy { OverdriveApp.from(this).prefs }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_ALBUM
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.toolbar.title = title

        adapter = SongAdapter(
            onPlay = { list, index ->
                Playback.start(this, list, index, prefs.shuffleDefault, prefs.repeatMode)
            },
            onMenu = { song, anchor -> showMenu(song, anchor) }
        )
        binding.list.recycler.layoutManager = LinearLayoutManager(this)
        binding.list.recycler.adapter = adapter
        binding.list.emptyText.text = getString(R.string.empty_songs)
        binding.list.emptyAction.visibility = View.GONE
        binding.list.loading.visibility = View.VISIBLE

        lifecycleScope.launch {
            val all = repository.loadSongs(prefs.minDurationSec * 1000L)
            val songs = when (type) {
                TYPE_ARTIST -> if (id != 0L) all.filter { it.artistId == id }
                else all.filter { it.artist.equals(title, ignoreCase = true) }

                else -> if (id != 0L) all.filter { it.albumId == id }
                else all.filter { it.album.equals(title, ignoreCase = true) }
            }
            binding.list.loading.visibility = View.GONE
            binding.list.emptyView.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(songs)
        }
    }

    private fun showMenu(song: Song, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.inflate(R.menu.menu_song)
        popup.menu.findItem(R.id.action_favorite).title =
            if (prefs.isFavorite(song.id)) getString(R.string.remove_favorite)
            else getString(R.string.add_favorite)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_play -> {
                    Playback.start(this, listOf(song), 0, false, prefs.repeatMode); true
                }

                R.id.action_play_next -> {
                    Playback.playNext(this, song); true
                }

                R.id.action_add_queue -> {
                    Playback.enqueue(this, song); true
                }

                R.id.action_favorite -> {
                    prefs.setFavorite(song.id, !prefs.isFavorite(song.id)); true
                }

                else -> false
            }
        }
        popup.show()
    }

    companion object {
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val TYPE_ALBUM = "album"
        const val TYPE_ARTIST = "artist"
    }
}
