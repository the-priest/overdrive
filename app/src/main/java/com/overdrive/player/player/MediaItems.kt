@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.overdrive.player.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.overdrive.player.data.Song

const val EXTRA_ALBUM_ID = "overdrive.album_id"
const val EXTRA_ARTIST_ID = "overdrive.artist_id"
const val EXTRA_ALBUM_RAW = "overdrive.album"
const val EXTRA_ARTIST_RAW = "overdrive.artist"

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist.ifBlank { "Unknown artist" })
                .setAlbumTitle(album.ifBlank { "Unknown album" })
                .setAlbumArtist(artist.ifBlank { null })
                .setArtworkUri(albumArtUri)
                .setTrackNumber(if (track > 0) track % 1000 else null)
                .setReleaseYear(if (year > 0) year else null)
                .setDurationMs(if (duration > 0) duration else null)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(
                    Bundle().apply {
                        putLong(EXTRA_ALBUM_ID, albumId)
                        putLong(EXTRA_ARTIST_ID, artistId)
                        putString(EXTRA_ALBUM_RAW, album)
                        putString(EXTRA_ARTIST_RAW, artist)
                    }
                )
                .build()
        )
        .build()

fun List<Song>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }

/** The MediaStore id of the song behind this item, or -1. */
val MediaItem.songId: Long get() = mediaId.toLongOrNull() ?: -1L

/** Content URI of the audio file, usable for embedded artwork lookups. */
val MediaItem.audioUri: android.net.Uri?
    get() = localConfiguration?.uri
        ?: songId.takeIf { it >= 0 }?.let { com.overdrive.player.data.MusicRepository.songUri(it) }
