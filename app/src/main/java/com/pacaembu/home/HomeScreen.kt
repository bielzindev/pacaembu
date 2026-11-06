package com.pacaembu.home

import android.app.WallpaperManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pacaembu.R
import com.pacaembu.core.AppRepository
import com.pacaembu.core.BuiltIn
import com.pacaembu.core.BuiltInResolver
import com.pacaembu.core.HomeLayout
import com.pacaembu.core.LayoutOps
import com.pacaembu.core.Prefs
import com.pacaembu.icons.AppIcon
import com.pacaembu.notif.NotificationCenter
import com.pacaembu.ui.IosAlert
import com.pacaembu.ui.IosStatusBar
import com.pacaembu.ui.ShadowSoft
import com.pacaembu.ui.T
import com.pacaembu.ui.iosText
import com.pacaembu.ui.linen
import com.pacaembu.ui.statusBarHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin

private val Ease = tween<IntOffset>(260)

@Composable
fun HomeScreen(st: HomeState) {
    val c = LocalContext.current
    val density = LocalDensity.current
    val topDp = statusBarHeight()
    val bottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()

    // Installed apps (minus the system apps that a built-in shortcut already shows).
    val apps = AppRepository.apps.value
    val hideDup = Prefs.hideDuplicates.value
    var installed by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(apps, hideDup) {
        if (!AppRepository.loaded.value) return@LaunchedEffect
        installed = withContext(Dispatchers.Default) {
            val hidden = if (hideDup) BuiltIn.entries.mapNotNull { runCatching { BuiltInResolver.targetPackage(c, it) }.getOrNull() }.toSet() else emptySet()
            apps.filter { it.component.packageName !in hidden }.map { it.key }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val topPx = with(density) { topDp.toPx() }
        val g = remember(wPx, hPx, topPx, bottomPx) { Geo(wPx, hPx, density.density, topPx, bottomPx) }
        st.capacity = g.capacity
        LaunchedEffect(installed, g.capacity) {
            val inst = installed ?: return@LaunchedEffect
            if (st.drag == null) st.load(inst)
        }
        if (st.loaded) HomeLoaded(st, g, topDp)
    }
}

@Composable
private fun HomeLoaded(st: HomeState, g: Geo, topDp: Dp) {
        val c = LocalContext.current
        val view = LocalView.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()

        val pageCount = st.layout.pages.size + 1 + (if (st.drag != null) 1 else 0)
        val pager = rememberPagerState(initialPage = 1) { pageCount }
        val curPage = { pager.currentPage - 1 }
        val nc = remember { Animatable(0f) }

        // jiggle clock for edit mode
        val tAnim = remember { Animatable(0f) }
        LaunchedEffect(st.edit) {
            if (st.edit) while (true) { tAnim.snapTo(0f); tAnim.animateTo((2 * Math.PI).toFloat(), tween(330, easing = LinearEasing)) }
            else tAnim.snapTo(0f)
        }
        val tState = tAnim.asState()

        // ------------------------------------------------ effects
        LaunchedEffect(st.hover) {
            val h = st.hover ?: return@LaunchedEffect
            st.folderTarget = null
            if (h.center && h.target != null) {
                val draggedIsFolder = st.drag?.let { HomeLayout.isFolder(it) } ?: true
                if (!draggedIsFolder) { delay(450); st.folderTarget = h.target }
                else { delay(250); st.applyHoverMove(h.copy(center = false)) }
            } else {
                delay(110); st.applyHoverMove(h)
            }
        }
        var edge by remember { mutableIntStateOf(0) }
        LaunchedEffect(edge) {
            while (edge != 0 && st.drag != null) {
                delay(650)
                val target = (pager.currentPage + edge).coerceIn(1, pager.pageCount - 1)
                if (target != pager.currentPage) pager.animateScrollToPage(target)
                st.hover = st.hoverAt(g, st.dragPos, curPage())
            }
        }
        LaunchedEffect(st.homeSeq) {
            if (st.homeSeq == 0) return@LaunchedEffect
            when {
                nc.value > 0f -> nc.animateTo(0f)
                st.openFolder != null -> st.openFolder = null
                st.edit -> st.edit = false
                pager.currentPage != 1 -> pager.animateScrollToPage(1)
                else -> pager.animateScrollToPage(0)
            }
        }
        LaunchedEffect(pager) {
            snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { p ->
                runCatching {
                    val wm = WallpaperManager.getInstance(c)
                    val n = (pageCount - 1).coerceAtLeast(1)
                    wm.setWallpaperOffsetSteps(1f / n, 0f)
                    wm.setWallpaperOffsets(view.windowToken, (p / n).coerceIn(0f, 1f), 0.5f)
                }
            }
        }
        BackHandler(st.edit || st.openFolder != null || nc.value > 0f || pager.currentPage != 1) { st.homeSeq++ }

        val fg = st.folderGeom(g)

        Box(
            Modifier.fillMaxSize()
                // long press -> edit mode + drag (runs before children so it can take the gesture over)
                .pointerInput(g) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var out = false
                            while (!out) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val ch = ev.changes.firstOrNull { it.id == down.id }
                                out = ch == null || !ch.pressed ||
                                    (ch.position - down.position).getDistance() > viewConfiguration.touchSlop
                            }
                            true
                        }
                        if (cancelled != null) return@awaitEachGesture
                        if (nc.value > 0f || pager.currentPage == 0) return@awaitEachGesture
                        val pos = down.position
                        // inside an open folder: holding an app takes it out
                        val f = st.folderGeom(g)
                        if (f != null) {
                            val folder = st.layout.folders[f.id]
                            if (folder != null && pos.y >= f.top && pos.y <= f.top + f.height) {
                                folder.items.forEachIndexed { i, k ->
                                    val tl = st.folderItemTopLeft(g, f, i)
                                    if (pos.x >= tl.x && pos.x < tl.x + g.cellW && pos.y >= tl.y && pos.y < tl.y + g.cellH) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        st.edit = true
                                        st.takeOutOfFolder(f.id, k)
                                    }
                                }
                            }
                            consumeRest(down.id)
                            return@awaitEachGesture
                        }
                        val hit = st.itemAt(g, pos, curPage())
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        st.edit = true
                        if (hit == null) { consumeRest(down.id); return@awaitEachGesture }
                        st.drag = hit.first
                        st.grab = pos - hit.second
                        st.dragPos = pos
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            ev.changes.forEach { it.consume() }
                            if (!ch.pressed) break
                            st.dragPos = ch.position
                            val p = ch.position
                            edge = when { p.x < 22 * g.dp -> -1; p.x > g.w - 22 * g.dp -> 1; else -> 0 }
                            val h = st.hoverAt(g, p, curPage())
                            if (h != st.hover) st.hover = h
                        }
                        edge = 0
                        st.finishDrop(st.dragPos - st.grab)
                    }
                }
                // pull down from the top half: Notification Center
                .pointerInput(g) {
                    var active = false
                    detectVerticalDragGestures(
                        onDragStart = { o -> active = !st.edit && st.drag == null && st.openFolder == null && o.y < g.h * 0.45f && pager.currentPage != 0 },
                        onDragEnd = { if (active) scope.launch { nc.animateTo(if (nc.value > 0.3f) 1f else 0f, tween(280)) }; active = false },
                        onDragCancel = { if (active) scope.launch { nc.animateTo(0f) }; active = false },
                        onVerticalDrag = { ch, dy ->
                            if (active) { ch.consume(); scope.launch { nc.snapTo((nc.value + dy / g.h).coerceIn(0f, 1f)) } }
                        },
                    )
                }
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = st.drag == null && st.openFolder == null,
                beyondViewportPageCount = 1,
                key = { it },
            ) { p ->
                if (p == 0) Spotlight(st, topDp)
                else PageGrid(st, g, p - 1, fg, tState)
            }

            PageDots(g, pageCount, pager.currentPage, fg)
            Dock(st, g, fg, tState)

            // folder panel
            if (fg != null) FolderPanel(st, g, fg, tState)

            // dragged icon follows the finger, above everything
            st.drag?.let { k ->
                val tl = st.dragPos - st.grab
                Box(Modifier.offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }.zIndex(10f)) {
                    IconCell(st, k, g, lifted = true, tState = tState, inDock = false)
                }
            }

            IosStatusBar(Modifier.align(Alignment.TopCenter), Brush.verticalGradient(listOf(Color(0x99000000), Color(0x66000000))))

            if (nc.value > 0f) NotificationCenter(nc.value, onClose = { scope.launch { nc.animateTo(0f, tween(260)) } }, onDrag = { dy ->
                scope.launch { nc.snapTo((nc.value + dy / g.h).coerceIn(0f, 1f)) }
            }, onRelease = { scope.launch { nc.animateTo(if (nc.value > 0.7f) 1f else 0f, tween(240)) } })

            st.confirmDelete?.let { k ->
                IosAlert(
                    stringResource(R.string.delete_app_title, st.label(k)),
                    stringResource(R.string.delete_app_msg),
                    listOf(
                        stringResource(R.string.cancel) to { st.confirmDelete = null },
                        stringResource(R.string.delete) to { st.confirmDelete = null; st.uninstall(k) },
                    ),
                    onDismiss = { st.confirmDelete = null },
                )
            }
            st.notFound?.let { name ->
                IosAlert(stringResource(R.string.no_app_title, name), stringResource(R.string.no_app_msg),
                    listOf(stringResource(R.string.ok) to { st.notFound = null }), onDismiss = { st.notFound = null })
            }
        }
}

