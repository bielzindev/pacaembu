package com.pacaembu.apps

import android.app.AlarmManager
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pacaembu.R
import com.pacaembu.core.Prefs
import com.pacaembu.ui.BarTint
import com.pacaembu.ui.Cell
import com.pacaembu.ui.GlossyButton
import com.pacaembu.ui.Group
import com.pacaembu.ui.GroupFooter
import com.pacaembu.ui.IosBarButton
import com.pacaembu.ui.IosNavBar
import com.pacaembu.ui.IosScreen
import com.pacaembu.ui.IosTabBar
import com.pacaembu.ui.Separator
import com.pacaembu.ui.SysState
import com.pacaembu.ui.T
import com.pacaembu.ui.Tab
import com.pacaembu.ui.TrackSysState
import com.pacaembu.ui.iosText
import com.pacaembu.ui.iosWindow
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

class ClockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iosWindow()
        setContent { ClockApp() }
    }
}

@Composable
private fun ClockApp() {
    var tab by remember { mutableIntStateOf(0) }
    TrackSysState()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> WorldClock()
                1 -> Alarm()
                2 -> Stopwatch()
                else -> TimerTab()
            }
        }
        IosTabBar(listOf(
            Tab(stringResource(R.string.clock_world), R.drawable.ion_globe),
            Tab(stringResource(R.string.clock_alarm), R.drawable.ion_alarm),
            Tab(stringResource(R.string.clock_stopwatch), R.drawable.ion_stopwatch),
            Tab(stringResource(R.string.clock_timer), R.drawable.ion_timer),
        ), tab) { tab = it }
    }
}

// ------------------------------------------------------------- world clock

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorldClock() {
    val c = LocalContext.current
    var zones by remember { mutableStateOf(Prefs.worldClocks.value.split(',').filter { it.isNotBlank() }) }
    var adding by remember { mutableStateOf(false) }
    val now = SysState.now.longValue
    if (adding) {
        ZonePicker(onPick = { z ->
            adding = false
            if (z != null && z !in zones) { zones = zones + z; Prefs.worldClocks.value = zones.joinToString(",") }
        })
        return
    }
    IosScreen {
        IosNavBar(stringResource(R.string.clock_world), right = { IosBarButton("+", { adding = true }) })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(10.dp))
            Group {
                zones.forEachIndexed { i, z ->
                    if (i > 0) Separator()
                    val tz = TimeZone.getTimeZone(z)
                    val cal = Calendar.getInstance(tz).apply { timeInMillis = now }
                    val local = Calendar.getInstance()
                    val dayDiff = cal.get(Calendar.DAY_OF_YEAR) - local.get(Calendar.DAY_OF_YEAR)
                    val day = when { dayDiff == 0 -> R.string.today; dayDiff > 0 -> R.string.tomorrow; else -> R.string.yesterday }
                    val fmt = DateFormat.getTimeFormat(c).apply { timeZone = tz }
                    Row(
                        Modifier.fillMaxWidth().height(76.dp).combinedClickable(onClick = {}, onLongClick = {
                            zones = zones - z; Prefs.worldClocks.value = zones.joinToString(",")
                        }).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnalogClock(cal, Modifier.size(60.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(z.substringAfterLast('/').replace('_', ' '), iosText(18, Color.Black, FontWeight.Bold), maxLines = 1)
                            T(stringResource(day), iosText(14, Color.Gray))
                        }
                        T(fmt.format(Date(now)), iosText(18, Color(0xFF385487)))
                    }
                }
            }
            GroupFooter(stringResource(R.string.clock_world_hint))
        }
    }
}

@Composable
private fun AnalogClock(cal: Calendar, modifier: Modifier) {
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val day = h in 6..17
    val face = if (day) Color.White else Color(0xFF111111)
    val ink = if (day) Color(0xFF111111) else Color.White
    Canvas(modifier) {
        val r = size.minDimension / 2
        val ctr = Offset(size.width / 2, size.height / 2)
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFDADADA), Color(0xFF8A8A8A))), r)
        drawCircle(face, r * 0.9f)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val p1 = Offset(ctr.x + (sin(a) * r * 0.72f).toFloat(), ctr.y - (cos(a) * r * 0.72f).toFloat())
            val p2 = Offset(ctr.x + (sin(a) * r * 0.82f).toFloat(), ctr.y - (cos(a) * r * 0.82f).toFloat())
            drawLine(ink, p1, p2, if (i % 3 == 0) 3f else 1.5f)
        }
        val m = cal.get(Calendar.MINUTE)
        fun hand(deg: Double, len: Float, w: Float, col: Color) {
            val a = Math.toRadians(deg)
            drawLine(col, ctr, Offset(ctr.x + (sin(a) * len).toFloat(), ctr.y - (cos(a) * len).toFloat()), w, StrokeCap.Round)
        }
        hand(((h % 12) + m / 60.0) * 30, r * 0.45f, 5f, ink)
        hand(m * 6.0, r * 0.68f, 3.5f, ink)
        hand(cal.get(Calendar.SECOND) * 6.0, r * 0.72f, 1.5f, Color(0xFFD8352A))
        drawCircle(Color(0xFFD8352A), 3f, ctr)
    }
}

