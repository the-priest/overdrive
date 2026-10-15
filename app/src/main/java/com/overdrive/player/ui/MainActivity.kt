package com.overdrive.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.material.tabs.TabLayoutMediator
import com.overdrive.player.R
import com.overdrive.player.databinding.ActivityMainBinding
import com.overdrive.player.player.Playback
import com.overdrive.player.player.PlayerConnection
import com.overdrive.player.player.audioUri
import com.overdrive.player.player.songId
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.ThemeHelper
import com.overdrive.player.util.applySystemBarPadding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var themeAccent = 0
    private val handler = Handler(Looper.getMainLooper())
    private var searchItem: MenuItem? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPermissionResult() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == SettingsActivity.RESULT_RESCAN) rescan()
    }

    private val progressTask = object : Runnable {
        override fun run() {
            updateMiniProgress()
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED
                )
            ) {
                updateMiniPlayer(player)
            }
        }
    }

    private val closeSearchCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            searchItem?.collapseActionView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themeAccent = ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding(top = true, bottom = true)

        setupToolbar()
        setupPager()
        setupMiniPlayer()
        onBackPressedDispatcher.addCallback(this, closeSearchCallback)

        PlayerConnection.init(this)
        PlayerConnection.bind(this, this, playerListener) { updateMiniPlayer(it) }

        if (savedInstanceState == null) {
            ensurePermissions()
            handleIntent(intent)
        } else {
            refreshPermissionState()
            if (viewModel.songs().isEmpty() && viewModel.hasPermission.value == true) viewModel.load()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // The accent was changed in Settings: rebuild with the new theme.
        if (ThemeHelper.accentIndex(this) != themeAccent) {
            recreate()
            return
        }
        // Permission granted from the system settings screen while we were away.
        val before = viewModel.hasPermission.value
        refreshPermissionState()
        if (before == false && viewModel.hasPermission.value == true) viewModel.load()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressTask)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
    }

    // --- setup --------------------------------------------------------------

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_equalizer ->
                    startActivity(Intent(this, EqualizerActivity::class.java))
                R.id.action_settings ->
                    settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                R.id.action_shuffle_all -> Playback.shuffle(this, viewModel.songs())
                R.id.action_queue -> startActivity(Intent(this, QueueActivity::class.java))
                R.id.action_rescan -> rescan()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }

        val item = binding.toolbar.menu.findItem(R.id.action_search)
        searchItem = item
        (item.actionView as? SearchView)?.apply {
            queryHint = getString(R.string.search_hint)
            maxWidth = Int.MAX_VALUE
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.setQuery(newText.orEmpty())
                    return true
                }
            })
        }
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                closeSearchCallback.isEnabled = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                closeSearchCallback.isEnabled = false
                viewModel.setQuery("")
                return true
            }
        })
    }

    private fun setupPager() {
        val titles = intArrayOf(
            R.string.tab_songs,
            R.string.tab_albums,
            R.string.tab_artists,
            R.string.tab_favorites
        )
        binding.pager.adapter = LibraryPagerAdapter(this)
        binding.pager.offscreenPageLimit = 1
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.setText(titles[position])
        }.attach()
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
        binding.miniPlayPause.setOnClickListener { Playback.togglePlayPause(this) }
        binding.miniNext.setOnClickListener { PlayerConnection.controller?.seekToNextMediaItem() }
        binding.miniQueue.setOnClickListener {
            startActivity(Intent(this, QueueActivity::class.java))
        }
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

    private fun refreshPermissionState() {
        viewModel.hasPermission.value = hasAudioPermission()
    }

    private fun ensurePermissions() {
        val needed = ArrayList<String>()
        if (!hasAudioPermission()) needed.add(audioPermission())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) onPermissionResult() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun onPermissionResult() {
        refreshPermissionState()
        if (hasAudioPermission()) viewModel.load()
    }

    /** Called from the empty state: ask again, or send the user to settings. */
    fun onGrantOrRescan() {
        when {
            hasAudioPermission() -> rescan()
            shouldShowRequestPermissionRationale(audioPermission()) ||
                viewModel.hasPermission.value == null -> ensurePermissions()
            else -> {
                // "Don't ask again" was chosen: the dialog will not show anymore.
                Toast.makeText(this, R.string.permission_denied_forever, Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", packageName, null))
                )
            }
        }
    }

    private fun rescan() {
        if (!hasAudioPermission()) {
            onGrantOrRescan()
            return
        }
        ArtworkLoader.clear()
        Toast.makeText(this, R.string.rescanning, Toast.LENGTH_SHORT).show()
        viewModel.load()
    }

    // --- mini player --------------------------------------------------------

    private fun updateMiniPlayer(player: Player) {
        val item: MediaItem? = player.currentMediaItem
        viewModel.nowPlayingId.value = item?.songId ?: -1L
        if (item == null) {
            binding.miniPlayer.visibility = View.GONE
            handler.removeCallbacks(progressTask)
            return
        }
        binding.miniPlayer.visibility = View.VISIBLE
        val metadata = item.mediaMetadata
        binding.miniTitle.text = metadata.title ?: getString(R.string.app_name)
        binding.miniArtist.text = metadata.artist ?: ""
        binding.miniPlayPause.setImageResource(
            if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.miniPlayPause.contentDescription =
            getString(if (player.isPlaying) R.string.pause else R.string.play)
        ArtworkLoader.load(
            this, binding.miniArt, metadata.artworkUri, item.audioUri, 128
        )
        updateMiniProgress()
        handler.removeCallbacks(progressTask)
        if (player.isPlaying) handler.postDelayed(progressTask, PROGRESS_INTERVAL_MS)
    }

    private fun updateMiniProgress() {
        val c = PlayerConnection.controller ?: return
        val duration = c.duration
        val position = c.currentPosition
        binding.miniProgress.progress =
            if (duration > 0) ((position * 1000L) / duration).toInt().coerceIn(0, 1000) else 0
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
        private const val PROGRESS_INTERVAL_MS = 500L
    }
}
