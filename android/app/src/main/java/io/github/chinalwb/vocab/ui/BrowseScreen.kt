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

private const val SORT_LEVEL = "LEVEL"
private const val SORT_NEW = "NEW"
private const val SORT_OLD = "OLD"
private val SORTS = listOf(SORT_LEVEL to "按级别", SORT_NEW to "最新收录", SORT_OLD to "最早收录")

private fun levelHeader(level: String) = levelStyle(level).let { "${it.short} · ${it.name}" }

/** "2026-10-09" (a leading ~ marks a guessed date); undated entries sort as oldest. */
private fun dateKey(e: Entry) = e.date.removePrefix("~").trim()

private fun monthHeader(key: String): String =
    Regex("^(\\d{4})-(\\d{2})").find(key)?.destructured?.let { (y, m) -> "$y 年 ${m.toInt()} 月" } ?: "没有日期"

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
internal fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, onSearch: () -> Unit = {}) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
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
internal val FilterIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
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

/** "试 N · 对 M" for 自测题 and anything I've tried in 自测, null for everything else. */
internal fun Map<String, List<Attempt>>.stFoot(e: Entry): String? =
    if (e.level == "SELFTEST" || !this[e.anchor].isNullOrEmpty()) countsShort(this[e.anchor].orEmpty()) else null

