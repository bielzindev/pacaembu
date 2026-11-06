package com.pacaembu.apps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pacaembu.R
import com.pacaembu.ui.EmptyLabel
import com.pacaembu.ui.IosNavBar
import com.pacaembu.ui.IosScreen
import com.pacaembu.ui.IosStatusBar
import com.pacaembu.ui.IosTabBar
import com.pacaembu.ui.Separator
import com.pacaembu.ui.ShadowDarkUp
import com.pacaembu.ui.ShadowWhiteDown
import com.pacaembu.ui.T
import com.pacaembu.ui.Tab
import com.pacaembu.ui.iosText
import com.pacaembu.ui.iosWindow
import com.pacaembu.ui.lerpColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhoneActivity : ComponentActivity() {
    val permSeq = mutableIntStateOf(0)
    private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permSeq.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iosWindow()
        val need = listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) ask.launch(need.toTypedArray())
        val start = if (intent?.data?.scheme == "tel") 2 else 0
        val number = intent?.data?.schemeSpecificPart ?: ""
        setContent { PhoneApp(permSeq.intValue, start, number) }
    }
}

/** Brazilian style: +55 (DD) NNNNN-NNNN. */
fun formatPhone(raw: String): String {
    val plus = raw.trim().startsWith("+")
    val d = raw.filter { it.isDigit() }
    fun local(x: String) = when (x.length) {
        8 -> x.substring(0, 4) + "-" + x.substring(4)
        9 -> x.substring(0, 5) + "-" + x.substring(5)
        else -> x
    }
    return when {
        d.startsWith("55") && (d.length == 12 || d.length == 13) -> "+55 (${d.substring(2, 4)}) " + local(d.substring(4))
        !plus && (d.length == 10 || d.length == 11) -> "(${d.substring(0, 2)}) " + local(d.substring(2))
        !plus && (d.length == 8 || d.length == 9) -> local(d)
        else -> raw
    }
}

fun call(c: Context, number: String) {
    val uri = Uri.parse("tel:" + Uri.encode(number))
    val granted = ContextCompat.checkSelfPermission(c, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    runCatching { c.startActivity(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)) }
}

@Composable
private fun PhoneApp(permSeq: Int, start: Int, number: String) {
    var tab by remember { mutableIntStateOf(start) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> Recents(permSeq)
                1 -> Contacts(permSeq)
                else -> Keypad(number)
            }
        }
        IosTabBar(listOf(
            Tab(stringResource(R.string.phone_recents), R.drawable.ion_time),
            Tab(stringResource(R.string.phone_contacts), R.drawable.ion_people),
            Tab(stringResource(R.string.phone_keypad), R.drawable.ion_keypad),
        ), tab) { tab = it }
    }
}

private data class CallRow(val number: String, val name: String?, val type: Int, val date: Long)

@Composable
private fun Recents(permSeq: Int) {
    val c = LocalContext.current
    var rows by remember { mutableStateOf<List<CallRow>?>(null) }
    LaunchedEffect(permSeq) {
        rows = withContext(Dispatchers.IO) {
            runCatching {
                val out = ArrayList<CallRow>()
                c.contentResolver.query(CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                    null, null, CallLog.Calls.DATE + " DESC")?.use { cur ->
                    while (cur.moveToNext() && out.size < 150) out.add(CallRow(cur.getString(0) ?: "", cur.getString(1), cur.getInt(2), cur.getLong(3)))
                }
                out
            }.getOrNull()
        }
    }
    IosScreen {
        IosNavBar(stringResource(R.string.phone_recents))
        val list = rows
        Box(Modifier.fillMaxSize().background(Color.White)) {
            if (list == null) EmptyLabel(stringResource(R.string.phone_need_perm))
            else if (list.isEmpty()) EmptyLabel(stringResource(R.string.phone_no_recents))
            else LazyColumn(Modifier.fillMaxSize()) {
                items(list.size) { i ->
                    val r = list[i]
                    val missed = r.type == CallLog.Calls.MISSED_TYPE || r.type == CallLog.Calls.REJECTED_TYPE
                    Row(
                        Modifier.fillMaxWidth().clickable { call(c, r.number) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            T(r.name?.takeIf { it.isNotBlank() } ?: formatPhone(r.number), iosText(17, if (missed) Color(0xFFD32F2F) else Color.Black, FontWeight.Bold), maxLines = 1)
                            T(stringResource(when (r.type) {
                                CallLog.Calls.OUTGOING_TYPE -> R.string.call_outgoing
                                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> R.string.call_missed
                                else -> R.string.call_incoming
                            }), iosText(13, Color.Gray))
                        }
                        T(DateUtils.getRelativeTimeSpanString(r.date).toString(), iosText(14, Color(0xFF385487)), maxLines = 1)
                    }
                    Separator()
                }
            }
        }
    }
}

private data class ContactRow(val name: String, val number: String)

