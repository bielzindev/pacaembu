package com.pacaembu.ui

import android.app.Activity
import android.text.format.DateFormat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pacaembu.R
import java.util.Date

// ---------------------------------------------------------------- colors & type

object IosColors {
    val NavTop = Color(0xFFB0BCCD)
    val NavBottom = Color(0xFF6D84A2)
    val NavBorder = Color(0xFF2D3642)
    val GroupBorder = Color(0xFFABABAB)
    val Separator = Color(0xFFE0E0E0)
    val Detail = Color(0xFF385487)
    val GroupHeader = Color(0xFF4C566C)
    val Blue = Color(0xFF1F70D8)
    val Chevron = Color(0xFF7F7F7F)
}

val IosFont = FontFamily.SansSerif

fun iosText(size: Int, color: Color = Color.Black, weight: FontWeight = FontWeight.Normal, shadow: Shadow? = null, align: TextAlign = TextAlign.Start) =
    TextStyle(fontFamily = IosFont, fontSize = size.sp, color = color, fontWeight = weight, shadow = shadow, textAlign = align)

val ShadowDarkUp = Shadow(Color(0x99000000), Offset(0f, -2f), 0f)
val ShadowWhiteDown = Shadow(Color(0xCCFFFFFF), Offset(0f, 2f), 0f)
val ShadowSoft = Shadow(Color(0xCC000000), Offset(0f, 2f), 4f)

@Composable
fun T(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    BasicText(text, modifier, style, overflow = TextOverflow.Ellipsis, maxLines = maxLines)

// ---------------------------------------------------------------- window

/** Draws edge to edge with the system status bar hidden: Pacaembu draws its own. */
fun Activity.iosWindow() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    val c = WindowInsetsControllerCompat(window, window.decorView)
    c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    c.hide(WindowInsetsCompat.Type.statusBars())
    c.isAppearanceLightNavigationBars = false
}

@Composable
fun statusBarHeight(): Dp {
    val d = LocalDensity.current
    val cut = with(d) { WindowInsets.displayCutout.getTop(d).toDp() }
    return max(20.dp, cut)
}

// ---------------------------------------------------------------- status bar

@Composable
fun IosStatusBar(modifier: Modifier = Modifier, background: Brush = Brush.verticalGradient(listOf(Color(0xFF2B2B2B), Color.Black))) {
    TrackSysState()
    val c = LocalContext.current
    val h = statusBarHeight()
    val time = remember(SysState.now.longValue) {
        DateFormat.getTimeFormat(c).format(Date(SysState.now.longValue))
    }
    val white = iosText(12, Color.White, FontWeight.Bold)
    Box(modifier.fillMaxWidth().height(h).background(background)) {
        Row(Modifier.align(Alignment.BottomStart).height(20.dp).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SignalBars(SysState.signal.intValue)
            Spacer(Modifier.width(4.dp))
            val carrier = SysState.carrier.value.ifEmpty { "Pacaembu" }
            T(carrier, white, maxLines = 1, modifier = Modifier.widthIn(max = 90.dp))
            if (SysState.wifi.value) {
                Spacer(Modifier.width(4.dp))
                Image(painterResource(R.drawable.ion_wifi), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(Color.White))
            }
        }
        T(time, white, Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp))
        Row(Modifier.align(Alignment.BottomEnd).height(20.dp).padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            T("${SysState.battery.intValue}%", white)
            Spacer(Modifier.width(4.dp))
            BatteryGlyph(SysState.battery.intValue, SysState.charging.value)
        }
    }
}

@Composable
private fun SignalBars(level: Int) {
    Canvas(Modifier.size(width = 22.dp, height = 11.dp)) {
        val n = 5; val w = size.width / (n * 1.5f)
        for (i in 0 until n) {
            val h = size.height * (i + 1) / n
            val on = i <= level
            drawRect(if (on) Color.White else Color(0x55FFFFFF), Offset(i * w * 1.5f, size.height - h), Size(w, h))
        }
    }
}