@Composable
private fun ZonePicker(onPick: (String?) -> Unit) {
    var q by remember { mutableStateOf("") }
    val all = remember { TimeZone.getAvailableIDs().filter { it.contains('/') && !it.startsWith("Etc") && !it.startsWith("SystemV") }.sortedBy { it.substringAfterLast('/') } }
    val list = all.filter { q.isBlank() || it.contains(q.trim().replace(' ', '_'), ignoreCase = true) }
    IosScreen {
        IosNavBar(stringResource(R.string.clock_choose_city), right = { IosBarButton(stringResource(R.string.cancel), { onPick(null) }) })
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFB8C1CB), Color(0xFF8D99A6)))).padding(8.dp)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (q.isEmpty()) T(stringResource(R.string.search), iosText(15, Color.Gray))
                BasicTextField(q, { q = it }, singleLine = true, textStyle = iosText(15), modifier = Modifier.fillMaxWidth())
            }
        }
        LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
            items(list.size) { i ->
                val z = list[i]
                Column(Modifier.fillMaxWidth().clickable { onPick(z) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    T(z.substringAfterLast('/').replace('_', ' '), iosText(17, Color.Black, FontWeight.Bold))
                    T(z.substringBefore('/'), iosText(13, Color.Gray))
                }
                Separator()
            }
        }
    }
}

// ------------------------------------------------------------- alarm

@Composable
private fun Alarm() {
    val c = LocalContext.current
    val next = remember(SysState.now.longValue) { c.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime }
    IosScreen {
        IosNavBar(stringResource(R.string.clock_alarm), right = {
            IosBarButton("+", { runCatching { c.startActivity(Intent(AlarmClock.ACTION_SET_ALARM)) } })
        })
        Spacer(Modifier.height(10.dp))
        Group {
            Cell(stringResource(R.string.alarm_next), detail = next?.let {
                DateFormat.format(if (DateFormat.is24HourFormat(c)) "EEE H:mm" else "EEE h:mm a", Date(it)).toString()
            } ?: stringResource(R.string.alarm_none))
            Separator()
            Cell(stringResource(R.string.alarm_show), chevron = true) { runCatching { c.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) } }
        }
        GroupFooter(stringResource(R.string.alarm_footer))
    }
}

// ------------------------------------------------------------- stopwatch

private fun fmtElapsed(ms: Long): String {
    val cs = (ms / 10) % 100; val s = (ms / 1000) % 60; val m = ms / 60000
    return "%02d:%02d.%02d".format(m, s, cs)
}

@Composable
private fun Stopwatch() {
    var running by remember { mutableStateOf(false) }
    var base by remember { mutableLongStateOf(0L) }
    var acc by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val laps = remember { mutableStateListOf<Long>() }
    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); delay(31) } }
    val elapsed = acc + (if (running) now - base else 0L)
    IosScreen {
        IosNavBar(stringResource(R.string.clock_stopwatch))
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFF4F4F4), Color(0xFFD6D6D6)))).padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
            T(fmtElapsed(elapsed), iosText(58, Color.Black, FontWeight.Bold, align = TextAlign.Center))
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlossyButton(stringResource(if (running) R.string.sw_stop else R.string.sw_start), if (running) Color(0xFFD23A2E) else Color(0xFF3EAA2D), Modifier.weight(1f)) {
                if (running) { acc += System.currentTimeMillis() - base; running = false }
                else { base = System.currentTimeMillis(); now = base; running = true }
            }
            GlossyButton(stringResource(if (running || elapsed == 0L) R.string.sw_lap else R.string.sw_reset), Color(0xFFE6E6E6), Modifier.weight(1f), textColor = Color(0xFF333333)) {
                if (running) laps.add(0, elapsed) else { acc = 0; laps.clear() }
            }
        }
        LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
            items(laps.size) { i ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    T(stringResource(R.string.sw_lap_n, laps.size - i), iosText(17, Color.Black, FontWeight.Bold), Modifier.weight(1f))
                    val prev = laps.getOrNull(i + 1) ?: 0L
                    T(fmtElapsed(laps[i] - prev), iosText(17, Color(0xFF385487)))
                }
                Separator()
            }
        }
    }
}

