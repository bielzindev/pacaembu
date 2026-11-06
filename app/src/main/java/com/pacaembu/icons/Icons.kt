package com.pacaembu.icons

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.pacaembu.core.AppRepository
import com.pacaembu.core.BuiltIn
import com.pacaembu.core.BuiltInResolver
import com.pacaembu.core.IconStyle
import com.pacaembu.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Global clock for live icons (Clock hands, Calendar date). Updated by the home screen. */
object LiveClock {
    val minute = mutableLongStateOf(System.currentTimeMillis() / 60000)
    /** Bumped when icon settings change (gloss, pack) to drop cached bitmaps. */
    val iconEpoch = mutableIntStateOf(0)
}

object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun clear() { cache.evictAll(); IconPacks.reset(); LiveClock.iconEpoch.intValue++ }

    fun get(k: String) = cache.get(k)
    fun put(k: String, b: ImageBitmap) { cache.put(k, b) }

    /** Renders the icon of [key] ("builtin:x" / "app:pkg/cls") at [px] pixels. Blocking. */
    fun render(c: Context, key: String, px: Int): Bitmap? {
        val pack = Prefs.iconPack.value
        BuiltIn.byKey[key]?.let { b ->
            // A pack can theme a shortcut through the app it opens (e.g. the browser).
            if (pack.isNotEmpty() && b.internal == null) {
                BuiltInResolver.targetComponent(c, b)?.let { cn ->
                    IconPacks.drawableFor(c, pack, cn)?.let { return IconFactory.app(it, px, true) }
                }
            }
            return IconFactory.builtIn(c, b, px)
        }
        if (!key.startsWith("app:")) return null
        val cn = ComponentName.unflattenFromString(key.removePrefix("app:")) ?: return null
        IconPacks.drawableFor(c, pack, cn)?.let { return IconFactory.app(it, px, true) }
        val info = AppRepository.activityInfo(cn) ?: return null
        val d = runCatching { info.getIcon(android.util.DisplayMetrics.DENSITY_XXXHIGH) }.getOrNull() ?: return null
        return IconFactory.app(d, px, false)
    }
}

/** An iOS 6 icon for an app key, rendered off the main thread and cached. */
@Composable
fun AppIcon(key: String, size: Dp, modifier: Modifier = Modifier) {
    val c = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val b = BuiltIn.byKey[key]
    val live = when (b?.style) {
        IconStyle.CLOCK -> LiveClock.minute.longValue
        IconStyle.CALENDAR -> LiveClock.minute.longValue / (60 * 24)
        IconStyle.WEATHER -> Prefs.lastTemp.value.hashCode().toLong()
        else -> 0L
    }
    val cacheKey = "$key|$px|$live|${LiveClock.iconEpoch.intValue}|${Prefs.gloss.value}|${Prefs.iconPack.value}|${AppRepository.version.intValue}"
    val img by produceState(IconCache.get(cacheKey), cacheKey) {
        val hit = IconCache.get(cacheKey)
        if (hit != null) { value = hit; return@produceState }
        val made = withContext(Dispatchers.Default) {
            runCatching { IconCache.render(c, key, px) }.getOrNull()?.asImageBitmap()
        }
        if (made != null) { IconCache.put(cacheKey, made); value = made }
    }
    Box(modifier.size(size)) {
        img?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}