@Composable
fun BatteryGlyph(level: Int, charging: Boolean) {
    Canvas(Modifier.size(width = 24.dp, height = 11.dp)) {
        val bw = size.width - 3.dp.toPx()
        drawRoundRect(Color.White, Offset.Zero, Size(bw, size.height), CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx()))
        drawRect(Color.White, Offset(bw, size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f))
        val inset = 2.dp.toPx()
        val fill = (bw - inset * 2) * (level.coerceIn(0, 100) / 100f)
        drawRect(if (charging) Color(0xFF8BE36B) else Color.White, Offset(inset, inset), Size(fill, size.height - inset * 2))
    }
}

// ---------------------------------------------------------------- navigation bar

enum class BarTint(val top: Color, val bottom: Color, val border: Color) {
    BLUE(IosColors.NavTop, IosColors.NavBottom, IosColors.NavBorder),
    BLACK(Color(0xFF4A4A4A), Color(0xFF0C0C0C), Color.Black),
    BROWN(Color(0xFF9A6A3D), Color(0xFF5A3616), Color(0xFF33200C)),
}

private fun glossBrush(top: Color, bottom: Color) = Brush.verticalGradient(
    0f to lerpColor(top, Color.White, 0.18f), 0.5f to top, 0.5f to lerpColor(top, bottom, 0.45f), 1f to bottom,
)

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t,
)

/** Status bar + 44dp gradient navigation bar with centred title and optional back/right buttons. */
@Composable
fun IosNavBar(
    title: String,
    tint: BarTint = BarTint.BLUE,
    back: String? = null,
    onBack: (() -> Unit)? = null,
    right: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        IosStatusBar(background = Brush.verticalGradient(listOf(lerpColor(tint.top, Color.Black, 0.35f), lerpColor(tint.top, Color.Black, 0.25f))))
        Box(
            Modifier.fillMaxWidth().height(44.dp)
                .background(Brush.verticalGradient(listOf(lerpColor(tint.top, Color.White, 0.1f), tint.bottom)))
                .drawWithContent {
                    drawContent()
                    drawLine(tint.border, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
                    drawLine(Color(0x55FFFFFF), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f)
                }
        ) {
            T(title, iosText(20, Color.White, FontWeight.Bold, ShadowDarkUp, TextAlign.Center), Modifier.align(Alignment.Center).padding(horizontal = 90.dp), maxLines = 1)
            if (onBack != null) {
                BackButton(back ?: stringResource(R.string.back), tint, onBack, Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
            }
            if (right != null) Row(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), content = right)
        }
    }
}

private val BackShape: Shape = GenericShape { size, _ ->
    val r = 5f * (size.height / 30f)
    val tip = size.height * 0.42f
    moveTo(0f, size.height / 2)
    lineTo(tip, 0f)
    lineTo(size.width - r, 0f)
    quadraticBezierTo(size.width, 0f, size.width, r)
    lineTo(size.width, size.height - r)
    quadraticBezierTo(size.width, size.height, size.width - r, size.height)
    lineTo(tip, size.height)
    close()
}

@Composable
fun BackButton(text: String, tint: BarTint, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BarButtonBox(text, tint, BackShape, onClick, modifier, startPad = 14.dp)
}

/** Bordered bar button; [done] gives the blue "Done/Save" style. */
@Composable
fun IosBarButton(text: String, onClick: () -> Unit, tint: BarTint = BarTint.BLUE, done: Boolean = false, modifier: Modifier = Modifier) {
    BarButtonBox(text, tint, RoundedCornerShape(5.dp), onClick, modifier, override = if (done) Color(0xFF3F7FE6) else null)
}

