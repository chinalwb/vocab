package io.github.chinalwb.vocab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.Button
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.next

/**
 * One entry. Cross-references push another EntryScreen on the nav back stack,
 * so the system back gesture walks back through the chain like the page's ← 返回.
 *
 * Three bands, top to bottom: the title (meta: a tap opens the details sheet), the progress
 * stepper, and the card, which takes whatever height is left and scrolls inside.
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
    /** 下一个 in the list this was opened from ("6 / 20"), when there is one */
    nextLabel: String? = null,
    onNext: (() -> Unit)? = null,
) {
    if (entry != null) LaunchedEffect(entry.anchor) { onSeen(entry.anchor) }
    var details by rememberSaveable { mutableStateOf(false) }
    // The card can take the whole page: by its ⤢ button, or on its own while the keyboard is up
    // (the meta and progress bands would only crowd the little room that's left). The top bar
    // then carries the headword.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    // 选词 needs the room too (its chips stand in for the keyboard). Either one turns the card
    // full screen for the rest of that try — switching 选词 → 打字 keeps it — and ⤡ shrinks it
    // back; it lets go on its own once the answer is shown. The body reports where it is.
    var answering by remember { mutableStateOf(false) }
    var chipsAnswering by remember { mutableStateOf(false) }
    var autoOn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(chipsAnswering, imeVisible) { if (chipsAnswering || imeVisible) autoOn = true }
    LaunchedEffect(answering) { if (!answering) autoOn = false }
    val fullscreen = expanded || autoOn
    val toggleFull = { if (fullscreen) { expanded = false; autoOn = false } else expanded = true }
    Scaffold(
        topBar = {
            TopAppBar(
                // Stable padding: the status bar may be re-showing while this page flies in.
                windowInsets = WindowInsets.statusBarsIgnoringVisibility,
                title = {
                    if (fullscreen && entry != null) Text(entry.title, fontFamily = AppFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else Text(entry?.let { levelStyle(it.level).name } ?: "")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    if (onNext != null) TextButton(onClick = onNext) {
                        Text("${nextLabel.orEmpty()} · 下一个", color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = entry?.let { levelColor(it.level) } ?: Color.Unspecified),
            )
        }
    ) { pad ->
        // Only the body grows out of the tapped card; the top bar fades in on its own,
        // so the flying title never has to pass underneath it.
        Column(
            Modifier
                .padding(pad)
                // the keyboard takes room from the page (the card shrinks) instead of panning
                // the whole window, which pushed the top bar under the status bar
                .consumeWindowInsets(pad)
                .imePadding()
                .fillMaxSize()
                .then(if (entry != null) Modifier.sharedEntryContainer(entry.anchor) else Modifier)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp)
        ) {
            if (entry == null) {
                Text("找不到这个条目,可能已在词库里改名或删除。", Modifier.padding(vertical = 16.dp))
                return@Column
            }
            AnimatedVisibility(visible = !fullscreen) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Meta(entry) { details = true }
                    Spacer(Modifier.height(14.dp))
                    Stepper(stage, selfTest = entry.level == "SELFTEST", onStage)
                }
            }
            Spacer(Modifier.height(14.dp))
            // The entry itself is a card with two sides, like a coin: the notes (a carousel) and
            // its 自测 — one of its examples, 中→英 — which every stage has; 考核 opens on the quiz,
            // the rest on the notes. Once every question is answered right in this stage, the
            // quiz side offers the move to the next one.
            val selfTest = entry.level == "SELFTEST"
            // kept out here so flipping to the notes and back doesn't lose my attempt; a new try
            // resets them: 再试一次 asks the same example again, 下一句 the next one
            var tryNo by rememberSaveable(entry.anchor, stage) { mutableStateOf(attempts.size) }
            var exNo by rememberSaveable(entry.anchor, stage) { mutableStateOf(attempts.size) }
            val ex = remember(entry.anchor, exNo, excluded) { if (selfTest) null else entry.quizExample(exNo, excluded) }
            var draft by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf("") }
            var revealed by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf(false) }
            var graded by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf<Boolean?>(null) }
            var quizUp by rememberSaveable(entry.anchor, stage) { mutableStateOf(stage == Stage.Test) }
            val quiz = quizUp
            val notesUp = !quizUp
            val retry = { tryNo = maxOf(tryNo + 1, attempts.size); quizUp = true }
            val nextExample = { exNo = (ex?.k ?: exNo) + 1; retry() }
            val ready = revealed && advanceReady(entry, stage, attempts, since, excluded)
            // With an answer to check against, the words can be tapped together (选词) instead
            // of typed; the strays come from the entry's other sentences.
            val answer = if (selfTest) entry.selfTestAnswer.lines().firstOrNull { it.isNotBlank() } else ex?.en
            val (chips, setChips) = rememberChipsMode()
            SideEffect {
                answering = quiz && !revealed
                chipsAnswering = answering && chips && answer != null
            }
            val reveal = {
                when {
                    selfTest -> onAttempt(draft)
                    ex != null -> onGrade(draft, draft.isNotBlank() && bestDiff(draft, ex.en).same, ex.en)
                    draft.isBlank() -> onGrade("", false, "")
                }
                revealed = true
            }
            FlipCard(
                back = quiz,
                color = levelColor(entry.level),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                front = {
                    EntryNotes(
                        entry, onXref, inCard = true, modifier = Modifier.weight(1f),
                        quiz = if (selfTest) null else ExampleQuiz(excluded, onToggleExample),
                        trailing = { ExpandButton(fullscreen, toggleFull) },
                    )
                    FlipButton(if (revealed) "回到自测" else "自测一下") { quizUp = true }
                },
                backSide = {
                    SideHeading("自测") {
                        if (answer != null && !revealed) { AnswerModeToggle(chips, setChips); Spacer(Modifier.width(6.dp)) }
                        if (!revealed && stage != Stage.Test) Text(
                            "看笔记",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { quizUp = false }.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                        ExpandButton(fullscreen, toggleFull)
                    }
                    if (!revealed) {
                        val bank = if (answer != null && chips) {
                            val seed = entry.anchor.hashCode() + tryNo
                            val others = remember(entry.anchor, excluded) {
                                entry.quizExamples(excluded).map { plain(it.en) } + entry.selfTestAnswer.lines()
                            }.filter { it.isNotBlank() && it != answer }
                            rememberWordBank(answer, strayWords(answer, others, seed), seed).also { st -> LaunchedEffect(st.pickedText) { draft = st.draft } }
                        } else null
                        // The question with the answer right under it (the text box, or the sentence
                        // being built), scrolling together; the chips and 显示答案 stay put at the bottom.
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!imeVisible && bank == null) SelfTestStats(attempts.at(ex), one = ex != null)
                            when {
                                // a 自测题's question is its title — off the page while full screen
                                selfTest -> if (fullscreen) Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                // a moved entry asks one of its examples (中→英); with none, like a review card
                                ex != null -> ExamplePrompt(ex, compact = imeVisible || (bank != null && !fullscreen))
                                else -> ReviewFront(entry, kindOf(entry, inTest = true))
                            }
                            if (bank != null) WordBankAnswer(bank) else DraftField(draft, { draft = it }, maxLines = 6)
                        }
                        if (bank != null) WordBankChips(bank)
                        Button(onClick = reveal, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("显示答案") }
                    } else Scrolling {
                        SelfTestStats(attempts.at(ex), one = ex != null)
                        if (selfTest) SelfTestResult(draft, entry.selfTestAnswer)
                        else MovedResult(ex, draft, graded) { graded = it; onGrade(draft, it, "") }
                        // 移到 X moves and goes on to the next one in the list; a move on the stepper stays put
                        if (ready) AdvanceBanner(stage) { stage.next?.let(onStage); onNext?.invoke() }
                        // 再试一次 asks the same sentence again; with more than one, 下一句 the next
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RetryButton(retry)
                            if (ex != null && ex.of > 1) OutlinedButton(onClick = nextExample, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("下一句 ›") }
                            FlipButton("翻面看笔记") { quizUp = false }
                        }
                        SelfTestHistory(attempts.at(ex), one = ex != null)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            if (details) ModalBottomSheet(onDismissRequest = { details = false }) { Details(entry) }
        }
    }
}

