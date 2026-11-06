package com.pacaembu.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.pacaembu.R
import com.pacaembu.core.BuiltIn
import com.pacaembu.core.IconStyle
import com.pacaembu.core.Prefs
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws iOS 6 style icons into bitmaps: rounded square (radius ≈ 17.5% like the 57pt icon),
 * vertical gradient, glyph from the Ionicons set with a soft engraved shadow, and the classic
 * glossy highlight on the upper half.
 */
object IconFactory {
    const val RADIUS = 0.175f

    fun roundRect(s: Float) = Path().apply { addRoundRect(RectF(0f, 0f, s, s), s * RADIUS, s * RADIUS, Path.Direction.CW) }

    fun builtIn(c: Context, b: BuiltIn, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val s = size.toFloat()
        cv.save()
        cv.clipPath(roundRect(s))
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.shader = LinearGradient(0f, 0f, 0f, s, b.top.toInt(), b.bottom.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, s, s, bg)
        when (b.style) {
            IconStyle.GLYPH -> glyph(c, cv, b.glyph, b.glyphColor.toInt(), s, 0.62f)
            IconStyle.SETTINGS -> {
                glyph(c, cv, R.drawable.ion_cog, 0x66FFFFFF, s, 0.80f, dy = s * 0.012f)
                glyph(c, cv, R.drawable.ion_cog, b.glyphColor.toInt(), s, 0.80f)
            }
            IconStyle.PHOTOS -> {
                glyph(c, cv, R.drawable.ion_flower, 0xFFFFC414.toInt(), s, 0.74f)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B3E12.toInt() }
                cv.drawCircle(s / 2, s / 2, s * 0.085f, p)
            }
            IconStyle.WEATHER -> weather(c, cv, s)
            IconStyle.CALENDAR -> calendar(cv, s)
            IconStyle.CLOCK -> clock(cv, s)
            IconStyle.NOTES -> notes(cv, s)
        }
        if (Prefs.gloss.value) gloss(cv, s)
        cv.restore()
        border(cv, s)
        return bmp
    }

    /** A third-party icon (or an icon pack drawable) in the iOS 6 shape. */
    fun app(d: Drawable, size: Int, fromPack: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val s = size.toFloat()
        if (fromPack) {
            // Icon packs already have their own shape and shine: draw as they are.
            d.setBounds(0, 0, size, size); d.draw(cv); return bmp
        }
        cv.save()
        cv.clipPath(roundRect(s))
        if (d is AdaptiveIconDrawable) {
            // Layers are 108dp with the visible 72dp in the middle: draw them 1.5x so they fill.
            val o = (s * 0.25f).toInt()
            d.background?.let { it.setBounds(-o, -o, size + o, size + o); it.draw(cv) }
            d.foreground?.let { it.setBounds(-o, -o, size + o, size + o); it.draw(cv) }
        } else {
            cv.drawColor(Color.WHITE)
            d.setBounds(0, 0, size, size); d.draw(cv)
        }
        if (Prefs.gloss.value) gloss(cv, s)
        cv.restore()
        border(cv, s)
        return bmp
    }

    private fun glyph(c: Context, cv: Canvas, res: Int, color: Int, s: Float, frac: Float, dy: Float = 0f) {
        val d = ContextCompat.getDrawable(c, res)?.mutate() ?: return
        val g = (s * frac).toInt()
        val l = ((s - g) / 2).toInt()
        val t = ((s - g) / 2 + dy).toInt()
        // engraved shadow
        if (dy == 0f && Color.alpha(color) == 255) {
            d.setTint(0x55000000)
            d.setBounds(l, t + (s * 0.02f).toInt(), l + g, t + g + (s * 0.02f).toInt()); d.draw(cv)
        }
        d.setTint(color)
        d.setBounds(l, t, l + g, t + g); d.draw(cv)
    }

    fun gloss(cv: Canvas, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, s * 0.5f, 0x88FFFFFF.toInt(), 0x14FFFFFF, Shader.TileMode.CLAMP)
        cv.drawOval(RectF(-s * 0.32f, -s * 0.62f, s * 1.32f, s * 0.50f), p)
    }

    private fun border(cv: Canvas, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = s / 90f; color = 0x40000000
        }
        val h = p.strokeWidth / 2
        cv.drawRoundRect(RectF(h, h, s - h, s - h), s * RADIUS, s * RADIUS, p)
    }

    private fun weather(c: Context, cv: Canvas, s: Float) {
        glyph(c, cv, R.drawable.ion_sunny, 0xFFFFD43B.toInt(), s, 0.46f, dy = -s * 0.12f)
        val d = ContextCompat.getDrawable(c, R.drawable.ion_cloudy)?.mutate() ?: return
        d.setTint(Color.WHITE)
        d.setBounds((s * 0.30f).toInt(), (s * 0.18f).toInt(), (s * 0.90f).toInt(), (s * 0.78f).toInt()); d.draw(cv)
        val t = Prefs.lastTemp.value.ifEmpty { "" }
        if (t.isNotEmpty()) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = s * 0.26f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER; setShadowLayer(s * 0.02f, 0f, s * 0.015f, 0x66000000)
            }
            cv.drawText(t, s / 2, s * 0.90f, p)
        }
    }

    private fun calendar(cv: Canvas, s: Float) {
        val now = Calendar.getInstance()
        val head = Paint(Paint.ANTI_ALIAS_FLAG)
        head.shader = LinearGradient(0f, 0f, 0f, s * 0.27f, 0xFFE95B4F.toInt(), 0xFFB61E15.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, s, s * 0.27f, head)
        val wd = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: ""
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = s * 0.15f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(s * 0.01f, 0f, -s * 0.01f, 0x66000000)
        }
        cv.drawText(wd.replaceFirstChar { it.titlecase() }, s / 2, s * 0.205f, tp)
        val np = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF303030.toInt(); textSize = s * 0.58f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        cv.drawText(now.get(Calendar.DAY_OF_MONTH).toString(), s / 2, s * 0.83f, np)
    }

    private fun clock(cv: Canvas, s: Float) {
        val cx = s / 2; val cy = s / 2; val r = s * 0.40f
        val face = Paint(Paint.ANTI_ALIAS_FLAG)
        face.shader = RadialGradient(cx, cy - r * 0.3f, r * 1.3f, Color.WHITE, 0xFFD9D9D9.toInt(), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, face)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF222222.toInt(); strokeWidth = s * 0.018f }
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val r1 = if (i % 3 == 0) r * 0.78f else r * 0.86f
            cv.drawLine(cx + (sin(a) * r1).toFloat(), cy - (cos(a) * r1).toFloat(),
                cx + (sin(a) * r * 0.94f).toFloat(), cy - (cos(a) * r * 0.94f).toFloat(), tick)
        }
        val now = Calendar.getInstance()
        val m = now.get(Calendar.MINUTE); val h = now.get(Calendar.HOUR) + m / 60f
        val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt(); strokeCap = Paint.Cap.ROUND }
        fun line(angleDeg: Double, len: Float, w: Float, p: Paint) {
            p.strokeWidth = w
            val a = Math.toRadians(angleDeg)
            cv.drawLine(cx, cy, cx + (sin(a) * len).toFloat(), cy - (cos(a) * len).toFloat(), p)
        }
        line(h * 30.0, r * 0.55f, s * 0.045f, hand)
        line(m * 6.0, r * 0.82f, s * 0.032f, hand)
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD8352A.toInt(); strokeCap = Paint.Cap.ROUND }
        line(now.get(Calendar.SECOND) * 6.0, r * 0.86f, s * 0.012f, red)
        cv.drawCircle(cx, cy, s * 0.025f, red)
    }

    private fun notes(cv: Canvas, s: Float) {
        val leather = Paint(Paint.ANTI_ALIAS_FLAG)
        leather.shader = LinearGradient(0f, 0f, 0f, s * 0.24f, 0xFF8C5A2E.toInt(), 0xFF5E3816.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, s, s * 0.24f, leather)
        val stitch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE9C99A.toInt(); strokeWidth = s * 0.012f; style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(s * 0.04f, s * 0.03f), 0f)
        }
        cv.drawLine(0f, s * 0.20f, s, s * 0.20f, stitch)
        val line = Paint().apply { color = 0x664A6FBF; strokeWidth = s * 0.012f }
        var y = s * 0.40f
        while (y < s) { cv.drawLine(0f, y, s, y, line); y += s * 0.13f }
        val margin = Paint().apply { color = 0x88D23A3A.toInt(); strokeWidth = s * 0.012f }
        cv.drawLine(s * 0.16f, s * 0.24f, s * 0.16f, s, margin)
    }
}
