package com.pacaembu.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/** Small reactive preference store: reading [value] inside a composable recomposes on change. */
object Prefs {
    internal lateinit var sp: SharedPreferences

    fun init(c: Context) {
        if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("pacaembu", Context.MODE_PRIVATE)
    }

    class BoolPref(private val key: String, private val def: Boolean) {
        private val state by lazy { mutableStateOf(sp.getBoolean(key, def)) }
        var value: Boolean
            get() = state.value
            set(v) { state.value = v; sp.edit().putBoolean(key, v).apply() }
    }

    class StringPref(private val key: String, private val def: String) {
        private val state by lazy { mutableStateOf(sp.getString(key, def) ?: def) }
        var value: String
            get() = state.value
            set(v) { state.value = v; sp.edit().putString(key, v).apply() }
    }

    /** Glossy highlight over icons (iOS 6 default). */
    val gloss = BoolPref("gloss", true)
    /** Package of the selected third-party icon pack ("" = built-in Pacaembu icons). */
    val iconPack = StringPref("icon_pack", "")
    /** Hide system apps that a built-in shortcut already represents (Mail, Browser…). */
    val hideDuplicates = BoolPref("hide_duplicates", true)
    /** Show the Pacaembu lock screen when the screen turns off. */
    val lockScreen = BoolPref("lock_screen", false)
    /** Show icon labels on the home screen. */
    val labels = BoolPref("labels", true)
    /** Weather in Fahrenheit. */
    val fahrenheit = BoolPref("fahrenheit", false)
    /** Last weather reading, shown by the Weather icon ("" = unknown). */
    val lastTemp = StringPref("last_temp", "")
    /** Home layout JSON. */
    val layout = StringPref("layout", "")
    /** Notes JSON. */
    val notes = StringPref("notes", "")
    /** World clock time zones, comma separated. */
    val worldClocks = StringPref("world_clocks", "America/Sao_Paulo,Europe/Lisbon,America/New_York,Asia/Tokyo")
}
