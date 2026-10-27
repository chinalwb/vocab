package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Block
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.data.Section
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned

@Composable
fun Chip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// a section heading that is only its label ("**常见搭配:**") — the card's tab already says it
private val LABEL_ONLY = Regex("""^\*\*[^*]+[:：]\s*\*\*$""")

/**
 * Which examples are asked ([excluded]: the plain English of the ones left out — 不再考这句, in every
 * stage), the toggle, and [onFav] for the long-press menu's 收藏. [tapToggles]: a tap toggles too
 * (the entry page); in a 复习 / 考核 run only the long-press menu does, so a stray tap can't.
 */
class ExampleQuiz(val excluded: Set<String>, val onToggle: (String) -> Unit, val onFav: (String) -> Unit = {}, val tapToggles: Boolean = true)

/** The ⭐ example sentences by anchor (their plain English), shown wherever notes are. */
val LocalStarred = androidx.compose.runtime.compositionLocalOf<Map<String, Set<String>>> { emptyMap() }

/** The entries with a 我的笔记, by anchor → its text (the 📝 on a card). */
val LocalNoted = androidx.compose.runtime.compositionLocalOf<Map<String, String>> { emptyMap() }

/** Saves a 我的笔记 (anchor, text) — for the cards that show the box away from the entry page (复习's 先看一遍). */
val LocalOnNote = androidx.compose.runtime.staticCompositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

/** Renders the blocks build.py produced; xref taps are handed to [onXref]. */
@Composable
fun EntryBody(entry: Entry, onXref: (String) -> Unit, quiz: ExampleQuiz? = null) = Blocks(entry.anchor, entry.blocks, onXref, quiz)

/** Notes blocks outside the entry page (阅读, under a reveal); [highlight] marks one example (its plain English), [quiz] gives the long-press menu. */
@Composable
fun NoteBlocks(anchor: String, blocks: List<Block>, onXref: (String) -> Unit, highlight: String? = null, quiz: ExampleQuiz? = null) = Blocks(anchor, blocks, onXref, quiz, highlight = highlight)

/** The blocks as a still picture (分享图片): no 🔊, no ⭐, nothing to tap. */
@Composable
fun StillBlocks(anchor: String, blocks: List<Block>) = Blocks(anchor, blocks, {}, still = true)

