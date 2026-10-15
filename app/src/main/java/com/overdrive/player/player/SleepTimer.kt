@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.overdrive.player.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pauses playback after a delay, fading the volume out over the last
 * [FADE_MS] so you are not jolted awake by a sudden stop. Can also stop at the
 * end of the current track. The service hands it the player; the UI only reads
 * [state] and calls [start]/[cancel].
 */
object SleepTimer {

    sealed interface State {
        data object Off : State
        data class Counting(val endsAtElapsed: Long) : State
        data object EndOfTrack : State
    }

    private const val FADE_MS = 10_000L
    private const val TICK_MS = 250L

    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            // pauseAtEndOfMediaItems has done its job once the track has ended.
            if (_state.value == State.EndOfTrack &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            ) {
                cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_state.value == State.EndOfTrack && playbackState == Player.STATE_ENDED) cancel()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val p = player
            val s = _state.value
            if (p == null || s !is State.Counting) return
            val remaining = s.endsAtElapsed - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                p.pause()
                p.volume = 1f
                _state.value = State.Off
                return
            }
            p.volume = if (remaining < FADE_MS) (remaining.toFloat() / FADE_MS).coerceIn(0f, 1f) else 1f
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun attach(exoPlayer: ExoPlayer) {
        player = exoPlayer
        exoPlayer.addListener(listener)
    }

    fun detach() {
        cancel()
        player?.removeListener(listener)
        player = null
    }

    fun start(durationMs: Long) {
        cancel()
        _state.value = State.Counting(SystemClock.elapsedRealtime() + durationMs)
        handler.post(tick)
    }

    fun stopAtEndOfTrack() {
        cancel()
        player?.pauseAtEndOfMediaItems = true
        _state.value = State.EndOfTrack
    }

    fun cancel() {
        handler.removeCallbacks(tick)
        player?.let {
            it.volume = 1f
            it.pauseAtEndOfMediaItems = false
        }
        _state.value = State.Off
    }

    /** Milliseconds left on a countdown, or null when not counting down. */
    fun remainingMs(): Long? = (state.value as? State.Counting)
        ?.let { (it.endsAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0) }
}
