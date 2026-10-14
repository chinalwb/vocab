package io.github.chinalwb.vocab.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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

@Composable
/** A labelled line of the verdict; [say] puts a 🔊 at its end that reads that text. */
private fun Line(label: String, text: AnnotatedString, say: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, fontFamily = AppFont, fontSize = 17.sp, lineHeight = 24.sp)
        }
        if (say != null) SpeakButton(say)
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

/**
 * The verdict for an entry with no sentence to compare against (example sentences and 自测题 get
 * RevealVerdict): what I wrote, then 我写对了 / 没写对 until I've graded it.
 */
@Composable
fun MovedResult(draft: String, graded: Boolean?, onGrade: (Boolean) -> Unit) {
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

/** Only the attempts at [ex] (an example question shows its own); a 自测题 keeps them all. */
fun List<Attempt>.at(ex: QuizExample?): List<Attempt> = if (ex == null) this else filter { it.ex == ex.en }

/** "已尝试 N 次 · 答对 M 次 · …" — the same line the page shows (stStatsLine); [one] = one sentence's. */
fun statsLine(attempts: List<Attempt>, one: Boolean = false): String {
    val s = attempts.summary()
    val pre = if (one) "这一句:" else ""
    val last = s.last ?: return pre + "还没自测过"
    return pre + "已尝试 ${s.tries} 次 · 答对 ${s.ok} 次" + (if (s.peeks > 0) " · 偷看 ${s.peeks} 次" else "") +
        " · 上次 ${whenOf(last.t)} " + when { last.text.isEmpty() -> "偷看"; last.ok -> "✅"; else -> "❌" }
}

/** Short form for cards: "试 2 · 对 1" / "未自测". */
fun countsShort(attempts: List<Attempt>): String =
    attempts.summary().let { if (it.tries == 0) "未自测" else "试 ${it.tries} · 对 ${it.ok}" }

/** Newest first, collapsed until tapped. */
@Composable
fun SelfTestHistory(attempts: List<Attempt>, one: Boolean = false) {
    if (attempts.isEmpty()) return
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text((if (open) "▾ " else "▸ ") + (if (one) "这一句的" else "") + "历史记录(${attempts.size})")
        }
        if (open) attempts.asReversed().forEach { a ->
            androidx.compose.foundation.layout.Row {
                Text(whenOf(a.t), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp, end = 8.dp))
                Text(when { a.text.isEmpty() -> "👀"; a.ok -> "✅"; else -> "❌" }, modifier = Modifier.padding(end = 8.dp))
                Text(
                    (if (a.text.isEmpty()) "(没写就看了答案)" else a.text) + when (a.via) { "review" -> " · 复习"; "test", "selftest" -> " · 考核"; else -> "" },
                    fontFamily = AppFont, fontSize = 15.sp,
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

private val LEAD_PUNCT = Regex("^[^\\p{L}\\p{N}]+")
private val EDGE_PUNCT = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")

/**
 * A sentence as chips: a word keeps the punctuation after it ("view." "However,", 用户 2026-10-14),
 * loses any before it (opening quotes, brackets); pure punctuation is dropped.
 */
fun chipWords(sentence: String): List<String> =
    sentence.split(Regex("\\s+")).map { it.replace(LEAD_PUNCT, "") }.filter { it.isNotEmpty() }

/** A chip without its punctuation: what strays and duplicates are compared by. */
fun bareWord(w: String): String = w.replace(EDGE_PUNCT, "").lowercase()

private val STRAY_POOL = listOf("the", "a", "an", "to", "of", "in", "on", "at", "for", "with", "is", "are", "was", "has", "that", "it", "and", "but")

/** A few words that aren't in [answer]: from [others] (the entry's other sentences) first, then stock function words. */
fun strayWords(answer: String, others: List<String>, seed: Int, n: Int = 6): List<String> {
    val have = chipWords(answer).map(::bareWord).toSet()
    // strays go in bare, so a "." can't mark them as a sentence end
    val own = others.flatMap { chipWords(it) }.map { it.replace(EDGE_PUNCT, "") }.filter { bareWord(it) !in have }.distinctBy(::bareWord).shuffled(Random(seed))
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
fun AnswerModeToggle(chips: Boolean, onChips: (Boolean) -> Unit, labels: Pair<String, String> = "选词" to "打字") {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(true to labels.first, false to labels.second).forEach { (v, label) ->
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
 * 选词, four at a time (用户 2026-10-13): after k picks, the chips are the answer's word k+1, two
 * words from later in the answer (so the order is what's tested, not recognising words) and a
 * stray, shuffled. A wrong pick still moves on — 显示答案's word diff shows where it went wrong.
 * Only the last word can be taken back. [pickedText] is the words so far, space-separated (chip
 * words never hold spaces), and is the draft 显示答案 checks like typed text.
 */
class WordBankState(val words: List<String>, private val strays: List<String>, private val seed: Int, picked: String) {
    var pickedText by mutableStateOf(picked)
    val picked: List<String> get() = pickedText.split(" ").filter { it.isNotEmpty() }
    val draft: String get() = pickedText
    val done: Boolean get() = picked.size >= words.size
    val options: List<String> get() = optionsAt(picked.size)
    fun pick(w: String) { pickedText = (picked + w).joinToString(" ") }
    fun undo() { pickedText = picked.dropLast(1).joinToString(" ") }
    fun clear() { pickedText = "" }

    private fun optionsAt(k: Int): List<String> {
        if (k >= words.size) return emptyList()
        val rnd = Random(seed * 31 + k)
        val right = words[k]
        val seen = mutableSetOf(bareWord(right))
        val later = words.drop(k + 1).shuffled(rnd).filter { seen.add(bareWord(it)) }.take(2)
        val fill = strays.shuffled(rnd).filter { seen.add(bareWord(it)) }.take(CHIPS - 1 - later.size)
        return (listOf(right) + later + fill).shuffled(rnd)
    }

    companion object { const val CHIPS = 4 }
}

@Composable
fun rememberWordBank(answer: String, strays: List<String>, seed: Int): WordBankState {
    val words = remember(answer) { chipWords(answer) }
    // the picks are saved as text so a rotation keeps them
    var pickedText by rememberSaveable(answer, seed) { mutableStateOf("") }
    val st = remember(words, strays, seed) { WordBankState(words, strays, seed, pickedText) }
    LaunchedEffect(st.pickedText) { pickedText = st.pickedText }
    return st
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

/**
 * How many questions are still to be answered right before the next stage is offered — the
 * progress panel's hint. Null when there's no next stage or nothing to check against.
 */
fun remainingToAdvance(entry: Entry, stage: Stage, attempts: List<Attempt>, since: Long, excluded: Set<String>): Int? {
    if (stage.next == null) return null
    val questions = if (entry.level == "SELFTEST") listOf("") else entry.quizExamples(excluded).map { plain(it.en) }
    if (questions.isEmpty()) return null
    return questions.count { q -> attempts.none { it.t >= since && it.ok && it.ex == q } }
}