/** A section's blocks without its label-only title ("**常见搭配:**"), which a tab or heading already says. */
fun sectionBlocks(s: Section): List<Block> =
    if ((s.blocks.firstOrNull() as? Block.Para)?.text?.let(LABEL_ONLY::matches) == true) s.blocks.drop(1) else s.blocks

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Blocks(anchor: String, blocks: List<Block>, onXref: (String) -> Unit, quiz: ExampleQuiz? = null, highlight: String? = null, still: Boolean = false) {
    val starred = if (still) emptySet() else LocalStarred.current[anchor].orEmpty()
    // long-press on an example (entry page, and a run's notes): 复制 / 收藏 / 不再考这句, in a bottom sheet
    var menuFor by remember { mutableStateOf<io.github.chinalwb.vocab.data.Example?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val link = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEachIndexed { i, block -> when (block) {
            // a table's bold title line is drawn by the table itself, with the ⤢ beside it
            is Block.Para -> if (!titlesTable(blocks, i)) Text(inline(block.text, link, onXref), style = body)
            is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.items.forEach { item ->
                    Row {
                        Text("•", style = body, color = muted, modifier = Modifier.width(18.dp))
                        Text(inline(item, link, onXref), style = body)
                    }
                }
            }
            is Block.Examples -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                block.items.forEachIndexed { i, ex ->
                    // every translated example is a 自测 question unless I tap it out: the number
                    // sits in a filled dot while it's in, a hollow one (and dimmed text) when out
                    val asks = quiz != null && ex.quizable
                    val out = asks && plain(ex.en) in quiz!!.excluded
                    // the sentence just asked, under a reveal
                    val lit = highlight != null && plain(ex.en) == highlight
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (lit) Modifier.background(sheetColors().well, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 6.dp) else Modifier)
                            .then(
                                if (quiz != null) Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(
                                    onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menuFor = ex },
                                    onClick = { if (asks && quiz.tapToggles) quiz.onToggle(plain(ex.en)) },
                                ) else Modifier
                            )
                            .alpha(if (out) 0.45f else 1f),
                    ) {
                        Box(Modifier.width(22.dp).padding(top = 4.dp)) {
                            if (asks) Box(
                                Modifier
                                    .size(18.dp)
                                    .then(if (out) Modifier.border(1.5.dp, muted, CircleShape) else Modifier.background(ink, CircleShape)),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (out) muted else MaterialTheme.colorScheme.surface) }
                            else Text("${i + 1}", style = body, color = muted)
                        }
                        Column(Modifier.weight(1f)) {
                            val en = inline(ex.en, link, onXref)
                            Text(
                                if (plain(ex.en) in starred) androidx.compose.ui.text.buildAnnotatedString { append(en); append(" ⭐") } else en,
                                style = body.copy(fontFamily = AppFont, fontSize = 17.sp),
                            )
                            if (ex.zh.isNotEmpty()) {
                                Text(inline(ex.zh, link, onXref), style = MaterialTheme.typography.bodyMedium, color = muted)
                            }
                        }
                        if (!still) SpeakButton(plain(ex.en), Modifier.padding(start = 4.dp), size = 18.dp)
                    }
                }
                if (quiz != null && block.items.any { it.quizable }) Text(
                    if (quiz.tapToggles) "● 自测、复习和考核都会考这些例句;点一句或长按选「不再考这句」(至少留一句);长按也能复制、收藏"
                    else "● 长按一句:不再考这句、复制、收藏",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(start = 22.dp),
                )
            }
            is Block.Quote -> Quote(block.lines, link, onXref)
            is Block.Table -> Table(block, link, onXref, title = (blocks.getOrNull(i - 1) as? Block.Para)?.text?.takeIf { titlesTable(blocks, i - 1) }, still = still)
            Block.Unknown -> {}
        } }
    }
    val ex = menuFor
    if (ex != null && quiz != null) ExampleMenu(
        ex,
        starred = plain(ex.en) in starred,
        inQuiz = if (ex.quizable) plain(ex.en) !in quiz.excluded else null,
        onFav = { quiz.onFav(plain(ex.en)) },
        onToggle = { quiz.onToggle(plain(ex.en)) },
        onDismiss = { menuFor = null },
    )
}

/** The long-press menu on an example: 复制, 收藏 / 取消收藏, and 不再考这句 / 恢复考这句 when it can be asked ([inQuiz] non-null). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExampleMenu(ex: io.github.chinalwb.vocab.data.Example, starred: Boolean, inQuiz: Boolean?, onFav: () -> Unit, onToggle: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            // the sentence itself on top, so it's clear which one the menu is for
            Text(plain(ex.en), fontFamily = AppFont, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.padding(horizontal = 24.dp))
            if (ex.zh.isNotEmpty()) Text(plain(ex.zh), style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp))
            MenuRow(ContentCopyIcon, "复制") {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("例句", plain(ex.en)))
                // Android 13+ shows its own copied notice
                if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            MenuRow(if (starred) androidx.compose.material.icons.Icons.Default.Star else StarBorderIcon, if (starred) "取消收藏" else "收藏") { onFav(); onDismiss() }
            if (inQuiz != null) MenuRow(
                if (inQuiz) androidx.compose.material.icons.Icons.Default.Clear else androidx.compose.material.icons.Icons.Default.Add,
                if (inQuiz) "不再考这句" else "恢复考这句",
            ) { onToggle(); onDismiss() }
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        androidx.compose.material3.Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Quote(lines: List<String>, link: androidx.compose.ui.graphics.Color, onXref: (String) -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
        )
        Column(Modifier.padding(start = 12.dp)) {
            lines.forEach {
                Text(
                    inline(it, link, onXref),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AppFont, fontSize = 17.sp, lineHeight = 26.sp),
                )
            }
        }
    }
}

/**
 * A comparison table (用户 2026-10-27; worth / worthy / worthwhile side by side), rules between rows
 * only. Column widths work the way a browser's do: every column gets at least its longest word,
 * the room left over goes to the columns with the most text, and the table fills the card.
 * Chinese can break after any character, so a column is also kept at least four characters wide
 * (or its whole text, if shorter): 形容词 stays on one line instead of stacking.
 * If even the longest words don't fit, the columns shrink in step and long words wrap.
 */
