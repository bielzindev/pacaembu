package com.pacaembu.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pacaembu.R
import com.pacaembu.core.AppRepository
import com.pacaembu.core.BuiltIn
import com.pacaembu.core.HomeLayout
import com.pacaembu.core.LayoutOps
import com.pacaembu.core.Prefs
import kotlin.math.ceil
import kotlin.math.min

/** Pixel geometry of the springboard, shared by drawing and hit testing. */
class Geo(val w: Float, val h: Float, val dp: Float, top: Float, val bottom: Float) {
    val gridTop = top + 10 * dp
    val dockH = 96 * dp
    val dockTop = h - bottom - dockH
    val dotsH = 22 * dp
    val gridBottom = dockTop - dotsH
    val cols = 4
    val cellW = w / cols
    val rows = ((gridBottom - gridTop) / (88 * dp)).toInt().coerceIn(4, 6)
    val cellH = (gridBottom - gridTop) / rows
    val icon = min(60 * dp, cellW * 0.72f)
    val iconTop = 6 * dp
    val capacity get() = cols * rows

    fun cell(i: Int) = Offset((i % cols) * cellW, gridTop + (i / cols) * cellH)
    fun iconRect(cellTopLeft: Offset) = Rect(Offset(cellTopLeft.x + (cellW - icon) / 2, cellTopLeft.y + iconTop), androidx.compose.ui.geometry.Size(icon, icon))

    private val slotW get() = w / 4
    fun dockCell(i: Int, n: Int) = Offset((w - n * slotW) / 2 + i * slotW, dockTop + 12 * dp)
    fun dockIndexAt(x: Float, n: Int) = ((x - (w - n * slotW) / 2) / slotW).toInt().coerceIn(0, (n - 1).coerceAtLeast(0))
}

data class Hover(val slot: LayoutOps.Slot, val target: String?, val center: Boolean)

/** Where an open folder sits and how much the rows around it are pushed. */
data class FolderGeom(val id: String, val page: Int, val row: Int, val top: Float, val height: Float, val up: Float, val down: Float, val arrowX: Float, val inDock: Boolean)

class HomeState(private val ctx: Context) {
    var layout by mutableStateOf(HomeLayout(emptyList(), emptyList(), emptyMap()))
    var loaded by mutableStateOf(false)
    var edit by mutableStateOf(false)
    var openFolder by mutableStateOf<String?>(null)
    var drag by mutableStateOf<String?>(null)
    var dragPos by mutableStateOf(Offset.Zero)
    var grab by mutableStateOf(Offset.Zero)
    var hover by mutableStateOf<Hover?>(null)
    var folderTarget by mutableStateOf<String?>(null)
    var dropKey by mutableStateOf<String?>(null)
    var dropFrom by mutableStateOf(Offset.Zero)
    var dropSeq by mutableIntStateOf(0)
    var homeSeq by mutableIntStateOf(0)
    var confirmDelete by mutableStateOf<String?>(null)
    var notFound by mutableStateOf<String?>(null)
    var capacity = 20

    fun load(installed: List<String>) {
        val saved = HomeLayout.fromJson(Prefs.layout.value)
        val base = saved ?: HomeLayout.default(installed)
        layout = LayoutOps.reconcile(base, installed, capacity)
        loaded = true
        save()
    }

    fun save() { Prefs.layout.value = layout.toJson() }

    fun label(k: String): String {
        if (HomeLayout.isFolder(k)) return layout.folders[HomeLayout.folderId(k)]?.name ?: ""
        BuiltIn.byKey[k]?.let { return ctx.getString(it.label) }
        return AppRepository.apps.value.firstOrNull { it.key == k }?.label ?: ""
    }

    // ------------------------------------------------------------ folder geometry

    fun folderGeom(g: Geo): FolderGeom? {
        val id = openFolder ?: return null
        val f = layout.folders[id] ?: return null
        val rows = ceil(f.items.size / 4f).toInt().coerceAtLeast(1)
        val height = 58 * g.dp + rows * g.cellH
        return when (val s = LayoutOps.find(layout, "folder:$id")) {
            is LayoutOps.PageSlot -> {
                val row = s.index / g.cols
                var top = g.gridTop + (row + 1) * g.cellH
                val limit = g.h - g.bottom
                val up = (top + height - limit).coerceAtLeast(0f)
                top -= up
                FolderGeom(id, s.page, row, top, height, up, height - up, g.cell(s.index).x + g.cellW / 2, false)
            }
            is LayoutOps.DockSlot -> {
                val top = g.dockTop - height
                FolderGeom(id, -1, -1, top, height, height, 0f, g.dockCell(s.index, layout.dock.size).x + g.cellW / 2, true)
            }
            null -> null
        }
    }

