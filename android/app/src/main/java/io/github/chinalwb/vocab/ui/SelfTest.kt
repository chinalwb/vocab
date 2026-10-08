package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.chinalwb.vocab.review.Attempt
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
        val d = diffWords(mine, answer)
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
        if (onRetry != null) TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("再试一次") }
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

/** Input + 显示答案, shown on a 自测 entry before its body is revealed. */
@Composable
fun SelfTestInput(value: String, onChange: (String) -> Unit, onReveal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text("不看答案,写出英文…") },
            minLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onReveal) { Text("显示答案") }
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
                    (if (a.text.isEmpty()) "(没写就看了答案)" else a.text) + if (a.via == "review") " · 复习" else "",
                    fontFamily = FontFamily.Serif, fontSize = 15.sp,
                )
            }
        }
    }
}
