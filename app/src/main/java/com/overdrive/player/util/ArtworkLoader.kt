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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Small hand-rolled artwork loader. Reads album art out of the media store and
 * caches decoded bitmaps in memory. No third-party image library required.
 */
object ArtworkLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private fun cacheSizeKb(): Int {
        val max = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (max / 8).coerceAtLeast(4096)
    }

    fun load(
        context: Context,
        target: ImageView,
        albumArtUri: Uri?,
        audioUri: Uri?,
        sizePx: Int,
        placeholder: Int = R.drawable.ic_music_note,
        circle: Boolean = false
    ) {
        val key = "${albumArtUri ?: audioUri}#$sizePx$circle"
        target.tag = key

        cache.get(key)?.let {
            target.setImageBitmap(it)
            return
        }

        target.setImageResource(placeholder)

        val appContext = context.applicationContext
        scope.launch {
            val bitmap = decode(appContext, albumArtUri, audioUri, sizePx, circle)
            if (bitmap != null) {
                cache.put(key, bitmap)
                withContext(Dispatchers.Main) {
                    if (target.tag == key) target.setImageBitmap(bitmap)
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
        if (albumArtUri != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    return context.contentResolver.loadThumbnail(
                        albumArtUri, Size(sizePx, sizePx), null
                    )
                }
            }
            runCatching {
                context.contentResolver.openInputStream(albumArtUri)?.use { input ->
                    return decodeSampled(input.readBytes(), sizePx)
                }
            }
        }
        if (audioUri != null) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, audioUri)
                retriever.embeddedPicture?.let { bytes ->
                    return decodeSampled(bytes, sizePx)
                }
            } catch (_: Throwable) {
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
