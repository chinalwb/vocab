package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
    onOpen: (String) -> Unit,
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
            onStudied, onReveal, onGrade, onNext, onEnd, onOpen, modifier,
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
    onOpen: (String) -> Unit,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("复习中", mine.size, Modifier.weight(1f))
                        Stat("今天到期", plan.due.size, Modifier.weight(1f))
                        Stat("还没排期", plan.fresh.size, Modifier.weight(1f))
                    }
                    Button(onClick = onStart, enabled = queue.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (mine.isEmpty()) "还没有复习中的条目" else if (queue.isEmpty()) "今天的都复习完了" else "开始复习 ${queue.size} 条")
                    }
                    Text(
                        "第二遍学:先看一遍笔记,再做例句(中译英)。答对了下次隔得更久,答错了明天再来;" +
                            "一个条目的例句在这一阶段都答对过,就会提示移到考核。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Stage.Test -> {
                    val never = mine.count { selfTests[it.anchor].isNullOrEmpty() }
                    val missed = mine.count { e -> selfTests[e.anchor]?.lastOrNull()?.let { it.text.isEmpty() || !it.ok } == true }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("考核中", mine.size, Modifier.weight(1f))
                        Stat("没做过", never, Modifier.weight(1f))
                        Stat("上次没对", missed, Modifier.weight(1f))
                    }
                    Button(onClick = onStart, enabled = queue.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (queue.isEmpty()) "还没有考核中的条目" else "开始考核 ${queue.size} 题")
                    }
                    Text(
                        "直接做题,答完才看笔记。自测题逐词对照标准答案,其他条目对照例句;例句都答对过,就会提示移到掌握。" +
                            "没做过的和上次没做对的排在前面。",
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
        gridItems(mine, key = { it.anchor }) { e -> EntryTile(e, null, foot(e)) { onOpen(e.anchor) } }
    } else LazyColumn(contentPadding = padding, modifier = modifier) {
        item { header() }
        items(mine, key = { it.anchor }) { e ->
            EntryCard(e, null, foot(e), shared = false) { onOpen(e.anchor) }
            Spacer(Modifier.padding(4.dp))
        }
    }
}

/**
 * One card of a run. 复习 shows the notes first (看完了,做题); then the question with the
 * answer box under it, the chips (选词) and 显示答案 at the bottom; then the verdict, the
 * notes, and 下一题 — with the move to the next stage offered once this entry has earned it.
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
    // 选词 when there's an answer to check against (a 自测题's 答案, an example's English)
    val answer = if (exam) entry.selfTestAnswer.lines().firstOrNull { it.isNotBlank() } else ex?.en
    val (chips, setChips) = rememberChipsMode()
    val bank = if (answer != null && chips && !session.revealed && !studying) {
        val seed = entry.anchor.hashCode() + session.index
        val others = remember(entry.anchor) { entry.quizExamples().map { plain(it.en) } + entry.selfTestAnswer.lines() }
            .filter { it.isNotBlank() && it != answer }
        rememberWordBank(answer, strayWords(answer, others, seed), seed).also { st -> LaunchedEffect(st.pickedText) { draft = st.draft } }
    } else null
    val ready = session.revealed && advanceReady(entry, session.stage, attempts, since, excluded)
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            LinearProgressIndicator(
                progress = { session.index.toFloat() / session.queue.size },
                modifier = Modifier.weight(1f),
                trackColor = MaterialTheme.colorScheme.outline,
                drawStopIndicator = {},
            )
            Text("  ${session.index + 1} / ${session.queue.size}", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onEnd) { Text("结束") }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (studying) {
                // 复习: read it again first
                Text("先看一遍", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(entry.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp)
                Spacer(Modifier.height(12.dp))
                EntryNotes(entry, onXref)
                Spacer(Modifier.height(24.dp))
            } else {
                if (ex != null) ExamplePrompt(ex) else ReviewFront(entry, kindOf(entry, inTest = true))
                Spacer(Modifier.height(8.dp))
                SelfTestStats(attempts)
                if (!session.revealed) {
                    Spacer(Modifier.height(12.dp))
                    if (answer != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AnswerModeToggle(chips, setChips) }
                    Spacer(Modifier.height(8.dp))
                    if (bank != null) WordBankAnswer(bank) else DraftField(draft, { draft = it })
                } else {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(20.dp))
                    if (exam) SelfTestResult(draft, entry.selfTestAnswer)
                    else MovedResult(ex, draft, session.graded) { onGrade(draft, it) }
                    Spacer(Modifier.height(8.dp))
                    SelfTestHistory(attempts)
                    if (ready) { Spacer(Modifier.height(8.dp)); AdvanceBanner(session.stage) { onNext(true) } }
                    Spacer(Modifier.height(16.dp))
                    Text(entry.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    EntryNotes(entry, onXref)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            when {
                studying -> Button(onClick = onStudied, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("看完了,做题") }
                !session.revealed -> {
                    if (bank != null) { WordBankChips(bank); Spacer(Modifier.height(12.dp)) }
                    Button(onClick = { onReveal(draft) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("显示答案") }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ready) OutlinedButton(onClick = { onNext(true) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("移到${session.stage.next?.tab}")
                    }
                    Button(onClick = { onNext(false) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(if (session.index + 1 < session.queue.size) "下一题" else "完成")
                    }
                }
            }
        }
    }
}
