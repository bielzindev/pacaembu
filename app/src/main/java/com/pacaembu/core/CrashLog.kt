package com.pacaembu.core

import android.content.Context
import java.io.File

/** Keeps the last uncaught exception so it can be shown in Settings › About. */
object CrashLog {
    private lateinit var file: File

    fun install(c: Context) {
        file = File(c.filesDir, "crash.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { file.writeText(System.currentTimeMillis().toString() + "\n" + e.stackTraceToString()) }
            prev?.uncaughtException(t, e)
        }
    }

    fun read(): String = if (::file.isInitialized && file.exists()) file.readText() else ""
    fun clear() { if (::file.isInitialized) file.delete() }
}