    fun folderItemTopLeft(g: Geo, fg: FolderGeom, i: Int) =
        Offset((i % 4) * g.cellW, fg.top + 52 * g.dp + (i / 4) * g.cellH)

    // ------------------------------------------------------------ hit testing

    /** The app/folder under [pos] on layout page [page] or in the dock. */
    fun itemAt(g: Geo, pos: Offset, page: Int): Pair<String, Offset>? {
        if (pos.y >= g.dockTop) {
            val n = layout.dock.size
            if (n == 0) return null
            val i = g.dockIndexAt(pos.x, n)
            val tl = g.dockCell(i, n)
            if (pos.x < tl.x || pos.x > tl.x + g.cellW) return null
            return layout.dock[i] to tl
        }
        val list = layout.pages.getOrNull(page) ?: return null
        if (pos.y < g.gridTop || pos.y >= g.gridBottom) return null
        val i = ((pos.y - g.gridTop) / g.cellH).toInt() * g.cols + (pos.x / g.cellW).toInt().coerceIn(0, g.cols - 1)
        val k = list.getOrNull(i) ?: return null
        return k to g.cell(i)
    }

    fun hoverAt(g: Geo, pos: Offset, page: Int): Hover? {
        val dragged = drag ?: return null
        if (pos.y >= g.dockTop - 6 * g.dp) {
            val inDock = dragged in layout.dock
            val n = layout.dock.size
            val slots = if (inDock) n else min(n + 1, HomeLayout.DOCK_MAX)
            if (slots == 0) return Hover(LayoutOps.DockSlot(0), null, false)
            // over the centre of an existing dock icon?
            if (n > 0) {
                val i = g.dockIndexAt(pos.x, n)
                val r = g.iconRect(g.dockCell(i, n)).deflate(g.icon * 0.22f)
                if (r.contains(pos) && layout.dock[i] != dragged) return Hover(LayoutOps.DockSlot(i), layout.dock[i], true)
            }
            if (!inDock && n >= HomeLayout.DOCK_MAX) return null
            return Hover(LayoutOps.DockSlot(g.dockIndexAt(pos.x, slots)), null, false)
        }
        if (pos.y < g.gridTop || pos.y >= g.gridBottom) return null
        val list = layout.pages.getOrNull(page) ?: emptyList()
        val col = (pos.x / g.cellW).toInt().coerceIn(0, g.cols - 1)
        val row = ((pos.y - g.gridTop) / g.cellH).toInt().coerceIn(0, g.rows - 1)
        val i = row * g.cols + col
        val k = list.getOrNull(i)
        if (k != null && k != dragged) {
            val r = g.iconRect(g.cell(i)).deflate(g.icon * 0.22f)
            if (r.contains(pos)) return Hover(LayoutOps.PageSlot(page, i), k, true)
        }
        return Hover(LayoutOps.PageSlot(page, min(i, list.size)), k, false)
    }

    // ------------------------------------------------------------ edits

    fun applyHoverMove(h: Hover) {
        val k = drag ?: return
        LayoutOps.move(layout, k, h.slot, capacity)?.let { layout = it }
    }

    fun finishDrop(topLeft: Offset) {
        val k = drag ?: return
        val ft = folderTarget
        var opened: String? = null
        if (ft != null) {
            LayoutOps.dropOnto(layout, k, ft, ctx.getString(R.string.folder_default))?.let { (l, id) ->
                layout = l; opened = id
            }
        }
        layout = LayoutOps.trimEmptyPages(layout)
        drag = null; hover = null; folderTarget = null
        if (opened == null) {
            dropFrom = topLeft; dropKey = k; dropSeq++
        } else openFolder = opened
        save()
    }

    fun takeOutOfFolder(id: String, app: String) {
        layout = LayoutOps.takeOut(layout, id, app, capacity)
        if (layout.folders[id] == null) openFolder = null
        save()
    }

    fun renameFolder(id: String, name: String) {
        layout = LayoutOps.renameFolder(layout, id, name)
        save()
    }

    fun uninstall(k: String) {
        val pkg = k.removePrefix("app:").substringBefore('/')
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
