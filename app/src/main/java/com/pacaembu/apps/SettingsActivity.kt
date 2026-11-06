package com.pacaembu.apps

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pacaembu.R
import com.pacaembu.core.CrashLog
import com.pacaembu.core.Prefs
import com.pacaembu.icons.AppIcon
import com.pacaembu.icons.IconCache
import com.pacaembu.icons.IconPacks
import com.pacaembu.lock.LockService
import com.pacaembu.notif.NotifStore
import com.pacaembu.ui.Cell
import com.pacaembu.ui.Group
import com.pacaembu.ui.GroupFooter
import com.pacaembu.ui.GroupHeader
import com.pacaembu.ui.IosAlert
import com.pacaembu.ui.IosNavBar
import com.pacaembu.ui.IosScreen
import com.pacaembu.ui.IosSwitch
import com.pacaembu.ui.Separator
import com.pacaembu.ui.T
import com.pacaembu.ui.iosText
import com.pacaembu.ui.iosWindow

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iosWindow()
        setContent { SettingsApp(onExit = ::finish) }
    }

    override fun onResume() { super.onResume(); iosWindow(); refresh.value++ }

    companion object { val refresh = mutableStateOf(0) }
}

private enum class Page { MAIN, WALLPAPER_HOME, ICONS, LOCK, ABOUT }

@Composable
private fun SettingsApp(onExit: () -> Unit) {
    val stack = remember { mutableStateListOf(Page.MAIN) }
    val top = stack.last()
    BackHandler { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onExit() }
    AnimatedContent(
        targetState = top,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            (slideInHorizontally(tween(300)) { if (forward) it else -it / 3 }) togetherWith
                (slideOutHorizontally(tween(300)) { if (forward) -it / 3 else it })
        },
        label = "nav",
    ) { page ->
        val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
        when (page) {
            Page.MAIN -> Main(onOpen = { stack.add(it) })
            Page.WALLPAPER_HOME -> HomeSettings(back)
            Page.ICONS -> IconSettings(back)
            Page.LOCK -> LockSettings(back)
            Page.ABOUT -> About(back)
        }
    }
}

@Composable
private fun Scroll(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 20.dp)) { content() }
}

