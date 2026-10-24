package io.github.chinalwb.vocab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.PROMOTE_REPS
import io.github.chinalwb.vocab.review.MemoryPoint
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.next

/**
 * One entry (V2, ui/claude-code-v2, 用户 2026-10-14). Cross-references push another EntryScreen
 * on the nav back stack, so the system back gesture walks back through the chain.
 *
 * Notes: ← and the 下一个 pill on top, the headword with 🔊, a meta pill that opens the details,
 * the four-stage track (tap a stage to move there), then the notes on a card in the level colour
 * — section tabs, 卡片 / 全部 and ⤢ in the card's own top row, and at its foot how many sentences
 * are left in this stage beside 自测这条. ⤢ folds the headword and the track away so the card
 * takes the page; ⤡ in the same spot brings them back.
 *
 * 自测: the answer sheet (AnswerSheet.kt) under a row with ⤡ (back to the notes), the headword and
 * a bar per sentence; after the reveal, the verdict, the notes the sentence comes from, and
 * 再试一次 / 下一句. 考核 opens on the 自测 and keeps the notes shut until it's answered.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    entry: Entry?,
    onBack: () -> Unit,
    onXref: (String) -> Unit,
    onSeen: (String) -> Unit,
    attempts: List<Attempt> = emptyList(),
    /** a 自测题's answer, and whether it counts as right (both steps) */
    onAttempt: (String, Boolean) -> Unit = { _, _ -> },
    /** an attempt at an example sentence, with its verdict and the example (plain English) */
    onGrade: (String, Boolean, String) -> Unit = { _, _, _ -> },
    /** 算我对: the last attempt counts as right after all */
    onRight: () -> Unit = {},
    stage: Stage = Stage.Learn,
    /** when it entered [stage] (ms): only attempts since then count towards the next stage */
    since: Long = 0L,
    onStage: (Stage) -> Unit = {},
    /** examples opted out of 自测 (their plain English), and the tap that toggles one */
    excluded: Set<String> = emptySet(),
    /** the SM-2 card's right answers in a row: 复习 → 考核 needs [PROMOTE_REPS] of them */
    reps: Int = 0,
    /** 记忆: the graded days its forgetting curve is replayed from, and the SM-2 card's next day in 复习 */
    memory: List<MemoryPoint> = emptyList(),
    due: Long? = null,
    /**
     * due for 复习 today: the card's button reviews it as a run of one ([onReviewRun]) instead of the
     * page's 自测 — not before it's due, as reviewing early stretches its intervals
     */
    reviewNow: Boolean = false,
    onReviewRun: () -> Unit = {},
    onToggleExample: (String) -> Unit = {},
    /** long-press → 收藏 / 取消收藏 on an example (its plain English) */
    onFav: (String) -> Unit = {},
    /** 下一个 in the list this was opened from ("6 / 20"), when there is one */
    nextLabel: String? = null,
    onNext: (() -> Unit)? = null,
    /** 我的笔记: what I've written on this entry, and the save (as I type) */
    note: String = "",
    onNote: (String) -> Unit = {},
) {
    if (entry != null) LaunchedEffect(entry.anchor) { onSeen(entry.anchor) }
    val band = entry?.let { levelColor(it.level) } ?: MaterialTheme.colorScheme.surfaceContainer
    // not inside a Surface, so un-coloured text (the headword, the notes) would default to black —
    // unreadable in dark mode
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (entry != null) Modifier.sharedEntryContainer(entry.anchor) else Modifier)
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        if (entry == null) {
            TopRow(onBack, null, null)
            Text("找不到这个条目,可能已在词库里改名或删除。", Modifier.padding(20.dp))
            return@Column
        }
        val selfTest = entry.level == "SELFTEST"
        var details by rememberSaveable { mutableStateOf(false) }
        // ⤢: the card takes the page (the headword, meta pill and track fold away)
        var full by rememberSaveable { mutableStateOf(false) }
        // the attempt lives out here, so going to the notes and back doesn't lose it; a new try
        // resets it: 再试一次 asks the same example again, 下一句 the next one
        var tryNo by rememberSaveable(entry.anchor, stage) { mutableStateOf(attempts.size) }
        var exNo by rememberSaveable(entry.anchor, stage) { mutableStateOf(attempts.size) }
        val ex = remember(entry.anchor, exNo, excluded) { if (selfTest) null else entry.quizExample(exNo, excluded) }
        // 两步: ① 选词 then ② 打字 (stepLabel() in SelfTest.kt); the step's own state resets with it
        // a sentence answered right before skips ① (rightBefore()); read once, as the try starts
        val skipBank = remember(entry.anchor, stage, tryNo, exNo) {
            (if (selfTest) entry.selfTestAnswer.isNotBlank() else ex != null) && attempts.rightBefore(ex?.en ?: "")
        }
        var step by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf(if (skipBank) 2 else 1) }
        var bankOk by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf(skipBank) }
        var typedOk by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf(false) }
        var helped by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf(false) }
        var typed by rememberSaveable(entry.anchor, stage, tryNo, step) { mutableStateOf("") }
        // ② with a **bold** key part: just that part is typed, the rest is filled in (blanksOf())
        var fills by rememberSaveable(entry.anchor, stage, tryNo, step) { mutableStateOf("") }
        var righted by rememberSaveable(entry.anchor, stage, tryNo, step) { mutableStateOf(false) }
        var help by rememberSaveable(entry.anchor, stage, tryNo, step) { mutableStateOf(false) }
        var draft by rememberSaveable(entry.anchor, stage, tryNo, step) { mutableStateOf("") }
        var revealed by rememberSaveable(entry.anchor, stage, tryNo, step) { mutableStateOf(false) }
        var graded by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf<Boolean?>(null) }
        var quizUp by rememberSaveable(entry.anchor, stage) { mutableStateOf(stage == Stage.Test) }
        val retry = { tryNo = maxOf(tryNo + 1, attempts.size); quizUp = true }
        val nextExample = { exNo = (ex?.k ?: exNo) + 1; retry() }
        val ready = revealed && (step == 2 || !selfTest && ex == null) && advanceReady(entry, stage, attempts, since, excluded, reps)
        val answer = if (selfTest) entry.selfTestAnswer.lines().firstOrNull { it.isNotBlank() } else ex?.en
        val key = if (selfTest) entry.selfTestAnswer else ex?.en       // what a reveal checks against
        var bankDraft: () -> String? = { null }
        val reveal = {
            draft = bankDraft() ?: typed
            val right = draft.isNotBlank() && key != null && bestDiff(draft, key).same
            when {
                key != null && step == 1 -> bankOk = right            // ① checked, not logged
                key != null -> {                                       // ②: right only if ① was too
                    helped = bankDraft() != null
                    typedOk = right && !helped
                    if (selfTest) onAttempt(draft, bankOk && typedOk) else onGrade(draft, bankOk && typedOk, ex!!.en)
                }
                draft.isBlank() -> onGrade("", false, "")
            }
            revealed = true
        }
        // 考核 keeps the notes shut until the question is answered; elsewhere ⤡ goes back to them
        val toNotes = quizUp && (stage != Stage.Test || revealed)

        if (!quizUp) {
            TopRow(onBack, nextLabel, onNext, title = entry, showTitle = full)
            // ⤢ / ⤡ animate (用户 2026-10-24): the bands fold up and the card grows into the room
            AnimatedVisibility(!full, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Headword(entry)
                    MetaPill(entry) { details = true }
                    StageTrack(stage, onStage)
                }
            }
            val cardTop by animateDpAsState(if (full) 4.dp else 0.dp, label = "cardTop")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 8.dp, end = 8.dp, top = cardTop, bottom = 10.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(band),
            ) {
                NotesPage(
                    entry, onXref,
                    quiz = if (selfTest) null else ExampleQuiz(excluded, onToggleExample, onFav),
                    modifier = Modifier.weight(1f),
                    inCard = true,
                    trailing = {
                        IconButton(onClick = { full = !full }) {
                            Icon(if (full) CloseFullscreenIcon else OpenInFullIcon, if (full) "退出全屏" else "全屏", Modifier.size(20.dp))
                        }
                    },
                    // the 记忆 card after the notes, once there is a graded day or the entry is past 学习中;
                    // 我的笔记 last, always there to write in
                    extra = listOfNotNull(
                        if (memory.isNotEmpty() || stage != Stage.Learn) "记忆" to { MemoryCard(memory, stage, due, band) } else null,
                        "我的笔记" to @Composable { MyNote(entry.anchor, note, onNote) },
                    ),
                )
                CardFoot(
                    hint = progressHint(entry, stage, attempts, since, excluded, reps),
                    label = when {
                        reviewNow -> "复习这一条"
                        revealed -> "回到自测"
                        selfTest -> "自测一下"
                        else -> "自测这条"
                    },
                ) { if (reviewNow) onReviewRun() else quizUp = true }
            }
        } else {
            QuizTop(
                entry, ex,
                icon = if (toNotes) CloseFullscreenIcon else null,
                label = if (toNotes) "回到笔记" else "返回",
                onClick = if (toNotes) ({ quizUp = false }) else onBack,
            )
            val bank = if (!revealed && answer != null && (step == 1 || help)) {
                val seed = entry.anchor.hashCode() + tryNo
                val others = remember(entry.anchor, excluded) {
                    entry.quizExamples(excluded).map { plain(it.en) } + entry.selfTestAnswer.lines()
                }.filter { it.isNotBlank() && it != answer }
                rememberWordBank(answer, strayWords(answer, others, seed), seed)
            } else null
            bankDraft = { bank?.draft }
            val stats = statsLine(attempts.at(ex), one = ex != null)
            AnswerSheet(
                band,
                Modifier.weight(1f).navigationBarsPadding(),
                card = {
                    SheetMetaRow(
                        when {
                            selfTest -> "自测题 · 中译英"
                            ex != null -> "例句 ${ex.k + 1} / ${ex.of} · 中译英"
                            else -> "回想并写出来"
                        } + if (answer != null) stepLabel(step) else "",
                        toggle = if (!revealed && answer != null) ({
                            AnswerModeToggle(step == 1 || help, { if (step == 2) help = it }, lockSecond = step == 1)
                        }) else null,
                    )
                    if (!revealed) {
                        Question(entry, ex, compact = if (bank != null) bank.picked.isNotEmpty() else typed.isNotBlank())
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stats, style = MaterialTheme.typography.bodySmall, color = sheetColors().sub, modifier = Modifier.weight(1f, fill = false))
                            if (toNotes) Text(
                                "看笔记",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { quizUp = false }.padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                        val blanks = if (step == 2 && stage != Stage.Test) blanksOf(ex?.raw) else null
                        when {
                            bank != null -> ChipsAnswerBox(bank)
                            blanks != null -> FillAnswerBox(blanks, fillsOf(fills)) { fills = it.joinToString(FILL_SEP.toString()); typed = composeBlanks(blanks, it) }
                            else -> TypingAnswerBox(typed) { typed = it }
                        }
                    } else {
                        // the question again, quietly, above the verdict
                        QuestionQuiet(entry, ex)
                        if (key != null && step == 1) {
                            RevealVerdict(draft, key, STEP1_NOTE)
                            return@AnswerSheet
                        }
                        if (key != null) StepsLine(if (righted) RIGHTED else stepsText(bankOk, typedOk, helped, skipBank), righted || bankOk && typedOk)
                        when {
                            key != null -> RevealVerdict(draft, key, stats)
                            else -> MovedResult(draft, graded) { graded = it; onGrade(draft, it, "") }
                        }
                        if (key != null && !righted && !(bankOk && typedOk) && draft.isNotBlank()) RightMeRow { righted = true; onRight() }
                        // 移到 X moves and goes on to the next one in the list; the track only moves
                        if (ready) AdvanceBanner(stage) { stage.next?.let(onStage); onNext?.invoke() }
                        NotesForQuestion(entry, ex, onXref, onAll = if (toNotes) ({ quizUp = false }) else null)
                        SelfTestHistory(attempts.at(ex), one = ex != null)
                    }
                },
                panel = {
                    if (!revealed) {
                        if (bank != null) WordKeys(bank)
                        SheetButton("显示答案", onClick = reveal)
                    } else if (key != null && step == 1) SheetButton("下一步:打字 ›") { step = 2 }
                    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SheetButton("再试一次", Modifier.weight(1f), filled = false, onClick = retry)
                        if (ex != null && ex.of > 1) SheetButton("下一句 ›", Modifier.weight(1f), onClick = nextExample)
                        else SheetButton("看笔记", Modifier.weight(1f)) { quizUp = false }
                    }
                },
            )
        }
        if (details) ModalBottomSheet(onDismissRequest = { details = false }) { Details(entry) }
    }
    }
}

