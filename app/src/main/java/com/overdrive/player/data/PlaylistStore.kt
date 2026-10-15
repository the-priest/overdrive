package com.overdrive.player.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores playlists as a small JSON file in app storage, written atomically.
 * The whole set lives in memory and is exposed as a [StateFlow].
 */
class PlaylistStore(context: Context) {

    private val file = AtomicFile(File(context.applicationContext.filesDir, "playlists.json"))
    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun get(id: Long): Playlist? = _playlists.value.firstOrNull { it.id == id }

    fun create(name: String, songIds: List<Long> = emptyList()): Playlist {
        var created: Playlist? = null
        update { list ->
            val playlist = Playlist(
                id = (list.maxOfOrNull { it.id } ?: 0L) + 1,
                name = name.trim().ifEmpty { "Playlist" },
                songIds = PlaylistOps.add(emptyList(), songIds),
                createdAt = System.currentTimeMillis()
            )
            created = playlist
            list + playlist
        }
        return created!!
    }

    fun rename(id: Long, name: String) = edit(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: Long) = update { list -> list.filterNot { it.id == id } }

    /** Returns how many songs were actually added (duplicates are skipped). */
    fun addSongs(id: Long, songIds: List<Long>): Int {
        val before = get(id)?.songIds?.size ?: return 0
        edit(id) { it.copy(songIds = PlaylistOps.add(it.songIds, songIds)) }
        return (get(id)?.songIds?.size ?: before) - before
    }

    fun removeSong(id: Long, songId: Long) =
        edit(id) { it.copy(songIds = PlaylistOps.remove(it.songIds, songId)) }

    fun insertSong(id: Long, songId: Long, index: Int) = edit(id) { playlist ->
        val without = PlaylistOps.remove(playlist.songIds, songId).toMutableList()
        without.add(index.coerceIn(0, without.size), songId)
        playlist.copy(songIds = without)
    }

    fun reorder(id: Long, visibleOrder: List<Long>) =
        edit(id) { it.copy(songIds = PlaylistOps.reorder(it.songIds, visibleOrder)) }

    private inline fun edit(id: Long, crossinline transform: (Playlist) -> Playlist) =
        update { list -> list.map { if (it.id == id) transform(it) else it } }

    @Synchronized
    private fun update(transform: (List<Playlist>) -> List<Playlist>) {
        val updated = transform(_playlists.value)
        if (updated == _playlists.value) return
        _playlists.value = updated
        save(updated)
    }

    private fun load(): List<Playlist> = try {
        if (!file.baseFile.exists()) {
            emptyList()
        } else {
            val array = JSONArray(String(file.readFully(), Charsets.UTF_8))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val songs = o.optJSONArray("songs") ?: JSONArray()
                Playlist(
                    id = o.getLong("id"),
                    name = o.optString("name", "Playlist"),
                    songIds = (0 until songs.length()).map { songs.getLong(it) },
                    createdAt = o.optLong("created", 0L)
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not read playlists", e)
        emptyList()
    }

    private fun save(list: List<Playlist>) {
        val array = JSONArray()
        list.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("created", p.createdAt)
                    .put("songs", JSONArray(p.songIds))
            )
        }
        val stream = try {
            file.startWrite()
        } catch (e: Exception) {
            Log.e(TAG, "Could not save playlists", e)
            return
        }
        try {
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            Log.e(TAG, "Could not save playlists", e)
        }
    }

    companion object {
        private const val TAG = "PlaylistStore"
    }
}
