package io.github.chinalwb.vocab.ui

import io.github.chinalwb.vocab.data.LibraryState
import io.github.chinalwb.vocab.data.Read
import io.github.chinalwb.vocab.data.Block
import io.github.chinalwb.vocab.data.Section
import io.github.chinalwb.vocab.data.splitSections
import androidx.compose.runtime.remember
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 阅读 (⋮ → 阅读, 用户 2026-10-14): the same thing said two ways, a common version and a C1 one.
 * Read only — no quiz, no 进度, no 错题本. Data: readlist.md → data.json "reads", newest first.
 * The list shows each item's 关键词 (the C1 phrasing worth learning) and its Chinese, so you can
 * tell whether to open it; the detail page is [ReadDetail].
 */
@Composable
fun ReadScreen(
    lib: LibraryState,
    onOpen: (String) -> Unit,
    /** removed from the list (long-press → 从阅读清单移除, 用户 2026-10-23); readlist.md keeps them */
    removed: Set<String> = emptySet(),
    onRemove: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val data = lib.data
    if (data == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shown = data.reads.filter { it.anchor !in removed }
    val gone = data.reads.filter { it.anchor in removed }
    var showGone by rememberSaveable { mutableStateOf(false) }
    // the one just removed, for 撤销 at the top until the next removal
    var justRemoved by rememberSaveable { mutableStateOf<String?>(null) }
    val undo = justRemoved?.takeIf { it in removed }?.let { a -> data.reads.firstOrNull { it.anchor == a } }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "同一个意思的常用说法和 C1 说法。看关键词挑一条,点开读差别。只读,不考。",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (undo != null) item(key = "undo") {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "已移除「${undo.keywords.firstOrNull() ?: undo.title}」",
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onRemove(undo.anchor, false); justRemoved = null }) { Text("撤销") }
            }
        }
        if (shown.isEmpty()) item {
            Text(if (gone.isEmpty()) "还没有内容。" else "都移除了。", style = MaterialTheme.typography.bodyLarge, color = muted, modifier = Modifier.padding(top = 24.dp))
        }
        items(shown, key = { it.anchor }) { r ->
            ReadRow(r, menu = "从阅读清单移除", onMenu = { onRemove(r.anchor, true); justRemoved = r.anchor }) { onOpen(r.anchor) }
        }
        // what was removed stays reachable: shown faded, long-press → 恢复
        if (gone.isNotEmpty()) item(key = "gone") {
            TextButton(onClick = { showGone = !showGone }) { Text(if (showGone) "收起已移除的 ${gone.size} 条" else "已移除 ${gone.size} 条 · 显示") }
        }
        if (showGone) items(gone, key = { "gone:" + it.anchor }) { r ->
            ReadRow(r, menu = "恢复", faded = true, onMenu = { onRemove(r.anchor, false) }) { onOpen(r.anchor) }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ReadRow(r: Read, menu: String, onMenu: () -> Unit, faded: Boolean = false, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var open by remember { mutableStateOf(false) }
    // the C1 colour (用户 2026-10-14): these are C1 phrasings, and the cards stand off the page
    Box {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = levelColor("C1"),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (faded) 0.5f else 1f)
            .combinedClickable(onClick = onClick, onLongClick = { open = true }),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                r.keywords.joinToString("  ·  ").ifEmpty { r.title },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (r.keywords.isNotEmpty()) Text(
                r.title,
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (r.date.isNotEmpty()) Text(r.date, style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
    androidx.compose.material3.DropdownMenu(open, { open = false }) {
        androidx.compose.material3.DropdownMenuItem(text = { Text(menu) }, onClick = { open = false; onMenu() })
    }
    }
}

/**
 * One 阅读 item: the Chinese on top, then 常用说法, C1 说法 and 差别 with the entry page's
 * 卡片 | 全部 switch (the same remembered choice). 我的原句 is the last tab in 卡片 mode and sits
 * folded at the end in 全部 mode.
 */
@Composable
fun ReadDetail(lib: LibraryState, anchor: String, onXref: (String) -> Unit, modifier: Modifier = Modifier) {
    val r = lib.data?.reads?.firstOrNull { it.anchor == anchor }
    if (r == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val sections = remember(r) { splitSections(r.blocks) }
    var showOriginal by rememberSaveable(anchor) { mutableStateOf(false) }
    // 我的原句 with its mistakes on a red wash, then what each should have been
    val mine = remember(r) { listOf(Block.Quote(r.original)) + listOfNotNull(r.originalNotes.takeIf { it.isNotEmpty() }?.let { Block.Bullets(it) }) }
    // ⤢ (用户 2026-10-23, like the entry page): the Chinese and the date fold away, the card takes the page
    var full by rememberSaveable(anchor) { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        // ⤢ / ⤡ animate (用户 2026-10-24), as on the entry page
        AnimatedVisibility(!full, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (r.date.isNotEmpty()) Text(r.date, style = MaterialTheme.typography.labelMedium, color = muted)
            }
        }
        val cardTop by animateDpAsState(if (full) 4.dp else 8.dp, label = "cardTop")
        // the comparison on a card in the C1 colour, the same as the list's cards (用户 2026-10-23)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = cardTop, bottom = 10.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(levelColor("C1")),
        ) {
        NotesPager(
            r.anchor, sections, r.blocks, onXref, quiz = null,
            modifier = Modifier.weight(1f),
            title = "对比",
            inCard = true,
            trailing = {
                IconButton(onClick = { full = !full }) {
                    Icon(if (full) CloseFullscreenIcon else OpenInFullIcon, if (full) "退出全屏" else "全屏", Modifier.size(20.dp))
                }
            },
            // 我的原句: its own tab in 卡片 mode (用户 2026-10-14), folded at the end of the long page
            lastCard = if (r.original.isEmpty()) null else Section("我的原句", mine),
            footer = if (r.original.isEmpty()) null else { {
                Column(Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { showOriginal = !showOriginal }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("我的原句", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(if (showOriginal) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (showOriginal) "收起" else "展开", tint = muted)
                    }
                    AnimatedVisibility(showOriginal) { NoteBlocks(r.anchor, mine, onXref) }
                }
            } },
        )
        }
    }
}
