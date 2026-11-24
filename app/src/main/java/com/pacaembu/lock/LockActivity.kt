package com.pacaembu.lock

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pacaembu.R
import com.pacaembu.notif.NotifStore
import com.pacaembu.ui.IosStatusBar
import com.pacaembu.ui.ShadowDarkUp
import com.pacaembu.ui.SysState
import com.pacaembu.ui.T
import com.pacaembu.ui.iosText
import com.pacaembu.ui.iosWindow
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        iosWindow()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })
        setContent { LockScreen(onUnlock = ::unlock, onCamera = ::camera) }
    }

    override fun onResume() { super.onResume(); iosWindow() }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { finish() }
            })
            if (!km.isKeyguardSecure) finish()
        } else finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun camera() {
        runCatching { startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)) }
        finish()
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit, onCamera: () -> Unit) {
    val c = LocalContext.current
    val now = Date(SysState.now.longValue)
    val time = DateFormat.format(if (DateFormat.is24HourFormat(c)) "H:mm" else "h:mm", now).toString()
    val date = DateFormat.format(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEEMMMMd"), now).toString()
    val notes = NotifStore.items.value.take(4)
    val lift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0x22000000))) {
        val h = constraints.maxHeight.toFloat()
        Column(Modifier.fillMaxSize().graphicsLayer { translationY = -lift.value * h }) {
            IosStatusBar(background = Brush.verticalGradient(listOf(Color(0xCC000000), Color(0xCC000000))))
            // top glossy black bar with the big clock
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color(0xCC3A3A3A), 0.5f to Color(0xCC161616), 0.5f to Color(0xCC050505), 1f to Color(0xCC000000)))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(time, iosText(64, Color.White, FontWeight.Light, ShadowDarkUp, TextAlign.Center))
                T(date.replaceFirstChar { it.titlecase() }, iosText(15, Color.White, FontWeight.Bold, ShadowDarkUp, TextAlign.Center))
            }
            Spacer(Modifier.height(14.dp))
            notes.forEach { n ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color(0xB3000000)).padding(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        T(n.title.ifEmpty { n.app }, iosText(15, Color.White, FontWeight.Bold), maxLines = 1)
                        T(n.text, iosText(14, Color(0xFFCCCCCC)), maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            SlideBar(onUnlock, onCameraDrag = { dy ->
                scope.launch { lift.snapTo((lift.value - dy / h).coerceIn(0f, 1f)) }
            }, onCameraRelease = {
                scope.launch {
                    if (lift.value > 0.35f) { lift.animateTo(1f, tween(200)); onCamera() } else lift.animateTo(0f)
                }
            })
        }
    }
}

@Composable
private fun SlideBar(onUnlock: () -> Unit, onCameraDrag: (Float) -> Unit, onCameraRelease: () -> Unit) {
    val d = LocalDensity.current
    val scope = rememberCoroutineScope()
    val knob = remember { Animatable(0f) }
    val shimmer = rememberInfiniteTransition(label = "sh")
    val sx by shimmer.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "x")
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color(0xCC3A3A3A), 0.5f to Color(0xCC161616), 0.5f to Color(0xCC050505), 1f to Color(0xCC000000)))
            .navigationBarsPadding().height(96.dp).padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(end = 52.dp)) {
            val track = constraints.maxWidth.toFloat()
            val knobW = with(d) { 82.dp.toPx() }
            val max = track - knobW
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0E0E0E), Color(0xFF2B2B2B))))
                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // shimmering "slide to unlock"
                val grey = Color(0xFF8E8E8E)
                val st = iosText(20, Color.White, FontWeight.Normal, align = TextAlign.Center).copy(
                    brush = Brush.horizontalGradient(
                        0f to grey, (sx - 0.12f).coerceIn(0f, 1f) to grey, sx.coerceIn(0f, 1f) to Color.White,
                        (sx + 0.12f).coerceIn(0f, 1f) to grey, 1f to grey, startX = 0f, endX = track * 0.7f,
                    )
                )
                T(stringResource(R.string.slide_to_unlock), st,
                    Modifier.padding(start = 70.dp).graphicsLayer { alpha = (1f - knob.value / max.coerceAtLeast(1f)) })
            }
            Box(
                Modifier.offset { IntOffset(knob.value.roundToInt(), 0) }
                    .padding(3.dp).size(width = 76.dp, height = 46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(0f to Color(0xFFFFFFFF), 0.5f to Color(0xFFE6E6E6), 0.5f to Color(0xFFCFCFCF), 1f to Color(0xFFB8B8B8)))
                    .pointerInput(max) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (knob.value > max * 0.85f) { knob.animateTo(max, tween(80)); onUnlock() }
                                    else knob.animateTo(0f, tween(250))
                                }
                            },
                        ) { ch, dx -> ch.consume(); scope.launch { knob.snapTo((knob.value + dx).coerceIn(0f, max)) } }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ion_arrow_forward), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Color(0xFF8C8C8C)))
            }
        }
        // camera grabber: drag up
        Box(
            Modifier.align(Alignment.CenterEnd).size(width = 44.dp, height = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3C3C3C), Color(0xFF1A1A1A))))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = onCameraRelease, onDragCancel = onCameraRelease) { ch, dy -> ch.consume(); onCameraDrag(dy) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ion_camera), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color(0xFFBBBBBB)))
        }
    }
}