@Composable
private fun BarButtonBox(text: String, tint: BarTint, shape: Shape, onClick: () -> Unit, modifier: Modifier, startPad: Dp = 9.dp, override: Color? = null) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val base = override ?: lerpColor(tint.bottom, Color.Black, 0.1f)
    val top = if (pressed) lerpColor(base, Color.Black, 0.3f) else lerpColor(base, Color.White, 0.12f)
    Box(
        modifier.height(30.dp).widthIn(min = 50.dp)
            .clip(shape)
            .background(glossBrush(top, if (pressed) lerpColor(base, Color.Black, 0.4f) else base))
            .border(1.dp, tint.border.copy(alpha = 0.8f), shape)
            .clickable(src, null, onClick = onClick)
            .padding(start = startPad, end = 9.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, iosText(12, Color.White, FontWeight.Bold, ShadowDarkUp), maxLines = 1) }
}

// ---------------------------------------------------------------- grouped tables

@Composable
fun IosScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().pinstripe(), content = content)
}

@Composable
fun GroupHeader(text: String) {
    T(text, iosText(17, IosColors.GroupHeader, FontWeight.Bold, ShadowWhiteDown), Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
fun GroupFooter(text: String) {
    T(text, iosText(14, IosColors.GroupHeader, shadow = ShadowWhiteDown, align = TextAlign.Center), Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
}

/** White rounded group of cells. Separators are drawn between children. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(shape).background(Color.White).border(1.dp, IosColors.GroupBorder, shape),
        content = content,
    )
}

@Composable
fun Separator() { Box(Modifier.fillMaxWidth().height(1.dp).background(IosColors.Separator)) }

/** A grouped-table row: optional small icon, title, detail text / accessory, chevron. */
@Composable
fun Cell(
    title: String,
    detail: String? = null,
    icon: Int? = null,
    iconColor: Color = IosColors.Blue,
    chevron: Boolean = false,
    checked: Boolean = false,
    titleColor: Color = Color.Black,
    center: Boolean = false,
    accessory: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg = if (pressed && onClick != null) Brush.verticalGradient(listOf(Color(0xFF058CF5), Color(0xFF015FE6))) else Brush.verticalGradient(listOf(Color.White, Color.White))
    val fg = if (pressed && onClick != null) Color.White else titleColor
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(bg)
            .then(if (onClick != null) Modifier.clickable(src, null, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
    ) {
        if (icon != null) {
            SettingsIcon(icon, iconColor)
            Spacer(Modifier.width(10.dp))
        }
        T(title, iosText(17, fg, FontWeight.Bold), if (center) Modifier else Modifier.weight(1f), maxLines = 1)
        if (!center) {
            if (detail != null) T(detail, iosText(17, if (pressed && onClick != null) Color.White else IosColors.Detail), Modifier.padding(start = 8.dp).widthIn(max = 170.dp), maxLines = 1)
            accessory?.invoke()
            if (checked) T("✓", iosText(20, IosColors.Detail, FontWeight.Bold), Modifier.padding(start = 6.dp))
            if (chevron) {
                Image(painterResource(R.drawable.ion_chevron_forward), null, Modifier.padding(start = 6.dp).size(14.dp), colorFilter = ColorFilter.tint(if (pressed) Color.White else IosColors.Chevron))
            }
        }
    }
}

/** 29dp rounded glossy square with a white glyph, like the Settings app icons. */
@Composable
fun SettingsIcon(glyph: Int, color: Color, side: Dp = 29.dp) {
    val shape = RoundedCornerShape(side * 0.2f)
    Box(
        Modifier.size(side).clip(shape)
            .background(Brush.verticalGradient(listOf(lerpColor(color, Color.White, 0.35f), color, lerpColor(color, Color.Black, 0.15f))))
            .border(0.5.dp, Color(0x33000000), shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(glyph), null, Modifier.size(side * 0.62f), colorFilter = ColorFilter.tint(Color.White))
        Box(Modifier.fillMaxSize().drawWithContent {
            drawOval(Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x10FFFFFF)), 0f, size.height * 0.5f),
                Offset(-size.width * 0.3f, -size.height * 0.6f), Size(size.width * 1.6f, size.height * 1.1f))
        })
    }
}

// ---------------------------------------------------------------- switch

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 0.dp else (-52).dp, label = "sw")
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.size(width = 79.dp, height = 27.dp).clip(shape)
            .border(1.dp, Color(0xFF9A9A9A), shape)
            .clickable { onChange(!checked) }
    ) {
        Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset(x = x).width(131.dp).fillMaxHeight()) {
            Box(Modifier.width(52.dp).fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0659C6), Color(0xFF3F96F2)))), contentAlignment = Alignment.Center) {
                T(stringResource(R.string.switch_on), iosText(16, Color.White, FontWeight.Bold, ShadowDarkUp), Modifier.offset(x = 6.dp))
            }
            Box(
                Modifier.size(27.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFD0D0D0), Color(0xFFFFFFFF))))
                    .border(1.dp, Color(0xFFA0A0A0), CircleShape)
            )
            Box(Modifier.width(52.dp).fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFDADADA), Color(0xFFFDFDFD)))), contentAlignment = Alignment.Center) {
                T(stringResource(R.string.switch_off), iosText(16, Color(0xFF808080), FontWeight.Bold), Modifier.offset(x = (-6).dp))
            }
        }
    }
}