/** Swallows the rest of a gesture so children don't treat it as a tap. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.consumeRest(id: androidx.compose.ui.input.pointer.PointerId) {
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        ev.changes.forEach { it.consume() }
        val ch = ev.changes.firstOrNull { it.id == id } ?: return
        if (!ch.pressed) return
    }
}

// ---------------------------------------------------------------- page grid

private fun shiftFor(fg: FolderGeom?, page: Int, row: Int): Float {
    if (fg == null) return 0f
    if (fg.inDock) return -fg.up
    if (fg.page != page) return 0f
    return if (row <= fg.row) -fg.up else fg.down
}

@Composable
private fun PageGrid(st: HomeState, g: Geo, page: Int, fg: FolderGeom?, tState: androidx.compose.runtime.State<Float>) {
    val items = st.layout.pages.getOrNull(page) ?: emptyList()
    Box(Modifier.fillMaxSize()) {
        items.forEachIndexed { i, k ->
            key(k) {
                val base = g.cell(i)
                val target = Offset(base.x, base.y + shiftFor(fg, page, i / g.cols))
                Placed(st, k, target) { IconCell(st, k, g, lifted = false, tState = tState, inDock = false) }
            }
        }
    }
}

/** Positions an item absolutely; slides when its slot changes and from the finger after a drop. */
@Composable
private fun Placed(st: HomeState, k: String, target: Offset, content: @Composable () -> Unit) {
    val t = IntOffset(target.x.roundToInt(), target.y.roundToInt())
    val anim = remember { Animatable(t, IntOffset.VectorConverter) }
    var seen by remember { mutableIntStateOf(st.dropSeq) }
    val pendingDrop = st.dropKey == k && st.dropSeq != seen
    LaunchedEffect(t, st.dropSeq) {
        if (st.dropKey == k && st.dropSeq != seen) {
            seen = st.dropSeq
            anim.snapTo(IntOffset(st.dropFrom.x.roundToInt(), st.dropFrom.y.roundToInt()))
        }
        anim.animateTo(t, Ease)
    }
    val hidden = st.drag == k || pendingDrop
    Box(Modifier.offset { anim.value }.graphicsLayer { alpha = if (hidden) 0f else 1f }) { content() }
}

