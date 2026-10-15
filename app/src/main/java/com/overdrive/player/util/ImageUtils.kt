package com.overdrive.player.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap

object ImageUtils {

    fun toCircle(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        if (size <= 0) return source
        val output = createBitmap(size, size)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        val src = RectF(
            (size - source.width) / 2f,
            (size - source.height) / 2f,
            (size + source.width) / 2f,
            (size + source.height) / 2f
        )
        canvas.drawBitmap(source, null, src, paint)
        return output
    }
}