/** The card's 进度 note: nothing for where an entry starts, like the page's .stg. */
internal fun stageFoot(e: Entry, s: Stage): String? = when {
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
    /** The 筛选与排序 sheet; the 筛选 icon lives in the top bar now. */
    sheetOpen: Boolean = false,
    onSheetOpen: (Boolean) -> Unit = {},
    /** How many filters / a non-default sort are on, for the icon's dot. */
    onActiveCount: (Int) -> Unit = {},
) {
    var stageF by rememberSaveable { mutableStateOf<Stage?>(null) }
    val foot = { e: Entry -> listOfNotNull(selfTests.stFoot(e), stageFoot(e, stages.stageOf(e))).joinToString(" · ").ifEmpty { null } }
    // 排序: by level (grouped, the default) or by 收录 date, newest / oldest first, grouped by month
    var sortF by rememberSaveable { mutableStateOf(SORT_LEVEL) }
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

    // what the toggles leave; the chip rows and the list narrow it further (search has its own page)
    val base = remember(entries, changed, changedOnly, writeOnly) {
        entries.filter { e -> (!writeOnly || e.writes) && (!changedOnly || e.anchor in changed) }
    }
    // (header label, entries): level groups, or month groups when sorted by date
    val groups = remember(base, typeF, cefrF, stageF, stages, sortF) {
        val shown = base.filter { e -> (stageF == null || stages.stageOf(e) == stageF) && levelShown(e.level) }
        if (sortF == SORT_LEVEL) shown.groupBy { it.level }.let { g -> GROUP_ORDER.mapNotNull { lvl -> g[lvl]?.let { levelHeader(lvl) to it } } }
        else {
            val newest = compareByDescending<Entry> { dateKey(it) }.thenByDescending { it.no }
            shown.sortedWith(if (sortF == SORT_NEW) newest else newest.reversed())
                .groupBy { monthHeader(dateKey(it)) }.toList()
        }
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
    val shownCount = groups.sumOf { it.second.size }
    val clearAll = { typeF = ALL; cefrF = null; stageF = null; writeOnly = false; changedOnly = false; sortF = SORT_LEVEL }
    // what's on, as removable chips under the search; the same list drives the button's badge
    val active = buildList {
        if (typeF != ALL) add((TYPES.first { it.first == typeF }.second + (cefrF?.let { " · $it" } ?: "")) to { typeF = ALL; cefrF = null })
        stageF?.let { add(it.label to { stageF = null }) }
        if (writeOnly) add("只看会写" to { writeOnly = false })
        if (changedOnly) add("有更新" to { changedOnly = false })
        if (sortF != SORT_LEVEL) add(SORTS.first { it.first == sortF }.second to { sortF = SORT_LEVEL })
    }
    androidx.compose.runtime.LaunchedEffect(active.size) { onActiveCount(active.size) }
    val filters: @Composable () -> Unit = {
        if (active.isNotEmpty()) FlowRow(
            Modifier.fillMaxWidth().padding(top = 4.dp),
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
            onDismissRequest = { onSheetOpen(false) },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            // the sheet lives in its own window, out of reach of the haze, so no blur: nearly opaque
            // with a glass rim (at 90% the cards' text showed through and fought with the chips)
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
            scrimColor = Color.Black.copy(alpha = 0.25f),
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, glassRim(), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        ) {
            val present = entries.map { it.level }.toSet()
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("筛选与排序", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (active.isNotEmpty()) TextButton(onClick = clearAll) { Text("重置") }
                }
                FilterSection("排序") {
                    SORTS.forEach { (k, label) -> FilterChip(sortF == k, { sortF = k }, { Text(label) }) }
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
                androidx.compose.material3.Button(onClick = { onSheetOpen(false) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
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
        start = 8.dp,
        end = 8.dp,
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
                item(span = StaggeredGridItemSpan.FullLine) { filters() }
                item(span = StaggeredGridItemSpan.FullLine) { status() }
                gridItems(groups.flatMap { it.second }, key = { it.anchor }) { e ->
                    EntryTile(e, badgeOf(e), foot(e)) { onOpen(e.anchor) }
                }
            }
        } else {
            LazyColumn(state = listState, contentPadding = padding) {
                item { filters() }
                item { status() }
                groups.forEach { (lvl, list) ->
                    stickyHeader(key = "h-$lvl") {
                        GroupHeader(
                            "$lvl · ${list.size}",
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
private fun GroupHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
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
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/**
 * A note on the wall, after Google Keep: plain sans-serif text in a soft hierarchy (title,
 * then IPA and gloss a step lighter), roomy padding, large corners, and the facts that used
 * to be a "#49 · A1 · 自测中" line as small label pills at the bottom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryTile(e: Entry, badge: String?, foot: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .sharedEntryContainer(e.anchor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(levelColor(e.level))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val (head, note) = splitTitle(e.title)
        val ink = LocalContentColor.current
        Text(
            head,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedEntryTitle(e.anchor),
        )
        if (note != null) Text(note, fontSize = 13.sp, lineHeight = 18.sp, color = ink.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (e.ipa.isNotEmpty()) Text(e.ipa, fontSize = 14.sp, color = ink.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (e.gloss.isNotEmpty()) Text(
            cardGloss(e.gloss), fontSize = 14.sp, lineHeight = 20.sp, color = ink.copy(alpha = 0.8f),
            maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
        )
        Labels(e, badge, foot, Modifier.padding(top = 6.dp))
    }
}

/** The gloss as a card shows it: a leading "释义:" says nothing on a card. */
private fun cardGloss(g: String) = plain(g).replaceFirst(Regex("^释义\\s*[::]\\s*"), "")

/** The bottom pills: 新 / 已更新 first (filled), then the level, then 自测中 / 已掌握 / 试 N · 对 M. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Labels(e: Entry, badge: String?, foot: String?, modifier: Modifier = Modifier) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val pill = if (dark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.6f)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (badge != null) Badge(badge)
        (listOf(levelStyle(e.level).short) + foot.orEmpty().split(" · ").filter { it.isNotBlank() }).forEach {
            Text(it, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.8f),
                modifier = Modifier.background(pill, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun SyncStatus(lib: LibraryState, changed: Int, onMarkAllSeen: () -> Unit) {
    lib.data ?: return
    // pull-to-refresh already says "checked just now"; only speak up when there is news
    val text = when {
        // news shows as 新 / 已更新 pills on the cards and 全部标为已读 in the ⋮ menu
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
internal fun EntryCard(e: Entry, badge: String?, foot: String?, shared: Boolean = true, onClick: () -> Unit) {
    // the list's wider version of the wall's note: same type, same pills, the date at the end
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (shared) Modifier.sharedEntryContainer(e.anchor, shape) else Modifier)
            .clip(shape)
            .background(levelColor(e.level))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val (head, note) = splitTitle(e.title)
        val ink = LocalContentColor.current
        Text(
            head,
            fontSize = 19.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = if (shared) Modifier.sharedEntryTitle(e.anchor) else Modifier,
        )
        if (note != null) Text(note, fontSize = 13.sp, color = ink.copy(alpha = 0.6f))
        val sub = listOf(e.ipa, e.pos).filter { it.isNotEmpty() }.joinToString("  ")
        if (sub.isNotEmpty()) Text(sub, fontSize = 14.sp, color = ink.copy(alpha = 0.7f))
        if (e.gloss.isNotEmpty()) Text(cardGloss(e.gloss), fontSize = 15.sp, lineHeight = 21.sp, color = ink.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Labels(e, badge, listOfNotNull(foot, if (e.writes) "会写" else null).joinToString(" · ").ifEmpty { null }, Modifier.weight(1f))
            if (e.date.isNotEmpty()) Text(e.date, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.55f))
        }
    }
}
