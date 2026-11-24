package com.pacaembu.apps

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacaembu.R
import com.pacaembu.core.Prefs
import com.pacaembu.ui.BarTint
import com.pacaembu.ui.IosBarButton
import com.pacaembu.ui.IosNavBar
import com.pacaembu.ui.T
import com.pacaembu.ui.iosWindow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

class NotesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iosWindow()
        setContent { NotesApp() }
    }
}

data class Note(val id: Long, val text: String, val time: Long)

private object NotesStore {
    fun load(): List<Note> = runCatching {
        val a = JSONArray(Prefs.notes.value.ifEmpty { "[]" })
        (0 until a.length()).map { val o = a.getJSONObject(it); Note(o.getLong("id"), o.getString("text"), o.getLong("time")) }
    }.getOrDefault(emptyList()).sortedByDescending { it.time }

    fun save(list: List<Note>) {
        Prefs.notes.value = JSONArray(list.map { JSONObject().put("id", it.id).put("text", it.text).put("time", it.time) }).toString()
    }
}

private val Paper = Color(0xFFFCF3B5)
private val Ink = Color(0xFF3B2A15)
private val NoteFont = TextStyle(fontFamily = FontFamily.Cursive, fontSize = 21.sp, color = Ink, lineHeight = 30.sp)

private fun Modifier.ruled(): Modifier = drawBehind {
    drawRect(Paper)
    val step = 30.sp.toPx()
    var y = step + 6.dp.toPx()
    while (y < size.height) {
        drawLine(Color(0x553E7BC8), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += step
    }
    drawLine(Color(0x66D04040), Offset(28.dp.toPx(), 0f), Offset(28.dp.toPx(), size.height), 1.dp.toPx())
    drawLine(Color(0x66D04040), Offset(31.dp.toPx(), 0f), Offset(31.dp.toPx(), size.height), 1.dp.toPx())
}

@Composable
private fun NotesApp() {
    var notes by remember { mutableStateOf(NotesStore.load()) }
    var open by remember { mutableStateOf<Note?>(null) }
    fun upsert(n: Note) {
        notes = (notes.filter { it.id != n.id } + n).filter { it.text.isNotBlank() }.sortedByDescending { it.time }
        NotesStore.save(notes)
    }
    val cur = open
    if (cur == null) {
        Column(Modifier.fillMaxSize().ruled()) {
            IosNavBar(stringResource(R.string.notes_title, notes.size), tint = BarTint.BROWN, right = {
                IosBarButton("+", { open = Note(System.currentTimeMillis(), "", System.currentTimeMillis()) }, BarTint.BROWN)
            })
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                items(notes.size, key = { notes[it].id }) { i ->
                    val n = notes[i]
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clickable { open = n }.padding(start = 40.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(n.text.lineSequence().firstOrNull { it.isNotBlank() } ?: "", NoteFont, Modifier.weight(1f), maxLines = 1)
                        T(shortTime(n.time), NoteFont.copy(fontSize = 15.sp, color = Color(0xFF8A6A3A)))
                    }
                }
            }
        }
    } else {
        NoteEditor(cur, onDone = { upsert(it); open = null }, onDelete = {
            notes = notes.filter { it.id != cur.id }; NotesStore.save(notes); open = null
        })
    }
}

@Composable
private fun shortTime(t: Long): String {
    val c = LocalContext.current
    val today = android.text.format.DateUtils.isToday(t)
    return if (today) DateFormat.getTimeFormat(c).format(Date(t)) else DateFormat.getDateFormat(c).format(Date(t))
}

@Composable
private fun NoteEditor(note: Note, onDone: (Note) -> Unit, onDelete: () -> Unit) {
    val c = LocalContext.current
    var text by remember { mutableStateOf(note.text) }
    val finish = { onDone(note.copy(text = text, time = if (text != note.text) System.currentTimeMillis() else note.time)) }
    BackHandler { finish() }
    Column(Modifier.fillMaxSize().ruled()) {
        IosNavBar(stringResource(R.string.app_notes), tint = BarTint.BROWN, back = stringResource(R.string.app_notes), onBack = finish)
        T(DateFormat.getLongDateFormat(c).format(Date(note.time)) + "  " + DateFormat.getTimeFormat(c).format(Date(note.time)),
            NoteFont.copy(fontSize = 14.sp, color = Color(0xFF8A6A3A)), Modifier.fillMaxWidth().padding(start = 40.dp, top = 8.dp))
        BasicTextField(
            text, { text = it },
            textStyle = NoteFont,
            cursorBrush = SolidColor(Ink),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 40.dp, end = 12.dp, top = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF9A6A3D), Color(0xFF5A3616)))).navigationBarsPadding().height(44.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clickable {
                runCatching { c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)) }
            }.padding(10.dp)) { Image(painterResource(R.drawable.ion_share_outline), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White)) }
            Box(Modifier.clickable { onDelete() }.padding(10.dp)) {
                Image(painterResource(R.drawable.ion_trash), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White))
            }
        }
    }
}
