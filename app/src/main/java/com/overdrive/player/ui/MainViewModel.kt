package com.overdrive.player.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.overdrive.player.OverdriveApp
import com.overdrive.player.data.Album
import com.overdrive.player.data.Artist
import com.overdrive.player.data.Library
import com.overdrive.player.data.MusicRepository
import com.overdrive.player.data.Song
import com.overdrive.player.data.SongSort
import com.overdrive.player.prefs.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository(app)
    private val prefs = OverdriveApp.from(app).prefs

    private val allSongs = MutableLiveData<List<Song>>(emptyList())
    private val allAlbums = MutableLiveData<List<Album>>(emptyList())
    private val allArtists = MutableLiveData<List<Artist>>(emptyList())

    val favorites = MutableLiveData<List<Song>>(emptyList())
    val loading = MutableLiveData(false)

    /** Null until the permission has been checked at least once. */
    val hasPermission = MutableLiveData<Boolean?>(null)

    /** MediaStore id of the track in the player, for list highlighting. */
    val nowPlayingId = MutableLiveData(-1L)

    private val query = MutableLiveData("")
    private val _sort = MutableLiveData(SongSort.fromOrdinal(prefs.songSort))
    val sort: LiveData<SongSort> = _sort

    val visibleSongs = MediatorLiveData<List<Song>>()
    val visibleAlbums = MediatorLiveData<List<Album>>()
    val visibleArtists = MediatorLiveData<List<Artist>>()

    val isSearching: Boolean get() = !query.value.isNullOrBlank()

    private var favoriteIds: MutableSet<Long> = prefs.favoriteIds()
    private var loadJob: Job? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            // Favorites can be toggled from any screen; keep the tab current.
            SettingsStore.K_FAVS -> refreshFavorites()
            SettingsStore.K_MIN_LEN -> load()
        }
    }

    init {
        visibleSongs.addSource(allSongs) { recomputeSongs() }
        visibleSongs.addSource(query) { recomputeSongs() }
        visibleSongs.addSource(_sort) { recomputeSongs() }
        visibleAlbums.addSource(allAlbums) { recomputeAlbums() }
        visibleAlbums.addSource(query) { recomputeAlbums() }
        visibleArtists.addSource(allArtists) { recomputeArtists() }
        visibleArtists.addSource(query) { recomputeArtists() }
        prefs.registerOnChange(prefsListener)
    }

    override fun onCleared() {
        prefs.unregisterOnChange(prefsListener)
    }

    fun load() {
        loadJob?.cancel()
        loading.value = true
        loadJob = viewModelScope.launch {
            val list = repository.loadSongs(prefs.minDurationSec * 1000L)
            allSongs.value = list
            allAlbums.value = Library.albums(list)
            allArtists.value = Library.artists(list)
            refreshFavorites()
            loading.value = false
        }
    }

    fun songs(): List<Song> = allSongs.value.orEmpty()

    fun setQuery(value: String) {
        if (query.value != value) query.value = value
    }

    fun setSort(value: SongSort) {
        prefs.songSort = value.ordinal
        _sort.value = value
    }

    private fun recomputeSongs() {
        val sorted = Library.sort(allSongs.value.orEmpty(), _sort.value ?: SongSort.TITLE)
        visibleSongs.value = Library.search(sorted, query.value.orEmpty())
    }

    private fun recomputeAlbums() {
        val q = query.value.orEmpty().trim().lowercase()
        val all = allAlbums.value.orEmpty()
        visibleAlbums.value = if (q.isEmpty()) all else all.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    private fun recomputeArtists() {
        val q = query.value.orEmpty().trim().lowercase()
        val all = allArtists.value.orEmpty()
        visibleArtists.value = if (q.isEmpty()) all else all.filter {
            it.name.lowercase().contains(q)
        }
    }

    private fun refreshFavorites() {
        favoriteIds = prefs.favoriteIds()
        favorites.value = Library.sort(
            allSongs.value.orEmpty().filter { favoriteIds.contains(it.id) },
            SongSort.TITLE
        )
    }

    fun isFavorite(id: Long): Boolean = favoriteIds.contains(id)
}
