package com.pacaembu.core

import org.json.JSONArray
import org.json.JSONObject

data class Folder(val id: String, val name: String, val items: List<String>)

/**
 * The home screen: pages of item keys, the dock and the folders. Item keys are app keys
 * ("app:pkg/cls", "builtin:id") or "folder:<id>". Immutable: every edit returns a copy.
 */
data class HomeLayout(
    val pages: List<List<String>>,
    val dock: List<String>,
    val folders: Map<String, Folder>,
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("pages", JSONArray(pages.map { JSONArray(it) }))
        o.put("dock", JSONArray(dock))
        val f = JSONArray()
        folders.values.forEach {
            f.put(JSONObject().put("id", it.id).put("name", it.name).put("items", JSONArray(it.items)))
        }
        o.put("folders", f)
        return o.toString()
    }

    /** Every app key placed anywhere (pages, dock, inside folders). */
    fun allAppKeys(): Set<String> {
        val s = HashSet<String>()
        fun add(k: String) {
            if (isFolder(k)) folders[folderId(k)]?.items?.let { s.addAll(it) } else s.add(k)
        }
        pages.forEach { p -> p.forEach(::add) }
        dock.forEach(::add)
        return s
    }

    companion object {
        const val DOCK_MAX = 4
        const val FOLDER_MAX = 12

        fun isFolder(k: String) = k.startsWith("folder:")
        fun folderId(k: String) = k.removePrefix("folder:")

        fun fromJson(s: String): HomeLayout? = runCatching {
            val o = JSONObject(s)
            val pa = o.getJSONArray("pages")
            val pages = (0 until pa.length()).map { i -> pa.getJSONArray(i).strings() }
            val dock = o.getJSONArray("dock").strings()
            val fa = o.optJSONArray("folders") ?: JSONArray()
            val folders = (0 until fa.length()).map { i ->
                val f = fa.getJSONObject(i)
                Folder(f.getString("id"), f.getString("name"), f.getJSONArray("items").strings())
            }.associateBy { it.id }
            HomeLayout(pages, dock, folders)
        }.getOrNull()

        private fun JSONArray.strings() = (0 until length()).map { getString(it) }

        fun default(apps: List<String>): HomeLayout {
            val first = BuiltIn.defaultPage.map { it.key }
            val dock = BuiltIn.defaultDock.map { it.key }
            return HomeLayout(listOf(first) + listOf(apps), dock, emptyMap())
        }
    }
}

object LayoutOps {
    /**
     * Makes the layout match what is installed: drops apps that are gone, removes empty folders
     * and appends apps that are not on the home screen yet (iOS has no app drawer, so every app
     * lives somewhere on the home screen). Pages are re-flowed to [capacity] items.
     */
    fun reconcile(l: HomeLayout, installed: List<String>, capacity: Int): HomeLayout {
        val ok = installed.toHashSet()
        BuiltIn.entries.forEach { ok.add(it.key) }
        val folders = l.folders.mapValues { (_, f) -> f.copy(items = f.items.filter { it in ok }) }
            .filterValues { it.items.isNotEmpty() }
        fun keep(k: String) = if (HomeLayout.isFolder(k)) HomeLayout.folderId(k) in folders else k in ok
        var pages = l.pages.map { p -> p.filter(::keep) }
        val dock = l.dock.filter(::keep).take(HomeLayout.DOCK_MAX)
        val tmp = HomeLayout(pages, dock, folders)
        val present = tmp.allAppKeys()
        val missing = (BuiltIn.entries.map { it.key } + installed).filter { it !in present }.distinct()
        if (missing.isNotEmpty()) pages = pages + listOf(missing)
        return reflow(HomeLayout(pages, dock, folders), capacity, keepEmpty = false)
    }

    /** Pushes overflow to the next page and (optionally) drops empty pages. */
    fun reflow(l: HomeLayout, capacity: Int, keepEmpty: Boolean): HomeLayout {
        val out = ArrayList<MutableList<String>>()
        var carry = ArrayList<String>()
        for (p in l.pages) {
            val cur = ArrayList<String>(carry); cur.addAll(p)
            carry = ArrayList()
            while (cur.size > capacity) carry.add(0, cur.removeAt(cur.size - 1))
            out.add(cur)
        }
        while (carry.isNotEmpty()) {
            val cur = ArrayList(carry.take(capacity)); carry = ArrayList(carry.drop(capacity)); out.add(cur)
        }
        val pages = if (keepEmpty) out else out.filter { it.isNotEmpty() }
        return l.copy(pages = pages.ifEmpty { listOf(mutableListOf()) })
    }

