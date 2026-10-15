package com.overdrive.player.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Thin, typed wrapper around [SharedPreferences]. Everything the app remembers
 * lives here: playback behaviour, equalizer state, the volume boost level, the
 * last queue and the list of favourite tracks.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var equalizerEnabled: Boolean
        get() = prefs.getBoolean(K_EQ_ENABLED, false)
        set(v) = prefs.edit { putBoolean(K_EQ_ENABLED, v) }

    /** Index into the device equalizer preset list, or -1 for a custom curve. */
    var eqPreset: Int
        get() = prefs.getInt(K_EQ_PRESET, -1)
        set(v) = prefs.edit { putInt(K_EQ_PRESET, v) }

    fun bandLevel(band: Int): Int = prefs.getInt(K_EQ_BAND + band, 0)

    fun setBandLevel(band: Int, level: Int) =
        prefs.edit { putInt(K_EQ_BAND + band, level) }

    /** Writes a full equalizer curve in a single commit. */
    fun setBandLevels(levels: IntArray) = prefs.edit {
        levels.forEachIndexed { band, level -> putInt(K_EQ_BAND + band, level) }
    }

    /** 0..1000 */
    var bassBoost: Int
        get() = prefs.getInt(K_BASS, 0).coerceIn(0, 1000)
        set(v) = prefs.edit { putInt(K_BASS, v.coerceIn(0, 1000)) }

    /** 0..1000 */
    var virtualizer: Int
        get() = prefs.getInt(K_VIRT, 0).coerceIn(0, 1000)
        set(v) = prefs.edit { putInt(K_VIRT, v.coerceIn(0, 1000)) }

    /** Volume boost as a percentage: 100 (off) .. 200 (max). */
    var volumeBoost: Int
        get() = prefs.getInt(K_BOOST, 100).coerceIn(100, 200)
        set(v) = prefs.edit {
            val clamped = v.coerceIn(100, 200)
            putInt(K_BOOST, clamped)
            if (clamped > 100) putInt(K_BOOST_LAST, clamped)
        }

    /** The last boost level the user had switched on, used by the quick toggle. */
    val lastActiveBoost: Int
        get() = prefs.getInt(K_BOOST_LAST, 150).coerceIn(101, 200)

    var pauseOnDisconnect: Boolean
        get() = prefs.getBoolean(K_PAUSE_DISCONNECT, true)
        set(v) = prefs.edit { putBoolean(K_PAUSE_DISCONNECT, v) }

    /** Turn shuffle on every time the app starts fresh. */
    var shuffleDefault: Boolean
        get() = prefs.getBoolean(K_SHUFFLE, false)
        set(v) = prefs.edit { putBoolean(K_SHUFFLE, v) }

    /** The shuffle state the player was last left in. */
    var shuffleEnabled: Boolean
        get() = prefs.getBoolean(K_SHUFFLE_STATE, false)
        set(v) = prefs.edit { putBoolean(K_SHUFFLE_STATE, v) }

    /** Reopen the last queue (paused) when the app starts. */
    var resumeLastSession: Boolean
        get() = prefs.getBoolean(K_RESUME, true)
        set(v) = prefs.edit { putBoolean(K_RESUME, v) }

    /** Tracks shorter than this many seconds are hidden from the library. */
    var minDurationSec: Int
        get() = prefs.getInt(K_MIN_LEN, 0)
        set(v) = prefs.edit { putInt(K_MIN_LEN, v) }

    /** 0 = red, 1 = amber, 2 = blue. */
    var accent: Int
        get() = prefs.getInt(K_ACCENT, 0)
        set(v) = prefs.edit { putInt(K_ACCENT, v) }

    /** Player.REPEAT_MODE_* */
    var repeatMode: Int
        get() = prefs.getInt(K_REPEAT, 0)
        set(v) = prefs.edit { putInt(K_REPEAT, v) }

    /** Ordinal of [com.overdrive.player.data.SongSort]. */
    var songSort: Int
        get() = prefs.getInt(K_SORT, 0)
        set(v) = prefs.edit { putInt(K_SORT, v) }

    // --- Last session -------------------------------------------------------

    fun saveQueue(ids: List<Long>, index: Int, positionMs: Long) = prefs.edit {
        putString(K_QUEUE, ids.joinToString(","))
        putInt(K_QUEUE_INDEX, index)
        putLong(K_QUEUE_POS, positionMs)
    }

    fun savePosition(index: Int, positionMs: Long) = prefs.edit {
        putInt(K_QUEUE_INDEX, index)
        putLong(K_QUEUE_POS, positionMs)
    }

    fun savedQueueIds(): List<Long> =
        prefs.getString(K_QUEUE, "").orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }

    val savedQueueIndex: Int get() = prefs.getInt(K_QUEUE_INDEX, 0)
    val savedQueuePosition: Long get() = prefs.getLong(K_QUEUE_POS, 0L)

    // --- Favourites ---------------------------------------------------------

    fun favoriteIds(): MutableSet<Long> =
        (prefs.getStringSet(K_FAVS, emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toMutableSet()

    fun isFavorite(id: Long): Boolean =
        (prefs.getStringSet(K_FAVS, emptySet()) ?: emptySet()).contains(id.toString())

    fun setFavorite(id: Long, favorite: Boolean) {
        // Copy: the set returned by getStringSet must never be modified in place.
        val current = HashSet(prefs.getStringSet(K_FAVS, emptySet()) ?: emptySet())
        if (favorite) current.add(id.toString()) else current.remove(id.toString())
        prefs.edit { putStringSet(K_FAVS, current) }
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
        private const val K_BOOST_LAST = "volume_boost_last"
        private const val K_SHUFFLE = "shuffle_default"
        private const val K_SHUFFLE_STATE = "shuffle_state"
        private const val K_RESUME = "resume_last_session"
        private const val K_ACCENT = "accent"
        private const val K_REPEAT = "repeat_mode"
        private const val K_SORT = "song_sort"
        private const val K_QUEUE = "queue_ids"
        private const val K_QUEUE_INDEX = "queue_index"
        private const val K_QUEUE_POS = "queue_position"

        const val K_PAUSE_DISCONNECT = "pause_on_disconnect"
        const val K_MIN_LEN = "min_len"
        const val K_FAVS = "favorites"
    }
}
