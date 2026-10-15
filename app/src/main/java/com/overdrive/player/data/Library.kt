package com.overdrive.player.data

/**
 * Pure, Android-free library operations: grouping, sorting, filtering and
 * searching. Everything here is unit tested.
 */
object Library {

    private val CASE_INSENSITIVE = String.CASE_INSENSITIVE_ORDER

    /** Untagged values sort after real names. */
    private val NAME_ORDER = Comparator<String> { a, b ->
        when {
            a.isBlank() && b.isBlank() -> 0
            a.isBlank() -> 1
            b.isBlank() -> -1
            else -> CASE_INSENSITIVE.compare(a, b)
        }
    }

    private val BY_TITLE: Comparator<Song> =
        compareBy(CASE_INSENSITIVE) { it.title }

    /** Album running order: disc/track number, then title. */
    val TRACK_ORDER: Comparator<Song> =
        compareBy<Song> { if (it.track > 0) it.track else Int.MAX_VALUE }.then(BY_TITLE)

    fun sort(songs: List<Song>, sort: SongSort): List<Song> = when (sort) {
        SongSort.TITLE -> songs.sortedWith(BY_TITLE)
        SongSort.ARTIST -> songs.sortedWith(
            Comparator<Song> { a, b -> NAME_ORDER.compare(a.artist, b.artist) }
                .then { a, b -> NAME_ORDER.compare(a.album, b.album) }
                .then(TRACK_ORDER)
        )
        SongSort.ALBUM -> songs.sortedWith(
            Comparator<Song> { a, b -> NAME_ORDER.compare(a.album, b.album) }
                .then(TRACK_ORDER)
        )
        SongSort.RECENTLY_ADDED -> songs.sortedWith(
            compareByDescending<Song> { it.dateAdded }.then(BY_TITLE)
        )
        SongSort.DURATION -> songs.sortedWith(
            compareByDescending<Song> { it.duration }.then(BY_TITLE)
        )
    }

    fun albumKey(song: Song): String =
        if (song.albumId != 0L) "id:${song.albumId}" else "name:${song.album.lowercase()}"

    fun artistKey(song: Song): String =
        if (song.artistId != 0L) "id:${song.artistId}" else "name:${song.artist.lowercase()}"

    fun albums(songs: List<Song>): List<Album> =
        songs.groupBy(::albumKey)
            .map { (_, tracks) ->
                val first = tracks.first()
                // A compilation has many artists; show the most common one.
                val artist = tracks.groupingBy { it.artist }.eachCount()
                    .maxByOrNull { it.value }?.key.orEmpty()
                Album(
                    id = first.albumId,
                    title = first.album,
                    artist = artist,
                    songCount = tracks.size,
                    duration = tracks.sumOf { it.duration },
                    year = tracks.maxOf { it.year }
                )
            }
            .sortedWith { a, b -> NAME_ORDER.compare(a.title, b.title) }

    fun artists(songs: List<Song>): List<Artist> =
        songs.groupBy(::artistKey)
            .map { (_, tracks) ->
                val first = tracks.first()
                Artist(
                    id = first.artistId,
                    name = first.artist,
                    songCount = tracks.size,
                    albumCount = tracks.distinctBy(::albumKey).size,
                    coverAlbumId = tracks.firstOrNull { it.albumId != 0L }?.albumId ?: 0L
                )
            }
            .sortedWith { a, b -> NAME_ORDER.compare(a.name, b.name) }

    /** Songs of one album in running order. */
    fun songsOfAlbum(songs: List<Song>, albumKey: String): List<Song> =
        songs.filter { albumKey(it) == albumKey }.sortedWith(TRACK_ORDER)

    /** Songs of one artist, grouped by album (newest first) in running order. */
    fun songsOfArtist(songs: List<Song>, artistKey: String): List<Song> =
        songs.filter { artistKey(it) == artistKey }
            .sortedWith(
                compareByDescending<Song> { it.year }
                    .then { a, b -> NAME_ORDER.compare(a.album, b.album) }
                    .then(TRACK_ORDER)
            )

    /**
     * Matches every whitespace-separated word of [query] against title,
     * artist and album, so "metallica one" finds the right track.
     */
    fun search(songs: List<Song>, query: String): List<Song> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return songs
        return songs.filter { song ->
            val haystack = "${song.title} ${song.artist} ${song.album}".lowercase()
            words.all { haystack.contains(it) }
        }
    }
}
