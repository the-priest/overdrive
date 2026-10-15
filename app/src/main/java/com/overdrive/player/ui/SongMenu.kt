package com.overdrive.player.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.data.Library
import com.overdrive.player.data.Song
import com.overdrive.player.player.Playback

/** The per-song overflow menu, shared by every list in the app. */
object SongMenu {

    fun show(
        activity: Activity,
        song: Song,
        anchor: View,
        showAlbum: Boolean = true,
        showArtist: Boolean = true
    ) {
        val prefs = OverdriveApp.from(activity).prefs
        val popup = PopupMenu(activity, anchor)
        popup.inflate(R.menu.menu_song)
        val favorite = prefs.isFavorite(song.id)
        popup.menu.findItem(R.id.action_favorite).setTitle(
            if (favorite) R.string.remove_favorite else R.string.add_favorite
        )
        popup.menu.findItem(R.id.action_go_to_album).isVisible = showAlbum
        popup.menu.findItem(R.id.action_go_to_artist).isVisible = showArtist
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_play -> Playback.start(activity, listOf(song), 0)
                R.id.action_play_next -> {
                    Playback.playNext(activity, listOf(song))
                    toast(activity, R.string.queued_next)
                }
                R.id.action_add_queue -> {
                    Playback.enqueue(activity, listOf(song))
                    toast(activity, R.string.queued_end)
                }
                R.id.action_favorite -> {
                    prefs.setFavorite(song.id, !favorite)
                    toast(activity, if (favorite) R.string.removed_favorite else R.string.added_favorite)
                }
                R.id.action_go_to_album -> openAlbum(activity, song)
                R.id.action_go_to_artist -> openArtist(activity, song)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    fun openAlbum(context: Context, song: Song) =
        openAlbum(context, Library.albumKey(song), song.album, song.albumId)

    fun openArtist(context: Context, song: Song) =
        openArtist(context, Library.artistKey(song), song.artist)

    fun openAlbum(context: Context, key: String, rawTitle: String, albumId: Long) {
        context.startActivity(
            Intent(context, CollectionActivity::class.java)
                .putExtra(CollectionActivity.EXTRA_TYPE, CollectionActivity.TYPE_ALBUM)
                .putExtra(CollectionActivity.EXTRA_KEY, key)
                .putExtra(CollectionActivity.EXTRA_ID, albumId)
                .putExtra(
                    CollectionActivity.EXTRA_TITLE,
                    rawTitle.ifBlank { context.getString(R.string.unknown_album) }
                )
        )
    }

    fun openArtist(context: Context, key: String, rawName: String) {
        context.startActivity(
            Intent(context, CollectionActivity::class.java)
                .putExtra(CollectionActivity.EXTRA_TYPE, CollectionActivity.TYPE_ARTIST)
                .putExtra(CollectionActivity.EXTRA_KEY, key)
                .putExtra(
                    CollectionActivity.EXTRA_TITLE,
                    rawName.ifBlank { context.getString(R.string.unknown_artist) }
                )
        )
    }

    private fun toast(context: Context, text: Int) =
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