private val BOLD_LINE = Regex("""^\*\*([^*]+)\*\*$""")

/** Is blocks[i] a table's title — a whole-bold line ("**对比:worth / worthy / worthwhile**") right above it? */
private fun titlesTable(blocks: List<Block>, i: Int): Boolean =
    blocks.getOrNull(i + 1) is Block.Table && (blocks.getOrNull(i) as? Block.Para)?.text?.let(BOLD_LINE::matches) == true

/**
 * The table under its title line; a rotate-phone icon at the end of that line (not in a shared picture) opens it
 * full screen, in landscape. [title] is the bold line's markdown, or null when there's none.
 */
@Composable
private fun Table(table: Block.Table, link: androidx.compose.ui.graphics.Color, onXref: (String) -> Unit, title: String?, still: Boolean) {
    var full by rememberSaveable { mutableStateOf(false) }
    val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { if (title != null) Text(inline(title, link, onXref), style = body) }
            if (!still) androidx.compose.material3.IconButton(onClick = { full = true }, Modifier.size(36.dp)) {
                androidx.compose.material3.Icon(ScreenRotationIcon, "横屏查看表格", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TableGrid(table, link, onXref)
    }
    if (full) TableScreen(table, title?.let { BOLD_LINE.matchEntire(it)?.groupValues?.get(1) }, link, onXref) { full = false }
}

/**
 * 横屏查看表格 (用户 2026-10-27): the table alone on a full-screen page, the phone turned to landscape
 * while it's open (the activity handles the rotation itself — configChanges in the manifest — so
 * this page stays open through it) and turned back on ✕ or Back. Taps on cross-references still work.
 */
@Composable
private fun TableScreen(table: Block.Table, title: String?, link: androidx.compose.ui.graphics.Color, onXref: (String) -> Unit, onClose: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (before != null) activity.requestedOrientation = before }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title ?: "表格", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = onClose) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close, "关闭", Modifier.size(22.dp))
                    }
                }
                Box(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
                    TableGrid(table, link, onXref)
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun TableGrid(table: Block.Table, link: androidx.compose.ui.graphics.Color, onXref: (String) -> Unit) {
    val cols = table.head.size
    val rows = listOf(table.head) + table.rows
    val style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)
    val head = MaterialTheme.colorScheme.onSurfaceVariant
    val rule = MaterialTheme.colorScheme.outlineVariant
    androidx.compose.ui.layout.Layout(content = {
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, cell ->
                Text(
                    inline(cell, link, onXref),
                    style = if (r == 0) style.copy(fontWeight = FontWeight.Bold) else style,
                    modifier = Modifier.padding(end = if (c < cols - 1) 12.dp else 0.dp, top = 7.dp, bottom = 7.dp),
                )
            }
            // under the header a darker rule, then a light one between rows
            if (r < rows.size - 1) Box(Modifier.background(if (r == 0) head else rule))
        }
    }) { measurables, constraints ->
        val cells = measurables.chunked(cols + 1).map { it.take(cols) }
        val rules = measurables.chunked(cols + 1).mapNotNull { it.getOrNull(cols) }
        val floor = (style.fontSize * 4).roundToPx() + 12.dp.roundToPx()
        val max = IntArray(cols) { c -> cells.maxOf { it[c].maxIntrinsicWidth(Int.MAX_VALUE) } }
        val min = IntArray(cols) { c ->
            cells.maxOf { maxOf(it[c].minIntrinsicWidth(Int.MAX_VALUE), minOf(it[c].maxIntrinsicWidth(Int.MAX_VALUE), floor)) }
        }
        val avail = if (constraints.hasBoundedWidth) constraints.maxWidth else max.sum()
        val (minSum, maxSum) = min.sum() to max.sum()
        val width = IntArray(cols) { c ->
            when {
                maxSum <= avail -> max[c] + (avail - maxSum) * max[c] / maxSum.coerceAtLeast(1)
                minSum >= avail -> min[c] * avail / minSum.coerceAtLeast(1)
                else -> min[c] + ((avail - minSum).toLong() * (max[c] - min[c]) / (maxSum - minSum)).toInt()
            }
        }
        val placed = cells.map { row -> row.mapIndexed { c, m -> m.measure(androidx.compose.ui.unit.Constraints.fixedWidth(width[c])) } }
        val line = rules.map { it.measure(androidx.compose.ui.unit.Constraints.fixed(avail, 1.dp.roundToPx())) }
        val heights = placed.map { row -> row.maxOf { it.height } }
        layout(avail, heights.sum() + line.sumOf { it.height }) {
            var y = 0
            placed.forEachIndexed { r, row ->
                var x = 0
                row.forEachIndexed { c, p -> p.place(x, y); x += width[c] }
                y += heights[r]
                line.getOrNull(r)?.let { it.place(0, y); y += it.height }
            }
        }
    }
}

