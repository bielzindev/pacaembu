package com.pacaembu.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.Executors

data class AppInfo(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
)

/** Installed launchable apps (main profile), kept up to date through LauncherApps callbacks. */
object AppRepository {
    val apps = mutableStateOf<List<AppInfo>>(emptyList())
    /** Bumped whenever packages change, so icon caches can refresh. */
    val version = mutableIntStateOf(0)
    val loaded = mutableStateOf(false)

    private lateinit var ctx: Context
    private lateinit var launcherApps: LauncherApps
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    fun keyOf(cn: ComponentName) = "app:" + cn.flattenToShortString()

    fun init(c: Context) {
        ctx = c.applicationContext
        launcherApps = ctx.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(p: String?, u: UserHandle?) = refresh()
            override fun onPackageAdded(p: String?, u: UserHandle?) = refresh()
            override fun onPackageChanged(p: String?, u: UserHandle?) = refresh()
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
        }, main)
        refresh()
    }

    fun refresh() {
        io.execute {
            val list = runCatching {
                launcherApps.getActivityList(null, Process.myUserHandle())
                    .filter { it.componentName.packageName != ctx.packageName }
                    .map { it.toInfo() }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
            main.post {
                apps.value = list
                version.intValue++
                loaded.value = true
            }
        }
    }

    fun activityInfo(cn: ComponentName): LauncherActivityInfo? = runCatching {
        launcherApps.resolveActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER).setComponent(cn), Process.myUserHandle())
    }.getOrNull()

    fun launch(cn: ComponentName, bounds: android.graphics.Rect?, opts: android.os.Bundle?) {
        runCatching { launcherApps.startMainActivity(cn, Process.myUserHandle(), bounds, opts) }
    }

    private fun LauncherActivityInfo.toInfo() =
        AppInfo(keyOf(componentName), label.toString(), componentName, user)
}
