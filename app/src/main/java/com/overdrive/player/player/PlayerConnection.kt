package com.overdrive.player.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide connection to [PlaybackService]. Activities ask for the
 * [MediaController] once and keep it for the lifetime of the process, so the
 * music never stops just because a screen goes away.
 */
object PlayerConnection {

    @Volatile
    var controller: MediaController? = null
        private set

    private var future: ListenableFuture<MediaController>? = null
    private val pending = CopyOnWriteArrayList<(MediaController) -> Unit>()

    @Synchronized
    fun init(context: Context) {
        if (future != null) return
        val app = context.applicationContext
        val token = SessionToken(
            app,
            ComponentName(app, PlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(app, token).buildAsync()
        future = controllerFuture
        controllerFuture.addListener(
            {
                runCatching {
                    val c = controllerFuture.get()
                    controller = c
                    pending.forEach { it(c) }
                    pending.clear()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    /** Runs [block] as soon as the controller is connected. */
    fun withController(context: Context, block: (MediaController) -> Unit) {
        init(context)
        val current = controller
        if (current != null) block(current) else pending.add(block)
    }

    fun release() {
        controller = null
        future?.let { runCatching { MediaController.releaseFuture(it) } }
        future = null
    }
}
