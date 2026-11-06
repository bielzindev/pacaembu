package com.pacaembu.ui

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/** Procedural iOS 6 textures (no image assets): linen, pinstripes and paper. */
object Textures {
    val linen: ImageBitmap by lazy {
        val w = 128; val h = 128
        val r = Random(6)
        val px = IntArray(w * h)
        val rows = FloatArray(h) { r.nextFloat() * 10f - 5f }
        val cols = FloatArray(w) { r.nextFloat() * 10f - 5f }
        for (y in 0 until h) for (x in 0 until w) {
            val v = (52 + rows[y] + cols[x] + r.nextFloat() * 14f - 7f).toInt().coerceIn(0, 250)
            px[y * w + x] = (0xFF shl 24) or (v shl 16) or ((v + 1) shl 8) or (v + 3).coerceAtMost(255)
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** The blue-grey vertical pinstripes behind grouped tables. */
    val pinstripe: ImageBitmap by lazy {
        val w = 10; val h = 4
        val px = IntArray(w * h) { i -> if (i % w < 6) 0xFFC5CCD4.toInt() else 0xFFCBD2D8.toInt() }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    val felt: ImageBitmap by lazy {
        val w = 96; val h = 96; val r = Random(3)
        val px = IntArray(w * h) {
            val v = r.nextInt(-10, 10)
            val g = (40 + v).coerceIn(0, 255)
            (0xFF shl 24) or ((g - 8).coerceAtLeast(0) shl 16) or (g shl 8) or (g - 6).coerceAtLeast(0)
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

fun Modifier.tiled(img: ImageBitmap): Modifier = drawBehind {
    drawRect(ShaderBrush(ImageShader(img, TileMode.Repeated, TileMode.Repeated)))
}

fun Modifier.linen() = tiled(Textures.linen)
fun Modifier.pinstripe() = tiled(Textures.pinstripe)