/**
 * The meta band, kept to the headword: the title, and under it a pill (type · 词性 · 音标 ›)
 * that opens the details sheet — the whole band is the tap target. Everything else about the
 * entry lives in the sheet; it rarely matters while reading.
 */
@Composable
private fun Meta(entry: Entry, onDetails: () -> Unit) {
    val line = listOf(levelStyle(entry.level).short, entry.pos, entry.ipa).filter { it.isNotEmpty() }.joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            entry.title,
            fontFamily = AppFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            modifier = Modifier.sharedEntryTitle(entry.anchor),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onDetails)
                .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                line,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "详情", Modifier.padding(start = 2.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        val say = entry.speakText()
        if (say.isNotEmpty()) SpeakButton(say)
        }
    }
}

/** The details sheet: what the chips used to show, as label / value rows. */
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

/**
 * The progress band, drawn as levels: the stages as nodes on a track. The track
 * is filled up to the node the entry is at; the nodes behind it are ticked, the ones ahead are
 * empty rings. Tapping another node moves the entry there (a 自测题 starts at 考核, but may be
 * moved anywhere like the rest — the greyed-out ticks it used to get read as a glitch).
 */
@Composable
private fun Stepper(stage: Stage, selfTest: Boolean, onStage: (Stage) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.outline
    val node = 30.dp
    val cur = stage.ordinal
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // the track runs between the node centres, under the nodes
                val w = size.width / Stage.entries.size
                val y = (node / 2).toPx()
                val stroke = 3.dp.toPx()
                drawLine(track, Offset(w / 2, y), Offset(size.width - w / 2, y), stroke)
                drawLine(ink, Offset(w / 2, y), Offset(w * (cur + 0.5f), y), stroke)
            },
    ) {
        Stage.entries.forEachIndexed { i, s ->
            val here = i == cur
            val passed = i < cur
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !here) { onStage(s) }
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(node)
                        .then(
                            when {
                                // the current level: a filled node with a ring of ground around it
                                here -> Modifier.border(2.dp, ink, CircleShape).padding(4.dp).background(ink, CircleShape)
                                passed -> Modifier.background(ink, CircleShape)
                                else -> Modifier.background(MaterialTheme.colorScheme.background, CircleShape).border(2.dp, track, CircleShape)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        passed -> Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface)
                        here -> Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.surface, CircleShape))
                        // the levels ahead stay empty: a number in them read as a count
                        else -> {}
                    }
                }
                Text(
                    s.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (here) ink else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A card with two sides that turns over in 3D when [back] changes: rotates about its vertical
 * axis and dips a little at the half-way point, where the side shown swaps. It is as tall as
 * [modifier] makes it; each side scrolls inside.
 */
@Composable
private fun FlipCard(
    back: Boolean,
    color: Color,
    modifier: Modifier,
    front: @Composable ColumnScope.() -> Unit,
    backSide: @Composable ColumnScope.() -> Unit,
) {
    val angle by animateFloatAsState(
        if (back) 180f else 0f,
        tween(650, easing = FastOutSlowInEasing),
        label = "flip",
    )
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 16f * density
                val dip = 1f - 0.08f * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
                scaleX = dip; scaleY = dip
            }
            .shadow(2.dp, shape)
            .background(color, shape)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // the far side is drawn mirrored back, so it reads the right way round
                .graphicsLayer { if (angle > 90f) rotationY = 180f }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = if (angle > 90f) backSide else front,
        )
    }
}

/** The rest of a card side, scrolling inside the card. */
@Composable
private fun ColumnScope.Scrolling(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** "自测 ——— ⤢", matching the notes side's 笔记 heading. */
@Composable
private fun SideHeading(label: String, trailing: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(horizontal = 12.dp).weight(1f), color = MaterialTheme.colorScheme.outline)
        trailing()
    }
}

/** ⤢ / ⤡ at the end of a card heading: the card to the whole page, and back. */
@Composable
private fun ExpandButton(expanded: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(
            if (expanded) CloseFullscreenIcon else OpenInFullIcon,
            if (expanded) "退出全屏" else "全屏",
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Turns the card over: an outlined button with the ↻ glyph. */
@Composable
private fun FlipButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp)) {
        Icon(Icons.Default.Refresh, null, Modifier.padding(end = 6.dp))
        Text(label)
    }
}
