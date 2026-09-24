package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.data.LibraryState

private const val CHANGED = "CHANGED"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    lib: LibraryState,
    checking: Boolean,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onMarkAllSeen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val entries = lib.data?.entries.orEmpty()
    val changed = lib.newAnchors + lib.updatedAnchors
    if (filter == CHANGED && changed.isEmpty()) filter = null

    val groups = remember(entries, query, filter, changed) {
        val q = query.trim().lowercase()
        val shown = entries.filter { e ->
            (q.isEmpty() || q in e.searchText) && when (filter) {
                null -> true
                CHANGED -> e.anchor in changed
                else -> e.level == filter
            }
        }.groupBy { it.level }
        GROUP_ORDER.mapNotNull { lvl -> shown[lvl]?.let { lvl to it } }
    }

    if (lib.data == null && lib.loadError == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    PullToRefreshBox(isRefreshing = checking, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索单词、释义、例句…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "清空") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            item {
                val present = entries.map { it.level }.toSet()
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    item { FilterChip(filter == null, { filter = null }, { Text("全部 ${entries.size}") }) }
                    if (changed.isNotEmpty()) item {
                        FilterChip(filter == CHANGED, { filter = if (filter == CHANGED) null else CHANGED }, { Text("有更新 ${changed.size}") })
                    }
                    items(GROUP_ORDER.filter { it in present }) { lvl ->
                        FilterChip(filter == lvl, { filter = if (filter == lvl) null else lvl }, { Text(levelStyle(lvl).short) })
                    }
                }
            }
            item { SyncStatus(lib, changed.size, onMarkAllSeen) }
            if (lib.loadError != null) item { Text(lib.loadError, color = MaterialTheme.colorScheme.error) }
            if (groups.isEmpty() && lib.data != null) item {
                Text("没有匹配的条目", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 32.dp))
            }
            groups.forEach { (lvl, list) ->
                stickyHeader(key = "h-$lvl") {
                    val s = levelStyle(lvl)
                    Text(
                        "${s.short} · ${s.name} · ${list.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                items(list, key = { it.anchor }) { e ->
                    EntryCard(
                        e,
                        badge = when (e.anchor) {
                            in lib.newAnchors -> "新"
                            in lib.updatedAnchors -> "已更新"
                            else -> null
                        },
                        onClick = { onOpen(e.anchor) },
                    )
                    Spacer(Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SyncStatus(lib: LibraryState, changed: Int, onMarkAllSeen: () -> Unit) {
    val data = lib.data ?: return
    val checked = lib.lastChecked?.let { "上次检查 ${ago(it)}" } ?: "尚未联网检查(使用内置词库)"
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "$checked · 词库生成于 ${data.generated.take(10)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (changed > 0) TextButton(onClick = onMarkAllSeen) { Text("全部标为已读") }
    }
}

private fun ago(t: Long): String {
    val min = (System.currentTimeMillis() - t) / 60_000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "$min 分钟前"
        min < 24 * 60 -> "${min / 60} 小时前"
        else -> "${min / (24 * 60)} 天前"
    }
}

@Composable
private fun EntryCard(e: Entry, badge: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(levelColor(e.level), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            val sub = listOf(e.ipa, e.pos).filter { it.isNotEmpty() }.joinToString("  ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
            if (e.gloss.isNotEmpty()) {
                Text(plain(e.gloss), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (e.date.isNotEmpty()) {
                Text(
                    e.date,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
