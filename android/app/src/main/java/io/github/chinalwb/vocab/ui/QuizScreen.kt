package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.data.LibraryState
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.ReviewData
import io.github.chinalwb.vocab.review.PROMOTE_REPS
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.next
import io.github.chinalwb.vocab.review.planToday
import io.github.chinalwb.vocab.review.stageOf
import io.github.chinalwb.vocab.review.today

/** The entries in [stage], newest 收录 first — what its tab lists. */
fun stageEntries(stage: Stage, entries: List<Entry>, stages: Map<String, String>): List<Entry> =
    entries.filter { stages.stageOf(it) == stage }
        .sortedWith(compareByDescending<Entry> { it.date.removePrefix("~") }.thenByDescending { it.no })

/**
 * What 开始复习 / 开始考核 runs through. 复习: what's due today, then what was never scheduled
 * (planToday). 考核: never tried first, then the ones I got wrong or peeked at last time, then
 * the oldest attempt — same as stQueue() in template.html.
 */
fun quizQueue(stage: Stage, entries: List<Entry>, stages: Map<String, String>, log: Map<String, List<Attempt>>, review: ReviewData): List<Entry> {
    val mine = entries.filter { stages.stageOf(it) == stage }
    return when (stage) {
        Stage.Review -> planToday(mine, review).all
        else -> mine.sortedWith(
            compareBy<Entry>({ e -> testGroup(log[e.anchor]?.lastOrNull()) }, { e -> log[e.anchor]?.lastOrNull()?.t ?: e.no.toLong() }),
        )
    }
}

/** 考核's three groups by the last attempt: 0 没做过, 1 上次没对 (wrong or peeked), 2 上次答对. */
private fun testGroup(last: Attempt?): Int = when { last == null -> 0; last.text.isEmpty() || !last.ok -> 1; else -> 2 }

/**
 * One stage's tab: its entries under a header that starts the run (复习, 考核) or just counts
 * them (掌握). While a run is on, it takes the whole screen — the app's bars hide (MainActivity).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizScreen(
    stage: Stage,
    lib: LibraryState,
    session: QuizSession?,
    stages: Map<String, String>,
    stageTimes: Map<String, Long>,
    selfTests: Map<String, List<Attempt>>,
    review: ReviewData,
    excluded: Map<String, Set<String>>,
    tiles: Boolean,
    /** an entry tapped, with the list's anchors (in order) for 下一个; empty from a quiz card */
    onOpen: (String, List<String>) -> Unit,
    onStart: () -> Unit,
    onStudied: () -> Unit,
    onReveal: (String, Boolean) -> Unit,
    onGrade: (String, Boolean) -> Unit,
    /** within an entry: ① → ②, or (true) on to its next sentence */
    onOnward: (Boolean) -> Unit,
    onNext: (advance: Boolean) -> Unit,
    onEnd: () -> Unit,
    /** 撤销 on the last page: put back what the run moved by itself */
    onUndo: () -> Unit,
    /** 算我对: this sentence was right after all */
    onRight: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val current = session?.current
    when {
        session != null && session.stage == stage && current != null -> QuizCard(
            session, current, selfTests[current.anchor].orEmpty(), stageTimes[current.anchor] ?: 0L, excluded[current.anchor].orEmpty(),
            review.cards[current.anchor]?.reps ?: 0, onStudied, onReveal, onGrade, onOnward, onNext, onEnd, { onOpen(it, emptyList()) }, onRight, modifier,
        )
        session != null && session.stage == stage -> Column(
            modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("完成 🎉", style = MaterialTheme.typography.headlineSmall)
            Text(
                "这一轮做了 ${session.done} 条" +
                    (if (session.checked > 0) ",答对 ${session.ok} / ${session.checked} 句" else "") +
                    (if (session.advanced > 0) ",${session.advanced} 条进入了${session.stage.next?.tab ?: ""}" else "") + "。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // what the run moved by itself, by name, with one 撤销 for all of it
            val titles = { list: List<String> -> list.mapNotNull { lib.data?.byAnchor?.get(it)?.title }.joinToString("、") }
            if (session.promoted.isNotEmpty()) Text("自动移到考核(复习里连续答对 $PROMOTE_REPS 次):${titles(session.promoted)}")
            if (session.demoted.isNotEmpty()) Text("答错了,退回复习(排期从明天重新开始):${titles(session.demoted)}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onEnd) { Text("返回") }
                if (session.promoted.isNotEmpty() || session.demoted.isNotEmpty()) OutlinedButton(onClick = onUndo) { Text("撤销移动") }
            }
        }
        else -> StageList(stage, lib, stages, stageTimes, selfTests, review, excluded, tiles, onOpen, onStart, modifier, contentPadding)
    }
}

