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
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.stageOf

/**
 * Everything in 自测中, in the order the 自测 tab asks it — same as stQueue() in template.html:
 * never tried first, then the ones I got wrong or peeked at last time, then the oldest attempt.
 * Entries other than 自测题 have no attempt log, so they count as never tried.
 */
fun testQueue(entries: List<Entry>, stages: Map<String, String>, log: Map<String, List<Attempt>>): List<Entry> =
    entries.filter { stages.stageOf(it) == Stage.Test }.sortedWith(
        compareBy<Entry>({ e ->
            val last = log[e.anchor]?.lastOrNull()
            when { last == null -> 0; last.text.isEmpty() || !last.ok -> 1; else -> 2 }
        }, { e -> log[e.anchor]?.lastOrNull()?.t ?: e.no.toLong() }),
    )

@Composable
fun SelfTestScreen(
    lib: LibraryState,
    session: TestSession?,
    stages: Map<String, String>,
    selfTests: Map<String, List<Attempt>>,
    onStart: () -> Unit,
    onReveal: (String) -> Unit,
    onNext: (mastered: Boolean) -> Unit,
    onEnd: () -> Unit,
    onXref: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = session?.current
    when {
        session != null && current != null -> TestCard(session, current, selfTests[current.anchor].orEmpty(), onReveal, onNext, onEnd, onXref, modifier)
        session != null -> Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("完成 🎉", style = MaterialTheme.typography.headlineSmall)
            Text(
                "这一轮做了 ${session.done} 题" +
                    (if (session.checked > 0) ",自测题答对 ${session.ok} / ${session.checked}" else "") +
                    (if (session.mastered > 0) ",${session.mastered} 条标了已掌握" else "") + "。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onEnd) { Text("返回") }
        }
        else -> {
            val q = testQueue(lib.data?.entries.orEmpty(), stages, selfTests)
            val never = q.count { selfTests[it.anchor].isNullOrEmpty() }
            val missed = q.count { e -> selfTests[e.anchor]?.lastOrNull()?.let { it.text.isEmpty() || !it.ok } == true }
            Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("自测中", q.size, Modifier.weight(1f))
                    Stat("没做过", never, Modifier.weight(1f))
                    Stat("上次没对", missed, Modifier.weight(1f))
                }
                Button(onClick = onStart, enabled = q.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (q.isEmpty()) "还没有自测中的条目" else "开始自测 ${q.size} 题")
                }
                HorizontalDivider()
                Text(
                    "「自测中」的条目都在这里,一题一题做:看中文写英文,再看答案。自测题逐词对照标准答案,其他条目对照笔记自己判断。" +
                        "做熟了点「已掌握」,它就不再出现;在条目里点「移到自测」可以加进来。没做过的和上次没做对的排在前面。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TestCard(
    session: TestSession,
    entry: Entry,
    attempts: List<Attempt>,
    onReveal: (String) -> Unit,
    onNext: (Boolean) -> Unit,
    onEnd: () -> Unit,
    onXref: (String) -> Unit,
    modifier: Modifier,
) {
    val exam = entry.level == "SELFTEST"
    var draft by rememberSaveable(session.index) { mutableStateOf("") }
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
                .verticalScroll(rememberScrollState(), reverseScrolling = false)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // an entry I moved to 自测 is produced (中→英) even if it's only 认识
            ReviewFront(entry, kindOf(entry, inTest = true))
            if (exam) {
                Spacer(Modifier.height(8.dp))
                SelfTestStats(attempts)
            }
            if (!session.revealed) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("不看笔记,写出英文…") },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))
                if (exam) {
                    SelfTestResult(draft, entry.selfTestAnswer)
                    Spacer(Modifier.height(8.dp))
                    SelfTestHistory(attempts)
                    Spacer(Modifier.height(16.dp))
                } else {
                    if (draft.isNotBlank()) {
                        Text("你写的", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            draft.trim(),
                            fontFamily = FontFamily.Serif,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .padding(top = 6.dp, bottom = 8.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                        )
                    }
                    Text("对照下面的笔记,自己判断写得对不对。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                }
                Text(entry.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                Spacer(Modifier.height(12.dp))
                EntryBody(entry, onXref)
                Spacer(Modifier.height(24.dp))
            }
        }
        Box(Modifier.padding(16.dp)) {
            if (!session.revealed) {
                Button(onClick = { onReveal(draft) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("显示答案") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 已掌握: out of 自测 and 复习 until I move it back from the entry page
                    OutlinedButton(onClick = { onNext(true) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("标为已掌握") }
                    Button(onClick = { onNext(false) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(if (session.index + 1 < session.queue.size) "下一题" else "完成")
                    }
                }
            }
        }
    }
}
