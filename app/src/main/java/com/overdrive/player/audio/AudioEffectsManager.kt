package com.overdrive.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.overdrive.player.prefs.SettingsStore

/**
 * Owns the platform audio effects and keeps them in sync with [SettingsStore].
 *
 * Effects are attached to the audio session of the player, so they only apply
 * to Overdrive's own output and never touch other apps. The volume boost is a
 * [LoudnessEnhancer] mapped from 0 to +20 dB at the 200% setting.
 */
class AudioEffectsManager(private val prefs: SettingsStore) {

    @Volatile
    private var sessionId: Int = 0

    var equalizer: Equalizer? = null
        private set

    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    /** True once the effects are bound to a real audio session. */
    val isAttached: Boolean get() = equalizer != null

    /** Whether the device supports a bass boost strength other than on/off. */
    val bassBoostSupported: Boolean
        get() = bassBoost?.strengthSupported ?: true

    val virtualizerSupported: Boolean
        get() = virtualizer?.strengthSupported ?: true

    @Synchronized
    fun attach(newSessionId: Int) {
        if (newSessionId <= 0) return
        if (newSessionId == sessionId && equalizer != null) return
        releaseInternal()

        sessionId = newSessionId
        equalizer = create("Equalizer") { Equalizer(0, newSessionId) }
        bassBoost = create("BassBoost") { BassBoost(0, newSessionId) }
        virtualizer = create("Virtualizer") { Virtualizer(0, newSessionId) }
        loudness = create("LoudnessEnhancer") { LoudnessEnhancer(newSessionId) }

        applyEqualizer()
        applyBassBoost()
        applyVirtualizer()
        applyVolumeBoost()
    }

    private inline fun <T> create(name: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$name not available on this device", e)
            null
        }

    @Synchronized
    fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
        sessionId = 0
    }

    // --- Equalizer ----------------------------------------------------------

    @Synchronized
    fun setEqualizerEnabled(enabled: Boolean) {
        prefs.equalizerEnabled = enabled
        applyEqualizer()
    }

    @Synchronized
    fun selectPreset(index: Int) {
        prefs.eqPreset = index
        applyEqualizer()
        // Remember the preset's curve so tweaking one band later keeps the rest.
        equalizer?.let { eq -> runCatching { prefs.setBandLevels(readBands(eq)) } }
    }

    @Synchronized
    fun setBandLevel(band: Int, level: Int) {
        if (prefs.eqPreset >= 0) {
            // Leaving a preset: start the custom curve from the preset's shape
            // instead of silently resetting every other band to flat.
            equalizer?.let { eq -> runCatching { prefs.setBandLevels(readBands(eq)) } }
            prefs.eqPreset = -1
        }
        prefs.setBandLevel(band, level)
        runCatching { equalizer?.setBandLevel(band.toShort(), level.toShort()) }
    }

    /** Applies a whole custom curve, e.g. one of the built-in rock presets. */
    @Synchronized
    fun setCustomCurve(levels: IntArray) {
        prefs.eqPreset = -1
        prefs.setBandLevels(levels)
        applyEqualizer()
    }

    @Synchronized
    fun resetEqualizer(bandCount: Int) {
        setCustomCurve(IntArray(bandCount))
    }

    private fun readBands(eq: Equalizer): IntArray =
        IntArray(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()).toInt() }

    @Synchronized
    fun applyEqualizer() {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = prefs.equalizerEnabled
            val preset = prefs.eqPreset
            if (preset in 0 until eq.numberOfPresets.toInt()) {
                eq.usePreset(preset.toShort())
            } else {
                val range = eq.bandLevelRange
                for (band in 0 until eq.numberOfBands.toInt()) {
                    val level = prefs.bandLevel(band).coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(band.toShort(), level.toShort())
                }
            }
        }.onFailure { Log.w(TAG, "Could not apply equalizer", it) }
    }

    // --- Bass boost / virtualizer ------------------------------------------

    @Synchronized
    fun setBassBoost(strength: Int) {
        prefs.bassBoost = strength
        applyBassBoost()
    }

    @Synchronized
    fun applyBassBoost() {
        val bb = bassBoost ?: return
        runCatching {
            bb.enabled = prefs.bassBoost > 0
            bb.setStrength(prefs.bassBoost.toShort())
        }
    }

    @Synchronized
    fun setVirtualizer(strength: Int) {
        prefs.virtualizer = strength
        applyVirtualizer()
    }

    @Synchronized
    fun applyVirtualizer() {
        val v = virtualizer ?: return
        runCatching {
            v.enabled = prefs.virtualizer > 0
            v.setStrength(prefs.virtualizer.toShort())
        }
    }

    // --- Volume boost (LoudnessEnhancer) -----------------------------------

    @Synchronized
    fun setVolumeBoost(percent: Int) {
        prefs.volumeBoost = percent
        applyVolumeBoost()
    }

    /** Quick toggle: off, or back to the last level the user picked. */
    @Synchronized
    fun toggleVolumeBoost() {
        setVolumeBoost(if (prefs.volumeBoost > 100) 100 else prefs.lastActiveBoost)
    }

    @Synchronized
    fun applyVolumeBoost() {
        val le = loudness ?: return
        runCatching {
            le.setTargetGain(volumeBoostToMillibel(prefs.volumeBoost))
            le.enabled = prefs.volumeBoost > 100
        }
    }

    companion object {
        private const val TAG = "AudioEffects"

        /**
         * Maps 100%..200% onto 0..2000 millibel, i.e. up to +20 dB of gain.
         * That is roughly four times the perceived loudness at the top end.
         */
        fun volumeBoostToMillibel(percent: Int): Int {
            val clamped = percent.coerceIn(100, 200)
            return (clamped - 100) * 20
        }
    }
}