/** One group of a stage's list: a heading and its entries, each with the line under its title. */
private class Group(val title: String, val entries: List<Entry>, val sub: (Entry) -> String)

/**
 * A stage's tab (V2): one summary card — the big number, a few counts, the start button — then
 * the entries grouped by why they're there, one line each, so the first screen shows six or
 * seven of them. 掌握 keeps the plain cards (or tiles) under its counts.
 */
@Composable
private fun StageList(
    stage: Stage,
    lib: LibraryState,
    stages: Map<String, String>,
    stageTimes: Map<String, Long>,
    selfTests: Map<String, List<Attempt>>,
    review: ReviewData,
    excluded: Map<String, Set<String>>,
    tiles: Boolean,
    onOpen: (String, List<String>) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val data = lib.data
    if (data == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val mine = remember(data, stages, stage) { stageEntries(stage, data.entries, stages) }
    val queue = remember(mine, selfTests, review, stage) { quizQueue(stage, mine, stages, selfTests, review) }
    val today = today()
    val padding = PaddingValues(
        start = 8.dp, end = 8.dp,
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 24.dp,
    )
    if (stage == Stage.Done) {
        DoneList(mine, data.entries.size, selfTests, tiles, onOpen, padding, modifier)
        return
    }
    // the ones whose every question has been answered right since they came in: offered on
    val ready = remember(mine, selfTests, stageTimes, excluded, stage, review) {
        mine.filter { advanceReady(it, stage, selfTests[it.anchor].orEmpty(), stageTimes[it.anchor] ?: 0L, excluded[it.anchor].orEmpty(), review.cards[it.anchor]?.reps ?: 0) }
    }
    val counts = { e: Entry -> selfTests[e.anchor]?.takeIf { it.isNotEmpty() }?.let(::countsShort) }
    val gloss = { e: Entry -> if (e.level == "SELFTEST") "先说出英文,再点开对答案" else cardGloss(e.gloss) }
    val groups = remember(mine, selfTests, review, stage) {
        if (stage == Stage.Review) {
            val plan = planToday(mine, review)
            val later = (mine - plan.due.toSet() - plan.fresh.toSet()).sortedBy { review.cards[it.anchor]?.due ?: Long.MAX_VALUE }
            val dueLine = { e: Entry ->
                review.cards[e.anchor]?.due?.let { d -> when { d <= today -> "今天到期"; d == today + 1 -> "明天到期"; else -> "${d - today} 天后到期" } }
            }
            listOf(
                Group("今天到期", plan.due) { e -> listOfNotNull(counts(e), dueLine(e)).joinToString(" · ").ifEmpty { gloss(e) } },
                Group("新加入", plan.fresh) { e -> counts(e) ?: gloss(e) },
                Group("以后到期", later) { e -> listOfNotNull(counts(e), dueLine(e)).joinToString(" · ").ifEmpty { gloss(e) } },
            )
        } else {
            val by = queue.groupBy { testGroup(selfTests[it.anchor]?.lastOrNull()) }
            listOf(
                Group("没做过", by[0].orEmpty()) { e -> gloss(e) },
                Group("上次没对", by[1].orEmpty()) { e -> counts(e) ?: gloss(e) },
                Group("上次答对", by[2].orEmpty()) { e -> counts(e) ?: gloss(e) },
            )
        }.filter { it.entries.isNotEmpty() }
    }
    val order = groups.flatMap { it.entries }.map { it.anchor }
    LazyColumn(contentPadding = padding, modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            if (stage == Stage.Review) {
                val plan = planToday(mine, review)
                Summary(
                    big = "${queue.size}",
                    caption = if (mine.isEmpty()) "条 · 还没有复习中的条目" else if (queue.isEmpty()) "条 · 今天的都复习完了" else "条 · 今天要复习",
                    chips = listOf("今天到期 ${plan.due.size}", "新加入 ${plan.fresh.size}"),
                    bar = null,
                    button = "开始复习 ${queue.size} 条",
                    enabled = queue.isNotEmpty(),
                    note = "先看一遍笔记,再翻面做例句。答对了下次隔得更久,答错了明天再来;连续答对 3 次(隔开几天)、例句也都答对过,就自动移到考核。",
                    onStart = onStart,
                )
            } else {
                val n = groups.associate { it.title to it.entries.size }
                Summary(
                    big = "${mine.size}",
                    caption = "条考核中",
                    chips = emptyList(),
                    bar = listOf(n["没做过"] ?: 0, n["上次没对"] ?: 0, n["上次答对"] ?: 0),
                    button = "开始考核 ${queue.size} 题",
                    enabled = queue.isNotEmpty(),
                    note = null,
                    onStart = onStart,
                )
            }
        }
        if (ready.isNotEmpty()) item {
            ReadyBanner(ready.size, stage) { onOpen(ready.first().anchor, ready.map { it.anchor }) }
        }
        groups.forEach { g ->
            item(key = "h:" + g.title) {
                Text(
                    "${g.title} · ${g.entries.size}",
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                )
            }
            items(g.entries, key = { g.title + ":" + it.anchor }) { e ->
                StageRow(e, g.sub(e)) { onOpen(e.anchor, order) }
            }
        }
    }
}

/**
 * The tab's summary card: inverted in light mode, a raised grey in dark. [bar] (考核): the three
 * groups as one segmented bar with a legend — 没做过 / 上次没对 / 上次答对.
 */
@Composable
private fun Summary(
    big: String,
    caption: String,
    chips: List<String>,
    bar: List<Int>?,
    button: String,
    enabled: Boolean,
    note: String?,
    onStart: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val bg = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.inverseSurface
    val fg = if (dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.inverseOnSurface
    val missed = Color(0xFFF2A65A)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(big, color = fg, fontSize = 52.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold)
            Text(caption, color = fg, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (chips.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach {
                Text(it, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        if (bar != null && bar.sum() > 0) {
            val colors = listOf(fg.copy(alpha = 0.22f), missed, fg)
            Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                bar.forEachIndexed { i, n -> if (n > 0) Box(Modifier.weight(n.toFloat()).height(10.dp).background(colors[i], RoundedCornerShape(5.dp))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("没做过", "上次没对", "上次答对").forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(colors[i], CircleShape))
                        Text("$label ${bar[i]}", color = fg, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Button(
            onClick = onStart,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
        ) {
            Text(button, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
        }
        if (note != null) Text(note, color = fg.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
    }
}

/** "N 条的题都答对过了,可以移到掌握 ›": opens the first of them, 下一个 walks the rest. */
@Composable
private fun ReadyBanner(n: Int, stage: Stage, onClick: () -> Unit) {
    val next = stage.next ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.background)
        }
        Text("$n 条的题都答对过了,可以移到${next.tab}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
    }
}

/** One line of a stage's list: the title, the line under it, the level as a pill. */
@Composable
internal fun StageRow(e: Entry, sub: String, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(levelColor(e.level))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                e.title.removePrefix("自测:").removePrefix("自测:"),
                fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = ink.copy(alpha = 0.76f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            levelStyle(e.level).short,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 掌握: its counts, then the cards (or tiles), as before. */
@Composable
private fun DoneList(
    mine: List<Entry>,
    total: Int,
    selfTests: Map<String, List<Attempt>>,
    tiles: Boolean,
    onOpen: (String, List<String>) -> Unit,
    padding: PaddingValues,
    modifier: Modifier,
) {
    val foot = { e: Entry -> listOfNotNull(selfTests.stFoot(e), stageFoot(e, Stage.Done)).joinToString(LABEL_SEP).ifEmpty { null } }
    val header: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("已掌握", mine.size, Modifier.weight(1f))
                Stat("共", total, Modifier.weight(1f))
            }
            Text(
                "已掌握的条目不再进复习和考核。在条目里点进度条上的别的阶段,可以移回去。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(top = 4.dp))
        }
    }
    if (tiles) LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = modifier,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) { header() }
        gridItems(mine, key = { it.anchor }) { e -> EntryTile(e, null, foot(e)) { onOpen(e.anchor, mine.map { it.anchor }) } }
    } else LazyColumn(contentPadding = padding, modifier = modifier) {
        item { header() }
        items(mine, key = { it.anchor }) { e ->
            EntryCard(e, null, foot(e), shared = false) { onOpen(e.anchor, mine.map { it.anchor }) }
            Spacer(Modifier.padding(4.dp))
        }
    }
}

/**
 * One card of a run (V2): ✕, the progress bar and "3 / 32" on top, and the answer sheet under it.
 * 复习 shows the notes first, on a card in the entry's level colour (翻面做题), and turns it over
 * to the sheet; 考核 goes straight to the question. After the reveal: the verdict, the notes the
 * sentence comes from, and 下一题 — with the move to the next stage offered once it's earned.
 * An entry asks every one of its sentences in turn (用户 2026-10-15), each ① 选词 then ② 打字; the
 * moves come after its last one (revealQuiz() in VocabViewModel.kt, renderQuizCard() in template.html).
 */
@Composable
private fun QuizCard(
    session: QuizSession,
    entry: Entry,
    attempts: List<Attempt>,
    since: Long,
    excluded: Set<String>,
    reps: Int,
    onStudied: () -> Unit,
    onReveal: (String, Boolean) -> Unit,
    onGrade: (String, Boolean) -> Unit,
    onOnward: (Boolean) -> Unit,
    onNext: (Boolean) -> Unit,
    onEnd: () -> Unit,
    onXref: (String) -> Unit,
    onRight: () -> Unit,
    modifier: Modifier,
) {
    val exam = entry.level == "SELFTEST"
    val ex = session.example
    val studying = session.stage == Stage.Review && !session.studied
    val answer = if (exam) entry.selfTestAnswer.lines().firstOrNull { it.isNotBlank() } else ex?.en
    val step = if (answer != null) session.step else 2
    // per sentence and step: what's typed, the 选词 hint (② only), and what the reveal checked
    val key = "${session.index}/${session.k}/$step"
    var typed by rememberSaveable(key) { mutableStateOf("") }
    var help by rememberSaveable(key) { mutableStateOf(false) }
    var draft by rememberSaveable(key) { mutableStateOf("") }
    var fills by rememberSaveable(key) { mutableStateOf("") }
    val bank = if (answer != null && (step == 1 || help) && !session.revealed && !studying) {
        val seed = entry.anchor.hashCode() + session.index + 7 * session.k
        val others = remember(entry.anchor) { entry.quizExamples().map { plain(it.en) } + entry.selfTestAnswer.lines() }
            .filter { it.isNotBlank() && it != answer }
        rememberWordBank(answer, strayWords(answer, others, seed), seed)
    } else null
    val reveal = { draft = bank?.draft ?: typed; onReveal(draft, bank != null) }
    val final = session.revealed && step == 2 && session.lastQ
    val ready = final && advanceReady(entry, session.stage, attempts, since, excluded, reps)
    // 复习 → 考核 happens by itself on 下一题; 考核 sends a miss back to 复习 — say so, no button
    val auto = ready && session.stage == Stage.Review
    val back = final && session.stage == Stage.Test && session.missed && !ready
    val color = levelColor(entry.level)
    // the turn from the notes (front) to the question (back), 复习 only
    val angle by androidx.compose.animation.core.animateFloatAsState(
        if (studying) 0f else 180f, androidx.compose.animation.core.tween(550), label = "turn",
    )
    val front = session.stage == Stage.Review && angle <= 90f
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val stats = statsLine(attempts.at(ex), one = ex != null)
    Column(modifier.fillMaxSize()) {
        RunTopBar(
            onEnd,
            progress = (session.index + if (final) 1 else 0).toFloat() / session.queue.size,
            label = "${session.index + 1} / ${session.queue.size}",
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .graphicsLayer {
                    if (session.stage == Stage.Review) {
                        rotationY = angle
                        cameraDistance = 16f * density
                    }
                }
        ) {
            if (front) StudyCard(entry, color, onXref, onStudied)
            else Box(Modifier.fillMaxSize().graphicsLayer { if (session.stage == Stage.Review) rotationY = 180f }) {
                AnswerSheet(
                    color,
                    Modifier.fillMaxSize(),
                    card = {
                        SheetMetaRow(
                            (if (exam) "自测题" else entry.title) + (ex?.let { " · 例句 ${it.k + 1} / ${it.of}" } ?: "") + " · 中译英" +
                                (if (answer != null) stepLabel(step) else ""),
                            toggle = if (answer != null && !session.revealed) ({
                                AnswerModeToggle(step == 1 || help, { if (step == 2) help = it }, lockSecond = step == 1)
                            }) else null,
                        )
                        if (!session.revealed) {
                            val compact = if (bank != null) bank.picked.isNotEmpty() else typed.isNotBlank()
                            when {
                                exam -> QuestionText(entry.title.removePrefix("自测:").removePrefix("自测:"), compact)
                                ex != null -> QuestionText(ex.zh, compact)
                                else -> ReviewFront(entry, kindOf(entry, inTest = true))
                            }
                            Text(stats, style = MaterialTheme.typography.bodySmall, color = sheetColors().sub)
                            val blanks = if (step == 2 && !studying) blanksOf(ex?.raw) else null
                            when {
                                bank != null -> ChipsAnswerBox(bank)
                                blanks != null -> FillAnswerBox(blanks, fillsOf(fills)) { fills = it.joinToString(FILL_SEP.toString()); typed = composeBlanks(blanks, it) }
                                else -> TypingAnswerBox(typed) { typed = it }
                            }
                        } else {
                            val q = if (exam) entry.title.removePrefix("自测:").removePrefix("自测:") else ex?.zh
                            if (q != null) Text(q, style = MaterialTheme.typography.bodyLarge, color = sheetColors().sub)
                            val full = if (exam) entry.selfTestAnswer else ex?.en
                            if (full != null && step == 1) {   // ① checked, not logged
                                RevealVerdict(draft, full, STEP1_NOTE)
                                return@AnswerSheet
                            }
                            val right = session.bankOk && session.typedOk
                            if (full != null) StepsLine(if (session.righted) RIGHTED else stepsText(session.bankOk, session.typedOk, session.helped), session.righted || right)
                            when {
                                full != null -> RevealVerdict(draft, full, stats)
                                else -> MovedResult(draft, session.graded) { onGrade(draft, it) }
                            }
                            if (full != null && !session.righted && !right && draft.isNotBlank()) RightMeRow(onRight)
                            if (final && ex != null && ex.of > 1) StepsLine(
                                "这一条的 ${ex.of} 句都做完了:" + if (session.entryOk) "全部答对" else "有没答对的,整条记为没对", session.entryOk,
                            )
                            when {
                                auto -> AutoMoveNote("复习里连续答对 $PROMOTE_REPS 次了,下一题时自动移到考核")
                                back -> AutoMoveNote("没答对,下一题时退回复习,排期从明天重新开始")
                                ready -> AdvanceBanner(session.stage) { onNext(true) }
                            }
                            NotesForQuestion(entry, ex, onXref, onAll = { onXref(entry.anchor) })
                            SelfTestHistory(attempts.at(ex), one = ex != null)
                        }
                    },
                    panel = {
                        if (!session.revealed) {
                            if (bank != null) WordKeys(bank)
                            SheetButton("显示答案", onClick = reveal)
                        } else if (answer != null && step == 1) SheetButton("下一步:打字 ›") { onOnward(false) }
                        else if (!session.lastQ) SheetButton("下一句 › ${(ex?.k ?: 0) + 2} / ${ex?.of}") { onOnward(true) }
                        else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (ready && !auto) SheetButton("移到${session.stage.next?.tab}", Modifier.weight(1f), filled = false) { onNext(true) }
                            SheetButton(if (session.index + 1 < session.queue.size) "下一题 ›" else "完成", Modifier.weight(1f)) { onNext(false) }
                        }
                    },
                )
            }
        }
    }
}

/** What 下一题 will do to this entry by itself (no button: the run decides). */
@Composable
private fun AutoMoveNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** 复习's front: the notes on a card in the level colour, with 翻面做题 at its foot. */
@Composable
private fun StudyCard(entry: Entry, color: Color, onXref: (String) -> Unit, onStudied: () -> Unit) {
    val sub = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(color),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${levelStyle(entry.level).short} · 先看一遍", style = MaterialTheme.typography.labelLarge, color = sub)
            Text(
                entry.title, fontFamily = AppFont, fontWeight = FontWeight.Bold,
                fontSize = if (entry.title.length > 18) 24.sp else 32.sp, lineHeight = if (entry.title.length > 18) 30.sp else 38.sp,
            )
            if (entry.ipa.isNotEmpty()) Text(entry.ipa, style = MaterialTheme.typography.bodyMedium, color = sub)
        }
        NotesPage(entry, onXref, quiz = null, modifier = Modifier.weight(1f), inCard = true)
        Box(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
            SheetButton("翻面做题", onClick = onStudied)
        }
    }
}
