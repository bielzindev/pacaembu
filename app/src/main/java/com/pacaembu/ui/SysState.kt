package com.pacaembu.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.pacaembu.icons.LiveClock

/** Battery, clock, Wi-Fi and signal for the status bar. */
object SysState {
    val now = mutableLongStateOf(System.currentTimeMillis())
    val battery = mutableIntStateOf(100)
    val charging = mutableStateOf(false)
    val wifi = mutableStateOf(false)
    val signal = mutableIntStateOf(4)
    val carrier = mutableStateOf("")

    fun update(c: Context, i: Intent? = null) {
        now.longValue = System.currentTimeMillis()
        LiveClock.minute.longValue = now.longValue / 60000
        val b = i?.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED }
            ?: c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (b != null) {
            val lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (lvl >= 0 && sc > 0) battery.intValue = lvl * 100 / sc
            val st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging.value = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        }
        runCatching {
            val cm = c.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            wifi.value = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        runCatching {
            val tm = c.getSystemService(TelephonyManager::class.java)
            carrier.value = tm.networkOperatorName ?: ""
            tm.signalStrength?.let { signal.intValue = it.level }
        }
    }
}

/** Keeps [SysState] fresh while the calling screen is on. */
@Composable
fun TrackSysState() {
    val c = LocalContext.current
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = SysState.update(ctx, intent)
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        c.registerReceiver(r, f)
        SysState.update(c)
        onDispose { runCatching { c.unregisterReceiver(r) } }
    }
}
