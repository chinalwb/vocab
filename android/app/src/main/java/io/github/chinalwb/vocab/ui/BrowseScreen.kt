package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as gridItems
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(
    lib: LibraryState,
    checking: Boolean,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onMarkAllSeen: () -> Unit,
    tiles: Boolean,
    /** The Scaffold's padding — applied inside the list so content can scroll under the bars. */
    contentPadding: PaddingValues,
    /** 1 = status bar showing, 0 = hidden; read at draw time to place pinned headers. */
    statusBarShown: () -> Float,
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
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val badgeOf = { e: Entry ->
        when (e.anchor) {
            in lib.newAnchors -> "新"
            in lib.updatedAnchors -> "已更新"
            else -> null
        }
    }
    val search: @Composable () -> Unit = {
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
    val filters: @Composable () -> Unit = {
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
    val status: @Composable () -> Unit = {
        Column {
            SyncStatus(lib, changed.size, onMarkAllSeen)
            if (lib.loadError != null) Text(lib.loadError, color = MaterialTheme.colorScheme.error)
            if (groups.isEmpty() && lib.data != null) {
                Text("没有匹配的条目", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 32.dp))
            }
        }
    }
    val padding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 24.dp,
    )
    val pull = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val statusBar = WindowInsets.statusBarsIgnoringVisibility.getTop(LocalDensity.current)

    PullToRefreshBox(
        isRefreshing = checking,
        onRefresh = onRefresh,
        state = pull,
        modifier = Modifier.fillMaxSize(),
        // The box now starts under the top bar, so drop the spinner below it.
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pull,
                isRefreshing = checking,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
            )
        },
    ) {
        if (tiles) {
            // Keep-style masonry: one continuous wall, no section headers — the tile
            // colour and its level label carry the grouping, so short groups leave no gaps.
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(160.dp),
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine) { search() }
                item(span = StaggeredGridItemSpan.FullLine) { filters() }
                item(span = StaggeredGridItemSpan.FullLine) { status() }
                gridItems(groups.flatMap { it.second }, key = { it.anchor }) { e ->
                    EntryTile(e, badgeOf(e)) { onOpen(e.anchor) }
                }
            }
        } else {
            LazyColumn(state = listState, contentPadding = padding) {
                item { search() }
                item { filters() }
                item { status() }
                groups.forEach { (lvl, list) ->
                    stickyHeader(key = "h-$lvl") {
                        GroupHeader(
                            lvl, list.size,
                            // The list runs under the status bar now; nudge a header that is
                            // pinned (or about to be) down so it never sits behind the clock.
                            Modifier.graphicsLayer {
                                val info = listState.layoutInfo
                                val item = info.visibleItemsInfo.firstOrNull { it.key == "h-$lvl" }
                                val y = (item?.offset ?: 0) - info.viewportStartOffset
                                translationY = (statusBar * statusBarShown() - y).coerceAtLeast(0f)
                            },
                        )
                    }
                    items(list, key = { it.anchor }) { e ->
                        EntryCard(e, badgeOf(e)) { onOpen(e.anchor) }
                        Spacer(Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(level: String, count: Int, modifier: Modifier = Modifier) {
    val s = levelStyle(level)
    Text(
        "${s.short} · ${s.name} · $count",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A compact note for the tile grid — the gloss is cut shorter than in the list. */
@Composable
private fun EntryTile(e: Entry, badge: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(levelColor(e.level))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (badge != null) Badge(badge)
        Text(
            e.title,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (e.ipa.isNotEmpty()) {
            Text(e.ipa, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (e.gloss.isNotEmpty()) {
            Text(plain(e.gloss), style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        Text(
            levelStyle(e.level).short,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(top = 4.dp)
                .border(1.dp, LocalContentColor.current.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
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
                if (badge != null) Badge(badge)
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
