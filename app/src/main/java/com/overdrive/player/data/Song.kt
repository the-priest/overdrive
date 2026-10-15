package com.overdrive.player.data

import android.net.Uri

/**
 * A single audio track discovered in the device media store.
 *
 * Kept free of Android types in its constructor so the library logic in
 * [Library] can be unit tested on the JVM; URIs are derived on demand.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val artistId: Long,
    val duration: Long,
    /** MediaStore track number: disc * 1000 + track, 0 when unknown. */
    val track: Int = 0,
    val year: Int = 0,
    /** Seconds since the epoch. */
    val dateAdded: Long = 0L
) {
    val uri: Uri get() = MusicRepository.songUri(id)
    val albumArtUri: Uri get() = MusicRepository.albumArtUri(albumId)
}

/** A group of songs sharing an album. [title] is blank for untagged files. */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val duration: Long,
    val year: Int
) {
    /** Stable identity, also valid for the "unknown album" bucket (id 0). */
    val key: String get() = if (id != 0L) "id:$id" else "name:${title.lowercase()}"
    val albumArtUri: Uri get() = MusicRepository.albumArtUri(id)
}

/** A group of songs sharing an artist. [name] is blank for untagged files. */
data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    /** An album of this artist, used for the artwork thumbnail. */
    val coverAlbumId: Long
) {
    val key: String get() = if (id != 0L) "id:$id" else "name:${name.lowercase()}"
    val albumArtUri: Uri get() = MusicRepository.albumArtUri(coverAlbumId)
}

/** Sort orders offered on the Songs tab. */
enum class SongSort {
    TITLE,
    ARTIST,
    ALBUM,
    RECENTLY_ADDED,
    DURATION;

    companion object {
        fun fromOrdinal(value: Int): SongSort = entries.getOrElse(value) { TITLE }
    }
}
