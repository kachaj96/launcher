package com.adrian.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The launcher draws its own wallpaper. Two private files:
 * - `wallpaper_src.jpg`: the picked photo (EXIF-rotated, max 3000 px), kept for re-adjusting
 * - `wallpaper.jpg`: the part chosen in the editor, cut to the screen's shape
 */
class Wallpaper(private val context: Context) {

    private val file: File get() = File(context.filesDir, "wallpaper.jpg")
    private val sourceFile: File get() = File(context.filesDir, "wallpaper_src.jpg")

    fun exists(): Boolean = file.exists()

    /** Decodes the wallpaper, downsampled to roughly the screen height. */
    fun load(): Bitmap? = decodeSampled(file, context.resources.displayMetrics.heightPixels)

    /** The photo to adjust: the original if kept, otherwise the current wallpaper. */
    fun loadSource(): Bitmap? =
        decodeSampled(if (sourceFile.exists()) sourceFile else file, 3000)

    /**
     * Stores the picked photo as the editing source. ImageDecoder applies the EXIF
     * rotation, so portrait phone photos stay upright.
     */
    fun saveSource(uri: Uri): Boolean = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val longEdge = max(w, h)
            if (longEdge > 3000) {
                val f = 3000f / longEdge
                decoder.setTargetSize((w * f).toInt(), (h * f).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        sourceFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        true
    }.getOrDefault(false)

    /** Cuts [crop] (in [source] pixels) out of the source and saves it at screen size. */
    fun applyCrop(source: Bitmap, crop: Rect): Boolean = runCatching {
        val r = Rect(
            crop.left.coerceIn(0, source.width - 1),
            crop.top.coerceIn(0, source.height - 1),
            crop.right.coerceIn(1, source.width),
            crop.bottom.coerceIn(1, source.height),
        )
        val cut = Bitmap.createBitmap(source, r.left, r.top, r.width(), r.height())
        val metrics = context.resources.displayMetrics
        val targetH = metrics.heightPixels.coerceAtLeast(1)
        val scaled = if (cut.height > targetH) {
            val f = targetH.toFloat() / cut.height
            Bitmap.createScaledBitmap(cut, (cut.width * f).roundToInt().coerceAtLeast(1), targetH, true)
        } else {
            cut
        }
        // Keep the source when the user first adjusts an older wallpaper without one.
        if (!sourceFile.exists() && file.exists()) file.copyTo(sourceFile, overwrite = true)
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    }.getOrDefault(false)

    fun clear() {
        file.delete()
        sourceFile.delete()
    }

    private fun decodeSampled(f: File, targetHeight: Int): Bitmap? {
        if (!f.exists()) return null
        val target = targetHeight.coerceAtLeast(1)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
