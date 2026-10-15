package com.overdrive.player.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin, typed wrapper around [SharedPreferences]. Everything the app remembers
 * lives here: playback behaviour, equalizer state, the volume boost level and
 * the list of favourite tracks.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val raw: SharedPreferences get() = prefs

    var equalizerEnabled: Boolean
        get() = prefs.getBoolean(K_EQ_ENABLED, false)
        set(v) = prefs.edit().putBoolean(K_EQ_ENABLED, v).apply()

    /** Index into the device equalizer preset list, or -1 for a custom curve. */
    var eqPreset: Int
        get() = prefs.getInt(K_EQ_PRESET, -1)
        set(v) = prefs.edit().putInt(K_EQ_PRESET, v).apply()

    fun bandLevel(band: Int): Int = prefs.getInt(K_EQ_BAND + band, 0)

    fun setBandLevel(band: Int, level: Int) =
        prefs.edit().putInt(K_EQ_BAND + band, level).apply()

    /** 0..1000 */
    var bassBoost: Int
        get() = prefs.getInt(K_BASS, 0).coerceIn(0, 1000)
        set(v) = prefs.edit().putInt(K_BASS, v.coerceIn(0, 1000)).apply()

    /** 0..1000 */
    var virtualizer: Int
        get() = prefs.getInt(K_VIRT, 0).coerceIn(0, 1000)
        set(v) = prefs.edit().putInt(K_VIRT, v.coerceIn(0, 1000)).apply()

    /** Volume boost as a percentage: 100 (off) .. 200 (max). */
    var volumeBoost: Int
        get() = prefs.getInt(K_BOOST, 100).coerceIn(100, 200)
        set(v) = prefs.edit().putInt(K_BOOST, v.coerceIn(100, 200)).apply()

    var pauseOnDisconnect: Boolean
        get() = prefs.getBoolean(K_PAUSE_DISCONNECT, true)
        set(v) = prefs.edit().putBoolean(K_PAUSE_DISCONNECT, v).apply()

    var shuffleDefault: Boolean
        get() = prefs.getBoolean(K_SHUFFLE, false)
        set(v) = prefs.edit().putBoolean(K_SHUFFLE, v).apply()

    /** Tracks shorter than this many seconds are hidden from the library. */
    var minDurationSec: Int
        get() = prefs.getInt(K_MIN_LEN, 0)
        set(v) = prefs.edit().putInt(K_MIN_LEN, v).apply()

    /** 0 = red, 1 = amber, 2 = blue. */
    var accent: Int
        get() = prefs.getInt(K_ACCENT, 0)
        set(v) = prefs.edit().putInt(K_ACCENT, v).apply()

    /** Player.REPEAT_MODE_* */
    var repeatMode: Int
        get() = prefs.getInt(K_REPEAT, 0)
        set(v) = prefs.edit().putInt(K_REPEAT, v).apply()

    // --- Favourites ---------------------------------------------------------

    fun favoriteIds(): MutableSet<Long> =
        (prefs.getStringSet(K_FAVS, emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toMutableSet()

    fun isFavorite(id: Long): Boolean =
        (prefs.getStringSet(K_FAVS, emptySet()) ?: emptySet()).contains(id.toString())

    fun setFavorite(id: Long, favorite: Boolean) {
        val current = (prefs.getStringSet(K_FAVS, emptySet()) ?: emptySet()).toMutableSet()
        if (favorite) current.add(id.toString()) else current.remove(id.toString())
        prefs.edit().putStringSet(K_FAVS, current).apply()
    }

    fun registerOnChange(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterOnChange(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        private const val FILE = "overdrive_settings"
        private const val K_EQ_ENABLED = "eq_enabled"
        private const val K_EQ_PRESET = "eq_preset"
        private const val K_EQ_BAND = "eq_band_"
        private const val K_BASS = "bass_boost"
        private const val K_VIRT = "virtualizer"
        private const val K_BOOST = "volume_boost"
        private const val K_PAUSE_DISCONNECT = "pause_on_disconnect"
        private const val K_SHUFFLE = "shuffle_default"
        private const val K_MIN_LEN = "min_len"
        private const val K_ACCENT = "accent"
        private const val K_REPEAT = "repeat_mode"
        private const val K_FAVS = "favorites"
    }
}
