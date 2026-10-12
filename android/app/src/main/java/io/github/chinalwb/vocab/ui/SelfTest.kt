package io.github.chinalwb.vocab.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import kotlin.random.Random
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Block
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.data.Example
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.allCorrectSince
import io.github.chinalwb.vocab.review.next
import io.github.chinalwb.vocab.review.summary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A 自测 entry's answer: the quote under **答案**. */
val Entry.selfTestAnswer: String
    get() {
        var want = false
        for (b in blocks) {
            if (b is Block.Para && b.text.startsWith("**答案")) want = true
            else if (b is Block.Quote && want) return b.lines.joinToString("\n")
        }
        return ""
    }

/**
 * Word-level diff of my sentence against the answer — the same rules as diffWords() in
 * template.html: case, punctuation and curly quotes are ignored, the diff is an LCS.
 */
class WordDiff(val mine: List<String>, val answer: List<String>, val keepMine: Set<Int>, val keepAnswer: Set<Int>, val same: Boolean)

private fun norm(w: String) = w.lowercase().replace('’', '\'').replace('‘', '\'').replace(Regex("[^a-z0-9']"), "")

fun diffWords(mine: String, answer: String): WordDiff {
    val a = mine.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val b = answer.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val na = a.map(::norm)
    val nb = b.map(::norm)
    val l = Array(a.size + 1) { IntArray(b.size + 1) }
    for (x in a.indices.reversed()) for (y in b.indices.reversed()) {
        l[x][y] = if (na[x].isNotEmpty() && na[x] == nb[y]) l[x + 1][y + 1] + 1 else maxOf(l[x + 1][y], l[x][y + 1])
    }
    val keepA = mutableSetOf<Int>()
    val keepB = mutableSetOf<Int>()
    var x = 0
    var y = 0
    while (x < a.size && y < b.size) {
        if (na[x].isNotEmpty() && na[x] == nb[y]) { keepA += x++; keepB += y++ }
        else if (l[x + 1][y] >= l[x][y + 1]) x++ else y++
    }
    val same = na.filter { it.isNotEmpty() } == nb.filter { it.isNotEmpty() }
    return WordDiff(a, b, keepA, keepB, same)
}

/**
 * The 答案 quote may hold several acceptable answers, one per line (same as bestDiff() in
 * template.html): any match wins, otherwise diff against the one with the most words in common.
 */
fun bestDiff(mine: String, answer: String): WordDiff {
    val ds = answer.lines().filter { it.isNotBlank() }.map { diffWords(mine, it) }
    return ds.firstOrNull { it.same } ?: ds.maxByOrNull { it.keepAnswer.size } ?: diffWords(mine, "")
}

private fun marked(words: List<String>, keep: Set<Int>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    words.forEachIndexed { i, w ->
        if (i > 0) append(' ')
        if (i in keep) append(w) else withStyle(style) { append(w) }
    }
}

/** The verdict shown after 显示答案: ✅ when the words match, otherwise both lines marked up. */
@Composable
fun SelfTestResult(mine: String, answer: String, onRetry: (() -> Unit)? = null) {
    val dark = isSystemInDarkTheme()
    val err = if (dark) Color(0xFFF08A78) else Color(0xFFC2412D)
    val warn = (if (dark) Color(0xFFE6B35C) else Color(0xFFB7791F)).copy(alpha = 0.3f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val d = bestDiff(mine, answer)
        when {
            mine.isBlank() -> Text("没有写就看答案了 —— 下次先写再看。", style = MaterialTheme.typography.titleSmall)
            d.same -> Text("✅ 和答案一致(大小写、标点不计)", style = MaterialTheme.typography.titleSmall)
            else -> {
                Text("对照一下差异", style = MaterialTheme.typography.titleSmall)
                Line("我写的(删除线 = 多出来或写错的)", marked(d.mine, d.keepMine, SpanStyle(color = err, textDecoration = TextDecoration.LineThrough)))
                Line("答案(高亮 = 我漏掉或写错的)", marked(d.answer, d.keepAnswer, SpanStyle(background = warn)))
                Text("逐词对照只看字面:意思对、换了说法也可能没问题,看看下面的要点。", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        if (onRetry != null) RetryButton(onRetry)
    }
}

/** After a reveal, trying again is the next step, so it's the page's filled button. */
@Composable
fun RetryButton(onClick: () -> Unit) {
    Button(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp)) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Refresh, null, Modifier.padding(end = 6.dp))
        Text("再试一次")
    }
}