@Composable
private fun IconCell(st: HomeState, k: String, g: Geo, lifted: Boolean, tState: androidx.compose.runtime.State<Float>, inDock: Boolean) {
    val c = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val cellW = with(density) { g.cellW.toDp() }
    val iconDp = with(density) { g.icon.toDp() }
    val phase = remember(k) { (k.hashCode() % 628) / 100f }
    val isFolder = HomeLayout.isFolder(k)
    val target = st.folderTarget == k
    val scale by animateFloatAsState(if (lifted) 1.18f else if (target) 1.08f else 1f, label = "s")
    val bounds = remember { mutableStateOf<android.graphics.Rect?>(null) }
    Column(
        Modifier.width(cellW).padding(top = with(density) { g.iconTop.toDp() }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(iconDp)
                .onGloballyPositioned { lc ->
                    val p = lc.positionInWindow()
                    bounds.value = android.graphics.Rect(p.x.toInt(), p.y.toInt(), (p.x + lc.size.width).toInt(), (p.y + lc.size.height).toInt())
                }
                .graphicsLayer {
                    rotationZ = if (st.edit && !lifted) sin(tState.value + phase) * 1.6f else 0f
                    scaleX = scale; scaleY = scale
                    alpha = if (lifted) 0.85f else 1f
                }
                .clickable(remember { MutableInteractionSource() }, null) {
                    if (isFolder) { st.openFolder = HomeLayout.folderId(k); return@clickable }
                    if (st.edit) return@clickable
                    val ok = Launch.open(c, view, k, bounds.value)
                    if (!ok) st.notFound = st.label(k)
                }
        ) {
            if (target && !isFolder) FolderBack(iconDp)
            if (isFolder) FolderIcon(st, HomeLayout.folderId(k), iconDp) else AppIcon(k, iconDp)
            if (st.edit && !lifted && k.startsWith("app:")) {
                DeleteBadge(Modifier.align(Alignment.TopStart).offset((-8).dp, (-8).dp)) { st.confirmDelete = k }
            }
        }
        if (Prefs.labels.value && !lifted) {
            T(st.label(k), iosText(11, Color.White, FontWeight.Bold, ShadowSoft, TextAlign.Center), Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp), maxLines = 1)
        }
    }
}

