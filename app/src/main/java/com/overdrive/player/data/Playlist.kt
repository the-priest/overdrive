package com.overdrive.player.data

/** A user playlist. Song ids are unique within a playlist. */
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long = 0L
)

/** A playlist resolved against the current library (missing songs dropped). */
data class PlaylistSummary(val playlist: Playlist, val songs: List<Song>) {
    val duration: Long get() = songs.sumOf { it.duration }
}

/** Pure list operations behind [PlaylistStore], unit tested. */
object PlaylistOps {

    /** Appends [added], skipping songs that are already in the playlist. */
    fun add(existing: List<Long>, added: List<Long>): List<Long> {
        val present = existing.toHashSet()
        return existing + added.filter { present.add(it) }
    }

    fun remove(existing: List<Long>, songId: Long): List<Long> = existing.filter { it != songId }

    /**
     * Applies a new order for the songs the user can see. Ids that are not
     * visible (e.g. files that are currently missing) keep their place at the
     * end instead of being dropped.
     */
    fun reorder(existing: List<Long>, visibleOrder: List<Long>): List<Long> {
        val present = existing.toHashSet()
        val ordered = visibleOrder.filter { it in present }.distinct()
        val orderedSet = ordered.toHashSet()
        return ordered + existing.filter { it !in orderedSet }
    }

    /** Resolves ids to songs in playlist order. */
    fun resolve(playlist: Playlist, library: Map<Long, Song>): PlaylistSummary =
        PlaylistSummary(playlist, playlist.songIds.mapNotNull { library[it] })
}
