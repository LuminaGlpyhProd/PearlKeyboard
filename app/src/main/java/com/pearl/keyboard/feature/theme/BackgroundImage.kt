package com.pearl.keyboard.feature.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import java.io.File

/**
 * Stores and processes the user's keyboard-background image (#12).
 *
 * The picked image is copied into the app's private files dir (so we don't depend on
 * persistable URI permissions). [processed] returns a blurred/brightness-adjusted,
 * downsampled bitmap suitable for drawing behind the keys. The blur is a cheap
 * downscale-then-upscale (no RenderScript dependency) — fast and good enough behind
 * translucent keys.
 */
object BackgroundImage {

    private const val FILE_NAME = "keyboard_bg.jpg"
    private const val TARGET_W = 720

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Copy a picked image into private storage; returns its path or null on failure. */
    fun savePicked(context: Context, uri: Uri): String? = runCatching {
        val dst = file(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        dst.absolutePath
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** Decode + blur + brightness. Returns null if [path] is empty/unreadable. */
    fun processed(context: Context, path: String, blur: Int, brightnessPct: Int): Bitmap? {
        if (path.isEmpty()) return null
        var bmp = decodeSampled(path, TARGET_W) ?: return null

        if (blur > 0) {
            val scale = (1f - blur / 30f).coerceIn(0.08f, 1f)
            val w = (bmp.width * scale).toInt().coerceAtLeast(1)
            val h = (bmp.height * scale).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(bmp, w, h, true)
            bmp = Bitmap.createScaledBitmap(small, bmp.width, bmp.height, true)
        }

        if (brightnessPct != 100) {
            val factor = brightnessPct / 100f
            val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
            val cm = ColorMatrix(
                floatArrayOf(
                    factor, 0f, 0f, 0f, 0f,
                    0f, factor, 0f, 0f, 0f,
                    0f, 0f, factor, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            Canvas(out).drawBitmap(bmp, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
            bmp = out
        }
        return bmp
    }

    private fun decodeSampled(path: String, targetW: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > targetW * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }
}
