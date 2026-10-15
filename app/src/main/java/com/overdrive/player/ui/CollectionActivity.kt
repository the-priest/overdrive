package com.overdrive.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.Player
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.data.Library
import com.overdrive.player.data.MusicRepository
import com.overdrive.player.data.Song
import com.overdrive.player.databinding.ActivityCollectionBinding
import com.overdrive.player.databinding.ItemCollectionHeaderBinding
import com.overdrive.player.player.Playback
import com.overdrive.player.player.PlayerConnection
import com.overdrive.player.player.songId
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.ThemeHelper
import com.overdrive.player.util.applySystemBarPadding
import com.overdrive.player.util.formatTotalDuration
import kotlinx.coroutines.launch

/** Shows every song that belongs to one album or one artist. */
class CollectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionBinding
    private lateinit var songAdapter: SongAdapter
    private lateinit var headerAdapter: HeaderAdapter

    private val repository by lazy { MusicRepository(this) }
    private val prefs by lazy { OverdriveApp.from(this).prefs }

    private var songs: List<Song> = emptyList()
    private var isAlbum = true
    private var title = ""

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

        binding.toolbar.setNavigationOnClickListener { finish() }

        isAlbum = intent.getStringExtra(EXTRA_TYPE) != TYPE_ARTIST
        val key = intent.getStringExtra(EXTRA_KEY).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        songAdapter = SongAdapter(
            onPlay = { list, index -> Playback.start(this, list, index) },
            onMenu = { song, anchor ->
                SongMenu.show(this, song, anchor, showAlbum = !isAlbum, showArtist = isAlbum)
            },
            showTrackNumbers = isAlbum
        )
        headerAdapter = HeaderAdapter()
        binding.list.recycler.layoutManager = LinearLayoutManager(this)
        binding.list.recycler.adapter = ConcatAdapter(headerAdapter, songAdapter)
        binding.list.emptyText.setText(R.string.empty_songs)
        binding.list.emptyAction.visibility = View.GONE
        binding.list.loading.visibility = View.VISIBLE

        // Show the title in the toolbar only once the big header scrolls away.
        binding.list.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as LinearLayoutManager
                binding.toolbar.title = if (lm.findFirstVisibleItemPosition() > 0) title else ""
            }
        })

        PlayerConnection.bind(this, this, playerListener) {
            songAdapter.setPlayingId(it.currentMediaItem?.songId ?: -1L)
        }

        lifecycleScope.launch {
            val all = repository.loadSongs(prefs.minDurationSec * 1000L)
            songs = if (isAlbum) Library.songsOfAlbum(all, key) else Library.songsOfArtist(all, key)
            binding.list.loading.visibility = View.GONE
            binding.list.emptyView.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            songAdapter.submitList(songs)
            headerAdapter.notifyItemChanged(0)
        }
    }

    private inner class HeaderAdapter : RecyclerView.Adapter<HeaderAdapter.Holder>() {

        override fun getItemCount(): Int = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemCollectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind()

        inner class Holder(private val b: ItemCollectionHeaderBinding) : RecyclerView.ViewHolder(b.root) {

            init {
                b.playButton.setOnClickListener {
                    Playback.start(this@CollectionActivity, songs, 0, shuffle = false)
                }
                b.shuffleButton.setOnClickListener {
                    Playback.shuffle(this@CollectionActivity, songs)
                }
            }

            fun bind() {
                val context = b.root.context
                b.headerOverline.setText(if (isAlbum) R.string.tab_albums else R.string.tab_artists)
                b.headerTitle.text = title
                val count = context.resources.getQuantityString(R.plurals.songs, songs.size, songs.size)
                val total = songs.sumOf { it.duration }
                b.headerSubtitle.text = buildString {
                    if (isAlbum) {
                        val artist = songs.firstOrNull()?.artist.orEmpty()
                        if (artist.isNotBlank()) append(artist).append(" · ")
                        val year = songs.maxOfOrNull { it.year } ?: 0
                        if (year > 0) append(year).append(" · ")
                    } else {
                        val albums = songs.distinctBy(Library::albumKey).size
                        append(context.resources.getQuantityString(R.plurals.albums, albums, albums))
                        append(" · ")
                    }
                    append(count)
                    if (total > 0) append(" · ").append(formatTotalDuration(total))
                }
                b.playButton.isEnabled = songs.isNotEmpty()
                b.shuffleButton.isEnabled = songs.isNotEmpty()
                b.headerArt.shapeAppearanceModel = b.headerArt.shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(
                        if (isAlbum) 18 * context.resources.displayMetrics.density
                        else 100 * context.resources.displayMetrics.density
                    )
                    .build()
                val first = songs.firstOrNull { it.albumId != 0L } ?: songs.firstOrNull()
                if (first != null) {
                    ArtworkLoader.load(context, b.headerArt, first.albumArtUri, first.uri, 600)
                }
            }
        }
    }

    companion object {
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_KEY = "extra_key"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val TYPE_ALBUM = "album"
        const val TYPE_ARTIST = "artist"
    }
}
