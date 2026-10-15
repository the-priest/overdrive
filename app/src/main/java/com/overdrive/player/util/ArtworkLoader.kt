package com.overdrive.player.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import com.overdrive.player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Small hand-rolled artwork loader. Reads album art out of the media store and
 * caches decoded bitmaps in memory. No third-party image library required.
 *
 * Misses are remembered too: without that, every scroll past a song with no
 * artwork would re-open the audio file to look for embedded art.
 */
object ArtworkLoader {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val scope = CoroutineScope(SupervisorJob() + decodeDispatcher)

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val misses: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun cacheSizeKb(): Int {
        val max = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (max / 8).coerceAtLeast(4096)
    }

    /** Forget everything, e.g. after a library rescan. */
    fun clear() {
        cache.evictAll()
        misses.clear()
    }

    /**
     * Loads artwork into [target]. [onResult] runs on the main thread with the
     * bitmap (or null when the track has none), only if [target] still shows
     * the same request.
     */
    fun load(
        context: Context,
        target: ImageView,
        albumArtUri: Uri?,
        audioUri: Uri?,
        sizePx: Int,
        placeholder: Int = R.drawable.placeholder_art,
        circle: Boolean = false,
        onResult: ((Bitmap?) -> Unit)? = null
    ) {
        val key = "${albumArtUri ?: audioUri}|$audioUri#$sizePx$circle"
        if (target.tag == key && target.drawable != null && onResult == null) return
        target.tag = key

        cache.get(key)?.let {
            target.setImageBitmap(it)
            onResult?.invoke(it)
            return
        }

        target.setImageResource(placeholder)
        if (key in misses) {
            onResult?.invoke(null)
            return
        }

        val appContext = context.applicationContext
        scope.launch {
            val bitmap = decode(appContext, albumArtUri, audioUri, sizePx, circle)
            if (bitmap != null) cache.put(key, bitmap) else misses.add(key)
            withContext(Dispatchers.Main) {
                if (target.tag == key) {
                    if (bitmap != null) target.setImageBitmap(bitmap)
                    onResult?.invoke(bitmap)
                }
            }
        }
    }

    private fun decode(
        context: Context,
        albumArtUri: Uri?,
        audioUri: Uri?,
        sizePx: Int,
        circle: Boolean
    ): Bitmap? {
        val raw = decodeRaw(context, albumArtUri, audioUri, sizePx) ?: return null
        return if (circle) ImageUtils.toCircle(raw) else raw
    }

    private fun decodeRaw(
        context: Context,
        albumArtUri: Uri?,
        audioUri: Uri?,
        sizePx: Int
    ): Bitmap? {
        val resolver = context.contentResolver
        if (albumArtUri != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    return resolver.loadThumbnail(albumArtUri, Size(sizePx, sizePx), null)
                }
            }
            runCatching {
                resolver.openInputStream(albumArtUri)?.use { input ->
                    decodeSampled(input.readBytes(), sizePx)?.let { return it }
                }
            }
        }
        if (audioUri != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    return resolver.loadThumbnail(audioUri, Size(sizePx, sizePx), null)
                }
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, audioUri)
                retriever.embeddedPicture?.let { bytes ->
                    return decodeSampled(bytes, sizePx)
                }
            } catch (_: Exception) {
                // no embedded art
            } finally {
                runCatching { retriever.release() }
            }
        }
        return null
    }

    private fun decodeSampled(bytes: ByteArray, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx &&
            bounds.outHeight / (sample * 2) >= sizePx
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