@Composable
private fun DeleteBadge(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(26.dp).clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(2.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF555555), Color(0xFF0A0A0A))))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) { T("✕", iosText(11, Color.White, FontWeight.Bold)) }
}

@Composable
private fun FolderBack(size: Dp) {
    Box(Modifier.size(size).graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }.clip(RoundedCornerShape(size * 0.2f)).background(Color(0x99222222)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(size * 0.2f)))
}

@Composable
private fun FolderIcon(st: HomeState, id: String, size: Dp) {
    val f = st.layout.folders[id] ?: return
    val shape = RoundedCornerShape(size * 0.175f)
    Box(
        Modifier.size(size).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x99303030), Color(0xB3101010))))
            .border(1.dp, Color(0x40FFFFFF), shape)
            .padding(size * 0.1f)
    ) {
        val mini = size * 0.22f
        val gap = (size * 0.8f - mini * 3) / 2
        f.items.take(9).forEachIndexed { i, k ->
            Box(Modifier.offset((mini + gap) * (i % 3), (mini + gap) * (i / 3))) { AppIcon(k, mini) }
        }
    }
}

// ---------------------------------------------------------------- dock

@Composable
private fun Dock(st: HomeState, g: Geo, fg: FolderGeom?, tState: androidx.compose.runtime.State<Float>) {
    val dy by animateFloatAsState(if (fg != null && !fg.inDock) fg.down else 0f, tween(260), label = "dock")
    val d = LocalDensity.current
    Box(Modifier.fillMaxSize().graphicsLayer { translationY = dy }) {
        // glass shelf
        Canvas(Modifier.fillMaxSize()) {
            val back = g.dockTop + 50 * g.dp
            val front = g.dockTop + 72 * g.dp
            val inset = 26 * g.dp
            val top = Path().apply {
                moveTo(inset, back); lineTo(size.width - inset, back)
                lineTo(size.width - 2 * g.dp, front); lineTo(2 * g.dp, front); close()
            }
            drawPath(top, Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x99E8EEF5)), back, front))
            drawLine(Color(0xCCFFFFFF), Offset(inset, back), Offset(size.width - inset, back), 1.5f * g.dp)
            drawRect(Brush.verticalGradient(listOf(Color(0x66C9D2DC), Color(0x33FFFFFF)), front, size.height), Offset(2 * g.dp, front), Size(size.width - 4 * g.dp, size.height - front))
            drawLine(Color(0xEEFFFFFF), Offset(2 * g.dp, front), Offset(size.width - 2 * g.dp, front), 1.5f * g.dp)
        }
        val n = st.layout.dock.size
        st.layout.dock.forEachIndexed { i, k ->
            key(k) {
                Placed(st, k, g.dockCell(i, n)) { IconCell(st, k, g, lifted = false, tState = tState, inDock = true) }
            }
        }
        // reflections on the shelf
        st.layout.dock.forEachIndexed { i, k ->
            val tl = g.dockCell(i, n)
            val r = g.iconRect(tl)
            Box(
                Modifier.offset { IntOffset(r.left.roundToInt(), (r.bottom + 2 * g.dp).roundToInt()) }
                    .size(with(d) { r.width.toDp() }, with(d) { (14 * g.dp).toDp() })
                    .clipToBounds()
                    .graphicsLayer { alpha = if (st.drag == k) 0f else 0.22f }
            ) {
                Box(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer { scaleY = -1f }) {
                    AppIconOrFolder(st, k, with(d) { r.width.toDp() })
                }
            }
        }
    }
}

@Composable
private fun AppIconOrFolder(st: HomeState, k: String, size: Dp) {
    if (HomeLayout.isFolder(k)) FolderIcon(st, HomeLayout.folderId(k), size) else AppIcon(k, size)
}

// ---------------------------------------------------------------- page dots

@Composable
private fun PageDots(g: Geo, count: Int, current: Int, fg: FolderGeom?) {
    if (fg != null) return
    Row(
        Modifier.fillMaxWidth().offset { IntOffset(0, (g.gridBottom + 6 * g.dp).roundToInt()) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count) {
            val on = i == current
            if (i == 0) {
                Image(painterResource(R.drawable.ion_search), null, Modifier.padding(horizontal = 4.dp).size(9.dp),
                    colorFilter = ColorFilter.tint(if (on) Color.White else Color(0x80FFFFFF)))
            } else {
                Box(Modifier.padding(horizontal = 4.dp).size(7.dp).clip(CircleShape).background(if (on) Color.White else Color(0x66FFFFFF)))
            }
        }
    }
}

