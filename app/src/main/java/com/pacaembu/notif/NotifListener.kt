package com.pacaembu.notif

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

data class NotifItem(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val intent: PendingIntent?,
    val clearable: Boolean,
)

object NotifStore {
    val items = mutableStateOf<List<NotifItem>>(emptyList())
    var service: NotifListener? = null

    fun hasAccess(c: Context): Boolean {
        val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(c, NotifListener::class.java).flattenToString()
        return flat.split(':').any { it == me }
    }

    fun dismiss(n: NotifItem) { runCatching { service?.cancelNotification(n.key) } }
    fun dismissApp(pkg: String) { items.value.filter { it.pkg == pkg && it.clearable }.forEach(::dismiss) }
}

class NotifListener : NotificationListenerService() {
    override fun onListenerConnected() { NotifStore.service = this; publish() }
    override fun onListenerDisconnected() { NotifStore.service = null; NotifStore.items.value = emptyList() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        val list = runCatching { activeNotifications?.toList() ?: emptyList() }.getOrDefault(emptyList())
        val pm = packageManager
        NotifStore.items.value = list
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .filter { it.packageName != packageName }
            .mapNotNull { sbn ->
                val ex = sbn.notification.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
                if (title.isEmpty() && text.isEmpty()) return@mapNotNull null
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
                NotifItem(sbn.key, sbn.packageName, app, title, text, sbn.postTime, sbn.notification.contentIntent, sbn.isClearable)
            }
            .sortedByDescending { it.time }
    }
}
