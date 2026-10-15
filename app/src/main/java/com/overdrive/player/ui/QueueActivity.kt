@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.overdrive.player.ui

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.overdrive.player.R
import com.overdrive.player.databinding.ActivityQueueBinding
import com.overdrive.player.databinding.ItemQueueBinding
import com.overdrive.player.databinding.ItemQueueHeaderBinding
import com.overdrive.player.player.Playback
import com.overdrive.player.player.PlayerConnection
import com.overdrive.player.player.audioUri
import com.overdrive.player.util.ArtworkLoader
import com.overdrive.player.util.ThemeHelper
import com.overdrive.player.util.applySystemBarPadding
import com.overdrive.player.util.formatDuration
import com.overdrive.player.util.formatTotalDuration

/**
 * "Up Next": what is playing now and everything that will play after it, in
 * the order it will actually play (shuffle included). Drag the handle to
 * reorder, swipe to remove, tap to jump.
 */
class QueueActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQueueBinding
    private lateinit var adapter: QueueAdapter
    private lateinit var touchHelper: ItemTouchHelper

    private var dragging = false
    private var refreshPending = false
    private var dragFromSlot = -1
    private var dragSnapshot: List<Int> = emptyList()
    private var scrolledToTop = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED
                )
            ) {
                refresh(player)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding(top = true, bottom = true)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            val c = PlayerConnection.controller ?: return@setOnMenuItemClickListener false
            when (item.itemId) {
                R.id.action_shuffle -> c.shuffleModeEnabled = !c.shuffleModeEnabled
                R.id.action_clear -> clearUpcoming(c)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }

        adapter = QueueAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        touchHelper = ItemTouchHelper(TouchCallback())
        touchHelper.attachToRecyclerView(binding.recycler)

        PlayerConnection.bind(this, this, playerListener) { refresh(it) }
    }

    private fun refresh(player: Player) {
        if (dragging) {
            refreshPending = true
            return
        }
        refreshPending = false

        val current = player.currentMediaItem
        if (current == null) {
            adapter.submit(emptyList(), player)
            binding.emptyText.visibility = View.VISIBLE
            binding.toolbar.subtitle = null
            return
        }

        val upcoming = Playback.upNext(player)
        val rows = ArrayList<Row>(upcoming.size + 3)
        rows.add(Row.Header(getString(R.string.queue_now_playing), ""))
        rows.add(Row.Current(player.currentMediaItemIndex, current))
        if (upcoming.isNotEmpty()) {
            val total = upcoming.sumOf { it.item.mediaMetadata.durationMs ?: 0L }
            rows.add(
                Row.Header(
                    getString(
                        if (player.shuffleModeEnabled) R.string.queue_up_next_shuffled
                        else R.string.queue_up_next
                    ),
                    resources.getQuantityString(R.plurals.songs, upcoming.size, upcoming.size) +
                        if (total > 0) " · ${formatTotalDuration(total)}" else ""
                )
            )
            upcoming.forEach { rows.add(Row.Entry(it.index, it.item)) }
        }
        adapter.submit(rows, player)
        binding.emptyText.visibility = if (upcoming.isEmpty()) View.VISIBLE else View.GONE
        binding.toolbar.subtitle =
            resources.getQuantityString(R.plurals.songs, player.mediaItemCount, player.mediaItemCount)

        binding.toolbar.menu.findItem(R.id.action_shuffle)?.icon?.mutate()?.setTint(
            if (player.shuffleModeEnabled) ThemeHelper.accentColor(this)
            else ContextCompat.getColor(this, R.color.text_secondary)
        )
        binding.toolbar.menu.findItem(R.id.action_clear)?.isVisible = upcoming.isNotEmpty()

        if (!scrolledToTop) {
            scrolledToTop = true
            binding.recycler.scrollToPosition(0)
        }
    }

    private fun clearUpcoming(c: Player) {
        val index = c.currentMediaItemIndex
        val count = c.mediaItemCount
        if (index + 1 < count) c.removeMediaItems(index + 1, count)
        if (index > 0) c.removeMediaItems(0, index)
    }

    private fun removeEntry(entry: Row.Entry) {
        val c = PlayerConnection.controller ?: return
        if (entry.index >= c.mediaItemCount) return
        c.removeMediaItem(entry.index)
        Snackbar.make(binding.root, R.string.queue_removed, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                PlayerConnection.controller?.let { player ->
                    player.addMediaItem(entry.index.coerceAtMost(player.mediaItemCount), entry.item)
                }
            }
            .show()
    }

    // --- rows ---------------------------------------------------------------

    private sealed interface Row {
        data class Header(val title: String, val subtitle: String) : Row
        data class Current(val index: Int, val item: MediaItem) : Row
        data class Entry(val index: Int, val item: MediaItem) : Row
    }

    private inner class QueueAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        val rows = ArrayList<Row>()
        private var isPlaying = false
        private var canReorder = true

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newRows: List<Row>, player: Player) {
            rows.clear()
            rows.addAll(newRows)
            isPlaying = player.isPlaying
            canReorder = !player.shuffleModeEnabled
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = rows.size

        override fun getItemViewType(position: Int): Int =
            if (rows[position] is Row.Header) TYPE_HEADER else TYPE_ITEM

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderHolder(ItemQueueHeaderBinding.inflate(inflater, parent, false))
            } else {
                ItemHolder(ItemQueueBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).bind(row)
                is Row.Current -> (holder as ItemHolder).bind(row.item, null, current = true)
                is Row.Entry -> (holder as ItemHolder).bind(row.item, slotOf(position) + 1, current = false)
            }
        }

        /** Position of an entry within the Up Next section, 0-based. */
        fun slotOf(position: Int): Int {
            val first = rows.indexOfFirst { it is Row.Entry }
            return if (first < 0 || position < first) -1 else position - first
        }

        inner class HeaderHolder(private val b: ItemQueueHeaderBinding) :
            RecyclerView.ViewHolder(b.root) {
            fun bind(row: Row.Header) {
                b.headerTitle.text = row.title
                b.headerSubtitle.text = row.subtitle
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        inner class ItemHolder(private val b: ItemQueueBinding) : RecyclerView.ViewHolder(b.root) {

            init {
                b.root.setOnClickListener {
                    val c = PlayerConnection.controller ?: return@setOnClickListener
                    when (val row = rows.getOrNull(bindingAdapterPosition)) {
                        is Row.Current -> Playback.togglePlayPause(this@QueueActivity)
                        is Row.Entry -> {
                            c.seekToDefaultPosition(row.index)
                            c.play()
                        }
                        else -> Unit
                    }
                }
                b.dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        if (canReorder) {
                            touchHelper.startDrag(this)
                        } else {
                            Toast.makeText(
                                this@QueueActivity, R.string.queue_reorder_hint, Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    false
                }
            }

            fun bind(item: MediaItem, number: Int?, current: Boolean) {
                val context = b.root.context
                val metadata = item.mediaMetadata
                val accent = ThemeHelper.accentColor(context)
                b.title.text = metadata.title ?: ""
                b.subtitle.text = metadata.artist ?: ""
                b.duration.text = metadata.durationMs?.let { formatDuration(it) } ?: ""
                b.position.text = number?.toString() ?: ""
                b.position.visibility = if (current) View.GONE else View.VISIBLE
                b.dragHandle.visibility = if (current) View.GONE else View.VISIBLE
                b.dragHandle.alpha = if (canReorder) 1f else 0.3f
                b.playingIndicator.visibility = if (current) View.VISIBLE else View.GONE
                b.playingIndicator.alpha = if (current && !isPlaying) 0.5f else 1f
                b.art.alpha = if (current) 0.5f else 1f
                b.title.setTextColor(
                    if (current) accent else ContextCompat.getColor(context, R.color.text_primary)
                )
                b.queueRow.background = if (current) {
                    GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(ColorUtils.setAlphaComponent(accent, 0x40), 0x00000000)
                    ).apply { cornerRadius = 12 * context.resources.displayMetrics.density }
                } else {
                    ColorDrawable(ContextCompat.getColor(context, R.color.bg))
                }
                ArtworkLoader.load(context, b.art, metadata.artworkUri, item.audioUri, 128)
            }
        }
    }

    // --- drag & swipe -------------------------------------------------------

    private inner class TouchCallback : ItemTouchHelper.Callback() {

        override fun isLongPressDragEnabled(): Boolean = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val row = adapter.rows.getOrNull(viewHolder.bindingAdapterPosition)
            if (row !is Row.Entry) return 0
            return makeMovementFlags(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                ItemTouchHelper.START or ItemTouchHelper.END
            )
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0) return false
            if (adapter.rows[to] !is Row.Entry) return false
            java.util.Collections.swap(adapter.rows, from, to)
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                dragging = true
                dragFromSlot = adapter.slotOf(viewHolder.bindingAdapterPosition)
                dragSnapshot = adapter.rows.filterIsInstance<Row.Entry>().map { it.index }
                viewHolder.itemView.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                viewHolder.itemView.elevation = 12f
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            viewHolder.itemView.elevation = 0f
            if (!dragging) return
            dragging = false

            val position = viewHolder.bindingAdapterPosition
            val toSlot = if (position >= 0) adapter.slotOf(position) else dragFromSlot
            val fromSlot = dragFromSlot
            val c = PlayerConnection.controller
            if (c != null && fromSlot >= 0 && toSlot >= 0 && fromSlot != toSlot &&
                toSlot < dragSnapshot.size && fromSlot < dragSnapshot.size
            ) {
                val from = dragSnapshot[fromSlot]
                val to = Playback.queueMoveTarget(from, dragSnapshot[toSlot], toSlot < fromSlot)
                c.moveMediaItem(from, to.coerceIn(0, c.mediaItemCount - 1))
            } else if (c != null) {
                refresh(c)
            }
            if (refreshPending && c != null) refresh(c)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            val row = adapter.rows.getOrNull(position) as? Row.Entry ?: return
            adapter.rows.removeAt(position)
            adapter.notifyItemRemoved(position)
            removeEntry(row)
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}
