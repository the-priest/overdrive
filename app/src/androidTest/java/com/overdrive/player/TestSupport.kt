package com.overdrive.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Seeds the device's media store with a few generated tracks. */
object TestMedia {

    val TITLES = listOf(
        "Overdrive Test 1 Thunder",
        "Overdrive Test 2 Riff",
        "Overdrive Test 3 Doom"
    )
    const val FOLDER = "Overdrive Test"

    fun ensureTracks(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        TITLES.forEachIndexed { index, title ->
            val name = "$title.wav"
            val exists = resolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                arrayOf(name),
                null
            )?.use { it.count > 0 } ?: false
            if (exists) return@forEachIndexed

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$FOLDER")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(collection, values)) { "insert failed" }
            resolver.openOutputStream(uri)!!.use { it.write(wav(110.0 * (index + 2), seconds = 20)) }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    /** 16-bit mono PCM WAV with a quiet sine tone. */
    private fun wav(frequency: Double, seconds: Int): ByteArray {
        val rate = 22_050
        val samples = rate * seconds
        val data = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            data.putShort((sin(2 * PI * frequency * i / rate) * 3000).toInt().toShort())
        }
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2)
        }
        out.write(header.array())
        out.write(data.array())
        return out.toByteArray()
    }
}

/** Saves a screenshot to Pictures/OverdriveScreens so CI can pull it. */
object Screenshots {
    fun take(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(700)
        val full = instrumentation.uiAutomation.takeScreenshot() ?: return
        val bitmap = Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true)
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OverdriveScreens")
        }
        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
        ) ?: return
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }
}

fun waitUntil(what: String, timeoutMs: Long = 20_000, check: () -> Boolean) {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < end) {
        if (check()) return
        SystemClock.sleep(200)
    }
    throw AssertionError("Timed out waiting for $what")
}

fun <T> onMain(block: () -> T): T {
    var result: Result<T>? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
    return result!!.getOrThrow()
}