/** ←, the headword with 🔊 while full screen ([title], fading in with [showTitle]), and the 下一个 pill. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopRow(onBack: () -> Unit, nextLabel: String?, onNext: (() -> Unit)?, title: Entry? = null, showTitle: Boolean = title != null) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .height(52.dp)
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        Box(Modifier.weight(1f)) {
            // spelled out: inside the Row, the RowScope overload is picked and refuses the implicit receiver
            if (title != null) androidx.compose.animation.AnimatedVisibility(showTitle, enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title.title, fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    title.speakText().takeIf { it.isNotEmpty() }?.let { SpeakButton(it) }
                }
            }
        }
        if (onNext != null) NextPill(nextLabel.orEmpty(), onNext)
    }
}

/** "6 / 20 · 下一个 ›" as a pill in the top right. */
@Composable
private fun NextPill(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(36.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("下一个", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
    }
}

/** The headword with 🔊; long titles drop a size so they wrap in two lines, not three. */
@Composable
private fun Headword(entry: Entry) {
    val long = entry.title.length > 18
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 2.dp), verticalAlignment = Alignment.Top) {
        Text(
            entry.title,
            fontFamily = AppFont,
            fontWeight = FontWeight.Bold,
            fontSize = if (long) 26.sp else 32.sp,
            lineHeight = if (long) 32.sp else 38.sp,
            modifier = Modifier.weight(1f).sharedEntryTitle(entry.anchor),
        )
        entry.speakText().takeIf { it.isNotEmpty() }?.let { SpeakButton(it, size = 22.dp) }
    }
}

