package com.overdrive.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's audio library through [MediaStore]. Everything here is
 * read-only: the app never writes to the user's music collection.
 */
class MusicRepository(private val context: Context) {

    suspend fun loadSongs(minDurationMs: Long = 0L): List<Song> = withContext(Dispatchers.IO) {
        val songs = ArrayList<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = StringBuilder(
            "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_MUSIC} IS NULL)"
        )
        val args = ArrayList<String>()
        if (minDurationMs > 0) {
            selection.append(" AND ${MediaStore.Audio.Media.DURATION} >= ?")
            args.add(minDurationMs.toString())
        }

        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection.toString(),
                args.toTypedArray(),
                sortOrder
            )
        }.getOrNull()?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol).orEmpty().ifBlank { "Unknown track" }
                val artist = cleanField(cursor.getString(artistCol))
                val album = cleanField(cursor.getString(albumCol))
                val albumId = cursor.getLong(albumIdCol)
                val artistId = cursor.getLong(artistIdCol)
                val duration = cursor.getLong(durationCol)
                val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L

                songs.add(
                    Song(
                        id = id,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        ),
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        artistId = artistId,
                        duration = duration,
                        path = path,
                        dateAdded = dateAdded
                    )
                )
            }
        }
        songs
    }

    suspend fun songsOfAlbum(albumId: Long): List<Song> =
        loadSongs().filter { it.albumId == albumId }

    suspend fun songsOfArtist(artistId: Long): List<Song> =
        loadSongs().filter { it.artistId == artistId }

    private fun cleanField(value: String?): String {
        val v = value?.trim().orEmpty()
        return if (v.isBlank() || v == "<unknown>") "" else v
    }

    companion object {
        private val ALBUM_ART_BASE =
            Uri.parse("content://media/external/audio/albumart")

        fun albumArtUri(albumId: Long): Uri =
            ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

        /** Builds album and artist collections from a flat song list. */
        fun toAlbums(songs: List<Song>): List<Album> =
            songs.groupBy { if (it.albumId != 0L) it.albumId else it.album.hashCode().toLong() }
                .map { (key, tracks) ->
                    val first = tracks.first()
                    Album(
                        id = first.albumId,
                        name = first.album.ifBlank { "Unknown album" },
                        artist = first.artist.ifBlank { "Unknown artist" },
                        albumArtUri = albumArtUri(first.albumId),
                        songCount = tracks.size,
                        sortKey = key.toString()
                    )
                }
                .sortedBy { it.name.lowercase() }

        fun toArtists(songs: List<Song>): List<Artist> =
            songs.groupBy { if (it.artistId != 0L) it.artistId else it.artist.hashCode().toLong() }
                .map { (_, tracks) ->
                    val first = tracks.first()
                    Artist(
                        id = first.artistId,
                        name = first.artist.ifBlank { "Unknown artist" },
                        albumArtUri = albumArtUri(first.albumId),
                        songCount = tracks.size
                    )
                }
                .sortedBy { it.name.lowercase() }
    }
}