// ---------------------------------------------------------------- folder panel

@Composable
private fun FolderPanel(st: HomeState, g: Geo, fg: FolderGeom, tState: androidx.compose.runtime.State<Float>) {
    val f = st.layout.folders[fg.id] ?: return
    val d = LocalDensity.current
    val c = LocalContext.current
    val view = LocalView.current
    val open = remember(fg.id) { Animatable(0f) }
    LaunchedEffect(fg.id) { open.animateTo(1f, tween(260)) }
    // tap outside closes
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { st.openFolder = null })
    val top = fg.top
    Box(
        Modifier.offset { IntOffset(0, top.roundToInt()) }
            .fillMaxWidth()
            .height(with(d) { (fg.height * open.value).toDp() })
            .clip(RoundedCornerShape(0.dp))
            .linen()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent), 0f, 8 * g.dp))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000)), size.height - 8 * g.dp, size.height), Offset(0f, size.height - 8 * g.dp), Size(size.width, 8 * g.dp))
            }
            .clickable(remember { MutableInteractionSource() }, null) {}
    ) {
        Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            if (st.edit) {
                var name by remember(fg.id) { mutableStateOf(f.name) }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    BasicTextField(
                        name, { name = it; st.renameFolder(fg.id, it) },
                        textStyle = iosText(20, Color.White, FontWeight.Bold),
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                T(f.name, iosText(20, Color.White, FontWeight.Bold, ShadowSoft), maxLines = 1)
            }
        }
        f.items.forEachIndexed { i, k ->
            val tl = st.folderItemTopLeft(g, fg, i) - Offset(0f, top)
            Box(Modifier.offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }) {
                val cellW = with(d) { g.cellW.toDp() }
                val iconDp = with(d) { g.icon.toDp() }
                Column(Modifier.width(cellW).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(iconDp).graphicsLayer {
                        rotationZ = if (st.edit) sin(tState.value + i) * 1.6f else 0f
                    }.clickable {
                        if (!st.edit) {
                            if (!Launch.open(c, view, k, null)) st.notFound = st.label(k)
                        }
                    }) { AppIcon(k, iconDp) }
                    T(st.label(k), iosText(11, Color.White, FontWeight.Bold, ShadowSoft, TextAlign.Center), Modifier.padding(top = 4.dp), maxLines = 1)
                }
            }
        }
    }
    // the notch pointing at the folder icon
    Canvas(Modifier.fillMaxSize()) {
        val w = 14 * g.dp
        val p = Path().apply {
            if (fg.inDock) {
                val y = top + fg.height * open.value
                moveTo(fg.arrowX - w, y); lineTo(fg.arrowX, y + w * 0.8f); lineTo(fg.arrowX + w, y); close()
            } else {
                moveTo(fg.arrowX - w, top); lineTo(fg.arrowX, top - w * 0.8f); lineTo(fg.arrowX + w, top); close()
            }
        }
        drawPath(p, Color(0xFF3A3A3C))
    }
}

// ---------------------------------------------------------------- spotlight

@Composable
private fun Spotlight(st: HomeState, top: Dp) {
    val c = LocalContext.current
    val view = LocalView.current
    var q by remember { mutableStateOf("") }
    val all = remember(st.layout, AppRepository.apps.value) { st.layout.allAppKeys().map { it to st.label(it) }.sortedBy { it.second.lowercase() } }
    val results = if (q.isBlank()) emptyList() else all.filter { it.second.contains(q.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().background(Color(0xB3000000)).padding(top = top)) {
        Box(
            Modifier.fillMaxWidth().height(44.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFB8C1CB), Color(0xFF8D99A6))))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp)).background(Color.White).border(1.dp, Color(0xFF6E7A88), RoundedCornerShape(15.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ion_search), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(Color(0xFF8A8A8A)))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) T(stringResource(R.string.search_phone), iosText(15, Color(0xFF9A9A9A)))
                    BasicTextField(q, { q = it }, singleLine = true, textStyle = iosText(15, Color.Black), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            items(results.size) { i ->
                val (k, label) = results[i]
                Row(
                    Modifier.fillMaxWidth().height(50.dp).clickable { if (!Launch.open(c, view, k, null)) st.notFound = label }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(k, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    T(label, iosText(17, Color.White, FontWeight.Bold), maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
            }
        }
    }
}
