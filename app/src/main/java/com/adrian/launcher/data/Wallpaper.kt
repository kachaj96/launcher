package com.adrian.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import kotlin.math.max

/** The launcher draws its own wallpaper from a private copy of the photo the user picked. */
class Wallpaper(private val context: Context) {

    private val file: File get() = File(context.filesDir, "wallpaper.jpg")

    fun exists(): Boolean = file.exists()

    /** Decodes the stored photo, downsampled to roughly the screen height. */
    fun load(): Bitmap? {
        if (!file.exists()) return null
        val target = context.resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * Copies the picked photo in, re-encoded and capped at 2400 px on the long edge.
     * ImageDecoder applies the EXIF rotation, so portrait phone photos stay upright.
     */
    fun save(uri: Uri): Boolean = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val longEdge = max(w, h)
            if (longEdge > 2400) {
                val f = 2400f / longEdge
                decoder.setTargetSize((w * f).toInt(), (h * f).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    }.getOrDefault(false)

    fun clear() {
        file.delete()
    }
}
