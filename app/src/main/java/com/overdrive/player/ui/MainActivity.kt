package com.overdrive.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.data.Song
import com.overdrive.player.databinding.ActivityMainBinding
import com.overdrive.player.player.Playback
import com.overdrive.player.player.PlayerConnection
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.ThemeHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val prefs by lazy { OverdriveApp.from(this).prefs }

    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressTask: Runnable? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasAudioPermission()) viewModel.load()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            RESULT_OK -> viewModel.load()
            RESULT_FIRST_USER -> recreate()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
            updateMiniPlayer()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateMiniPlayer()
            if (isPlaying) startProgressLoop() else stopProgressLoop()
        }

        override fun onPlaybackStateChanged(playbackState: Int) = updateMiniPlayer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupPager()
        setupMiniPlayer()
        ensurePermissions()
    }

    override fun onStart() {
        super.onStart()
        PlayerConnection.init(this)
        PlayerConnection.withController(this) { c ->
            controller = c
            c.addListener(playerListener)
            updateMiniPlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        controller?.removeListener(playerListener)
        stopProgressLoop()
    }

    // --- setup --------------------------------------------------------------

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_equalizer -> {
                    startActivity(Intent(this, EqualizerActivity::class.java)); true
                }

                R.id.action_settings -> {
                    settingsLauncher.launch(Intent(this, SettingsActivity::class.java)); true
                }

                R.id.action_shuffle_all -> {
                    shuffleAll(); true
                }

                R.id.action_rescan -> {
                    ensurePermissions(); true
                }

                else -> false
            }
        }

        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        configureSearch(searchItem.actionView as? SearchView)
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                configureSearch(item.actionView as? SearchView)
                binding.pager.setCurrentItem(0, false)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.setQuery("")
                return true
            }
        })
    }

    private fun configureSearch(searchView: SearchView?) {
        if (searchView == null || searchView.getTag(R.id.action_search) != null) return
        searchView.setTag(R.id.action_search, true)
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupPager() {
        val titles = arrayOf(
            getString(R.string.tab_songs),
            getString(R.string.tab_albums),
            getString(R.string.tab_artists),
            getString(R.string.tab_favorites)
        )
        binding.pager.adapter = LibraryPagerAdapter(this)
        binding.pager.offscreenPageLimit = 1
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = titles[position]
        }.attach()
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
        binding.miniPlayPause.setOnClickListener { Playback.togglePlayPause(this) }
        binding.miniNext.setOnClickListener { controller?.seekToNextMediaItem() }
    }

    // --- permissions --------------------------------------------------------

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission()) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensurePermissions() {
        val needed = ArrayList<String>()
        if (!hasAudioPermission()) needed.add(audioPermission())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) viewModel.load() else permissionLauncher.launch(needed.toTypedArray())
    }

    fun onGrantOrRescan() {
        if (hasAudioPermission()) viewModel.load() else ensurePermissions()
    }

    // --- playback -----------------------------------------------------------

    fun playList(list: List<Song>, index: Int) {
        Playback.start(this, list, index, prefs.shuffleDefault, prefs.repeatMode)
    }

    private fun shuffleAll() {
        val all = viewModel.songs.value.orEmpty()
        if (all.isEmpty()) return
        Playback.start(this, all, all.indices.random(), true, prefs.repeatMode)
    }

    fun openCollection(type: String, id: Long, name: String) {
        startActivity(
            Intent(this, CollectionActivity::class.java).apply {
                putExtra(CollectionActivity.EXTRA_TYPE, type)
                putExtra(CollectionActivity.EXTRA_ID, id)
                putExtra(CollectionActivity.EXTRA_TITLE, name)
            }
        )
    }

    fun showSongMenu(song: Song, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.inflate(R.menu.menu_song)
        popup.menu.findItem(R.id.action_favorite).title =
            if (viewModel.isFavorite(song.id)) getString(R.string.remove_favorite)
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
                    viewModel.toggleFavorite(song); true
                }

                R.id.action_go_to_album -> {
                    openCollection(
                        CollectionActivity.TYPE_ALBUM,
                        song.albumId,
                        song.album.ifBlank { getString(R.string.tab_albums) }
                    ); true
                }

                R.id.action_go_to_artist -> {
                    openCollection(
                        CollectionActivity.TYPE_ARTIST,
                        song.artistId,
                        song.artist.ifBlank { getString(R.string.tab_artists) }
                    ); true
                }

                else -> false
            }
        }
        popup.show()
    }

    // --- mini player --------------------------------------------------------

    private fun updateMiniPlayer() {
        val c = controller ?: return
        val item = c.currentMediaItem
        if (item == null) {
            binding.miniPlayer.visibility = View.GONE
            return
        }
        binding.miniPlayer.visibility = View.VISIBLE
        binding.miniTitle.text = item.mediaMetadata.title ?: getString(R.string.app_name)
        binding.miniArtist.text = item.mediaMetadata.artist ?: ""
        binding.miniPlayPause.setImageResource(
            if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        ArtworkLoader.load(
            this, binding.miniArt, item.mediaMetadata.artworkUri, null, 128
        )
        updateMiniProgress()
    }

    private fun updateMiniProgress() {
        val c = controller ?: return
        val duration = c.duration
        val position = c.currentPosition
        binding.miniProgress.progress =
            if (duration > 0) ((position * 1000L) / duration).toInt().coerceIn(0, 1000) else 0
    }

    private fun startProgressLoop() {
        stopProgressLoop()
        val task = object : Runnable {
            override fun run() {
                updateMiniProgress()
                handler.postDelayed(this, 500)
            }
        }
        progressTask = task
        handler.postDelayed(task, 500)
    }

    private fun stopProgressLoop() {
        progressTask?.let { handler.removeCallbacks(it) }
        progressTask = null
    }
}
