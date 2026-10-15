@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.overdrive.player.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.overdrive.player.data.Song

/** Convenience commands for handing songs to the playback service. */
object Playback {

    /**
     * Replaces the queue with [songs] and starts at [startIndex].
     * [shuffle] = null keeps whatever shuffle mode the player is already in.
     */
    fun start(
        context: Context,
        songs: List<Song>,
        startIndex: Int,
        shuffle: Boolean? = null
    ) {
        if (songs.isEmpty()) return
        PlayerConnection.withController(context) { controller ->
            val index = startIndex.coerceIn(0, songs.lastIndex)
            if (shuffle != null) controller.shuffleModeEnabled = shuffle
            controller.setMediaItems(songs.toMediaItems(), index, 0L)
            controller.prepare()
            controller.play()
        }
    }

    /** Plays the whole list in a random order. */
    fun shuffle(context: Context, songs: List<Song>) {
        if (songs.isEmpty()) return
        start(context, songs, songs.indices.random(), shuffle = true)
    }

    fun playNext(context: Context, songs: List<Song>) {
        if (songs.isEmpty()) return
        PlayerConnection.withController(context) { controller ->
            if (controller.mediaItemCount == 0) {
                start(context, songs, 0)
                return@withController
            }
            val insertAt = (controller.currentMediaItemIndex + 1)
                .coerceAtMost(controller.mediaItemCount)
            controller.addMediaItems(insertAt, songs.toMediaItems())
        }
    }

    fun enqueue(context: Context, songs: List<Song>) {
        if (songs.isEmpty()) return
        PlayerConnection.withController(context) { controller ->
            if (controller.mediaItemCount == 0) {
                start(context, songs, 0)
            } else {
                controller.addMediaItems(songs.toMediaItems())
            }
        }
    }

    fun togglePlayPause(context: Context) {
        PlayerConnection.withController(context) { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                if (controller.playbackState == Player.STATE_ENDED) {
                    controller.seekToDefaultPosition(controller.currentMediaItemIndex)
                }
                controller.play()
            }
        }
    }

    /** Restart the track if we are a few seconds in, otherwise go back one. */
    fun previous(player: Player) {
        if (player.currentPosition > RESTART_THRESHOLD_MS || !player.hasPreviousMediaItem()) {
            player.seekTo(0)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    fun cycleRepeat(player: Player) {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** One entry of the queue in actual play order. */
    data class QueueEntry(val index: Int, val item: MediaItem)

    /**
     * Returns the items that will play after the current one, in the order
     * the player will actually play them (shuffle order when shuffling).
     * With repeat-all, the list wraps around once.
     */
    fun upNext(player: Player, limit: Int = Int.MAX_VALUE): List<QueueEntry> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val shuffle = player.shuffleModeEnabled
        val wrap = player.repeatMode == Player.REPEAT_MODE_ALL
        val start = player.currentMediaItemIndex
        val result = ArrayList<QueueEntry>()
        var index = nextIndex(timeline, start, shuffle, wrap)
        while (index != C.INDEX_UNSET && index != start && result.size < limit) {
            result.add(QueueEntry(index, player.getMediaItemAt(index)))
            index = nextIndex(timeline, index, shuffle, wrap)
        }
        return result
    }

    private fun nextIndex(timeline: Timeline, index: Int, shuffle: Boolean, wrap: Boolean): Int =
        timeline.getNextWindowIndex(
            index,
            if (wrap) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF,
            shuffle
        )

    /**
     * Translates a drag in the Up Next list into a `moveMediaItem` target.
     * The item at playlist index [from] was dropped onto the slot of the item
     * at playlist index [target]. Dragging up places it before that item,
     * dragging down places it after it -- even across the repeat wrap-around.
     */
    fun queueMoveTarget(from: Int, target: Int, movingUp: Boolean): Int =
        if (movingUp) {
            if (target > from) target - 1 else target
        } else {
            if (target > from) target else target + 1
        }

    private const val RESTART_THRESHOLD_MS = 4_000L
}
