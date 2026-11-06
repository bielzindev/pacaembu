package com.pacaembu.notif

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pacaembu.R
import com.pacaembu.core.AppRepository
import com.pacaembu.icons.AppIcon
import com.pacaembu.ui.IosStatusBar
import com.pacaembu.ui.ShadowSoft
import com.pacaembu.ui.T
import com.pacaembu.ui.iosText
import com.pacaembu.ui.linen

/** The iOS 6 Notification Center: a linen sheet pulled down from the top, grouped by app. */
@Composable
fun NotificationCenter(progress: Float, onClose: () -> Unit, onDrag: (Float) -> Unit, onRelease: () -> Unit) {
    val c = LocalContext.current
    val items = NotifStore.items.value
    val access = NotifStore.hasAccess(c)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer { translationY = -h * (1f - progress) }
                .linen()
        ) {
            IosStatusBar(background = Brush.verticalGradient(listOf(Color(0xFF111111), Color.Black)))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (!access) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            T(stringResource(R.string.nc_no_access), iosText(15, Color(0xFFBBBBBB), align = TextAlign.Center))
                            Spacer(Modifier.height(12.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)).clickable {
                                    c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) { T(stringResource(R.string.nc_allow), iosText(15, Color.White, FontWeight.Bold)) }
                        }
                    }
                } else if (items.isEmpty()) {
                    item {
                        T(stringResource(R.string.nc_empty), iosText(17, Color(0xFF8A8A8A), FontWeight.Bold, align = TextAlign.Center),
                            Modifier.fillMaxWidth().padding(top = 40.dp))
                    }
                }
                val groups = items.groupBy { it.pkg }
                groups.forEach { (pkg, list) ->
                    item(key = "h:$pkg") { Header(pkg, list.first().app) { NotifStore.dismissApp(pkg) } }
                    items(list.size, key = { list[it].key }) { i ->
                        val n = list[i]
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                runCatching { n.intent?.send() }
                                onClose()
                            }.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                if (n.title.isNotEmpty()) T(n.title, iosText(15, Color.White, FontWeight.Bold, ShadowSoft), maxLines = 1)
                                if (n.text.isNotEmpty()) T(n.text, iosText(14, Color(0xFFBDBDBD)), maxLines = 2)
                            }
                            T(DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                iosText(12, Color(0xFF8A8A8A)), Modifier.padding(start = 8.dp), maxLines = 1)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22000000)))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
                    }
                }
            }
            // grabber: drag up or tap to close
            Box(
                Modifier.fillMaxWidth().navigationBarsPadding().height(36.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF111111))))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = onRelease, onDragCancel = onRelease) { ch, dy -> ch.consume(); onDrag(dy) }
                    }
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(width = 36.dp, height = 6.dp)) {
                    drawRoundRect(Color(0xFF6A6A6A), Offset.Zero, Size(size.width, size.height), CornerRadius(size.height / 2))
                }
            }
        }
    }
}

@Composable
private fun Header(pkg: String, app: String, onClear: () -> Unit) {
    val key = AppRepository.apps.value.firstOrNull { it.component.packageName == pkg }?.key
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF4B4B4E), Color(0xFF2E2E30)))).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (key != null) { AppIcon(key, 22.dp); Spacer(Modifier.width(8.dp)) }
        T(app, iosText(15, Color.White, FontWeight.Bold, ShadowSoft), Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF222222)).border(1.dp, Color(0xFF777777), CircleShape).clickable { onClear() },
            contentAlignment = Alignment.Center,
        ) { T("✕", iosText(11, Color(0xFFCCCCCC), FontWeight.Bold)) }
    }
}