@Composable
private fun Line(label: String, text: AnnotatedString) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 24.sp)
    }
}

/**
 * The box I write my answer in. The default outline is too faint on a tinted card — unfocused
 * it vanished, and the card gave no hint of where to type — so the border is ink-coloured and
 * the box sits on a lighter ground.
 */
@Composable
fun DraftField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("不看答案,写出英文…") },
        minLines = 3,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
        shape = RoundedCornerShape(12.dp),
        // ✕ to start over, once there's something to clear
        trailingIcon = if (value.isEmpty()) null else ({
            androidx.compose.material3.IconButton(onClick = { onChange("") }) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close, "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Input + 显示答案, shown on a 自测 entry before its body is revealed. */
@Composable
fun SelfTestInput(value: String, onChange: (String) -> Unit, onReveal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DraftField(value, onChange)
        Button(onClick = onReveal) { Text("显示答案") }
    }
}

/*
 * Entries moved to 自测 are quizzed like 自测题 (same rules as quizEx() in template.html): the
 * Chinese of one of their example sentences is the prompt, the English sentence the answer,
 * compared word by word. Each try takes the next example in turn (attempt count % examples).
 * Entries without a translated example (grammar notes, quick references) I grade myself.
 */
data class QuizExample(val en: String, val zh: String, val k: Int, val of: Int)

private val CJK = Regex("[\\u4e00-\\u9fff]")

/** An example that can be asked: an English sentence with a Chinese translation. */
val Example.quizable: Boolean
    get() = zh.isNotBlank() && Regex("[A-Za-z]").containsMatchIn(en) && CJK.containsMatchIn(zh)

/** The examples a moved entry can be quizzed on, in note order; [excluded] holds the plain English of the ones I opted out. */
fun Entry.quizExamples(excluded: Set<String> = emptySet()): List<Example> =
    blocks.filterIsInstance<Block.Examples>().flatMap { it.items }.filter { it.quizable && plain(it.en) !in excluded }

fun Entry.quizExample(tries: Int, excluded: Set<String> = emptySet()): QuizExample? {
    val xs = quizExamples(excluded)
    if (xs.isEmpty()) return null
    val k = tries % xs.size
    return QuizExample(plain(xs[k].en), plain(xs[k].zh), k, xs.size)
}

/** The prompt side for a moved entry with an example: 例句 k / n · 中译英. */
@Composable
fun ExamplePrompt(ex: QuizExample, /** while the keyboard is up: smaller, no hint */ compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
        Text("例句 ${ex.k + 1} / ${ex.of} · 中译英", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(ex.zh, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
        if (!compact) Text("把这句写成英文,写完和例句原文逐词对照。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The verdict for a moved entry after 显示答案: the word-by-word compare with its example, or —
 * with none to compare against — 我写对了 / 没写对 buttons until I've graded it.
 */
@Composable
fun MovedResult(ex: QuizExample?, draft: String, graded: Boolean?, onGrade: (Boolean) -> Unit) {
    if (ex != null) return SelfTestResult(draft, ex.en)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (draft.isBlank()) {
            Text("没有写就看答案了 —— 下次先写再看。", style = MaterialTheme.typography.titleSmall)
            return@Column
        }
        Line("我写的", AnnotatedString(draft.trim()))
        if (graded == null) {
            Text("这条没有例句可以对照,看下面的笔记自己判断:", style = MaterialTheme.typography.bodySmall, color = muted)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = { onGrade(true) }) { Text("我写对了") }
                androidx.compose.material3.OutlinedButton(onClick = { onGrade(false) }) { Text("没写对") }
            }
        } else Text(if (graded) "已记为 ✅" else "已记为 ❌", style = MaterialTheme.typography.titleSmall)
    }
}

private val WHEN = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())
private fun whenOf(t: Long) = WHEN.format(Instant.ofEpochMilli(t))

/** "已尝试 N 次 · 答对 M 次 · …" — the same line the page shows (stStatsLine). */
fun statsLine(attempts: List<Attempt>): String {
    val s = attempts.summary()
    val last = s.last ?: return "还没自测过"
    return "已尝试 ${s.tries} 次 · 答对 ${s.ok} 次" + (if (s.peeks > 0) " · 偷看 ${s.peeks} 次" else "") +
        " · 上次 ${whenOf(last.t)} " + when { last.text.isEmpty() -> "偷看"; last.ok -> "✅"; else -> "❌" }
}

/** Short form for cards: "试 2 · 对 1" / "未自测". */
fun countsShort(attempts: List<Attempt>): String =
    attempts.summary().let { if (it.tries == 0) "未自测" else "试 ${it.tries} · 对 ${it.ok}" }

@Composable
fun SelfTestStats(attempts: List<Attempt>) {
    Text(statsLine(attempts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Newest first, collapsed until tapped. */
@Composable
fun SelfTestHistory(attempts: List<Attempt>) {
    if (attempts.isEmpty()) return
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text((if (open) "▾ " else "▸ ") + "历史记录(${attempts.size})")
        }
        if (open) attempts.asReversed().forEach { a ->
            androidx.compose.foundation.layout.Row {
                Text(whenOf(a.t), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp, end = 8.dp))
                Text(when { a.text.isEmpty() -> "👀"; a.ok -> "✅"; else -> "❌" }, modifier = Modifier.padding(end = 8.dp))
                Text(
                    (if (a.text.isEmpty()) "(没写就看了答案)" else a.text) + when (a.via) { "review" -> " · 复习"; "test", "selftest" -> " · 考核"; else -> "" },
                    fontFamily = FontFamily.Serif, fontSize = 15.sp,
                )
            }
        }
    }
}

/*
 * 选词 — the quick way to answer, after Duolingo: the answer's words, shuffled with a few strays,
 * are chips; tapping them in order builds the sentence, which 显示答案 then checks exactly like
 * typed text. For something I already know, this beats typing it out on a phone. 打字 stays a
 * toggle away, remembered.
 */

private val EDGE_PUNCT = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")

/** A sentence as chips: punctuation off the ends of each word, pure punctuation dropped. */
fun chipWords(sentence: String): List<String> =
    sentence.split(Regex("\\s+")).map { it.replace(EDGE_PUNCT, "") }.filter { it.isNotEmpty() }

private val STRAY_POOL = listOf("the", "a", "an", "to", "of", "in", "on", "at", "for", "with", "is", "are", "was", "has", "that", "it", "and", "but")

/** A few words that aren't in [answer]: from [others] (the entry's other sentences) first, then stock function words. */
fun strayWords(answer: String, others: List<String>, seed: Int, n: Int = 3): List<String> {
    val have = chipWords(answer).map { it.lowercase() }.toSet()
    val own = others.flatMap { chipWords(it) }.filter { it.lowercase() !in have }.distinctBy { it.lowercase() }.shuffled(Random(seed))
    val stock = STRAY_POOL.filter { it !in have }.shuffled(Random(seed))
    return (own + stock).distinctBy { it.lowercase() }.take(n)
}

/** 选词 or 打字, remembered across launches (SharedPreferences "ui"/"chips"). */
@Composable
fun rememberChipsMode(): Pair<Boolean, (Boolean) -> Unit> {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var chips by remember { mutableStateOf(prefs.getBoolean("chips", true)) }
    return chips to { v: Boolean -> chips = v; prefs.edit().putBoolean("chips", v).apply() }
}

/** The 选词 | 打字 switch, a small pill at the end of its row. */
@Composable
fun AnswerModeToggle(chips: Boolean, onChips: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(true to "选词", false to "打字").forEach { (v, label) ->
            val on = v == chips
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(shape)
                    .then(if (on) Modifier.background(MaterialTheme.colorScheme.onSurface) else Modifier)
                    .clickable { onChips(v) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * The chips: [answer]'s words plus [strays], shuffled by [seed] (fixed for one try). [picked] are
 * bank indices in order; [draft] is the sentence they make, which 显示答案 checks like typed text.
 */
class WordBankState(val bank: List<String>, picked: String) {
    var pickedText by mutableStateOf(picked)
    val picked: List<Int> get() = pickedText.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
    val draft: String get() = picked.joinToString(" ") { bank[it] }
    fun pick(i: Int) { pickedText = (picked + i).joinToString(",") }
    fun unpick(i: Int) { pickedText = (picked - i).joinToString(",") }
    fun clear() { pickedText = "" }
}

@Composable
fun rememberWordBank(answer: String, strays: List<String>, seed: Int): WordBankState {
    val bank = remember(answer, strays, seed) { (chipWords(answer) + strays).shuffled(Random(seed)) }
    // the picks are saved as text so a rotation keeps them
    var pickedText by rememberSaveable(answer, seed) { mutableStateOf("") }
    val st = remember(bank) { WordBankState(bank, pickedText) }
    LaunchedEffect(st.pickedText) { pickedText = st.pickedText }
    return st
}

/**
 * The sentence being built, in a box styled like the text field; a tap on a chip puts it back,
 * 清空 (in the corner, once there's something) puts them all back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordBankAnswer(st: WordBankState) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val picked = st.picked
    Box(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .border(1.5.dp, muted, RoundedCornerShape(12.dp)),
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = if (picked.isEmpty()) 10.dp else 56.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (picked.isEmpty()) Text("点下面的词,拼出英文…", color = muted, modifier = Modifier.padding(4.dp))
            picked.forEach { i ->
                Text(
                    st.bank[i],
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurface)
                        .clickable { st.unpick(i) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        if (picked.isNotEmpty()) Text(
            "清空",
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(8.dp))
                .clickable { st.clear() }
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

/** The bank, kept near the thumb: a used chip leaves an empty slot, so the rest don't jump about. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordBankChips(st: WordBankState) {
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(10.dp)
    val picked = st.picked
    FlowRow(
        Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        st.bank.forEachIndexed { i, w ->
            val used = i in picked
            Box(
                Modifier
                    .clip(shape)
                    .then(if (used) Modifier.background(ink.copy(alpha = 0.08f)) else Modifier.background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, shape))
                    .clickable(enabled = !used) { st.pick(i) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) { Text(w, fontFamily = FontFamily.Serif, fontSize = 16.sp, modifier = Modifier.alpha(if (used) 0f else 1f)) }
        }
    }
}

/*
 * Moving on: once every question of an entry has been answered right since it entered its
 * stage — each example still in 自测, or a 自测题's one answer — the app offers the next stage.
 * Entries with nothing to check against (graded by me) move only by hand.
 */
fun advanceReady(entry: Entry, stage: Stage, attempts: List<Attempt>, since: Long, excluded: Set<String>): Boolean {
    if (stage.next == null) return false
    val questions = if (entry.level == "SELFTEST") listOf("") else entry.quizExamples(excluded).map { plain(it.en) }
    return attempts.allCorrectSince(since, questions)
}

/** "例句都答对过了 → 移到复习": the offer, with the move as its button. */
@Composable
fun AdvanceBanner(stage: Stage, onAdvance: () -> Unit) {
    val next = stage.next ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("这一阶段的题都答对过了", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdvance) { Text("移到${next.tab}") }
    }
}
