package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.next

/**
 * One entry (UI refactor, direction A, 用户 2026-10-14). Cross-references push another EntryScreen
 * on the nav back stack, so the system back gesture walks back through the chain.
 *
 * Two modes on one page. Notes: a band in the entry's level colour (headword, a meta line that
 * opens the details sheet, and the progress panel — where the entry is and what moves it on; a
 * tap opens the stage picker), then the notes as one scrolling page with section chips, and one
 * button at the bottom, 自测这条. Self-test: a compact band (headword, which sentence of how many),
 * the question with the answer under it, and the chips / 显示答案 at the bottom; after the
 * reveal, a one-line verdict, both sentences, and 再试一次 / 下一句. 考核 opens on the self-test
 * and keeps the notes shut until it's answered.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    entry: Entry?,
    onBack: () -> Unit,
    onXref: (String) -> Unit,
    onSeen: (String) -> Unit,
    attempts: List<Attempt> = emptyList(),
    onAttempt: (String) -> Unit = {},
    /** an attempt at an example sentence, with its verdict and the example (plain English) */
    onGrade: (String, Boolean, String) -> Unit = { _, _, _ -> },
    stage: Stage = Stage.Learn,
    /** when it entered [stage] (ms): only attempts since then count towards the next stage */
    since: Long = 0L,
    onStage: (Stage) -> Unit = {},
    /** examples opted out of 自测 (their plain English), and the tap that toggles one */
    excluded: Set<String> = emptySet(),
    onToggleExample: (String) -> Unit = {},
    /** long-press → 收藏 / 取消收藏 on an example (its plain English) */
    onFav: (String) -> Unit = {},
    /** 下一个 in the list this was opened from ("6 / 20"), when there is one */
    nextLabel: String? = null,
    onNext: (() -> Unit)? = null,
) {
    if (entry != null) LaunchedEffect(entry.anchor) { onSeen(entry.anchor) }
    val band = entry?.let { levelColor(it.level) } ?: MaterialTheme.colorScheme.surfaceContainer
    Column(
        Modifier
            .fillMaxSize()
            .then(if (entry != null) Modifier.sharedEntryContainer(entry.anchor) else Modifier)
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        if (entry == null) {
            Column(Modifier.background(band).windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            }
            Text("找不到这个条目,可能已在词库里改名或删除。", Modifier.padding(20.dp))
            return@Column
        }
        val selfTest = entry.level == "SELFTEST"
        var details by rememberSaveable { mutableStateOf(false) }
        var picking by rememberSaveable { mutableStateOf(false) }
        // the attempt lives out here, so going to the notes and back doesn't lose it; a new try
        // resets it: 再试一次 asks the same example again, 下一句 the next one
        var tryNo by rememberSaveable(entry.anchor, stage) { mutableStateOf(attempts.size) }
        var exNo by rememberSaveable(entry.anchor, stage) { mutableStateOf(attempts.size) }
        val ex = remember(entry.anchor, exNo, excluded) { if (selfTest) null else entry.quizExample(exNo, excluded) }
        var draft by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf("") }
        var revealed by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf(false) }
        var graded by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf<Boolean?>(null) }
        var quizUp by rememberSaveable(entry.anchor, stage) { mutableStateOf(stage == Stage.Test) }
        val retry = { tryNo = maxOf(tryNo + 1, attempts.size); quizUp = true }
        val nextExample = { exNo = (ex?.k ?: exNo) + 1; retry() }
        val ready = revealed && advanceReady(entry, stage, attempts, since, excluded)
        val answer = if (selfTest) entry.selfTestAnswer.lines().firstOrNull { it.isNotBlank() } else ex?.en
        val (chips, setChips) = rememberChipsMode()
        val reveal = {
            when {
                selfTest -> onAttempt(draft)
                ex != null -> onGrade(draft, draft.isNotBlank() && bestDiff(draft, ex.en).same, ex.en)
                draft.isBlank() -> onGrade("", false, "")
            }
            revealed = true
        }
        // 考核 keeps the notes shut until the question is answered; elsewhere ← goes back to them
        val toNotes = quizUp && (stage != Stage.Test || revealed)

        if (!quizUp) {
            NotesBand(
                entry, band, stage,
                hint = progressHint(entry, stage, attempts, since, excluded),
                nextLabel = nextLabel, onNext = onNext, onBack = onBack,
                onDetails = { details = true }, onProgress = { picking = true },
            )
            NotesPage(
                entry, onXref,
                quiz = if (selfTest) null else ExampleQuiz(excluded, onToggleExample, onFav),
                modifier = Modifier.weight(1f),
            )
            BottomBar {
                val n = entry.quizExamples(excluded).size
                PrimaryButton(
                    when {
                        revealed -> "回到自测"
                        !selfTest && n > 0 -> "自测这条 · $n 句"
                        else -> "自测一下"
                    },
                ) { quizUp = true }
            }
        } else {
            QuizBand(
                entry, band,
                label = when {
                    ex != null -> "自测 · 第 ${ex.k + 1} 句,共 ${ex.of} 句"
                    else -> "自测"
                },
                at = ex?.k, of = ex?.of,
                onBack = if (toNotes) ({ quizUp = false }) else onBack,
                backLabel = if (toNotes) "回到笔记" else "返回",
            )
            val bank = if (!revealed && answer != null && chips) {
                val seed = entry.anchor.hashCode() + tryNo
                val others = remember(entry.anchor, excluded) {
                    entry.quizExamples(excluded).map { plain(it.en) } + entry.selfTestAnswer.lines()
                }.filter { it.isNotBlank() && it != answer }
                rememberWordBank(answer, strayWords(answer, others, seed), seed).also { st -> LaunchedEffect(st.pickedText) { draft = st.draft } }
            } else null
            val imeVisible = WindowInsets.isImeVisible
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!revealed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (ex != null || selfTest) "中译英" else "回想并写出来",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (answer != null) AnswerModeToggle(chips, setChips)
                    }
                    Question(entry, ex, compact = imeVisible)
                    if (bank != null) WordBankAnswer(bank) else DraftField(draft, { draft = it }, maxLines = 6)
                } else {
                    // the question again, quietly, above the verdict
                    when {
                        selfTest -> Text(entry.title.removePrefix("自测:").removePrefix("自测:"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ex != null -> Text(ex.zh, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selfTest) SelfTestResult(draft, entry.selfTestAnswer)
                    else MovedResult(ex, draft, graded) { graded = it; onGrade(draft, it, "") }
                    // 移到 X moves and goes on to the next one in the list; the stage picker only moves
                    if (ready) AdvanceBanner(stage) { stage.next?.let(onStage); onNext?.invoke() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SelfTestStats(attempts.at(ex), one = ex != null)
                    SelfTestHistory(attempts.at(ex), one = ex != null)
                }
            }
            BottomBar {
                if (!revealed) {
                    if (bank != null) WordBankChips(bank)
                    PrimaryButton("显示答案", onClick = reveal)
                } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = retry, modifier = Modifier.weight(1f).height(52.dp)) { Text("再试一次", fontSize = 16.sp) }
                    if (ex != null && ex.of > 1) Button(onClick = nextExample, modifier = Modifier.weight(1f).height(52.dp)) { Text("下一句 ›", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    else Button(onClick = { quizUp = false }, modifier = Modifier.weight(1f).height(52.dp)) { Text("看笔记", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (details) ModalBottomSheet(onDismissRequest = { details = false }) { Details(entry) }
        if (picking) ModalBottomSheet(onDismissRequest = { picking = false }) {
            StagePicker(stage) { onStage(it); picking = false }
        }
    }
}

/** The notes mode's band: ← and 下一个, the headword with 🔊, the meta line, the progress panel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesBand(
    entry: Entry,
    band: Color,
    stage: Stage,
    hint: String,
    nextLabel: String?,
    onNext: (() -> Unit)?,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    onProgress: () -> Unit,
) {
    val sub = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(band)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp).size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Spacer(Modifier.weight(1f))
            if (onNext != null) TextButton(onClick = onNext) {
                Text("${nextLabel.orEmpty()} · 下一个", color = sub)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = sub)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.title,
                fontFamily = AppFont,
                fontWeight = FontWeight.Bold,
                fontSize = if (entry.title.length > 18) 26.sp else 34.sp,
                lineHeight = if (entry.title.length > 18) 32.sp else 40.sp,
                modifier = Modifier.weight(1f, fill = false).sharedEntryTitle(entry.anchor),
            )
            val say = entry.speakText()
            if (say.isNotEmpty()) SpeakButton(say, size = 22.dp)
        }
        val line = listOf(levelStyle(entry.level).short, entry.pos, entry.ipa).filter { it.isNotEmpty() }.joinToString(" · ")
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDetails).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(line, style = MaterialTheme.typography.bodyMedium, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "详情", Modifier.size(18.dp), tint = sub)
        }
        ProgressPanel(stage, hint, onProgress)
    }
}

/** Where the entry is, what moves it on, and four short bars; a tap opens the stage picker. */
@Composable
private fun ProgressPanel(stage: Stage, hint: String, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val done = stage == Stage.Done
    val fill = if (done) Color(0xFF2E6B3A) else ink
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stage.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "换阶段", Modifier.size(18.dp), tint = ink.copy(alpha = 0.6f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Stage.entries.forEach { s ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(if (s.ordinal <= stage.ordinal) fill else ink.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

/** The panel's hint: how many sentences are left before the next stage is offered. */
private fun progressHint(entry: Entry, stage: Stage, attempts: List<Attempt>, since: Long, excluded: Set<String>): String {
    val next = stage.next ?: return "已掌握,不再进复习和考核"
    val left = remainingToAdvance(entry, stage, attempts, since, excluded) ?: return "可以手动移到${next.tab}"
    return when {
        left == 0 -> "题都答对过了,可以移到${next.tab}"
        entry.level == "SELFTEST" -> "答对这道题可移到${next.tab}"
        else -> "再答对 $left 句可移到${next.tab}"
    }
}

/** The four stages as rows; tapping one moves the entry there (only moves — no jump to the next entry). */
@Composable
private fun StagePicker(stage: Stage, onPick: (Stage) -> Unit) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text("移到哪个阶段", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Stage.entries.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (s == stage) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                if (s == stage) Icon(Icons.Default.Check, "当前阶段")
            }
        }
    }
}

/** The self-test mode's band: ←, the headword, which sentence of how many, one bar per sentence. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizBand(entry: Entry, band: Color, label: String, at: Int?, of: Int?, onBack: () -> Unit, backLabel: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .fillMaxWidth()
            .background(band)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel) }
            Text(
                entry.title.removePrefix("自测:").removePrefix("自测:"),
                fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.72f), modifier = Modifier.padding(start = 8.dp))
        }
        if (at != null && of != null && of > 1) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(of) { i ->
                Box(Modifier.weight(1f).height(4.dp).background(if (i <= at) ink else ink.copy(alpha = 0.15f), RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** The question: a 自测题's Chinese title, an example's Chinese, or — with none — a review card. */
@Composable
private fun Question(entry: Entry, ex: QuizExample?, compact: Boolean) {
    val style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
    when {
        entry.level == "SELFTEST" -> Text(entry.title.removePrefix("自测:").removePrefix("自测:"), style = style, fontWeight = FontWeight.Bold)
        ex != null -> Text(ex.zh, style = style, fontWeight = FontWeight.Bold)
        else -> ReviewFront(entry, kindOf(entry, inTest = true))
    }
}

/** The bottom of either mode: what's there stays put above the navigation bar. */
@Composable
private fun BottomBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

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