/** "B2 · adj. · /…/ ›" — the details sheet behind it holds the rest (type, number, date). */
@Composable
private fun MetaPill(entry: Entry, onClick: () -> Unit) {
    val pos = entry.pos.substringBefore('(').substringBefore('(').trim()
    val rest = listOf(pos, entry.ipa).filter { it.isNotEmpty() }
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 10.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(levelStyle(entry.level).short, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        if (rest.isNotEmpty()) Text(
            " · " + rest.joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "详情", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The four stages as a track (学习中 → 复习中 → 考核中 → 已掌握): passed ones ticked, the current one
 * a ringed dot, the rest hollow; the line fills up to where the entry is. Tapping another stage
 * moves it there (it only moves — no jump to the next entry).
 */
@Composable
private fun StageTrack(stage: Stage, onPick: (Stage) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.outline
    val ground = MaterialTheme.colorScheme.background
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val n = Stage.entries.size
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .drawBehind {
                val col = size.width / n
                val y = 13.dp.toPx()
                val x0 = col / 2
                val x1 = size.width - col / 2
                drawLine(track, Offset(x0, y), Offset(x1, y), 3.dp.toPx(), StrokeCap.Round)
                if (stage.ordinal > 0) drawLine(ink, Offset(x0, y), Offset(x0 + (x1 - x0) * stage.ordinal / (n - 1), y), 3.dp.toPx(), StrokeCap.Round)
            },
    ) {
        Stage.entries.forEach { s ->
            val cur = s == stage
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !cur) { onPick(s) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    s.ordinal < stage.ordinal -> Box(Modifier.size(26.dp).background(ink, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = ground)
                    }
                    cur -> Box(Modifier.size(26.dp).background(ground, CircleShape).border(3.dp, ink, CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(10.dp).background(ink, CircleShape))
                    }
                    else -> Box(Modifier.size(26.dp).background(ground, CircleShape).border(2.dp, track, CircleShape))
                }
                Text(
                    s.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                    color = if (cur) ink else muted,
                )
            }
        }
    }
}

