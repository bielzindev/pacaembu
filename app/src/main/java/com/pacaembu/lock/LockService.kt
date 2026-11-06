package com.pacaembu.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.TelephonyManager
import com.pacaembu.R
import com.pacaembu.core.Prefs

/**
 * Shows the Pacaembu lock screen every time the screen goes off. Starting an activity from the
 * background needs "Display over other apps", which the Settings page asks for.
 */
class LockService : Service() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action != Intent.ACTION_SCREEN_OFF || !Prefs.lockScreen.value) return
            val tm = c.getSystemService(TelephonyManager::class.java)
            @Suppress("DEPRECATION")
            if (runCatching { tm.callState != TelephonyManager.CALL_STATE_IDLE }.getOrDefault(false)) return
            if (!Settings.canDrawOverlays(c)) return
            runCatching {
                c.startActivity(Intent(c, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("lock", getString(R.string.lock_channel), NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "lock")
            .setSmallIcon(R.drawable.ion_lock_closed)
            .setContentTitle(getString(R.string.lock_running))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7, n)
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    companion object {
        fun start(c: Context) { runCatching { c.startForegroundService(Intent(c, LockService::class.java)) } }
        fun stop(c: Context) { c.stopService(Intent(c, LockService::class.java)) }
    }
}