// ------------------------------------------------------------- timer

@Composable
private fun TimerTab() {
    val c = LocalContext.current
    val hours = remember { mutableIntStateOf(0) }
    val minutes = remember { mutableIntStateOf(5) }
    var endAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ringing by remember { mutableStateOf(false) }
    val running = endAt > 0
    LaunchedEffect(endAt) {
        while (endAt > 0) {
            now = System.currentTimeMillis()
            if (now >= endAt) {
                endAt = 0; ringing = true
                runCatching { RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.play() }
            }
            delay(200)
        }
    }
    IosScreen {
        IosNavBar(stringResource(R.string.clock_timer))
        Spacer(Modifier.height(16.dp))
        if (running) {
            val left = ((endAt - now) / 1000).coerceAtLeast(0)
            Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                T("%d:%02d:%02d".format(left / 3600, (left / 60) % 60, left % 60), iosText(60, Color(0xFF263040), FontWeight.Bold, align = TextAlign.Center))
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(216.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2F3440), Color(0xFF151820))))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Wheel(24, hours.intValue, stringResource(R.string.timer_hours), Modifier.weight(1f)) { hours.intValue = it }
                Wheel(60, minutes.intValue, stringResource(R.string.timer_min), Modifier.weight(1f)) { minutes.intValue = it }
            }
        }
        if (ringing) T(stringResource(R.string.timer_done), iosText(20, Color(0xFFC62828), FontWeight.Bold, align = TextAlign.Center), Modifier.fillMaxWidth().padding(10.dp))
        Spacer(Modifier.height(16.dp))
        GlossyButton(
            stringResource(if (running) R.string.timer_cancel else R.string.sw_start),
            if (running) Color(0xFFD23A2E) else Color(0xFF3EAA2D),
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            ringing = false
            endAt = if (running) 0 else {
                val ms = (hours.intValue * 3600L + minutes.intValue * 60L) * 1000
                if (ms > 0) System.currentTimeMillis() + ms else 0
            }
        }
        GroupFooter(stringResource(R.string.timer_footer))
    }
}

/** An iOS 6 picker wheel: white drum, dark shaded ends and a translucent selection bar. */
@Composable
fun Wheel(count: Int, value: Int, unit: String, modifier: Modifier, onChange: (Int) -> Unit) {
    val itemH = 40.dp
    val st = rememberLazyListState(initialFirstVisibleItemIndex = value)
    val fling = rememberSnapFlingBehavior(st)
    val half = with(androidx.compose.ui.platform.LocalDensity.current) { (itemH / 2).roundToPx() }
    val sel by remember { derivedStateOf { st.firstVisibleItemIndex + if (st.firstVisibleItemScrollOffset > half) 1 else 0 } }
    LaunchedEffect(sel, st.isScrollInProgress) { if (!st.isScrollInProgress) onChange(sel.coerceIn(0, count - 1)) }
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color.White)) {
        LazyColumn(Modifier.fillMaxSize(), state = st, flingBehavior = fling) {
            items(2) { Spacer(Modifier.height(itemH)) }
            items(count) { i ->
                Row(Modifier.fillMaxWidth().height(itemH).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(i.toString(), iosText(24, Color.Black, FontWeight.Bold, align = TextAlign.End), Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    T(if (i == value) unit else "", iosText(16, Color.Black, FontWeight.Bold), Modifier.width(48.dp))
                }
            }
            items(2) { Spacer(Modifier.height(itemH)) }
        }
        // shading at the drum ends
        Box(Modifier.fillMaxWidth().height(itemH * 2).background(Brush.verticalGradient(listOf(Color(0xCC1E1E1E), Color(0x00FFFFFF)))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(itemH * 2).background(Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color(0xCC1E1E1E)))))
        // selection bar
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(itemH + 4.dp)
                .background(Brush.verticalGradient(listOf(Color(0x40A9B8D9), Color(0x60657FB5))))
                .border(1.dp, Color(0x80546A99))
        )
    }
}
