package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.data.LibraryState
import io.github.chinalwb.vocab.review.Grade
import io.github.chinalwb.vocab.review.ReviewData
import io.github.chinalwb.vocab.review.planToday
import io.github.chinalwb.vocab.review.today

@Composable
fun ReviewScreen(
    lib: LibraryState,
    review: ReviewData,
    session: Session?,
    onStart: () -> Unit,
    onReveal: () -> Unit,
    onGrade: (Grade) -> Unit,
    onEnd: () -> Unit,
    onReset: () -> Unit,
    onXref: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = session?.current
    when {
        session != null && current != null -> Card(session, current, onReveal, onGrade, onEnd, onXref, modifier)
        session != null -> Finished(session.done, onEnd, modifier)
        else -> Overview(lib, review, onStart, onReset, modifier)
    }
}

@Composable
private fun Overview(lib: LibraryState, review: ReviewData, onStart: () -> Unit, onReset: () -> Unit, modifier: Modifier) {
    val entries = lib.data?.entries.orEmpty()
    val plan = planToday(entries, review)
    val learned = entries.count { it.anchor in review.cards }
    val today = today()
    val tomorrow = entries.count { e -> review.cards[e.anchor]?.due == today + 1 }
    var confirmReset by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("今天", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("待复习", plan.due.size, Modifier.weight(1f))
            Stat("新条目", plan.fresh.size, Modifier.weight(1f))
        }
        Button(onClick = onStart, enabled = plan.all.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (plan.all.isEmpty()) "今天没有要复习的了" else "开始复习 ${plan.all.size} 条")
        }
        HorizontalDivider()
        Text(
            "已学 $learned / 共 ${entries.size} 条 · 明天到期 $tomorrow 条\n" +
                "「会写」的条目反着考:先看中文或原句,自己写出英文再对答案;「认识」的条目看英文回想意思。" +
                "每天最多引入 10 条新条目,会写的优先。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.align(Alignment.End)) { Text("重置复习进度") }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("重置复习进度?") },
        text = { Text("所有条目的复习记录都会清空,从头开始。词库本身不受影响。") },
        confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }) { Text("重置") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
    )
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("$value", fontSize = 34.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Card(
    session: Session,
    entry: Entry,
    onReveal: () -> Unit,
    onGrade: (Grade) -> Unit,
    onEnd: () -> Unit,
    onXref: (String) -> Unit,
    modifier: Modifier,
) {
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
            val kind = kindOf(entry)
            // scratch space for the 中→英 / 先改后看 cards; never saved
            var draft by remember(session.index) { mutableStateOf("") }
            Front(entry, kind)
            if (kind.writes && !session.revealed) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("在这里写你的英文(可选,不会保存)") },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (session.revealed) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))
                if (kind == Kind.SelfTest && draft.isNotBlank()) {
                    SelfTestResult(draft, entry.selfTestAnswer)
                    Spacer(Modifier.height(16.dp))
                } else if (kind.writes && draft.isNotBlank()) {
                    Text("你写的", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        draft.trim(),
                        fontFamily = FontFamily.Serif,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .padding(top = 6.dp, bottom = 16.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    )
                }
                if (kind != Kind.Recognize) {
                    Text(entry.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Spacer(Modifier.height(12.dp))
                }
                EntryBody(entry, onXref)
                Spacer(Modifier.height(24.dp))
            }
        }
        Box(Modifier.padding(16.dp)) {
            if (!session.revealed) {
                Button(onClick = onReveal, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("显示答案") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Grade.entries.forEach { g ->
                        OutlinedButton(onClick = { onGrade(g) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(g.label) }
                    }
                }
            }
        }
    }
}

/** What a card asks — the same rules as kind() in template.html. */
enum class Kind(val writes: Boolean) { SelfTest(true), Sentence(true), Translate(true), Grammar(false), Produce(true), Recognize(false) }

fun kindOf(e: Entry): Kind = when {
    e.level == "SELFTEST" -> Kind.SelfTest   // title is the Chinese prompt, the quote is the answer
    e.level == "SENTENCE" && e.originalSentence != null -> Kind.Sentence
    e.title.startsWith("翻译") -> Kind.Translate
    e.level == "GRAMMAR" -> Kind.Grammar
    e.writes -> Kind.Produce       // 会写: 中→英
    else -> Kind.Recognize         // 认识: 英→中
}

/** A 中→英 prompt must not give the answer away: blank out the headword's English words. */
fun maskTitle(text: String, title: String): String =
    Regex("[A-Za-z]{3,}").findAll(title).map { it.value.lowercase() }.distinct()
        .fold(text) { t, w -> t.replace(Regex("\\b$w\\w*", RegexOption.IGNORE_CASE), "＿＿") }

/** The prompt side of a card. */
@Composable
private fun Front(entry: Entry, kind: Kind) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Chip(levelStyle(entry.level).short, levelColor(entry.level))
        val label: @Composable (String) -> Unit = {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val prompt: @Composable (String) -> Unit = { Text(it, style = MaterialTheme.typography.titleMedium, lineHeight = 26.sp) }
        when (kind) {
            Kind.SelfTest -> {
                label("自测 · 中译英")
                prompt(entry.title.replace(Regex("^自测[::]\\s*"), ""))
                Hint("不看笔记,直接写出英文。")
            }
            Kind.Sentence -> {
                label("我的原句")
                Quote(entry.originalSentence.orEmpty().lines(), MaterialTheme.colorScheme.primary) {}
                Hint("哪里不对、哪里不地道?先自己改一遍,再看答案。")
            }
            Kind.Translate -> {
                label("中译英")
                prompt(plain(entry.gloss).replace(Regex("^中译英练习[——-]*"), ""))
                Hint("先写出你的英文,再和几种译法对照。")
            }
            Kind.Produce -> {
                label("写出英文" + if (entry.pos.isNotEmpty()) " · ${entry.pos}" else "")
                prompt(maskTitle(plain(entry.gloss), entry.title))
                Hint("想出这个英文表达,再用它造一个句子。")
            }
            Kind.Grammar -> {
                Text(entry.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp)
                Hint("回想这条规则,以及正反例句。")
            }
            Kind.Recognize -> {
                Text(entry.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp)
                if (entry.ipa.isNotEmpty()) Text(entry.ipa, style = MaterialTheme.typography.bodyLarge)
                Hint("它是什么意思?什么语感?")
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Finished(done: Int, onEnd: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("完成 🎉", fontSize = 30.sp, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(8.dp))
        Text("这一轮复习了 $done 次", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onEnd) { Text("返回") }
    }
}
