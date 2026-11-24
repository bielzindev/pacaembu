package com.pacaembu.core

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.pacaembu.R
import com.pacaembu.apps.CalculatorActivity
import com.pacaembu.apps.ClockActivity
import com.pacaembu.apps.NotesActivity
import com.pacaembu.apps.PhoneActivity
import com.pacaembu.apps.SettingsActivity
import com.pacaembu.apps.WeatherActivity

/** How the icon art of a built-in is drawn (see IconFactory). */
enum class IconStyle { GLYPH, CALENDAR, CLOCK, WEATHER, PHOTOS, NOTES, SETTINGS }

/**
 * The apps that ship with the shell. Some are implemented here ([internal]); the rest are
 * iOS-styled shortcuts to whatever app of that kind the phone has ([system]).
 */
enum class BuiltIn(
    val id: String,
    @StringRes val label: Int,
    @DrawableRes val glyph: Int,
    val top: Long,
    val bottom: Long,
    val glyphColor: Long = 0xFFFFFFFF,
    val style: IconStyle = IconStyle.GLYPH,
    val internal: Class<out Activity>? = null,
    val system: ((Context) -> Intent?)? = null,
) {
    MESSAGES("messages", R.string.app_messages, R.drawable.ion_chatbubble, 0xFF86E870, 0xFF22A013,
        system = { category(Intent.CATEGORY_APP_MESSAGING) }),
    CALENDAR("calendar", R.string.app_calendar, R.drawable.ion_calendar, 0xFFFFFFFF, 0xFFE4E4E4,
        style = IconStyle.CALENDAR, system = { category(Intent.CATEGORY_APP_CALENDAR) }),
    PHOTOS("photos", R.string.app_photos, R.drawable.ion_flower, 0xFF8FCDF7, 0xFF2A78CC,
        style = IconStyle.PHOTOS, system = { category(Intent.CATEGORY_APP_GALLERY) }),
    CAMERA("camera", R.string.app_camera, R.drawable.ion_camera, 0xFFE3E5E8, 0xFF878C93, 0xFF2B2E33,
        system = { Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA) }),
    WEATHER("weather", R.string.app_weather, R.drawable.ion_partly_sunny, 0xFF52B1EE, 0xFF1C59AE,
        style = IconStyle.WEATHER, internal = WeatherActivity::class.java),
    CLOCK("clock", R.string.app_clock, R.drawable.ion_time, 0xFF444444, 0xFF050505,
        style = IconStyle.CLOCK, internal = ClockActivity::class.java),
    MAPS("maps", R.string.app_maps, R.drawable.ion_map, 0xFFF1F4E6, 0xFFB5CC8C, 0xFF2F78D2,
        system = { category(Intent.CATEGORY_APP_MAPS) }),
    FILES("files", R.string.app_files, R.drawable.ion_folder_open, 0xFF6CD0FB, 0xFF1C70CC,
        system = { category(Intent.CATEGORY_APP_FILES) }),
    NOTES("notes", R.string.app_notes, R.drawable.ion_create, 0xFFFAEE9C, 0xFFE8CF52,
        style = IconStyle.NOTES, internal = NotesActivity::class.java),
    CONTACTS("contacts", R.string.app_contacts, R.drawable.ion_person, 0xFFD2AE86, 0xFF84582E,
        system = { category(Intent.CATEGORY_APP_CONTACTS) }),
    CALCULATOR("calculator", R.string.app_calculator, R.drawable.ion_calculator, 0xFF4A4A4A, 0xFF101010, 0xFFF79A2E,
        internal = CalculatorActivity::class.java),
    STORE("store", R.string.app_store, R.drawable.ion_bag_handle, 0xFF66C0F7, 0xFF145FC4,
        system = { category(Intent.CATEGORY_APP_MARKET) }),
    SETTINGS("settings", R.string.app_settings, R.drawable.ion_cog, 0xFFC3C8CE, 0xFF6A7077, 0xFF3C4146,
        style = IconStyle.SETTINGS, internal = SettingsActivity::class.java),
    PHONE("phone", R.string.app_phone, R.drawable.ion_call, 0xFF7AE46A, 0xFF1A9516,
        internal = PhoneActivity::class.java),
    MAIL("mail", R.string.app_mail, R.drawable.ion_mail, 0xFF5CBBF9, 0xFF1658C2,
        system = { c -> defaultFor(c, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))) ?: category(Intent.CATEGORY_APP_EMAIL) }),
    BROWSER("browser", R.string.app_browser, R.drawable.ion_compass, 0xFFFFFFFF, 0xFFD5DCE4, 0xFF1C74DD,
        system = { c -> defaultFor(c, Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))) ?: category(Intent.CATEGORY_APP_BROWSER) }),
    MUSIC("music", R.string.app_music, R.drawable.ion_musical_notes, 0xFFFFB259, 0xFFEC4F0B,
        system = { category(Intent.CATEGORY_APP_MUSIC) });

    val key get() = "builtin:$id"

    companion object {
        val byKey = entries.associateBy { it.key }
        val defaultPage = listOf(MESSAGES, CALENDAR, PHOTOS, CAMERA, WEATHER, CLOCK, MAPS, FILES,
            NOTES, CONTACTS, CALCULATOR, STORE, SETTINGS)
        val defaultDock = listOf(PHONE, MAIL, BROWSER, MUSIC)
    }
}

private fun category(cat: String): Intent = Intent(Intent.ACTION_MAIN).addCategory(cat)

/** Launcher intent of the user's default app for [probe], or null if there is no default. */
private fun defaultFor(c: Context, probe: Intent): Intent? {
    val ri = c.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
    val pkg = ri.activityInfo?.packageName ?: return null
    if (pkg == "android") return null // the chooser: no default yet
    return c.packageManager.getLaunchIntentForPackage(pkg)
}

/** Resolves a built-in shortcut to an explicit intent and the package it opens. */
object BuiltInResolver {
    fun intent(c: Context, b: BuiltIn): Intent? {
        b.internal?.let { return Intent(c, it) }
        val raw = b.system?.invoke(c) ?: return null
        if (raw.component != null) return raw
        val pm = c.packageManager
        val list = pm.queryIntentActivities(raw, 0)
        val pick = list.firstOrNull { it.activityInfo.packageName != c.packageName } ?: return null
        val ai = pick.activityInfo
        return Intent(raw).setComponent(ComponentName(ai.packageName, ai.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun targetPackage(c: Context, b: BuiltIn): String? =
        if (b.internal != null) null else intent(c, b)?.component?.packageName

    /** Launcher component of the app a shortcut opens (used for icon packs). */
    fun targetComponent(c: Context, b: BuiltIn): ComponentName? {
        val pkg = targetPackage(c, b) ?: return null
        return c.packageManager.getLaunchIntentForPackage(pkg)?.component
    }
}