// ---------------------------------------------------------------- tab bar

data class Tab(val label: String, val glyph: Int)

@Composable
fun IosTabBar(tabs: List<Tab>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color(0xFF3B3B3B), 0.5f to Color(0xFF1C1C1C), 0.5f to Color(0xFF0B0B0B), 1f to Color.Black))
            .navigationBarsPadding().height(49.dp).padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            Column(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp))
                    .background(if (sel) Color(0x26FFFFFF) else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(painterResource(t.glyph), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(if (sel) Color(0xFF3DB0F7) else Color(0xFF8E8E8E)))
                T(t.label, iosText(10, if (sel) Color.White else Color(0xFF9A9A9A), FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- alert

/** The translucent blue iOS 6 alert view. */
@Composable
fun IosAlert(title: String, message: String?, buttons: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x88000000)).clickable(remember { MutableInteractionSource() }, null) { onDismiss() }, contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(10.dp)
        Column(
            Modifier.width(284.dp).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xE6334C7E), Color(0xEB0C1A3E))))
                .border(2.dp, Color(0xCCFFFFFF), shape)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(title, iosText(18, Color.White, FontWeight.Bold, ShadowDarkUp, TextAlign.Center))
            if (message != null) T(message, iosText(16, Color.White, shadow = ShadowDarkUp, align = TextAlign.Center), Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.forEachIndexed { i, (label, action) ->
                    AlertButton(label, i == buttons.lastIndex && buttons.size > 1, Modifier.weight(1f), action)
                }
            }
        }
    }
}

@Composable
private fun AlertButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val base = if (primary) Color(0xFF4F6EA6) else Color(0xFF34497A)
    Box(
        modifier.height(43.dp).clip(shape)
            .background(glossBrush(lerpColor(base, Color.White, 0.25f), base))
            .border(1.dp, Color(0xFF0B1630), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(label, iosText(17, Color.White, FontWeight.Bold, ShadowDarkUp), maxLines = 1) }
}

// ---------------------------------------------------------------- big glossy button

@Composable
fun GlossyButton(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(9.dp)
    val c = if (pressed) lerpColor(color, Color.Black, 0.25f) else color
    Box(
        modifier.height(46.dp).clip(shape)
            .background(glossBrush(lerpColor(c, Color.White, 0.3f), c))
            .border(1.dp, lerpColor(color, Color.Black, 0.5f), shape)
            .clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(text, iosText(19, textColor, FontWeight.Bold, if (textColor == Color.White) ShadowDarkUp else ShadowWhiteDown), maxLines = 1) }
}

@Composable
fun BoxScope.EmptyLabel(text: String) {
    T(text, iosText(17, IosColors.GroupHeader, FontWeight.Bold, ShadowWhiteDown, TextAlign.Center), Modifier.align(Alignment.Center).padding(24.dp))
}