@Composable
private fun Main(onOpen: (Page) -> Unit) {
    val c = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val r = SettingsActivity.refresh.value
    fun sys(action: String) { runCatching { c.startActivity(Intent(action)) } }
    IosScreen {
        IosNavBar(stringResource(R.string.app_settings))
        Scroll {
            Spacer(Modifier.height(10.dp))
            Group {
                Cell(stringResource(R.string.set_airplane), icon = R.drawable.ion_airplane, iconColor = Color(0xFFF29B1F), chevron = true) { sys(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
                Separator()
                Cell(stringResource(R.string.set_wifi), icon = R.drawable.ion_wifi, iconColor = Color(0xFF2D8CF0), chevron = true) { sys(Settings.Panel.ACTION_WIFI) }
                Separator()
                Cell(stringResource(R.string.set_bluetooth), icon = R.drawable.ion_bluetooth, iconColor = Color(0xFF2D6FE0), chevron = true) { sys(Settings.ACTION_BLUETOOTH_SETTINGS) }
            }
            Spacer(Modifier.height(16.dp))
            Group {
                Cell(stringResource(R.string.set_notifications), icon = R.drawable.ion_notifications, iconColor = Color(0xFFE53935),
                    detail = stringResource(if (NotifStore.hasAccess(c)) R.string.on else R.string.off), chevron = true) {
                    sys(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                }
                Separator()
                Cell(stringResource(R.string.set_sounds), icon = R.drawable.ion_volume_high, iconColor = Color(0xFFE5484D), chevron = true) { sys(Settings.ACTION_SOUND_SETTINGS) }
                Separator()
                Cell(stringResource(R.string.set_brightness), icon = R.drawable.ion_sunny, iconColor = Color(0xFF3C7FE0), chevron = true) { sys(Settings.ACTION_DISPLAY_SETTINGS) }
                Separator()
                Cell(stringResource(R.string.set_wallpaper), icon = R.drawable.ion_image, iconColor = Color(0xFF34A6E0), chevron = true) {
                    runCatching { c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), c.getString(R.string.set_wallpaper))) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Group {
                Cell(stringResource(R.string.set_home), icon = R.drawable.ion_apps, iconColor = Color(0xFF6A6F78), chevron = true) { onOpen(Page.WALLPAPER_HOME) }
                Separator()
                Cell(stringResource(R.string.set_icons), icon = R.drawable.ion_color_palette, iconColor = Color(0xFF8E5BD8), chevron = true) { onOpen(Page.ICONS) }
                Separator()
                Cell(stringResource(R.string.set_lock), icon = R.drawable.ion_lock_closed, iconColor = Color(0xFF6A6F78), chevron = true) { onOpen(Page.LOCK) }
            }
            Spacer(Modifier.height(16.dp))
            Group {
                Cell(stringResource(R.string.set_default_launcher), icon = R.drawable.ion_phone_portrait, iconColor = Color(0xFF4CAF50), chevron = true) {
                    val rm = c.getSystemService(RoleManager::class.java)
                    val ok = runCatching {
                        if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                            (c as? ComponentActivity)?.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1); true
                        } else false
                    }.getOrDefault(false)
                    if (!ok) sys(Settings.ACTION_HOME_SETTINGS)
                }
                Separator()
                Cell(stringResource(R.string.set_battery), icon = R.drawable.ion_battery_full, iconColor = Color(0xFF43A047), chevron = true) { sys(Intent.ACTION_POWER_USAGE_SUMMARY) }
                Separator()
                Cell(stringResource(R.string.set_android), icon = R.drawable.ion_cog, iconColor = Color(0xFF7D838A), chevron = true) { sys(Settings.ACTION_SETTINGS) }
                Separator()
                Cell(stringResource(R.string.set_about), icon = R.drawable.ion_information_circle, iconColor = Color(0xFF7D838A), chevron = true) { onOpen(Page.ABOUT) }
            }
        }
    }
}

@Composable
private fun SwitchCell(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Cell(title, accessory = { IosSwitch(value, onChange) })
}

@Composable
private fun HomeSettings(back: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    IosScreen {
        IosNavBar(stringResource(R.string.set_home), back = stringResource(R.string.app_settings), onBack = back)
        Scroll {
            GroupHeader(stringResource(R.string.set_home_layout))
            Group {
                SwitchCell(stringResource(R.string.set_labels), Prefs.labels.value) { Prefs.labels.value = it }
                Separator()
                SwitchCell(stringResource(R.string.set_hide_dup), Prefs.hideDuplicates.value) { Prefs.hideDuplicates.value = it }
            }
            GroupFooter(stringResource(R.string.set_hide_dup_footer))
            Group { Cell(stringResource(R.string.set_reset_layout), titleColor = Color(0xFFC62828), center = true) { confirm = true } }
            GroupFooter(stringResource(R.string.set_home_footer))
        }
    }
    if (confirm) IosAlert(stringResource(R.string.set_reset_layout), stringResource(R.string.set_reset_msg), listOf(
        stringResource(R.string.cancel) to { confirm = false },
        stringResource(R.string.reset) to { confirm = false; Prefs.layout.value = ""; com.pacaembu.core.AppRepository.refresh() },
    ), onDismiss = { confirm = false })
}

@Composable
private fun IconSettings(back: () -> Unit) {
    val c = LocalContext.current
    val packs = remember { IconPacks.installed(c) }
    IosScreen {
        IosNavBar(stringResource(R.string.set_icons), back = stringResource(R.string.app_settings), onBack = back)
        Scroll {
            GroupHeader(stringResource(R.string.set_icon_style))
            Group {
                SwitchCell(stringResource(R.string.set_gloss), Prefs.gloss.value) { Prefs.gloss.value = it; IconCache.clear() }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("builtin:messages", "builtin:calendar", "builtin:photos", "builtin:clock", "builtin:settings").forEach {
                    AppIcon(it, 52.dp, Modifier.weight(1f).padding(horizontal = 4.dp))
                }
            }
            GroupHeader(stringResource(R.string.set_icon_pack))
            Group {
                Cell(stringResource(R.string.set_icon_pack_none), checked = Prefs.iconPack.value.isEmpty()) { Prefs.iconPack.value = ""; IconCache.clear() }
                packs.forEach {
                    Separator()
                    Cell(it.label, checked = Prefs.iconPack.value == it.pkg) { Prefs.iconPack.value = it.pkg; IconCache.clear() }
                }
            }
            GroupFooter(stringResource(if (packs.isEmpty()) R.string.set_icon_pack_empty else R.string.set_icon_pack_footer))
            Group {
                Cell(stringResource(R.string.set_find_packs), chevron = true) {
                    runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=ios%206%20icon%20pack&c=apps"))) }
                }
            }
            GroupFooter(stringResource(R.string.set_icons_credit))
        }
    }
}

@Composable
private fun LockSettings(back: () -> Unit) {
    val c = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val r = SettingsActivity.refresh.value
    val overlay = Settings.canDrawOverlays(c)
    IosScreen {
        IosNavBar(stringResource(R.string.set_lock), back = stringResource(R.string.app_settings), onBack = back)
        Scroll {
            Spacer(Modifier.height(10.dp))
            Group {
                SwitchCell(stringResource(R.string.set_lock_enable), Prefs.lockScreen.value) { on ->
                    Prefs.lockScreen.value = on
                    if (on) {
                        if (!Settings.canDrawOverlays(c)) runCatching {
                            c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName)))
                        }
                        LockService.start(c)
                    } else LockService.stop(c)
                }
                Separator()
                Cell(stringResource(R.string.set_overlay), detail = stringResource(if (overlay) R.string.on else R.string.off), chevron = true) {
                    runCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName))) }
                }
                Separator()
                Cell(stringResource(R.string.set_lock_preview), chevron = true) {
                    c.startActivity(Intent(c, com.pacaembu.lock.LockActivity::class.java))
                }
            }
            GroupFooter(stringResource(R.string.set_lock_footer))
        }
    }
}

@Composable
private fun About(back: () -> Unit) {
    val c = LocalContext.current
    val ver = remember { runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "" }
    var crash by remember { mutableStateOf(CrashLog.read()) }
    IosScreen {
        IosNavBar(stringResource(R.string.set_about), back = stringResource(R.string.app_settings), onBack = back)
        Scroll {
            Spacer(Modifier.height(10.dp))
            Group {
                Cell(stringResource(R.string.about_name), detail = "Pacaembu")
                Separator()
                Cell(stringResource(R.string.about_version), detail = ver)
                Separator()
                Cell(stringResource(R.string.about_android), detail = android.os.Build.VERSION.RELEASE)
                Separator()
                Cell(stringResource(R.string.about_model), detail = android.os.Build.MODEL)
            }
            GroupHeader(stringResource(R.string.about_crash))
            Group {
                if (crash.isEmpty()) Cell(stringResource(R.string.about_no_crash))
                else {
                    T(crash.take(1500), iosText(11, Color.DarkGray), Modifier.padding(10.dp))
                    Separator()
                    Cell(stringResource(R.string.about_clear), titleColor = Color(0xFFC62828), center = true) { CrashLog.clear(); crash = "" }
                }
            }
            GroupFooter(stringResource(R.string.set_icons_credit))
        }
    }
}
