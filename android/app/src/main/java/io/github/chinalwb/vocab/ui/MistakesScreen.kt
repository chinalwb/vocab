package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.data.LibraryState
import io.github.chinalwb.vocab.data.Mistake

/** One mistake with the entry it came from. */
private data class MistakeRow(val m: Mistake, val entry: Entry)

// Same accents as --err / --warn in template.html.
@Composable
private fun sevColor(sev: String): Color {
    val dark = isSystemInDarkTheme()
    return if (sev == "error") (if (dark) Color(0xFFF08A78) else Color(0xFFC2412D))
    else (if (dark) Color(0xFFE6B35C) else Color(0xFFB7791F))
}

/**
 * 错题本: every mistake the user made, grouped by type (build.py's MISTAKE_TYPES),
 * newest entry first inside each group. Tapping one opens the entry it came from.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MistakesScreen(lib: LibraryState, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val data = lib.data
    if (data == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var type by rememberSaveable { mutableStateOf<String?>(null) }

    val groups = remember(data) {
        val rows = data.entries
            .sortedByDescending { it.date.trimStart('~') }
            .flatMap { e -> e.mistakes.map { MistakeRow(it, e) } }
            .groupBy { it.m.type }
        // Types the app doesn't know yet (newer data) still show, after the known ones.
        val order = data.mistakeTypes.map { it.key }.ifEmpty { MISTAKE_TAGS }
        (order + (rows.keys - order.toSet())).mapNotNull { k -> rows[k]?.let { k to it } }
    }
    val desc = remember(data) { data.mistakeTypes.associate { it.key to it.desc } }
    val check = remember(data) { data.mistakeTypes.associate { it.key to it.check } }
    val total = groups.sumOf { it.second.size }
    if (type != null && groups.none { it.first == type }) type = null

    if (total == 0) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "还没有错题。词库更新后,标了类型的 ❌ / ⚠️ 会出现在这里。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        item {
            Text(
                "只收自己写错的地方。❌ 是语法错误,⚠️ 是能用但不是最常见的写法。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (check.values.any { it.isNotEmpty() }) item {
            // 发送前自检: most frequent mistake types first; a tap narrows the list to that type
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("发消息前 30 秒自检", style = MaterialTheme.typography.titleSmall)
                Text(
                    "按我最常犯的错误排序,随错题本自动更新。点一行只看这类。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                groups.sortedByDescending { it.second.size }.forEach { (k, list) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { type = k }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Chip(k, MaterialTheme.colorScheme.surfaceContainer)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(check[k].orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            val ex = Regex("\\*\\*(.+?)\\*\\*").find(list.first().m.text)?.groupValues?.get(1)
                            if (ex != null) Text("例:$ex", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${list.size} 处", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                item { FilterChip(type == null, { type = null }, { Text("全部 $total") }) }
                items(groups, key = { it.first }) { (k, list) ->
                    FilterChip(type == k, { type = if (type == k) null else k }, { Text("$k ${list.size}") })
                }
            }
        }
        groups.filter { type == null || it.first == type }.forEach { (k, list) ->
            stickyHeader(key = "h-$k") {
                Text(
                    listOfNotNull(k, desc[k], "${list.size} 处").joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = 16.dp, bottom = 8.dp),
                )
            }
            items(list, key = { "${k}-${it.entry.anchor}-${it.m.text.hashCode()}" }) { r ->
                MistakeCard(r.m, r.entry) { onOpen(r.entry.anchor) }
            }
        }
    }
}

@Composable
private fun MistakeCard(m: Mistake, entry: Entry, onClick: () -> Unit) {
    val sev = sevColor(m.sev)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(sev))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(if (m.sev == "error") "❌ " else "⚠️ ", color = sev, fontSize = 12.sp)
                // cross-references inside a mistake open the entry too, like a tap on the card
                Text(inline(m.text, MaterialTheme.colorScheme.primary) { onClick() }, style = MaterialTheme.typography.bodyMedium)
            }
            if (m.original.isNotEmpty()) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                    Text(
                        m.original,
                        fontFamily = FontFamily.Serif,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = muted,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "出自 ${entry.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(entry.date, style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