    sealed interface Slot
    data class PageSlot(val page: Int, val index: Int) : Slot
    data class DockSlot(val index: Int) : Slot

    fun find(l: HomeLayout, key: String): Slot? {
        l.pages.forEachIndexed { p, list -> val i = list.indexOf(key); if (i >= 0) return PageSlot(p, i) }
        val d = l.dock.indexOf(key); if (d >= 0) return DockSlot(d)
        return null
    }

    fun remove(l: HomeLayout, key: String): HomeLayout =
        l.copy(pages = l.pages.map { p -> p.filter { it != key } }, dock = l.dock.filter { it != key })

    /** Moves [key] to [to]; returns null when the move is not possible (full dock). */
    fun move(l: HomeLayout, key: String, to: Slot, capacity: Int): HomeLayout? {
        val from = find(l, key) ?: return null
        if (from == to) return null
        if (to is DockSlot && from !is DockSlot && l.dock.size >= HomeLayout.DOCK_MAX) return null
        val r = remove(l, key)
        val moved = when (to) {
            is DockSlot -> r.copy(dock = r.dock.toMutableList().apply { add(to.index.coerceIn(0, size), key) })
            is PageSlot -> {
                val pages = r.pages.map { it.toMutableList() }.toMutableList()
                while (pages.size <= to.page) pages.add(mutableListOf())
                val p = pages[to.page]
                p.add(to.index.coerceIn(0, p.size), key)
                r.copy(pages = pages)
            }
        }
        return reflowFrom(moved, capacity, (to as? PageSlot)?.page ?: 0)
    }

    /** Re-flows only overflow (keeps empty pages while dragging). */
    private fun reflowFrom(l: HomeLayout, capacity: Int, @Suppress("UNUSED_PARAMETER") page: Int) =
        reflow(l, capacity, keepEmpty = true)

    fun trimEmptyPages(l: HomeLayout) = l.copy(pages = l.pages.filter { it.isNotEmpty() }.ifEmpty { listOf(emptyList()) })

    /** Drops [dragged] onto [target]: adds to the folder or creates a new one in target's place. */
    fun dropOnto(l: HomeLayout, dragged: String, target: String, newName: String): Pair<HomeLayout, String>? {
        if (dragged == target || HomeLayout.isFolder(dragged)) return null
        if (HomeLayout.isFolder(target)) {
            val f = l.folders[HomeLayout.folderId(target)] ?: return null
            if (f.items.size >= HomeLayout.FOLDER_MAX) return null
            val r = remove(l, dragged)
            return r.copy(folders = r.folders + (f.id to f.copy(items = f.items + dragged))) to f.id
        }
        val id = System.currentTimeMillis().toString(36)
        val fk = "folder:$id"
        val r = remove(l, dragged)
        val pages = r.pages.map { p -> p.map { if (it == target) fk else it } }
        val dock = r.dock.map { if (it == target) fk else it }
        return HomeLayout(pages, dock, r.folders + (id to Folder(id, newName, listOf(target, dragged)))) to id
    }

    /** Takes [app] out of folder [id] and puts it right after the folder. */
    fun takeOut(l: HomeLayout, id: String, app: String, capacity: Int): HomeLayout {
        val f = l.folders[id] ?: return l
        val items = f.items - app
        val fk = "folder:$id"
        val folders = if (items.isEmpty()) l.folders - id else l.folders + (id to f.copy(items = items))
        var pages = l.pages.map { it.toMutableList() }
        var dock = l.dock
        val slot = find(l, fk)
        when (slot) {
            is PageSlot -> pages[slot.page].add(slot.index + 1, app)
            is DockSlot -> { pages = pages.toMutableList().also { it.add(mutableListOf(app)) } }
            null -> pages = pages.toMutableList().also { it.add(mutableListOf(app)) }
        }
        if (items.isEmpty()) {
            pages = pages.map { p -> p.filter { it != fk }.toMutableList() }
            dock = dock.filter { it != fk }
        }
        return reflow(HomeLayout(pages, dock, folders), capacity, keepEmpty = false)
    }

    fun renameFolder(l: HomeLayout, id: String, name: String): HomeLayout {
        val f = l.folders[id] ?: return l
        return l.copy(folders = l.folders + (id to f.copy(name = name)))
    }
}
