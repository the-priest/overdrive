package com.overdrive.player.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.databinding.ActivityNowPlayingBinding
import com.overdrive.player.player.Playback
import com.overdrive.player.player.PlayerConnection
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.ThemeHelper
import com.overdrive.player.util.formatDuration

class NowPlayingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNowPlayingBinding
    private val prefs by lazy { OverdriveApp.from(this).prefs }
    private val effects by lazy { OverdriveApp.from(this).audioEffects }

    private var controller: MediaController? = null
    private var userSeeking = false
    private var currentSongId: Long = -1L
    private var currentAlbum: String = ""
    private var currentArtist: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private var progressTask: Runnable? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refresh()
            if (isPlaying) startProgressLoop() else stopProgressLoop()
        }

        override fun onPlaybackStateChanged(playbackState: Int) = refresh()
        override fun onRepeatModeChanged(repeatMode: Int) = refresh()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupControls()
        binding.equalizer.setOnClickListener {
            startActivity(Intent(this, EqualizerActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        PlayerConnection.init(this)
        PlayerConnection.withController(this) { c ->
            controller = c
            c.addListener(playerListener)
            refresh()
        }
    }

    override fun onStop() {
        super.onStop()
        controller?.removeListener(playerListener)
        stopProgressLoop()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_go_to_album -> {
                    openCollection(CollectionActivity.TYPE_ALBUM, currentAlbum); true
                }

                R.id.action_go_to_artist -> {
                    openCollection(CollectionActivity.TYPE_ARTIST, currentArtist); true
                }

                else -> false
            }
        }
    }

    private fun setupControls() {
        binding.playPause.setOnClickListener { Playback.togglePlayPause(this) }
        binding.next.setOnClickListener { controller?.seekToNextMediaItem() }
        binding.prev.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            if (c.currentPosition > 4000) c.seekTo(0) else c.seekToPreviousMediaItem()
        }
        binding.shuffle.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            c.shuffleModeEnabled = !c.shuffleModeEnabled
        }
        binding.repeat.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            prefs.repeatMode = c.repeatMode
        }
        binding.favorite.setOnClickListener { toggleFavorite() }
        binding.boost.setOnClickListener { toggleBoost() }

        binding.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = controller?.duration ?: 0L
                    if (duration > 0) {
                        binding.timeCurrent.text = formatDuration(duration * progress / 1000)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val duration = controller?.duration ?: 0L
                if (duration > 0) {
                    controller?.seekTo(duration * seekBar.progress / 1000)
                }
                userSeeking = false
            }
        })
    }

    private fun toggleFavorite() {
        if (currentSongId < 0) return
        val nowFavorite = !prefs.isFavorite(currentSongId)
        prefs.setFavorite(currentSongId, nowFavorite)
        updateFavoriteIcon(nowFavorite)
    }

    private fun toggleBoost() {
        val newValue = if (prefs.volumeBoost > 100) 100 else 150
        effects.setVolumeBoost(newValue)
        updateBoostIcon()
    }

    private fun openCollection(type: String, name: String) {
        if (name.isBlank()) return
        startActivity(
            Intent(this, CollectionActivity::class.java).apply {
                putExtra(CollectionActivity.EXTRA_TYPE, type)
                putExtra(CollectionActivity.EXTRA_ID, 0L)
                putExtra(CollectionActivity.EXTRA_TITLE, name)
            }
        )
    }

    // --- rendering ----------------------------------------------------------

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        if (item == null) {
            finish()
            return
        }
        val metadata = item.mediaMetadata
        binding.title.text = metadata.title ?: getString(R.string.app_name)
        binding.artist.text = metadata.artist ?: ""
        currentAlbum = metadata.albumTitle?.toString().orEmpty()
        currentArtist = metadata.artist?.toString().orEmpty()
        currentSongId = item.mediaId.toLongOrNull() ?: -1L

        ArtworkLoader.load(this, binding.art, metadata.artworkUri, item.localConfiguration?.uri, 600)

        binding.playPause.setImageResource(
            if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        updateFavoriteIcon(prefs.isFavorite(currentSongId))
        updateBoostIcon()
        updateShuffleRepeat(c)
        updateProgress()
    }

    private fun updateShuffleRepeat(c: Player) {
        val accent = ThemeHelper.accentColor(this)
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)
        binding.shuffle.setColorFilter(if (c.shuffleModeEnabled) accent else secondary)
        when (c.repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                binding.repeat.setImageResource(R.drawable.ic_repeat_one)
                binding.repeat.setColorFilter(accent)
            }

            Player.REPEAT_MODE_ALL -> {
                binding.repeat.setImageResource(R.drawable.ic_repeat)
                binding.repeat.setColorFilter(accent)
            }

            else -> {
                binding.repeat.setImageResource(R.drawable.ic_repeat)
                binding.repeat.setColorFilter(secondary)
            }
        }
    }

    private fun updateFavoriteIcon(favorite: Boolean) {
        binding.favorite.setImageResource(
            if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        binding.favorite.setColorFilter(
            if (favorite) ContextCompat.getColor(this, R.color.rock_red)
            else ContextCompat.getColor(this, R.color.text_secondary)
        )
    }

    private fun updateBoostIcon() {
        val on = prefs.volumeBoost > 100
        binding.boost.setColorFilter(
            if (on) ThemeHelper.accentColor(this)
            else ContextCompat.getColor(this, R.color.text_secondary)
        )
        binding.boost.alpha = if (on) 1f else 0.85f
    }

    private fun updateProgress() {
        if (userSeeking) return
        val c = controller ?: return
        val duration = c.duration
        val position = c.currentPosition
        binding.timeCurrent.text = formatDuration(position)
        binding.timeTotal.text = formatDuration(duration)
        binding.seek.progress =
            if (duration > 0) ((position * 1000L) / duration).toInt().coerceIn(0, 1000) else 0
    }

    private fun startProgressLoop() {
        stopProgressLoop()
        val task = object : Runnable {
            override fun run() {
                updateProgress()
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