@Composable
private fun Contacts(permSeq: Int) {
    val c = LocalContext.current
    var rows by remember { mutableStateOf<List<ContactRow>?>(null) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(permSeq) {
        rows = withContext(Dispatchers.IO) {
            runCatching {
                val out = ArrayList<ContactRow>()
                val seen = HashSet<String>()
                c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")?.use { cur ->
                    while (cur.moveToNext()) {
                        val n = cur.getString(0)
                        val num = cur.getString(1)
                        if (n != null && num != null && seen.add(n + num.filter { it.isDigit() })) out.add(ContactRow(n, num))
                    }
                }
                out
            }.getOrNull()
        }
    }
    IosScreen {
        IosNavBar(stringResource(R.string.phone_contacts))
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFB8C1CB), Color(0xFF8D99A6)))).padding(8.dp)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (q.isEmpty()) T(stringResource(R.string.search), iosText(15, Color.Gray))
                BasicTextField(q, { q = it }, singleLine = true, textStyle = iosText(15), modifier = Modifier.fillMaxWidth())
            }
        }
        val list = rows?.filter { q.isBlank() || it.name.contains(q, true) || it.number.contains(q) }
        Box(Modifier.fillMaxSize().background(Color.White)) {
            if (list == null) EmptyLabel(stringResource(R.string.phone_need_perm))
            else LazyColumn(Modifier.fillMaxSize()) {
                var last = ""
                list.forEach { r ->
                    val letter = java.text.Normalizer.normalize(r.name.take(1), java.text.Normalizer.Form.NFD).firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"
                    if (letter != last) {
                        last = letter
                        item {
                            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFA5B1BF), Color(0xFF8E9BAB)))).padding(horizontal = 12.dp, vertical = 2.dp)) {
                                T(letter, iosText(16, Color.White, FontWeight.Bold, ShadowDarkUp))
                            }
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().clickable { call(c, r.number) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            T(r.name, iosText(17, Color.Black, FontWeight.Bold), maxLines = 1)
                            T(formatPhone(r.number), iosText(13, Color.Gray), maxLines = 1)
                        }
                        Separator()
                    }
                }
            }
        }
    }
}

private val KEYS = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")

@Composable
private fun Keypad(initial: String) {
    val c = LocalContext.current
    var num by remember { mutableStateOf(initial.filter { it.isDigit() || it in "+*#" }) }
    Column(Modifier.fillMaxSize().background(Color(0xFF1B1B1B))) {
        IosStatusBar()
        Box(
            Modifier.fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF141414)))),
            contentAlignment = Alignment.Center,
        ) { T(formatPhone(num), iosText(if (num.length > 12) 28 else 36, Color.White, FontWeight.Normal, ShadowDarkUp, TextAlign.Center), maxLines = 1) }
        Column(Modifier.weight(1f).fillMaxWidth()) {
            KEYS.chunked(3).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { (k, letters) -> Key(k, letters) { if (num.length < 20) num += k } }
                }
            }
            Row(Modifier.height(64.dp).fillMaxWidth()) {
                SideKey(R.drawable.ion_person) {
                    runCatching {
                        c.startActivity(Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                            .putExtra(ContactsContract.Intents.Insert.PHONE, num))
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(Brush.verticalGradient(0f to Color(0xFF7DDB6C), 0.5f to Color(0xFF3FB12C), 0.5f to Color(0xFF2E9A1D), 1f to Color(0xFF1F7E12)))
                        .border(1.dp, Color(0xFF0E3E08))
                        .clickable { if (num.isNotEmpty()) call(c, num) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ion_call), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White))
                        Spacer(Modifier.size(8.dp))
                        T(stringResource(R.string.phone_call), iosText(22, Color.White, FontWeight.Bold, ShadowDarkUp))
                    }
                }
                SideKey(R.drawable.ion_backspace) { num = num.dropLast(1) }
            }
            Spacer(Modifier.fillMaxWidth().height(0.dp))
        }
        Box(Modifier.fillMaxWidth().background(Color.Black)) { Spacer(Modifier.height(0.dp)) }
    }
}

@Composable
private fun RowScope.Key(k: String, letters: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val base = if (pressed) Color(0xFF3B6FD6) else Color(0xFFE4E6E9)
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .background(Brush.verticalGradient(listOf(lerpColor(base, Color.White, 0.5f), base, lerpColor(base, Color.Black, 0.18f))))
            .border(0.5.dp, Color(0xFF5E6266))
            .clickable(src, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        T(k, iosText(32, if (pressed) Color.White else Color.Black, FontWeight.Bold, if (pressed) ShadowDarkUp else ShadowWhiteDown))
        if (letters.isNotEmpty()) T(letters, iosText(11, if (pressed) Color.White else Color(0xFF555555), FontWeight.Bold))
    }
}

@Composable
private fun RowScope.SideKey(glyph: Int, onClick: () -> Unit) {
    Box(
        Modifier.weight(0.6f).fillMaxHeight()
            .background(Brush.verticalGradient(listOf(Color(0xFF555555), Color(0xFF222222))))
            .border(0.5.dp, Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Image(painterResource(glyph), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Color.White)) }
}
