package com.pacaembu.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val pkg: String, val label: String)

/**
 * Third-party icon packs in the common ADW / Nova / GO format: an app that declares one of the
 * theme intents and ships an appfilter.xml mapping ComponentInfo{pkg/cls} -> drawable name.
 * This lets people use any iOS 6 style icon pack they install.
 */
object IconPacks {
    private val THEME_ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME", "com.anddoes.launcher.THEME",
    )

    private var loadedPkg: String? = null
    private var map: Map<String, String> = emptyMap()
    private var res: Resources? = null

    fun installed(c: Context): List<IconPackInfo> {
        val pm = c.packageManager
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (a in THEME_ACTIONS) {
            pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA).forEach {
                val p = it.activityInfo.packageName
                if (p !in seen) seen[p] = IconPackInfo(p, it.loadLabel(pm).toString())
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    @Synchronized
    private fun ensure(c: Context, pkg: String) {
        if (loadedPkg == pkg) return
        loadedPkg = pkg
        map = emptyMap(); res = null
        if (pkg.isEmpty()) return
        runCatching {
            val r = c.packageManager.getResourcesForApplication(pkg)
            res = r
            val parser: XmlPullParser = run {
                val id = r.getIdentifier("appfilter", "xml", pkg)
                if (id != 0) r.getXml(id) else {
                    val p = XmlPullParserFactory.newInstance().newPullParser()
                    p.setInput(r.assets.open("appfilter.xml"), "UTF-8"); p
                }
            }
            val m = HashMap<String, String>()
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null && comp.startsWith("ComponentInfo{")) {
                        m[comp.removePrefix("ComponentInfo{").removeSuffix("}")] = dr
                    }
                }
                ev = parser.next()
            }
            map = m
        }
    }

    /** The pack's drawable for [cn], or null when the pack does not theme that app. */
    fun drawableFor(c: Context, pkg: String, cn: ComponentName): Drawable? {
        if (pkg.isEmpty()) return null
        ensure(c, pkg)
        val r = res ?: return null
        val name = map[cn.packageName + "/" + cn.className]
            ?: map[cn.flattenToShortString()]
            ?: map.entries.firstOrNull { it.key.startsWith(cn.packageName + "/") }?.value
            ?: return null
        val id = r.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return runCatching { r.getDrawableForDensity(id, android.util.DisplayMetrics.DENSITY_XXXHIGH, null) }.getOrNull()
    }

    fun reset() { loadedPkg = null }
}
