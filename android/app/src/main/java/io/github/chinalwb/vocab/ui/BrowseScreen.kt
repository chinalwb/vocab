package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.stageOf

private const val ALL = "ALL"
private const val WORD = "WORD"
private val CEFR = listOf("A1", "A2", "B1", "B2", "C1", "C2")
private val TYPES = listOf(ALL to "全部", WORD to "单词", "TERM" to "术语", "GRAMMAR" to "语法", "SENTENCE" to "句子", "SELFTEST" to "自测题")

/**
 * "name(动词,"指定"义)" → "name" + "动词,"指定"义": the headword stays big, the trailing
 * parenthetical becomes a small note. Titles without one come back whole.
 */
private fun splitTitle(t: String): Pair<String, String?> {
    val m = Regex("""^(.+?)\s*[((](.+)[))]$""").find(t.trim()) ?: return t to null
    return m.groupValues[1] to m.groupValues[2]
}

/** 48dp filled search box; Material's text fields have a 56dp minimum. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
        modifier = modifier.height(48.dp),
        decorationBox = { field ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxSize().glassFlat(RoundedCornerShape(50)).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = muted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (value.isEmpty()) Text("搜索单词、释义,或编号 #12…", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge)
                    field()
                }
                if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, "清空", tint = muted) }
            }
        },
    )
}

/** A titled group of chips in the 筛选 sheet; the chips wrap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** Three narrowing lines, the page's 筛选 icon (icons-core has no FilterList). */
private val FilterIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("filter", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            androidx.compose.ui.graphics.vector.PathParser().parsePathString("M4 6h16M7 12h10M10 18h4").toNodes(),
            stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }.build()
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
}

@Composable
private fun CheckToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    // the whole row is the touch target, so the box itself can line up with the chips above
    androidx.compose.foundation.layout.Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle).heightIn(min = 40.dp).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
        ) { androidx.compose.material3.Checkbox(checked, null) }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** "试 N · 对 M" for 自测 entries, null for everything else. */
private fun Map<String, List<Attempt>>.stFoot(e: Entry): String? =
    if (e.level == "SELFTEST") countsShort(this[e.anchor].orEmpty()) else null

