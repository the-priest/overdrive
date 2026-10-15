@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.overdrive.player.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.databinding.ActivityNowPlayingBinding
import com.overdrive.player.player.EXTRA_ALBUM_ID
import com.overdrive.player.player.EXTRA_ALBUM_RAW
import com.overdrive.player.player.EXTRA_ARTIST_ID
import com.overdrive.player.player.EXTRA_ARTIST_RAW
import com.overdrive.player.player.Playback
import com.overdrive.player.player.PlayerConnection
import com.overdrive.player.player.SleepTimer
import com.overdrive.player.player.audioUri
import com.overdrive.player.player.songId
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.ThemeHelper
import com.overdrive.player.util.applySystemBarPadding
import com.overdrive.player.util.enableDarkEdgeToEdge
import com.overdrive.player.util.formatDuration
import kotlinx.coroutines.launch
import kotlin.math.abs

class NowPlayingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNowPlayingBinding
    private val prefs by lazy { OverdriveApp.from(this).prefs }
    private val effects by lazy { OverdriveApp.from(this).audioEffects }

    private var userSeeking = false
    private var currentSongId: Long = -1L
    private var artKey: Any? = null

    private val handler = Handler(Looper.getMainLooper())
    private var themeAccent = 0
    private var backgroundColor = 0
    private var backgroundAnimator: ValueAnimator? = null

    private val progressTask = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    private val sleepTask = object : Runnable {
        override fun run() {
            updateSleepLabel()
            if (SleepTimer.state.value is SleepTimer.State.Counting) handler.postDelayed(this, 1000)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED
                )
            ) {
                if (player.currentMediaItem == null) {
                    finish()
                    return
                }
                bindTrack(player)
            }
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY
                )
            ) {
                bindPlayState(player)
            }
            if (events.containsAny(
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION
                )
            ) {
                bindModes(player)
                bindUpNext(player)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themeAccent = ThemeHelper.apply(this)
        theme.applyStyle(R.style.ThemeOverlay_Overdrive_NowPlaying, true)
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        binding = ActivityNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding(top = true, bottom = true)
        backgroundColor = ContextCompat.getColor(this, R.color.bg_grad_top)
        binding.title.isSelected = true // start the marquee

        setupToolbar()
        setupControls()

        PlayerConnection.bind(this, this, playerListener) { c ->
            if (c.currentMediaItem == null) {
                finish()
            } else {
                bindTrack(c)
                bindPlayState(c)
                bindModes(c)
                bindUpNext(c)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SleepTimer.state.collect {
                    handler.removeCallbacks(sleepTask)
                    sleepTask.run()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ThemeHelper.accentIndex(this) != themeAccent) {
            recreate()
            return
        }
        // Boost may have been changed on the equalizer screen.
        updateBoost()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressTask)
        handler.removeCallbacks(sleepTask)
    }

    override fun onDestroy() {
        backgroundAnimator?.cancel()
        super.onDestroy()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_go_to_album -> openAlbum()
                R.id.action_go_to_artist -> openArtist()
                R.id.action_sleep_timer -> SleepTimerDialog.show(this)
                R.id.action_add_playlist ->
                    if (currentSongId >= 0) PlaylistDialogs.addToPlaylist(this, listOf(currentSongId))
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControls() {
        binding.playPause.setOnClickListener { Playback.togglePlayPause(this) }
        binding.next.setOnClickListener { PlayerConnection.controller?.seekToNextMediaItem() }
        binding.prev.setOnClickListener { PlayerConnection.controller?.let(Playback::previous) }
        binding.shuffle.setOnClickListener {
            PlayerConnection.controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
        }
        binding.repeat.setOnClickListener { PlayerConnection.controller?.let(Playback::cycleRepeat) }
        binding.favorite.setOnClickListener { toggleFavorite() }
        binding.boost.setOnClickListener {
            effects.toggleVolumeBoost()
            updateBoost()
        }
        binding.boost.setOnLongClickListener {
            startActivity(Intent(this, EqualizerActivity::class.java))
            true
        }
        binding.equalizer.setOnClickListener {
            startActivity(Intent(this, EqualizerActivity::class.java))
        }
        binding.sleep.setOnClickListener { SleepTimerDialog.show(this) }
        binding.queue.setOnClickListener { openQueue() }
        binding.upNextCard.setOnClickListener { openQueue() }
        binding.artist.setOnClickListener { openArtist() }
        binding.album.setOnClickListener { openAlbum() }

        // Swipe the cover left/right to skip, like flipping through records.
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                if (abs(dx) < SWIPE_MIN_PX || abs(velocityX) < abs(velocityY)) return false
                val c = PlayerConnection.controller ?: return false
                if (dx < 0) c.seekToNextMediaItem() else Playback.previous(c)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                Playback.togglePlayPause(this@NowPlayingActivity)
                return true
            }
        })
        binding.art.setOnTouchListener { _, event -> detector.onTouchEvent(event) }

        binding.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = PlayerConnection.controller?.duration ?: 0L
                    if (duration > 0) {
                        binding.timeCurrent.text = formatDuration(duration * progress / 1000)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val c = PlayerConnection.controller
                val duration = c?.duration ?: 0L
                if (c != null && duration > 0) c.seekTo(duration * seekBar.progress / 1000)
                userSeeking = false
            }
        })
    }

    private fun openQueue() {
        startActivity(Intent(this, QueueActivity::class.java))
    }

    // --- rendering ----------------------------------------------------------

    private fun bindTrack(player: Player) {
        val item = player.currentMediaItem ?: return
        val metadata = item.mediaMetadata
        binding.title.text = metadata.title ?: getString(R.string.app_name)
        binding.artist.text = metadata.artist ?: ""
        binding.album.text = buildString {
            append(metadata.albumTitle ?: "")
            metadata.releaseYear?.let { append(" · ").append(it) }
        }
        currentSongId = item.songId
        updateFavorite()
        updateBoost()

        val key = metadata.artworkUri ?: item.audioUri
        if (key != artKey) {
            artKey = key
            ArtworkLoader.load(
                this, binding.art, metadata.artworkUri, item.audioUri, 720,
                onResult = ::onArtwork
            )
        }
    }

    /** Tints the backdrop with the cover's dominant dark colour. */
    private fun onArtwork(bitmap: Bitmap?) {
        val fallback = ContextCompat.getColor(this, R.color.bg_grad_top)
        if (bitmap == null) {
            animateBackground(fallback)
            return
        }
        Palette.from(bitmap).maximumColorCount(16).generate { palette ->
            if (isDestroyed) return@generate
            val base = if (palette == null) fallback
            else palette.getDarkVibrantColor(palette.getDarkMutedColor(fallback))
            // Keep it moody: never brighter than ~35% lightness.
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(base, hsl)
            hsl[2] = hsl[2].coerceAtMost(0.35f)
            animateBackground(ColorUtils.HSLToColor(hsl))
        }
    }

    private fun animateBackground(target: Int) {
        backgroundAnimator?.cancel()
        val from = backgroundColor
        val bottom = ContextCompat.getColor(this, R.color.bg)
        backgroundAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, target).apply {
            duration = 600
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                backgroundColor = color
                binding.root.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(color, ColorUtils.blendARGB(color, bottom, 0.65f), bottom)
                )
            }
            start()
        }
    }

    private fun bindPlayState(player: Player) {
        binding.playPause.setImageResource(
            if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.playPause.contentDescription =
            getString(if (player.isPlaying) R.string.pause else R.string.play)
        updateProgress()
        handler.removeCallbacks(progressTask)
        if (player.isPlaying) handler.postDelayed(progressTask, PROGRESS_INTERVAL_MS)
    }

    private fun bindModes(player: Player) {
        val accent = ThemeHelper.accentColor(this)
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)
        binding.shuffle.setColorFilter(if (player.shuffleModeEnabled) accent else secondary)
        when (player.repeatMode) {
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

    private fun bindUpNext(player: Player) {
        val upcoming = Playback.upNext(player)
        val next = upcoming.firstOrNull()
        if (next == null) {
            binding.upNextLabel.setText(R.string.queue_end_of_queue)
            binding.upNextTitle.setText(R.string.queue_nothing_next)
            binding.upNextCount.text = ""
            binding.upNextArt.setImageResource(R.drawable.placeholder_art)
            binding.upNextArt.tag = null
            return
        }
        val metadata = next.item.mediaMetadata
        binding.upNextLabel.setText(R.string.up_next_label)
        binding.upNextTitle.text = buildString {
            append(metadata.title ?: "")
            metadata.artist?.let { append(" — ").append(it) }
        }
        binding.upNextCount.text =
            if (upcoming.size > 1) {
                resources.getQuantityString(R.plurals.more_in_queue, upcoming.size - 1, upcoming.size - 1)
            } else {
                ""
            }
        ArtworkLoader.load(this, binding.upNextArt, metadata.artworkUri, next.item.audioUri, 128)
    }

    private fun updateFavorite() {
        val favorite = currentSongId >= 0 && prefs.isFavorite(currentSongId)
        binding.favorite.setImageResource(
            if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        binding.favorite.setColorFilter(
            if (favorite) ThemeHelper.accentColor(this)
            else ContextCompat.getColor(this, R.color.text_secondary)
        )
        binding.favorite.contentDescription =
            getString(if (favorite) R.string.remove_favorite else R.string.add_favorite)
    }

    private fun toggleFavorite() {
        if (currentSongId < 0) return
        val nowFavorite = !prefs.isFavorite(currentSongId)
        prefs.setFavorite(currentSongId, nowFavorite)
        updateFavorite()
        binding.favorite.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120)
            .withEndAction {
                binding.favorite.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            .start()
    }

    private fun updateBoost() {
        val boost = prefs.volumeBoost
        val on = boost > 100
        val color = if (on) ThemeHelper.accentColor(this)
        else ContextCompat.getColor(this, R.color.text_secondary)
        binding.boostIcon.setColorFilter(color)
        binding.boostLabel.setTextColor(color)
        binding.boostLabel.text = if (on) "$boost%" else getString(R.string.tool_boost)
        binding.boost.contentDescription =
            if (on) getString(R.string.boost_on, boost) else getString(R.string.boost_off)
    }

    private fun updateSleepLabel() {
        val accent = ThemeHelper.accentColor(this)
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)
        val (text, active) = when (SleepTimer.state.value) {
            SleepTimer.State.Off -> getString(R.string.tool_sleep) to false
            SleepTimer.State.EndOfTrack -> getString(R.string.tool_sleep) to true
            is SleepTimer.State.Counting ->
                formatDuration(SleepTimer.remainingMs() ?: 0L) to true
        }
        binding.sleepLabel.text = text
        binding.sleepLabel.setTextColor(if (active) accent else secondary)
        binding.sleepIcon.setColorFilter(if (active) accent else secondary)
    }

    private fun updateProgress() {
        if (userSeeking) return
        val c = PlayerConnection.controller ?: return
        val duration = c.duration
        val position = c.currentPosition
        binding.timeCurrent.text = formatDuration(position.coerceAtLeast(0))
            .let { if (position <= 0) getString(R.string.zero_time) else it }
        binding.timeTotal.text = formatDuration(duration)
        binding.seek.progress =
            if (duration > 0) ((position * 1000L) / duration).toInt().coerceIn(0, 1000) else 0
    }

    private fun openAlbum() {
        val item = PlayerConnection.controller?.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras
        val albumId = extras?.getLong(EXTRA_ALBUM_ID) ?: 0L
        val raw = extras?.getString(EXTRA_ALBUM_RAW)
            ?: item.mediaMetadata.albumTitle?.toString().orEmpty()
        val key = if (albumId != 0L) "id:$albumId" else "name:${raw.lowercase()}"
        SongMenu.openAlbum(this, key, raw, albumId)
    }

    private fun openArtist() {
        val item = PlayerConnection.controller?.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras
        val artistId = extras?.getLong(EXTRA_ARTIST_ID) ?: 0L
        val raw = extras?.getString(EXTRA_ARTIST_RAW)
            ?: item.mediaMetadata.artist?.toString().orEmpty()
        val key = if (artistId != 0L) "id:$artistId" else "name:${raw.lowercase()}"
        SongMenu.openArtist(this, key, raw)
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val SWIPE_MIN_PX = 120f
    }
}