/**
 * The entry page's notes (UI refactor, direction A, 用户 2026-10-14): one page that scrolls, every
 * section under its own small heading, and a row of section chips on top — a tap scrolls to that
 * section, and the chip of the section in view is the filled one. Replaces the carousel there.
 */
@Composable
fun NotesPage(
    entry: Entry,
    onXref: (String) -> Unit,
    quiz: ExampleQuiz?,
    modifier: Modifier = Modifier,
    inCard: Boolean = false,
    trailing: @Composable () -> Unit = {},
    extra: List<Pair<String, @Composable () -> Unit>> = emptyList(),
) = NotesPager(entry.anchor, entry.sections, entry.blocks, onXref, quiz, modifier, inCard = inCard, trailing = trailing, extra = extra)

/**
 * [NotesPage] for any sectioned blocks — the entry page's notes and 阅读's details share it, the
 * 卡片 | 全部 switch and its remembered choice included. [footer] goes at the end of the long page;
 * [lastCard], when given, is an extra tab after the sections in 卡片 mode only (阅读's 我的原句).
 */
@Composable
fun NotesPager(
    anchor: String,
    sections: List<Section>,
    blocks: List<Block>,
    onXref: (String) -> Unit,
    quiz: ExampleQuiz?,
    modifier: Modifier = Modifier,
    title: String = "笔记",
    footer: (@Composable () -> Unit)? = null,
    lastCard: Section? = null,
    /**
     * On the entry page's card (V2): one header row — the section tabs, the 卡片 / 全部 switch as
     * an icon, then [trailing] (⤢) — instead of the 笔记 heading row over a row of chips.
     */
    inCard: Boolean = false,
    trailing: @Composable () -> Unit = {},
    /** more cards after the notes, each drawn by its own composable — the entry page's 记忆 and 我的笔记 — in both modes */
    extra: List<Pair<String, @Composable () -> Unit>> = emptyList(),
) {
    // 分卡 (one section at a time) or 全部展开 (one long page): the user keeps both (用户 2026-10-14),
    // remembered across entries and launches — the same switch and key the carousel had
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("notes", Context.MODE_PRIVATE) }
    var all by remember { mutableStateOf(prefs.getBoolean("all", false)) }
    val scroll = rememberScrollState()
    val cards = if (lastCard == null) sections else sections + lastCard
    val pageCount = cards.size + extra.size
    // on the long page [extra] comes after the sections, from index sections.size
    val allLast = sections.lastIndex + extra.size
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    val tops = remember(anchor) { androidx.compose.runtime.mutableStateMapOf<Int, Int>() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slack = with(density) { 24.dp.roundToPx() }
    val inView by androidx.compose.runtime.remember(anchor) {
        androidx.compose.runtime.derivedStateOf {
            if (scroll.value >= scroll.maxValue && scroll.maxValue > 0) allLast
            else tops.entries.filter { it.value <= scroll.value + slack }.maxByOrNull { it.value }?.key ?: 0
        }
    }
    var pending by remember(anchor) { mutableStateOf<Int?>(null) }
    val pendingTop = pending?.let { tops[it] }
    LaunchedEffect(pendingTop) { if (pendingTop != null) { scroll.scrollTo(pendingTop); pending = null } }
    val tabbed = pageCount > 1 && !all
    // the chips follow what's on screen: every card in 卡片 mode, the long page's sections in 全部
    val tabs = (if (tabbed) cards else sections).map { it.label } + extra.map { it.first }
    val current = if (tabbed) pager.currentPage else inView
    val chips = rememberLazyListState()
    LaunchedEffect(current) { if (tabs.size > 1) chips.animateScrollToItem((current - 1).coerceAtLeast(0)) }
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val switchTo = { cards: Boolean ->
        if (cards == all) {
            val at = current
            all = !cards
            prefs.edit().putBoolean("all", all).apply()
            // keep the same section in view across the switch; the long page
            // scrolls once it has measured where that section is (below)
            if (all) pending = at.coerceAtMost(allLast) else scope.launch { pager.scrollToPage(at) }
        }
    }
    Column(modifier) {
        if (inCard) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp)) {
            LazyRow(
                state = chips,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            ) {
                itemsIndexed(tabs) { i, label ->
                    val on = i == current
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.surface else ink.copy(alpha = 0.76f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .then(if (on) Modifier.background(ink) else Modifier)
                            .clickable {
                                scope.launch { if (tabbed) pager.animateScrollToPage(i) else scroll.animateScrollTo(tops[i] ?: 0) }
                            }
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                    )
                }
            }
            if (pageCount > 1) androidx.compose.material3.IconButton(onClick = { switchTo(all) }) {
                androidx.compose.material3.Icon(
                    if (all) GridViewIcon else androidx.compose.material.icons.Icons.AutoMirrored.Filled.List,
                    if (all) "分卡显示" else "全部展开",
                    tint = ink.copy(alpha = 0.76f),
                )
            }
            trailing()
        } else if (pageCount > 1) {
            // "笔记 ——— 卡片 | 全部": the switch on a row of its own, so it can't pass for a section chip
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 12.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = muted)
                HorizontalDivider(Modifier.padding(horizontal = 12.dp).weight(1f), color = MaterialTheme.colorScheme.outline)
                // 卡片 | 全部: a two-way switch like 选词 | 打字, the side in use filled
                AnswerModeToggle(!all, switchTo, labels = "卡片" to "全部")
            }
            LazyRow(
                state = chips,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                itemsIndexed(tabs) { i, label ->
                    val on = i == current
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.surface else ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .then(if (on) Modifier.background(ink) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)))
                            .clickable {
                                scope.launch { if (tabbed) pager.animateScrollToPage(i) else scroll.animateScrollTo(tops[i] ?: 0) }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        if (tabbed) HorizontalPager(
            state = pager,
            pageSpacing = 16.dp,
            verticalAlignment = Alignment.Top,
            key = { "${anchor}/$it" },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = if (inCard) 12.dp else 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (page < cards.size) Section(anchor, cards[page], heading = false, onXref, quiz, muted) else extra.getOrNull(page - cards.size)?.second?.invoke()
            }
        } else Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = if (inCard) 12.dp else 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (sections.isEmpty()) Blocks(anchor, blocks, onXref, quiz)
            sections.forEachIndexed { i, s ->
                Column(
                    Modifier.onGloballyPositionedTop { tops[i] = it },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { Section(anchor, s, heading = i > 0, onXref, quiz, muted) }
            }
            extra.forEachIndexed { k, (label, body) ->
                Column(
                    Modifier.onGloballyPositionedTop { tops[sections.size + k] = it },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp), fontWeight = FontWeight.Bold, color = muted)
                    body()
                }
            }
            footer?.invoke()
        }
    }
}

