package com.overdrive.player.player

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.overdrive.player.OverdriveApp
import com.overdrive.player.data.MusicRepository
import com.overdrive.player.prefs.SettingsStore
import com.overdrive.player.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background playback served through a Media3 [MediaSession]. It owns the one
 * and only ExoPlayer instance, which gives us gapless auto-advance, queue
 * management, audio focus, headset disconnect handling and a media notification
 * for free. It also remembers the queue so it can be restored next launch.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var prefs: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var restoring = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsStore.K_PAUSE_DISCONNECT) {
            player.setHandleAudioBecomingNoisy(prefs.pauseOnDisconnect)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as OverdriveApp
        prefs = app.prefs

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(prefs.pauseOnDisconnect)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        player.repeatMode = prefs.repeatMode
        player.shuffleModeEnabled = prefs.shuffleDefault || prefs.shuffleEnabled

        // ExoPlayer already owns an audio session at this point, and
        // onAudioSessionIdChanged only fires on *changes* -- so attach now or
        // the equalizer and volume boost would never be applied.
        app.audioEffects.attach(player.audioSessionId)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                app.audioEffects.attach(audioSessionId)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                prefs.repeatMode = repeatMode
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                prefs.shuffleEnabled = shuffleModeEnabled
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                savePosition()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePosition()
            }
        })

        prefs.registerOnChange(prefsListener)
        SleepTimer.attach(player)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        if (prefs.resumeLastSession) restoreQueue()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        val current = mediaSession?.player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePosition()
        scope.cancel()
        prefs.unregisterOnChange(prefsListener)
        SleepTimer.detach()
        (application as OverdriveApp).audioEffects.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    // --- Last session -------------------------------------------------------

    private fun saveQueue() {
        if (restoring) return
        val ids = (0 until player.mediaItemCount).mapNotNull {
            player.getMediaItemAt(it).songId.takeIf { id -> id >= 0 }
        }
        prefs.saveQueue(ids, player.currentMediaItemIndex, player.currentPosition)
    }

    private fun savePosition() {
        if (restoring || player.mediaItemCount == 0) return
        prefs.savePosition(player.currentMediaItemIndex, player.currentPosition)
    }

    /**
     * Puts the last queue back (paused) so the mini player shows where you
     * left off. Songs deleted since then are skipped.
     */
    private fun restoreQueue() {
        val ids = prefs.savedQueueIds()
        if (ids.isEmpty()) return
        val savedIndex = prefs.savedQueueIndex
        val savedPosition = prefs.savedQueuePosition
        restoring = true
        scope.launch {
            try {
                val byId = MusicRepository(this@PlaybackService).loadSongs().associateBy { it.id }
                // Something was queued by the user while we were loading: keep it.
                if (player.mediaItemCount > 0) return@launch
                val savedCurrentId = ids.getOrNull(savedIndex)
                val songs = ids.mapNotNull { byId[it] }
                if (songs.isEmpty()) return@launch
                val index = songs.indexOfFirst { it.id == savedCurrentId }
                player.setMediaItems(
                    songs.toMediaItems(),
                    index.coerceAtLeast(0),
                    if (index >= 0) savedPosition else 0L
                )
                player.prepare()
            } finally {
                restoring = false
                // Persist whatever ended up in the player, including a queue
                // the user started while the restore was still loading.
                if (player.mediaItemCount > 0) saveQueue()
            }
        }
    }
}
