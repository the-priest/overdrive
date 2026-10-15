package com.overdrive.player.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Process-wide connection to [PlaybackService].
 *
 * Screens never hold on to a [MediaController] themselves. They [bind] a
 * [Player.Listener] to their lifecycle instead: it is attached while the
 * screen is started and transparently re-attached if the service is killed
 * and the controller has to reconnect. All calls happen on the main thread.
 */
object PlayerConnection {

    private const val TAG = "PlayerConnection"

    private var appContext: Context? = null
    private var current: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null

    private val pending = ArrayList<(MediaController) -> Unit>()
    private val bindings = LinkedHashSet<Binding>()

    private class Binding(
        val listener: Player.Listener,
        val onConnected: (MediaController) -> Unit
    )

    /** The connected controller, or null while (re)connecting. */
    val controller: MediaController?
        get() = current?.takeIf { it.isConnected }

    /** Starts the connection early so the first tap plays instantly. */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (controller == null) connect()
    }

    /** Runs [block] now if connected, otherwise as soon as the connection is up. */
    fun withController(context: Context, block: (MediaController) -> Unit) {
        appContext = context.applicationContext
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pending.add(block)
            connect()
        }
    }

    /**
     * Attaches [listener] to the controller while [owner] is started and calls
     * [onConnected] every time a controller becomes available to it.
     */
    fun bind(
        owner: LifecycleOwner,
        context: Context,
        listener: Player.Listener,
        onConnected: (MediaController) -> Unit = {}
    ) {
        appContext = context.applicationContext
        val binding = Binding(listener, onConnected)
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                bindings.add(binding)
                val c = controller
                if (c != null) attach(c, binding) else connect()
            }

            override fun onStop(owner: LifecycleOwner) {
                bindings.remove(binding)
                current?.removeListener(binding.listener)
            }
        })
    }

    private fun attach(c: MediaController, binding: Binding) {
        c.removeListener(binding.listener)
        c.addListener(binding.listener)
        binding.onConnected(c)
    }

    private fun connect() {
        if (future != null) return
        val context = appContext ?: return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val newFuture = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (current === controller) {
                        current = null
                        future = null
                        controller.release()
                        // Someone is still watching: reconnect right away.
                        if (bindings.isNotEmpty()) connect()
                    }
                }
            })
            .buildAsync()
        future = newFuture
        newFuture.addListener({
            val c = try {
                newFuture.get()
            } catch (e: Exception) {
                Log.e(TAG, "Could not connect to the playback service", e)
                null
            }
            if (c == null) {
                future = null
                pending.clear()
                return@addListener
            }
            current = c
            bindings.toList().forEach { attach(c, it) }
            val blocks = pending.toList()
            pending.clear()
            blocks.forEach { it(c) }
        }, ContextCompat.getMainExecutor(context))
    }
}