/** The card's 进度 note: nothing for where an entry starts, like the page's .stg. */
private fun stageFoot(e: Entry, s: Stage): String? = when {
    s == Stage.Done -> "✓ 已掌握"
    s == Stage.Test && e.level != "SELFTEST" -> "自测中"
    else -> null
}

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
    /** 自测 attempt history, shown as "试 N · 对 M" on 自测 cards. */
    selfTests: Map<String, List<Attempt>> = emptyMap(),
    /** 进度 moves by anchor (StageStore). */
    stages: Map<String, String> = emptyMap(),
) {
    var stageF by rememberSaveable { mutableStateOf<Stage?>(null) }
    val foot = { e: Entry -> listOfNotNull(selfTests.stFoot(e), stageFoot(e, stages.stageOf(e))).joinToString(" · ").ifEmpty { null } }
    var query by rememberSaveable { mutableStateOf("") }
    // 筛选, same as the page: 类型 (ALL / WORD / TERM …, with a CEFR sub-row under WORD) and
    // 进度, each single-choice; 只看会写 and 有更新 are toggles that narrow the rest.
    var typeF by rememberSaveable { mutableStateOf(ALL) }
    var cefrF by rememberSaveable { mutableStateOf<String?>(null) }
    var writeOnly by rememberSaveable { mutableStateOf(false) }
    var changedOnly by rememberSaveable { mutableStateOf(false) }
    val entries = lib.data?.entries.orEmpty()
    val changed = lib.newAnchors + lib.updatedAnchors
    if (changedOnly && changed.isEmpty()) changedOnly = false
    val levelShown = { lvl: String ->
        typeF == ALL || typeF == lvl || (typeF == WORD && lvl in CEFR && (cefrF == null || cefrF == lvl))
    }

    // what the search and the toggles leave; the chip rows and the list narrow it further
    val base = remember(entries, query, changed, changedOnly, writeOnly) {
        val q = query.trim().lowercase()
        // "12" or "#12" jumps to entry #12, like the page's search
        val numQ = Regex("^#?(\\d+)$").find(q)?.groupValues?.get(1)?.toInt()
        entries.filter { e ->
            (if (numQ != null) e.no == numQ else q.isEmpty() || q in e.searchText) && (!writeOnly || e.writes) &&
                (!changedOnly || e.anchor in changed)
        }
    }
    val groups = remember(base, typeF, cefrF, stageF, stages) {
        val shown = base.filter { e -> (stageF == null || stages.stageOf(e) == stageF) && levelShown(e.level) }.groupBy { it.level }
        GROUP_ORDER.mapNotNull { lvl -> shown[lvl]?.let { lvl to it } }
    }
    // chip counts: each row counts what its chips would show with every other filter kept, like the page
    val levelCounts = remember(base, stageF, stages) {
        base.filter { stageF == null || stages.stageOf(it) == stageF }.groupingBy { it.level }.eachCount()
    }
    val stageCounts = remember(base, typeF, cefrF, stages) {
        base.filter { levelShown(it.level) }.groupingBy { stages.stageOf(it) }.eachCount()
    }
    val typeCount = { k: String ->
        when (k) {
            ALL -> levelCounts.values.sum()
            WORD -> CEFR.sumOf { levelCounts[it] ?: 0 }
            else -> levelCounts[k] ?: 0
        }
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
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val shownCount = groups.sumOf { it.second.size }
    val clearAll = { typeF = ALL; cefrF = null; stageF = null; writeOnly = false; changedOnly = false }
    // what's on, as removable chips under the search; the same list drives the button's badge
    val active = buildList {
        if (typeF != ALL) add((TYPES.first { it.first == typeF }.second + (cefrF?.let { " · $it" } ?: "")) to { typeF = ALL; cefrF = null })
        stageF?.let { add(it.label to { stageF = null }) }
        if (writeOnly) add("只看会写" to { writeOnly = false })
        if (changedOnly) add("有更新" to { changedOnly = false })
    }
    val search: @Composable () -> Unit = {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(query, { query = it }, Modifier.weight(1f))
            androidx.compose.foundation.layout.Row(
                Modifier
                    .height(48.dp)
                    .glassFlat(RoundedCornerShape(50))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { sheetOpen = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(FilterIcon, null, Modifier.size(18.dp))
                Text("筛选", Modifier.padding(start = 6.dp))
                if (active.isNotEmpty()) androidx.compose.material3.Badge(Modifier.padding(start = 6.dp), containerColor = MaterialTheme.colorScheme.primary) { Text("${active.size}") }
            }
        }
    }
    val filters: @Composable () -> Unit = {
        if (active.isNotEmpty()) FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            active.forEach { (label, off) ->
                androidx.compose.material3.InputChip(
                    selected = false, onClick = off, label = { Text(label) },
                    trailingIcon = { Icon(Icons.Default.Clear, "去掉筛选:$label", Modifier.size(16.dp)) },
                )
            }
            Text("共 $shownCount 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End))
        }
        if (sheetOpen) androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            // the sheet lives in its own window, out of reach of the haze; translucent glass with a rim instead
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
            scrimColor = Color.Black.copy(alpha = 0.18f),
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, glassRim(), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        ) {
            val present = entries.map { it.level }.toSet()
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("筛选", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (active.isNotEmpty()) TextButton(onClick = clearAll) { Text("重置") }
                }
                FilterSection("类型") {
                    TYPES.filter { (k, _) -> k == ALL || k == WORD && CEFR.any { it in present } || k in present }.forEach { (k, label) ->
                        FilterChip(typeF == k, { typeF = k; if (k != WORD) cefrF = null }, { Text("$label ${typeCount(k)}") },
                            leadingIcon = if (k in LEVELS) ({ Dot(levelColor(k)) }) else null)
                    }
                }
                if (typeF == WORD) FilterSection("级别") {
                    FilterChip(cefrF == null, { cefrF = null }, { Text("全部 ${typeCount(WORD)}") })
                    CEFR.filter { it in present }.forEach { c ->
                        FilterChip(cefrF == c, { cefrF = c }, { Text("$c ${levelCounts[c] ?: 0}") }, leadingIcon = { Dot(levelColor(c)) })
                    }
                }
                FilterSection("进度") {
                    FilterChip(stageF == null, { stageF = null }, { Text("全部 ${stageCounts.values.sum()}") })
                    Stage.entries.forEach { s ->
                        FilterChip(stageF == s, { stageF = s }, { Text("${s.label} ${stageCounts[s] ?: 0}") })
                    }
                }
                // toggles narrow everything else, so they are checkboxes rather than another chip
                FilterSection("选项") {
                    CheckToggle("只看会写", writeOnly) { writeOnly = !writeOnly }
                    if (changed.isNotEmpty()) CheckToggle("有更新 ${changed.size}", changedOnly) { changedOnly = !changedOnly }
                }
                androidx.compose.material3.Button(onClick = { sheetOpen = false }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (shownCount > 0) "显示 $shownCount 条" else "没有匹配的条目")
                }
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
        start = 10.dp,
        end = 10.dp,
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
                    EntryTile(e, badgeOf(e), foot(e)) { onOpen(e.anchor) }
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
                        EntryCard(e, badgeOf(e), foot(e)) { onOpen(e.anchor) }
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
private fun EntryTile(e: Entry, badge: String?, foot: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .sharedEntryContainer(e.anchor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(levelColor(e.level))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (badge != null) Badge(badge)
        val (head, note) = splitTitle(e.title)
        Text(
            head,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedEntryTitle(e.anchor),
        )
        if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.6f),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (e.ipa.isNotEmpty()) {
            Text(e.ipa, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (e.gloss.isNotEmpty()) {
            Text(plain(e.gloss), style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        Text(
            listOfNotNull(e.no.takeIf { it > 0 }?.let { "#$it" }, levelStyle(e.level).short, foot).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SyncStatus(lib: LibraryState, changed: Int, onMarkAllSeen: () -> Unit) {
    lib.data ?: return
    // pull-to-refresh already says "checked just now"; only speak up when there is news
    val text = when {
        changed > 0 -> "有 $changed 条新增或更新"
        lib.lastChecked == null -> "尚未联网检查(使用内置词库)"
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text,
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
private fun EntryCard(e: Entry, badge: String?, foot: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .sharedEntryContainer(e.anchor, RoundedCornerShape(4.dp))
            .background(levelColor(e.level), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val (head, note) = splitTitle(e.title)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    head,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).sharedEntryTitle(e.anchor),
                )
                if (badge != null) Badge(badge)
            }
            if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.6f))
            val sub = listOf(e.ipa, e.pos).filter { it.isNotEmpty() }.joinToString("  ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
            if (e.gloss.isNotEmpty()) {
                Text(plain(e.gloss), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (e.date.isNotEmpty()) {
                Text(
                    listOfNotNull(e.no.takeIf { it > 0 }?.let { "#$it" }, foot, if (e.writes) "会写" else null, e.date).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
