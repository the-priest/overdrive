package com.overdrive.player.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.overdrive.player.data.MusicRepository
import com.overdrive.player.data.Song

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist.ifBlank { "Unknown artist" })
                .setAlbumTitle(album.ifBlank { "Unknown album" })
                .setArtworkUri(MusicRepository.albumArtUri(albumId))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()

fun List<Song>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }
