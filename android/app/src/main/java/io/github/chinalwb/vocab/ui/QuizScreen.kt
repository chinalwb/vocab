package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.data.LibraryState
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.ReviewData
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
            compareBy<Entry>({ e ->
                val last = log[e.anchor]?.lastOrNull()
                when { last == null -> 0; last.text.isEmpty() || !last.ok -> 1; else -> 2 }
            }, { e -> log[e.anchor]?.lastOrNull()?.t ?: e.no.toLong() }),
        )
    }
}

/**
 * One stage's tab: its entries, listed like 浏览, under a header that starts the run (复习,
 * 考核) or just counts them (掌握). While a run is on, the card takes the tab.
 */
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
    onReveal: (String) -> Unit,
    onGrade: (String, Boolean) -> Unit,
    onNext: (advance: Boolean) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val current = session?.current
    when {
        session != null && session.stage == stage && current != null -> QuizCard(
            session, current, selfTests[current.anchor].orEmpty(), stageTimes[current.anchor] ?: 0L, excluded[current.anchor].orEmpty(),
            onStudied, onReveal, onGrade, onNext, onEnd, { onOpen(it, emptyList()) }, modifier,
        )
        session != null && session.stage == stage -> Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("完成 🎉", style = MaterialTheme.typography.headlineSmall)
            Text(
                "这一轮做了 ${session.done} 题" +
                    (if (session.checked > 0) ",答对 ${session.ok} / ${session.checked}" else "") +
                    (if (session.advanced > 0) ",${session.advanced} 条进入了${session.stage.next?.tab ?: ""}" else "") + "。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onEnd) { Text("返回") }
        }
        else -> StageList(stage, lib, stages, selfTests, review, tiles, onOpen, onStart, modifier, contentPadding)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StageList(
    stage: Stage,
    lib: LibraryState,
    stages: Map<String, String>,
    selfTests: Map<String, List<Attempt>>,
    review: ReviewData,
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
    // the card's pills, the same as 浏览's: "试 N · 对 M", the stage (so a 自测题's type pill
    // isn't read as its stage), and for 复习 when it's next due
    val foot = { e: Entry ->
        val due = if (stage == Stage.Review) review.cards[e.anchor]?.due?.let { d ->
            when { d <= today -> "今天到期"; d == today + 1 -> "明天到期"; else -> "${d - today} 天后" }
        } else null
        listOfNotNull(selfTests.stFoot(e), stageFoot(e, stage), due).joinToString(LABEL_SEP).ifEmpty { null }
    }
    val header: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (stage) {
                Stage.Review -> {
                    val plan = planToday(mine, review)
                    // the ring: how much of 复习 is done for today (not due again yet)
                    RunHead(
                        big = queue.size, caption = if (queue.isEmpty()) "今天的都复习完了" else "今天要复习",
                        fill = if (mine.isEmpty()) 0f else 1f - queue.size.toFloat() / mine.size,
                        counts = listOf("今天到期" to plan.due.size, "还没排期" to plan.fresh.size),
                        next = queue.take(3),
                    )
                    Button(onClick = onStart, enabled = queue.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (mine.isEmpty()) "还没有复习中的条目" else if (queue.isEmpty()) "今天的都复习完了" else "开始复习 ${queue.size} 条", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "先看一遍笔记,再翻面做例句。答对了下次隔得更久,答错了明天再来;例句都答对过,就会提示移到考核。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Stage.Test -> {
                    val never = mine.count { selfTests[it.anchor].isNullOrEmpty() }
                    val missed = mine.count { e -> selfTests[e.anchor]?.lastOrNull()?.let { it.text.isEmpty() || !it.ok } == true }
                    val right = mine.count { e -> selfTests[e.anchor]?.lastOrNull()?.let { it.text.isNotEmpty() && it.ok } == true }
                    // the ring: how many were answered right the last time they came up
                    RunHead(
                        big = mine.size, caption = "考核中 · 上次答对 $right",
                        fill = if (mine.isEmpty()) 0f else right.toFloat() / mine.size,
                        counts = listOf("没做过" to never, "上次没对" to missed),
                        next = queue.take(3),
                    )
                    Button(onClick = onStart, enabled = queue.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (queue.isEmpty()) "还没有考核中的条目" else "开始考核 ${queue.size} 题", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "直接做题,答完才看笔记。没做过的和上次没做对的排在前面;例句都答对过,就会提示移到掌握。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("已掌握", mine.size, Modifier.weight(1f))
                        Stat("共", data.entries.size, Modifier.weight(1f))
                    }
                    Text(
                        "已掌握的条目不再进复习和考核。在条目里点进度条上的别的阶段,可以移回去。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
        }
    }
    val padding = PaddingValues(
        start = 8.dp, end = 8.dp,
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 24.dp,
    )
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
 * The head of 复习 / 考核 (UI refactor, direction B): a ring with the big number in it, two small
 * counts, and the next cards peeking out as a stack in their level colours.
 */
@Composable
private fun RunHead(big: Int, caption: String, fill: Float, counts: List<Pair<String, Int>>, next: List<Entry>) {
    val ink = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.padding(top = 8.dp).size(196.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val inset = 7.dp.toPx()
                val box = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                val at = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, 0f, 360f, false, at, box, style = stroke)
                if (fill > 0f) drawArc(ink, -90f, 360f * fill.coerceIn(0f, 1f), false, at, box, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$big", fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            counts.forEach { (label, n) -> Stat(label, n, Modifier.weight(1f)) }
        }
        if (next.isNotEmpty()) Box(Modifier.fillMaxWidth().height(84.dp)) {
            // back to front: the cards after the next one, narrower and higher, peek out on top
            next.drop(1).asReversed().forEachIndexed { i, e ->
                val k = next.size - 1 - i
                Box(
                    Modifier
                        .padding(horizontal = (12 * k).dp)
                        .padding(top = (10 * (2 - k)).dp.coerceAtLeast(0.dp))
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(levelColor(e.level), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .border(1.dp, MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                )
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(3.dp, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .background(levelColor(next[0].level), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "下一张 · " + next[0].title.removePrefix("自测:").removePrefix("自测:"),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One card of a run (UI refactor, direction B): a progress bar on top, then one card at a time.
 * 复习 shows the notes first on a card in the entry's level colour (看完了,翻面做题); the
 * question is a white card edged in that colour, the answer under it, the chips and 显示答案 at
 * the bottom; then the verdict, the notes, and 下一题 — with the move to the next stage offered
 * once this entry has earned it. Going from the notes to the question turns the card over.
 */
@Composable
private fun QuizCard(
    session: QuizSession,
    entry: Entry,
    attempts: List<Attempt>,
    since: Long,
    excluded: Set<String>,
    onStudied: () -> Unit,
    onReveal: (String) -> Unit,
    onGrade: (String, Boolean) -> Unit,
    onNext: (Boolean) -> Unit,
    onEnd: () -> Unit,
    onXref: (String) -> Unit,
    modifier: Modifier,
) {
    val exam = entry.level == "SELFTEST"
    val ex = session.example
    var draft by rememberSaveable(session.index) { mutableStateOf("") }
    val studying = session.stage == Stage.Review && !session.studied
    val answer = if (exam) entry.selfTestAnswer.lines().firstOrNull { it.isNotBlank() } else ex?.en
    val (chips, setChips) = rememberChipsMode()
    val bank = if (answer != null && chips && !session.revealed && !studying) {
        val seed = entry.anchor.hashCode() + session.index
        val others = remember(entry.anchor) { entry.quizExamples().map { plain(it.en) } + entry.selfTestAnswer.lines() }
            .filter { it.isNotBlank() && it != answer }
        rememberWordBank(answer, strayWords(answer, others, seed), seed).also { st -> LaunchedEffect(st.pickedText) { draft = st.draft } }
    } else null
    val ready = session.revealed && advanceReady(entry, session.stage, attempts, since, excluded)
    val color = levelColor(entry.level)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    // the turn from the notes (front) to the question (back), 复习 only
    val angle by androidx.compose.animation.core.animateFloatAsState(
        if (studying) 0f else 180f, androidx.compose.animation.core.tween(550), label = "turn",
    )
    val flipped = session.stage != Stage.Review || angle > 90f
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) {
            androidx.compose.material3.IconButton(onClick = onEnd) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, "结束这一轮")
            }
            LinearProgressIndicator(
                progress = { (session.index + if (session.revealed) 1 else 0).toFloat() / session.queue.size },
                modifier = Modifier.weight(1f).height(8.dp),
                trackColor = MaterialTheme.colorScheme.outline,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text("  ${session.index + 1} / ${session.queue.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .graphicsLayer {
                    if (session.stage == Stage.Review) {
                        rotationY = angle
                        cameraDistance = 16f * density
                    }
                }
                .then(
                    if (!flipped) Modifier.shadow(6.dp, shape).background(color, shape)
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape).border(2.dp, color, shape)
                )
                .clip(shape)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { if (session.stage == Stage.Review && angle > 90f) rotationY = 180f }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!flipped) {
                    // 复习: read it again first, on the front
                    Text("${levelStyle(entry.level).short} · 先看一遍", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text(entry.title, fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = if (entry.title.length > 18) 28.sp else 40.sp, lineHeight = if (entry.title.length > 18) 34.sp else 46.sp)
                    if (entry.ipa.isNotEmpty()) Text(entry.ipa, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    EntryNotes(entry, onXref)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (exam) "自测题" else entry.title) + " · 中译英",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (answer != null && !session.revealed) AnswerModeToggle(chips, setChips)
                    }
                    when {
                        exam -> Text(entry.title.removePrefix("自测:").removePrefix("自测:"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        ex != null -> Text(ex.zh, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        else -> ReviewFront(entry, kindOf(entry, inTest = true))
                    }
                    if (!session.revealed) {
                        if (bank != null) WordBankAnswer(bank) else DraftField(draft, { draft = it })
                        SelfTestStats(attempts.at(ex), one = ex != null)
                    } else {
                        if (exam) SelfTestResult(draft, entry.selfTestAnswer)
                        else MovedResult(ex, draft, session.graded) { onGrade(draft, it) }
                        if (ready) AdvanceBanner(session.stage) { onNext(true) }
                        SelfTestStats(attempts.at(ex), one = ex != null)
                        SelfTestHistory(attempts.at(ex), one = ex != null)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(entry.title, fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        EntryNotes(entry, onXref)
                    }
                }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                studying -> Button(onClick = onStudied, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("看完了,翻面做题", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                !session.revealed -> {
                    if (bank != null) WordBankChips(bank)
                    Button(onClick = { onReveal(draft) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("显示答案", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ready) OutlinedButton(onClick = { onNext(true) }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text("移到${session.stage.next?.tab}", fontSize = 16.sp)
                    }
                    Button(onClick = { onNext(false) }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(if (session.index + 1 < session.queue.size) "下一题" else "完成", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
