package io.github.chinalwb.vocab.ui

import io.github.chinalwb.vocab.data.LibraryState
import io.github.chinalwb.vocab.data.Read
import io.github.chinalwb.vocab.data.splitSections
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
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
fun ReadScreen(lib: LibraryState, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val data = lib.data
    if (data == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
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
        if (data.reads.isEmpty()) item {
            Text("还没有内容。", style = MaterialTheme.typography.bodyLarge, color = muted, modifier = Modifier.padding(top = 24.dp))
        }
        items(data.reads, key = { it.anchor }) { r -> ReadRow(r) { onOpen(r.anchor) } }
    }
}

@Composable
private fun ReadRow(r: Read, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
}

/**
 * One 阅读 item: the Chinese on top, then 常用说法, C1 说法 and 差别 with the entry page's
 * 卡片 | 全部 switch (the same remembered choice); 我的原句 sits folded after the last of them.
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
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (r.date.isNotEmpty()) Text(r.date, style = MaterialTheme.typography.labelMedium, color = muted)
        }
        NotesPager(
            r.anchor, sections, r.blocks, onXref, quiz = null,
            modifier = Modifier.weight(1f),
            title = "对比",
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
                    AnimatedVisibility(showOriginal) { Quote(r.original, MaterialTheme.colorScheme.primary, onXref) }
                }
            } },
        )
    }
}