/** The card's foot: what's left in this stage, and the way into 自测. */
@Composable
private fun CardFoot(hint: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(hint, style = MaterialTheme.typography.bodySmall, color = sheetColors().sub, maxLines = 2, modifier = Modifier.weight(1f))
        Button(onClick = onClick, modifier = Modifier.height(48.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 16.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
        }
    }
}

/**
 * The panel's hint: how many sentences are still to be answered right before the next stage is
 * offered — and in 复习, how many more right answers in a row its runs need (then it moves by itself).
 */
private fun progressHint(entry: Entry, stage: Stage, attempts: List<Attempt>, since: Long, excluded: Set<String>, reps: Int): String {
    val next = stage.next ?: return "已掌握,不再进复习和考核"
    val left = remainingToAdvance(entry, stage, attempts, since, excluded)
    if (stage == Stage.Review) {
        val more = PROMOTE_REPS - reps
        val todo = listOfNotNull(
            left?.takeIf { it > 0 }?.let { if (entry.level == "SELFTEST") "答对这道题" else "再答对 $it 句" },
            more.takeIf { it > 0 }?.let { "复习里再连续答对 $it 次" },
        )
        return if (todo.isEmpty()) "都做到了,可以移到${next.tab}" else todo.joinToString(",") + ",就自动移到${next.tab}"
    }
    left ?: return "可以手动移到${next.tab}"
    return when {
        left == 0 -> "题都答对过了,可以移到${next.tab}"
        entry.level == "SELFTEST" -> "答对这道题可移到${next.tab}"
        else -> "再答对 $left 句可移到${next.tab}"
    }
}

