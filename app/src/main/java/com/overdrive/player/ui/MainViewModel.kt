package com.overdrive.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.overdrive.player.OverdriveApp
import com.overdrive.player.data.Album
import com.overdrive.player.data.Artist
import com.overdrive.player.data.MusicRepository
import com.overdrive.player.data.Song
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository(app)
    private val prefs = OverdriveApp.from(app).prefs

    val songs = MutableLiveData<List<Song>>(emptyList())
    val albums = MutableLiveData<List<Album>>(emptyList())
    val artists = MutableLiveData<List<Artist>>(emptyList())
    val favorites = MutableLiveData<List<Song>>(emptyList())
    val loading = MutableLiveData(false)

    private val query = MutableLiveData("")

    val filteredSongs = MediatorLiveData<List<Song>>()

    private var favoriteIds: MutableSet<Long> = prefs.favoriteIds()

    init {
        filteredSongs.addSource(songs) { recomputeFilter() }
        filteredSongs.addSource(query) { recomputeFilter() }
    }

    fun load() {
        loading.value = true
        viewModelScope.launch {
            val minMs = prefs.minDurationSec * 1000L
            val list = repository.loadSongs(minMs)
            songs.value = list
            albums.value = MusicRepository.toAlbums(list)
            artists.value = MusicRepository.toArtists(list)
            favoriteIds = prefs.favoriteIds()
            favorites.value = list.filter { favoriteIds.contains(it.id) }
            loading.value = false
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    private fun recomputeFilter() {
        val q = query.value?.trim()?.lowercase().orEmpty()
        val all = songs.value ?: emptyList()
        filteredSongs.value = if (q.isEmpty()) {
            all
        } else {
            all.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
            }
        }
    }

    fun isFavorite(id: Long): Boolean = favoriteIds.contains(id)

    fun toggleFavorite(song: Song) {
        val nowFavorite = !favoriteIds.contains(song.id)
        prefs.setFavorite(song.id, nowFavorite)
        if (nowFavorite) favoriteIds.add(song.id) else favoriteIds.remove(song.id)
        favorites.value = (songs.value ?: emptyList()).filter { favoriteIds.contains(it.id) }
    }

    fun minDurationLabel(): String = when (prefs.minDurationSec) {
        0 -> "Off"
        60 -> "1 min"
        120 -> "2 min"
        300 -> "5 min"
        else -> "${prefs.minDurationSec} s"
    }

    fun setMinDuration(seconds: Int) {
        prefs.minDurationSec = seconds
        load()
    }
}
