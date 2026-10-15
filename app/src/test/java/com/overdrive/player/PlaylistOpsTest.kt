package com.overdrive.player

import com.overdrive.player.data.Playlist
import com.overdrive.player.data.PlaylistOps
import com.overdrive.player.data.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistOpsTest {

    @Test
    fun addSkipsDuplicatesAndKeepsOrder() {
        assertEquals(listOf(1L, 2L, 3L, 4L), PlaylistOps.add(listOf(1L, 2L), listOf(2L, 3L, 3L, 4L)))
        assertEquals(listOf(5L), PlaylistOps.add(emptyList(), listOf(5L, 5L)))
    }

    @Test
    fun removeDropsTheSong() {
        assertEquals(listOf(1L, 3L), PlaylistOps.remove(listOf(1L, 2L, 3L), 2L))
        assertEquals(listOf(1L), PlaylistOps.remove(listOf(1L), 9L))
    }

    @Test
    fun reorderKeepsHiddenSongsAtTheEnd() {
        // Song 9 is in the playlist but missing from the library right now.
        val existing = listOf(1L, 9L, 2L, 3L)
        assertEquals(listOf(3L, 1L, 2L, 9L), PlaylistOps.reorder(existing, listOf(3L, 1L, 2L)))
        // Ids that are not in the playlist are ignored.
        assertEquals(listOf(2L, 1L), PlaylistOps.reorder(listOf(1L, 2L), listOf(2L, 7L, 1L)))
    }

    @Test
    fun resolveDropsMissingSongsInPlaylistOrder() {
        fun song(id: Long) = Song(id, "t$id", "", "", 0, 0, 1000L * id)
        val library = listOf(song(1), song(2), song(3)).associateBy { it.id }
        val summary = PlaylistOps.resolve(Playlist(1, "Mix", listOf(3L, 99L, 1L)), library)
        assertEquals(listOf(3L, 1L), summary.songs.map { it.id })
        assertEquals(4000L, summary.duration)
    }
}
