package com.overdrive.player.ui

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R

/** Name prompts and the "Add to playlist" picker. */
object PlaylistDialogs {

    /** Asks for a playlist name and hands it to [onName] (never blank). */
    fun promptName(
        context: Context,
        title: Int,
        initial: String = "",
        positive: Int = R.string.playlist_create,
        onName: (String) -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        val input = TextInputEditText(context).apply {
            setText(initial)
            setSelection(initial.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
        }
        val layout = TextInputLayout(context).apply {
            hint = context.getString(R.string.playlist_name_hint)
            addView(input)
        }
        val container = FrameLayout(context).apply {
            val pad = (20 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(layout)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(positive) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) onName(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    onName(name)
                    dialog.dismiss()
                }
                true
            } else {
                false
            }
        }
        dialog.show()
        input.requestFocus()
    }

    /** Lets the user pick (or create) a playlist and adds [songIds] to it. */
    fun addToPlaylist(context: Context, songIds: List<Long>, suggestedName: String = "") {
        if (songIds.isEmpty()) return
        val store = OverdriveApp.from(context).playlists
        val playlists = store.playlists.value.sortedBy { it.name.lowercase() }
        val labels = listOf(context.getString(R.string.playlist_new_ellipsis)) + playlists.map { it.name }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.add_to_playlist)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    val fallback = context.getString(R.string.playlist_default_name, playlists.size + 1)
                    promptName(context, R.string.playlist_new, suggestedName.ifBlank { fallback }) { name ->
                        store.create(name, songIds)
                        toast(context, context.getString(R.string.playlist_created, name))
                    }
                } else {
                    val playlist = playlists[which - 1]
                    val added = store.addSongs(playlist.id, songIds)
                    toast(
                        context,
                        if (added == 0) context.getString(R.string.playlist_already_added, playlist.name)
                        else context.resources.getQuantityString(
                            R.plurals.playlist_added, added, added, playlist.name
                        )
                    )
                }
            }
            .show()
    }

    fun confirmDelete(context: Context, name: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.playlist_delete_title, name))
            .setMessage(R.string.playlist_delete_message)
            .setPositiveButton(R.string.playlist_delete) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(context: Context, text: String) =
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
