package com.overdrive.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's audio library through [MediaStore]. Everything here is
 * read-only: the app never writes to the user's music collection.
 */
class MusicRepository(context: Context) {

    private val resolver = context.applicationContext.contentResolver

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
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
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

        try {
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection.toString(),
                args.toTypedArray(),
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    songs.add(
                        Song(
                            id = cursor.getLong(idCol),
                            title = cursor.getString(titleCol).orEmpty().trim()
                                .ifBlank { "Unknown track" },
                            artist = cleanField(cursor.getString(artistCol)),
                            album = cleanField(cursor.getString(albumCol)),
                            albumId = cursor.getLong(albumIdCol),
                            artistId = cursor.getLong(artistIdCol),
                            duration = cursor.getLong(durationCol),
                            track = if (trackCol >= 0) cursor.getInt(trackCol) else 0,
                            year = if (yearCol >= 0) cursor.getInt(yearCol) else 0,
                            dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read the media store", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Media store query failed", e)
        }
        songs
    }

    private fun cleanField(value: String?): String {
        val v = value?.trim().orEmpty()
        return if (v.isBlank() || v == "<unknown>") "" else v
    }

    companion object {
        private const val TAG = "MusicRepository"

        private val ALBUM_ART_BASE: Uri =
            Uri.parse("content://media/external/audio/albumart")

        fun songUri(id: Long): Uri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

        fun albumArtUri(albumId: Long): Uri =
            ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)
    }
}
