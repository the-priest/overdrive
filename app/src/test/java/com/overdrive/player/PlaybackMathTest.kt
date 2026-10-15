package com.overdrive.player

import com.overdrive.player.audio.AudioEffectsManager
import com.overdrive.player.audio.RockPresets
import com.overdrive.player.player.Playback
import com.overdrive.player.util.formatDuration
import com.overdrive.player.util.formatTotalDuration
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMathTest {

    /** Simulates moveMediaItem on a list to check where things end up. */
    private fun move(list: List<Int>, from: Int, to: Int): List<Int> =
        list.toMutableList().also { it.add(to, it.removeAt(from)) }

    @Test
    fun queueMoveTargetDraggingDownLandsAfterTarget() {
        val list = (0..8).toList()
        val to = Playback.queueMoveTarget(from = 6, target = 8, movingUp = false)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 7, 8, 6), move(list, 6, to))
    }

    @Test
    fun queueMoveTargetDraggingUpLandsBeforeTarget() {
        val list = (0..8).toList()
        val to = Playback.queueMoveTarget(from = 8, target = 6, movingUp = true)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 8, 6, 7), move(list, 8, to))
    }

    @Test
    fun queueMoveTargetAcrossRepeatWrap() {
        // Current item is 5; with repeat-all the play order after it is 6,7,8,0,1,...
        val list = (0..8).toList()
        // Drag wrapped item 0 up to the top of Up Next (onto 6): plays right after 5.
        val up = move(list, 0, Playback.queueMoveTarget(0, 6, movingUp = true))
        assertEquals(listOf(1, 2, 3, 4, 5, 0, 6, 7, 8), up)
        // Drag 7 down past the wrap onto 2: plays after 2.
        val down = move(list, 7, Playback.queueMoveTarget(7, 2, movingUp = false))
        assertEquals(listOf(0, 1, 2, 7, 3, 4, 5, 6, 8), down)
    }

    @Test
    fun volumeBoostMapsToLoudnessGain() {
        assertEquals(0, AudioEffectsManager.volumeBoostToMillibel(100))
        assertEquals(1000, AudioEffectsManager.volumeBoostToMillibel(150))
        assertEquals(2000, AudioEffectsManager.volumeBoostToMillibel(200))
        assertEquals(0, AudioEffectsManager.volumeBoostToMillibel(20))
        assertEquals(2000, AudioEffectsManager.volumeBoostToMillibel(999))
    }

    @Test
    fun rockPresetsHitReferencePointsAndClampToDevice() {
        val fiveBand = listOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000)
        assertArrayEquals(
            intArrayOf(500, 200, -250, 300, 500),
            RockPresets.levelsFor(RockPresets.METAL, fiveBand, -1500, 1500)
        )
        // A device with a narrow range gets clamped, not overflowed.
        assertArrayEquals(
            intArrayOf(300, 300, 0, -150, -250),
            RockPresets.levelsFor(RockPresets.DOOM, fiveBand, -300, 300)
        )
        assertArrayEquals(IntArray(5), RockPresets.levelsFor(RockPresets.FLAT, fiveBand, -1500, 1500))
    }

    @Test
    fun rockPresetsInterpolateBetweenPoints() {
        val gain = RockPresets.gainAt(RockPresets.METAL, 120.0)
        // Between +5 dB at 60 Hz and +2 dB at 230 Hz.
        assertTrue(gain < 5.0 && gain > 2.0)
        assertEquals(5.0, RockPresets.gainAt(RockPresets.METAL, 20.0), 0.0001)
        assertEquals(5.0, RockPresets.gainAt(RockPresets.METAL, 20_000.0), 0.0001)
    }

    @Test
    fun durationsFormat() {
        assertEquals("--:--", formatDuration(0))
        assertEquals("0:07", formatDuration(7_400))
        assertEquals("4:05", formatDuration(245_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
        assertEquals("48 min", formatTotalDuration(48 * 60_000L))
        assertEquals("1 hr 12 min", formatTotalDuration(72 * 60_000L))
        assertEquals("2 hr", formatTotalDuration(120 * 60_000L))
        assertEquals("< 1 min", formatTotalDuration(30_000))
    }
}
