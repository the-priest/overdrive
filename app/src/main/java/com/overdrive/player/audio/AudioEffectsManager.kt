package com.overdrive.player.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.overdrive.player.prefs.SettingsStore

/**
 * Owns the platform audio effects and keeps them in sync with [SettingsStore].
 *
 * Effects are attached to the audio session of the player, so they only apply
 * to Overdrive's own output and never touch other apps. The volume boost is a
 * [LoudnessEnhancer] mapped from 0 to +20 dB at the 200% setting.
 */
class AudioEffectsManager(
    context: Context,
    private val prefs: SettingsStore
) {

    @Volatile
    private var sessionId: Int = 0

    var equalizer: Equalizer? = null
        private set

    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    /** True once the effects are bound to a real audio session. */
    val isAttached: Boolean get() = equalizer != null

    @Synchronized
    fun attach(newSessionId: Int) {
        if (newSessionId <= 0) return
        if (newSessionId == sessionId && equalizer != null) return
        releaseInternal()

        sessionId = newSessionId
        equalizer = runCatching { Equalizer(0, newSessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, newSessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, newSessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(newSessionId) }.getOrNull()

        applyEqualizer()
        applyBassBoost()
        applyVirtualizer()
        applyVolumeBoost()
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
    }

    @Synchronized
    fun setBandLevel(band: Int, level: Int) {
        prefs.eqPreset = -1
        prefs.setBandLevel(band, level)
        runCatching { equalizer?.setBandLevel(band.toShort(), level.toShort()) }
    }

    @Synchronized
    fun resetEqualizer(bandCount: Int) {
        prefs.eqPreset = -1
        for (band in 0 until bandCount) prefs.setBandLevel(band, 0)
        applyEqualizer()
    }

    @Synchronized
    fun applyEqualizer() {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = prefs.equalizerEnabled
            val preset = prefs.eqPreset
            if (preset in 0 until eq.numberOfPresets.toInt()) {
                eq.usePreset(preset.toShort())
            } else {
                for (band in 0 until eq.numberOfBands.toInt()) {
                    eq.setBandLevel(band.toShort(), prefs.bandLevel(band).toShort())
                }
            }
        }
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

    @Synchronized
    fun applyVolumeBoost() {
        val le = loudness ?: return
        runCatching {
            le.enabled = prefs.volumeBoost > 100
            le.setTargetGain(volumeBoostToMillibel(prefs.volumeBoost))
        }
    }

    companion object {
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
