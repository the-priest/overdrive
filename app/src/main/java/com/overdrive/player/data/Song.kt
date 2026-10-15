package com.overdrive.player.data

import android.net.Uri

/** A single audio track discovered in the device media store. */
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val artistId: Long,
    val duration: Long,
    val path: String,
    val dateAdded: Long
)

/** A group of songs sharing an album. */
data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val albumArtUri: Uri,
    val songCount: Int,
    val sortKey: String
)

/** A group of songs sharing an artist. */
data class Artist(
    val id: Long,
    val name: String,
    val albumArtUri: Uri,
    val songCount: Int
)
