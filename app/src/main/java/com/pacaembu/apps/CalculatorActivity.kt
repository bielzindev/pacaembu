package com.pacaembu.apps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pacaembu.ui.IosStatusBar
import com.pacaembu.ui.ShadowDarkUp
import com.pacaembu.ui.ShadowWhiteDown
import com.pacaembu.ui.T
import com.pacaembu.ui.iosText
import com.pacaembu.ui.iosWindow
import com.pacaembu.ui.lerpColor
import java.math.BigDecimal
import java.math.MathContext

class CalculatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iosWindow()
        setContent { Calculator() }
    }
}

/** Immediate-execution calculator like the iOS 6 one. */
private class Calc {
    var display by mutableStateOf("0")
    var op by mutableStateOf<Char?>(null)
    var memory by mutableStateOf(0.0)
    private var acc: Double? = null
    private var typing = false

    private fun value() = display.replace(',', '.').toDoubleOrNull() ?: 0.0

    fun digit(d: String) {
        if (!typing) { display = if (d == ".") "0." else d; typing = true; return }
        if (d == "." && display.contains('.')) return
        if (display.replace("-", "").replace(".", "").length >= 9) return
        display = if (display == "0" && d != ".") d else display + d
    }

    fun operator(o: Char) {
        if (typing || acc == null) compute()
        op = o; typing = false
    }

    fun equals() { compute(); op = null; acc = null }

    private fun compute() {
        val v = value()
        val a = acc
        val o = op
        val r = if (a == null || o == null) v else when (o) {
            '+' -> a + v; '−' -> a - v; '×' -> a * v
            '÷' -> if (v == 0.0) Double.NaN else a / v
            else -> v
        }
        acc = r
        display = format(r)
        typing = false
    }

    fun clear() { if (typing && display != "0") { display = "0"; typing = false } else { display = "0"; acc = null; op = null; typing = false } }
    fun negate() { display = format(-value()); if (!typing) acc = value() }
    fun mem(k: String) {
        when (k) {
            "mc" -> memory = 0.0
            "m+" -> memory += value()
            "m-" -> memory -= value()
            "mr" -> { display = format(memory); typing = false }
        }
        if (k != "mr") typing = false
    }

    private fun format(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "Error"
        val bd = BigDecimal(d).round(MathContext(9)).stripTrailingZeros()
        val s = if (bd.abs() >= BigDecimal("1e9") || (bd.abs() < BigDecimal("1e-8") && bd.signum() != 0)) "%.5e".format(d) else bd.toPlainString()
        return if (s == "-0") "0" else s
    }
}

@Composable
private fun Calculator() {
    val calc = remember { Calc() }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF151515)))).navigationBarsPadding()) {
        IosStatusBar()
        // LCD display
        Box(
            Modifier.fillMaxWidth().padding(10.dp).height(96.dp).clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFD9DED6), Color(0xFFB8BFB5))))
                .border(1.dp, Color(0xFF111111), RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (calc.memory != 0.0) T("M", iosText(14, Color(0xFF333333), FontWeight.Bold), Modifier.align(Alignment.TopStart).padding(top = 6.dp))
            T(calc.display, iosText(if (calc.display.length > 8) 44 else 56, Color(0xFF202020), FontWeight.Bold, ShadowWhiteDown, TextAlign.End), maxLines = 1)
        }
        Column(Modifier.weight(1f).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyRow { listOf("mc", "m+", "m-", "mr").forEach { k -> Key(k, KeyKind.FN) { calc.mem(k) } } }
            KeyRow {
                Key("C", KeyKind.FN) { calc.clear() }
                Key("±", KeyKind.FN) { calc.negate() }
                Key("÷", KeyKind.OP, selected = calc.op == '÷') { calc.operator('÷') }
                Key("×", KeyKind.OP, selected = calc.op == '×') { calc.operator('×') }
            }
            KeyRow {
                listOf("7", "8", "9").forEach { d -> Key(d) { calc.digit(d) } }
                Key("−", KeyKind.OP, selected = calc.op == '−') { calc.operator('−') }
            }
            KeyRow {
                listOf("4", "5", "6").forEach { d -> Key(d) { calc.digit(d) } }
                Key("+", KeyKind.OP, selected = calc.op == '+') { calc.operator('+') }
            }
            Row(Modifier.weight(2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyRow { listOf("1", "2", "3").forEach { d -> Key(d) { calc.digit(d) } } }
                    KeyRow {
                        Key("0", weight = 2.06f) { calc.digit("0") }
                        Key(".") { calc.digit(".") }
                    }
                }
                Key("=", KeyKind.EQ) { calc.equals() }
            }
        }
    }
}

private enum class KeyKind { NUM, FN, OP, EQ }

@Composable
private fun ColumnScope.KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun RowScope.Key(label: String, kind: KeyKind = KeyKind.NUM, selected: Boolean = false, weight: Float = 1f, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val base = when (kind) {
        KeyKind.NUM -> Color(0xFF3E3E3E)
        KeyKind.FN -> Color(0xFF6A6560)
        KeyKind.OP -> Color(0xFF6A6560)
        KeyKind.EQ -> Color(0xFFE8731B)
    }
    val c = if (pressed || selected) lerpColor(base, Color.White, 0.25f) else base
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.weight(weight).fillMaxHeight().clip(shape)
            .background(Brush.verticalGradient(0f to lerpColor(c, Color.White, 0.22f), 0.5f to c, 0.5f to lerpColor(c, Color.Black, 0.12f), 1f to lerpColor(c, Color.Black, 0.3f)))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color.Black, shape)
            .clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        T(label, iosText(if (kind == KeyKind.FN && label.length > 1) 20 else 30, Color.White, FontWeight.Bold, ShadowDarkUp))
    }
}
