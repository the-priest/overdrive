package com.overdrive.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.data.MusicRepository
import com.overdrive.player.data.Playlist
import com.overdrive.player.data.PlaylistOps
import com.overdrive.player.data.Song
import com.overdrive.player.databinding.ActivityCollectionBinding
import com.overdrive.player.databinding.ItemCollectionHeaderBinding
import com.overdrive.player.databinding.ItemSongBinding
import com.overdrive.player.player.Playback
import com.overdrive.player.player.PlayerConnection
import com.overdrive.player.player.songId
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.ThemeHelper
import com.overdrive.player.util.applySystemBarPadding
import com.overdrive.player.util.formatTotalDuration
import kotlinx.coroutines.launch

/** One playlist: play it, reorder it (long-press and drag), swipe songs away. */
class PlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionBinding
    private lateinit var headerAdapter: HeaderAdapter
    private lateinit var songAdapter: PlaylistSongAdapter
    private lateinit var touchHelper: ItemTouchHelper

    private val store by lazy { OverdriveApp.from(this).playlists }
    private var playlistId = -1L
    private var playlist: Playlist? = null
    private var library: Map<Long, Song>? = null
    private var dragging = false

    private val songs: List<Song> get() = songAdapter.items

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                songAdapter.setPlayingId(player.currentMediaItem?.songId ?: -1L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding(top = true, bottom = true)

        playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_playlist)
        binding.toolbar.setOnMenuItemClickListener { item ->
            val current = playlist ?: return@setOnMenuItemClickListener false
            when (item.itemId) {
                R.id.action_add_queue -> Playback.enqueue(this, songs)
                R.id.action_rename -> PlaylistDialogs.promptName(
                    this, R.string.playlist_rename, current.name, R.string.playlist_save
                ) { store.rename(current.id, it) }
                R.id.action_delete -> PlaylistDialogs.confirmDelete(this, current.name) {
                    store.delete(current.id)
                }
                else -> return@setOnMenuItemClickListener false
            }
            true
        }

        headerAdapter = HeaderAdapter()
        songAdapter = PlaylistSongAdapter()
        binding.list.recycler.layoutManager = LinearLayoutManager(this)
        binding.list.recycler.adapter = ConcatAdapter(headerAdapter, songAdapter)
        touchHelper = ItemTouchHelper(TouchCallback())
        touchHelper.attachToRecyclerView(binding.list.recycler)
        binding.list.emptyText.setText(R.string.empty_playlist)
        binding.list.emptyIcon.setImageResource(R.drawable.ic_queue_music)
        binding.list.emptyAction.visibility = View.GONE
        binding.list.loading.visibility = View.VISIBLE

        binding.list.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as LinearLayoutManager
                binding.toolbar.title =
                    if (lm.findFirstVisibleItemPosition() > 0) playlist?.name.orEmpty() else ""
            }
        })

        PlayerConnection.bind(this, this, playerListener) {
            songAdapter.setPlayingId(it.currentMediaItem?.songId ?: -1L)
        }

        lifecycleScope.launch {
            val prefs = OverdriveApp.from(this@PlaylistActivity).prefs
            library = MusicRepository(this@PlaylistActivity)
                .loadSongs(prefs.minDurationSec * 1000L)
                .associateBy { it.id }
            binding.list.loading.visibility = View.GONE
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.playlists.collect { render() }
            }
        }
    }

    private fun render() {
        val current = store.get(playlistId)
        if (current == null) {
            // Deleted (here or elsewhere).
            finish()
            return
        }
        playlist = current
        if (dragging) return
        val resolved = PlaylistOps.resolve(current, library.orEmpty()).songs
        songAdapter.submit(resolved)
        headerAdapter.notifyItemChanged(0)
        binding.list.emptyView.visibility = if (resolved.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun removeSong(song: Song) {
        val current = playlist ?: return
        val index = current.songIds.indexOf(song.id)
        store.removeSong(current.id, song.id)
        Snackbar.make(binding.root, R.string.playlist_removed_song, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { store.insertSong(current.id, song.id, index) }
            .show()
    }

    // --- adapters -----------------------------------------------------------

    private inner class HeaderAdapter : RecyclerView.Adapter<HeaderAdapter.Holder>() {

        override fun getItemCount(): Int = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemCollectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind()

        inner class Holder(private val b: ItemCollectionHeaderBinding) : RecyclerView.ViewHolder(b.root) {

            init {
                b.playButton.setOnClickListener {
                    Playback.start(this@PlaylistActivity, songs, 0, shuffle = false)
                }
                b.shuffleButton.setOnClickListener { Playback.shuffle(this@PlaylistActivity, songs) }
            }

            fun bind() {
                val context = b.root.context
                b.headerOverline.setText(R.string.playlist_label)
                b.headerTitle.text = playlist?.name.orEmpty()
                val count = songs.size
                val total = songs.sumOf { it.duration }
                b.headerSubtitle.text = buildString {
                    append(context.resources.getQuantityString(R.plurals.songs, count, count))
                    if (total > 0) append(" · ").append(formatTotalDuration(total))
                    if (count > 1) append("\n").append(context.getString(R.string.playlist_reorder_hint))
                }
                b.playButton.isEnabled = count > 0
                b.shuffleButton.isEnabled = count > 0
                val cover = songs.firstOrNull { it.albumId != 0L } ?: songs.firstOrNull()
                if (cover != null) {
                    ArtworkLoader.load(context, b.headerArt, cover.albumArtUri, cover.uri, 600)
                } else {
                    b.headerArt.tag = null
                    b.headerArt.setImageResource(R.drawable.placeholder_art)
                }
            }
        }
    }

    private inner class PlaylistSongAdapter : RecyclerView.Adapter<PlaylistSongAdapter.Holder>() {

        val items = ArrayList<Song>()
        private var playingId = -1L

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<Song>) {
            if (list == items) return
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun setPlayingId(id: Long) {
            if (id == playingId) return
            val old = playingId
            playingId = id
            items.forEachIndexed { index, song ->
                if (song.id == old || song.id == id) notifyItemChanged(index)
            }
        }

        fun move(from: Int, to: Int) {
            java.util.Collections.swap(items, from, to)
            notifyItemMoved(from, to)
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val song = items[position]
            SongRows.bind(holder.b, song, song.id == playingId, showTrackNumbers = false)
        }

        inner class Holder(val b: ItemSongBinding) : RecyclerView.ViewHolder(b.root) {
            init {
                b.root.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        Playback.start(this@PlaylistActivity, items.toList(), position)
                    }
                }
                b.root.setOnLongClickListener {
                    touchHelper.startDrag(this)
                    true
                }
                b.songMenu.setOnClickListener { anchor ->
                    val song = items.getOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                    SongMenu.show(this@PlaylistActivity, song, anchor) { removeSong(song) }
                }
            }
        }
    }

    private inner class TouchCallback : ItemTouchHelper.Callback() {

        override fun isLongPressDragEnabled(): Boolean = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int =
            if (viewHolder.bindingAdapter === songAdapter) {
                makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    ItemTouchHelper.START or ItemTouchHelper.END
                )
            } else {
                0
            }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            if (target.bindingAdapter !== songAdapter) return false
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0) return false
            songAdapter.move(from, to)
            return true
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                dragging = true
                viewHolder.itemView.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                viewHolder.itemView.elevation = 12f
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            viewHolder.itemView.elevation = 0f
            if (dragging) {
                dragging = false
                store.reorder(playlistId, songAdapter.items.map { it.id })
                render()
            }
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val song = songAdapter.items.getOrNull(viewHolder.bindingAdapterPosition) ?: return
            removeSong(song)
        }
    }

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlist_id"
    }
}
