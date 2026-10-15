package com.overdrive.player.audio

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Overdrive's own EQ curves. Devices expose anything from 5 to 10+ bands at
 * different centre frequencies, so each curve is defined at fixed reference
 * frequencies and interpolated (on a log-frequency scale) onto the device's
 * actual bands.
 */
object RockPresets {

    data class Preset(val id: String, val points: List<Pair<Double, Double>>)

    // (frequency Hz, gain dB)
    val FLAT = Preset("flat", listOf(60.0 to 0.0, 14_000.0 to 0.0))
    val METAL = Preset(
        "metal",
        listOf(60.0 to 5.0, 230.0 to 2.0, 910.0 to -2.5, 3_600.0 to 3.0, 14_000.0 to 5.0)
    )
    val ROCK = Preset(
        "rock",
        listOf(60.0 to 4.0, 230.0 to 2.0, 910.0 to -1.0, 3_600.0 to 2.5, 14_000.0 to 4.0)
    )
    val DJENT = Preset(
        "djent",
        listOf(60.0 to 6.0, 230.0 to 1.0, 910.0 to -3.0, 3_600.0 to 4.5, 14_000.0 to 3.0)
    )
    val DOOM = Preset(
        "doom",
        listOf(60.0 to 7.0, 230.0 to 4.5, 910.0 to 0.0, 3_600.0 to -1.5, 14_000.0 to -2.5)
    )
    val VOCAL = Preset(
        "vocal",
        listOf(60.0 to -2.0, 230.0 to -1.0, 910.0 to 3.0, 3_600.0 to 3.5, 14_000.0 to 1.0)
    )

    val ALL = listOf(FLAT, METAL, ROCK, DJENT, DOOM, VOCAL)

    /** Gain in dB of [preset] at [hz], linearly interpolated over log(frequency). */
    fun gainAt(preset: Preset, hz: Double): Double {
        val points = preset.points
        if (hz <= points.first().first) return points.first().second
        if (hz >= points.last().first) return points.last().second
        for (i in 0 until points.lastIndex) {
            val (f0, g0) = points[i]
            val (f1, g1) = points[i + 1]
            if (hz in f0..f1) {
                val t = (ln(hz) - ln(f0)) / (ln(f1) - ln(f0))
                return g0 + (g1 - g0) * t
            }
        }
        return 0.0
    }

    /**
     * Band levels in millibel for a device whose bands are centred at
     * [centerFreqsMilliHz], clamped to the device range.
     */
    fun levelsFor(preset: Preset, centerFreqsMilliHz: List<Int>, minMb: Int, maxMb: Int): IntArray =
        IntArray(centerFreqsMilliHz.size) { band ->
            val hz = centerFreqsMilliHz[band] / 1000.0
            (gainAt(preset, hz) * 100).roundToInt().coerceIn(minMb, maxMb)
        }
}
