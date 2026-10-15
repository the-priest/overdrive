package com.overdrive.player.player

import android.content.Context
import com.overdrive.player.data.Song

/** Small convenience wrapper for handing a song list to the playback service. */
object Playback {

    fun start(
        context: Context,
        songs: List<Song>,
        startIndex: Int,
        shuffle: Boolean,
        repeatMode: Int
    ) {
        if (songs.isEmpty()) return
        PlayerConnection.withController(context) { controller ->
            val index = startIndex.coerceIn(0, songs.lastIndex)
            controller.setMediaItems(songs.toMediaItems(), index, 0L)
            controller.shuffleModeEnabled = shuffle
            controller.repeatMode = repeatMode
            controller.prepare()
            controller.play()
        }
    }

    fun playNext(context: Context, song: Song) {
        PlayerConnection.withController(context) { controller ->
            val insertAt = (controller.currentMediaItemIndex + 1)
                .coerceAtMost(controller.mediaItemCount)
            controller.addMediaItem(insertAt, song.toMediaItem())
        }
    }

    fun enqueue(context: Context, song: Song) {
        PlayerConnection.withController(context) { controller ->
            controller.addMediaItem(song.toMediaItem())
        }
    }

    fun togglePlayPause(context: Context) {
        PlayerConnection.withController(context) { controller ->
            if (controller.isPlaying) controller.pause() else {
                if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                    controller.prepare()
                }
                controller.play()
            }
        }
    }
}