/** The 自测's row: ⤡ back to the notes (← out, while 考核 keeps them shut), the headword, a bar per sentence. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizTop(entry: Entry, ex: QuizExample?, icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .height(52.dp)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onClick) {
            if (icon != null) Icon(icon, label, Modifier.size(20.dp)) else Icon(Icons.AutoMirrored.Filled.ArrowBack, label)
        }
        Text(
            entry.title.removePrefix("自测:").removePrefix("自测:"),
            fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (ex != null) SentenceBars(ex.k, ex.of)
    }
}

/** The question: a 自测题's Chinese title, an example's Chinese, or — with none — a review card. */
@Composable
private fun Question(entry: Entry, ex: QuizExample?, compact: Boolean = false) {
    when {
        entry.level == "SELFTEST" -> QuestionText(entry.title.removePrefix("自测:").removePrefix("自测:"), compact)
        ex != null -> QuestionText(ex.zh, compact)
        else -> ReviewFront(entry, kindOf(entry, inTest = true))
    }
}

/** The question again after the reveal, small and quiet. */
@Composable
private fun QuestionQuiet(entry: Entry, ex: QuizExample?) {
    val text = when {
        entry.level == "SELFTEST" -> entry.title.removePrefix("自测:").removePrefix("自测:")
        ex != null -> ex.zh
        else -> return
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = sheetColors().sub)
}

/**
 * 我的笔记 (用户 2026-10-24): a box on the card that grows with what I write and saves as I type
 * (half a second after the last key, and on leaving the page). Same as myNoteSlide() on the page.
 * A note that comes in through 同步 shows unless I'm typing in the box.
 */
@Composable
fun MyNote(anchor: String, note: String, onNote: (String) -> Unit) {
    var text by rememberSaveable(anchor) { mutableStateOf(note) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(note) { if (!focused) text = note }
    LaunchedEffect(text) { delay(500); onNote(text) }
    val latest by rememberUpdatedState(text)
    val save by rememberUpdatedState(onNote)
    DisposableEffect(anchor) { onDispose { save(latest) } }
    val ink = LocalContentColor.current
    BasicTextField(
        text, { text = it },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink),
        cursorBrush = SolidColor(ink),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (!it.isFocused) onNote(text) },
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ink.copy(alpha = 0.06f))
                    .border(if (focused) 2.dp else 1.dp, ink.copy(alpha = if (focused) 0.9f else 0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (text.isEmpty()) Text("写下自己的理解、例句、容易混的地方…", style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = 0.5f))
                field()
            }
        },
    )
    Text(NOTE_HINT, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.65f))
}

/** under the box — the page says the same (myNoteSlide()) */
private const val NOTE_HINT = "自动保存。开了同步的话会存进 GitHub 的公开仓库,别写隐私内容。"

/** The details sheet: type, number, IPA, part of speech, CEFR, 会写 / 认识, date. */
@Composable
private fun Details(entry: Entry) {
    val rows = listOf(
        "类型" to "${levelStyle(entry.level).name} ${levelStyle(entry.level).short}",
        "编号" to (if (entry.no > 0) "#${entry.no}" else ""),
        "音标" to entry.ipa,
        "词性" to entry.pos,
        "CEFR" to entry.cefr,
        "掌握" to if (entry.writes) "会写" else "认识",
        "收录" to entry.date,
    ).filter { it.second.isNotBlank() }
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(entry.title, fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        rows.forEach { (label, value) ->
            Row {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