/**
 * One section of the notes. A section whose bold title was only its label ("**常见搭配:**") gets
 * it back as a small [heading] on the long page (on a tab the chip already says it); one with a
 * real title ("**义项二:值得做某事**") keeps that title as it is.
 */
@Composable
private fun Section(anchor: String, s: Section, heading: Boolean, onXref: (String) -> Unit, quiz: ExampleQuiz?, muted: androidx.compose.ui.graphics.Color) {
    val labelOnly = (s.blocks.firstOrNull() as? Block.Para)?.text?.let(LABEL_ONLY::matches) == true
    val blocks = if (labelOnly) s.blocks.drop(1) else s.blocks
    if (labelOnly && heading) Text(
        s.label,
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
        fontWeight = FontWeight.Bold,
        color = muted,
    )
    Blocks(anchor, blocks, onXref, quiz)
}

/** Reports the composable's top within its scrolling parent's content (px), for scroll-to-section. */
private fun Modifier.onGloballyPositionedTop(onTop: (Int) -> Unit): Modifier =
    this.then(Modifier.onGloballyPositioned { c ->
        val parent = c.parentLayoutCoordinates ?: return@onGloballyPositioned
        onTop(parent.localPositionOf(c, androidx.compose.ui.geometry.Offset.Zero).y.toInt())
    })
