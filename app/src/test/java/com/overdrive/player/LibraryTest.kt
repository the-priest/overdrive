package com.overdrive.player

import com.overdrive.player.data.Library
import com.overdrive.player.data.Song
import com.overdrive.player.data.SongSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {

    private fun song(
        id: Long,
        title: String,
        artist: String = "",
        album: String = "",
        albumId: Long = 0,
        artistId: Long = 0,
        duration: Long = 200_000,
        track: Int = 0,
        year: Int = 0,
        dateAdded: Long = 0
    ) = Song(id, title, artist, album, albumId, artistId, duration, track, year, dateAdded)

    private val library = listOf(
        song(1, "One", "Metallica", "...And Justice for All", albumId = 10, artistId = 100, track = 4, year = 1988, dateAdded = 5),
        song(2, "Blackened", "Metallica", "...And Justice for All", albumId = 10, artistId = 100, track = 1, year = 1988, dateAdded = 3),
        song(3, "Painkiller", "Judas Priest", "Painkiller", albumId = 20, artistId = 200, track = 1, year = 1990, dateAdded = 9, duration = 360_000),
        song(4, "untagged demo", dateAdded = 1),
        song(5, "Battery", "Metallica", "Master of Puppets", albumId = 30, artistId = 100, track = 1, year = 1986, dateAdded = 7),
        song(6, "another demo", dateAdded = 2)
    )

    @Test
    fun sortByTitleIsCaseInsensitive() {
        val titles = Library.sort(library, SongSort.TITLE).map { it.title }
        assertEquals(
            listOf("another demo", "Battery", "Blackened", "One", "Painkiller", "untagged demo"),
            titles
        )
    }

    @Test
    fun sortByArtistPutsUntaggedLastAndKeepsTrackOrder() {
        val ids = Library.sort(library, SongSort.ARTIST).map { it.id }
        // Judas Priest, then Metallica (albums A-Z, tracks in order), then untagged.
        assertEquals(listOf(3L, 2L, 1L, 5L, 6L, 4L), ids)
    }

    @Test
    fun sortRecentlyAddedNewestFirst() {
        assertEquals(listOf(3L, 5L, 1L, 2L, 6L, 4L), Library.sort(library, SongSort.RECENTLY_ADDED).map { it.id })
    }

    @Test
    fun sortByDurationLongestFirst() {
        assertEquals(3L, Library.sort(library, SongSort.DURATION).first().id)
    }

    @Test
    fun albumsGroupByIdAndUntaggedFilesFormOneAlbum() {
        val albums = Library.albums(library)
        assertEquals(4, albums.size)
        val justice = albums.first { it.id == 10L }
        assertEquals(2, justice.songCount)
        assertEquals(1988, justice.year)
        assertEquals("Metallica", justice.artist)
        // Untagged bucket sorts last and can be opened again by its key.
        val unknown = albums.last()
        assertEquals("", unknown.title)
        assertEquals(2, Library.songsOfAlbum(library, unknown.key).size)
    }

    @Test
    fun songsOfAlbumAreInTrackOrder() {
        val songs = Library.songsOfAlbum(library, "id:10")
        assertEquals(listOf("Blackened", "One"), songs.map { it.title })
    }

    @Test
    fun artistsCountAlbumsAndSongs() {
        val metallica = Library.artists(library).first { it.id == 100L }
        assertEquals(3, metallica.songCount)
        assertEquals(2, metallica.albumCount)
    }

    @Test
    fun songsOfArtistNewestAlbumFirst() {
        val titles = Library.songsOfArtist(library, "id:100").map { it.title }
        assertEquals(listOf("Blackened", "One", "Battery"), titles)
    }

    @Test
    fun searchMatchesAllWordsAcrossFields() {
        assertEquals(listOf(1L), Library.search(library, "metallica one").map { it.id })
        assertEquals(listOf(3L), Library.search(library, "  PRIEST  ").map { it.id })
        assertTrue(Library.search(library, "slayer").isEmpty())
        assertEquals(library, Library.search(library, "   "))
    }
}
