package io.github.chinalwb.vocab.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.chinalwb.vocab.data.LibraryState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 练习: a daily imitation task and a weekly paragraph, both sent to Claude for marking.
 * Picks the same tasks as renderPractice() in template.html: the pool is the 会写 entries
 * that aren't 句子 or 自测, sorted by anchor; day = epoch day, week = floor((day + 3) / 7).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(lib: LibraryState, onOpen: (String) -> Unit, modifier: Modifier = Modifier, embedded: Boolean = false) {
    val data = lib.data
    if (data == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val pool = remember(data) { data.entries.filter { it.writes && it.level != "SENTENCE" && it.level != "SELFTEST" }.sortedBy { it.anchor } }
    if (pool.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text("还没有标「会写」的条目。") }
        return
    }
    val prefs = LocalContext.current.getSharedPreferences("practice", Context.MODE_PRIVATE)
    val clipboard = LocalClipboardManager.current
    val today = LocalDate.now()
    val day = today.toEpochDay()
    val week = Math.floorDiv(day + 3, 7L)
    var skip by rememberSaveable { mutableIntStateOf(0) }
    val daily = pool[((day + skip) % pool.size).toInt()]
    val weekly = (0 until 5).map { pool[((week * 5 + it) % pool.size).toInt()] }.distinct()
    val dateStr = today.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
    val verb = if (daily.level == "GRAMMAR") "按" else "用"

    Column(
        // embedded in the 复习 overview, which already scrolls
        if (embedded) modifier.fillMaxWidth()
        else modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "写完点「复制」,发到和 Claude 的对话里,按 ❌ / ⚠️ / ✅ 批改。草稿存在手机上。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val dKey = "d.$day.${daily.anchor}"
        var dText by remember(dKey) { mutableStateOf(prefs.getString(dKey, "").orEmpty()) }
        Task("今日仿写", "$dateStr · 每天一题,从「会写」的条目里轮换") {
            Text(
                "$verb「${daily.title}」" + (if (verb == "按") "这条规则" else "里的表达") + ",写 3 句关于你自己工作或生活的真实句子。",
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = { onOpen(daily.anchor) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("打开这条笔记")
            }
            Draft(dText, "1. …\n2. …\n3. …") { dText = it; prefs.edit().putString(dKey, it).apply() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { skip++ }) { Text("换一题") }
                Box(Modifier.weight(1f))
                CopyButton {
                    clipboard.setText(AnnotatedString("【今日仿写 $dateStr】$verb「${daily.title}」写 3 句真实的句子,请按 ❌ / ⚠️ / ✅ 批改:\n\n${dText.trim()}"))
                }
            }
        }

        val wKey = "w.$week"
        var wText by remember(wKey) { mutableStateOf(prefs.getString(wKey, "").orEmpty()) }
        Task("本周段落", "每周一换 · 80–100 词") {
            Text("写一段 80–100 词的短文(工作近况、这周学到的东西都行),至少用上其中 3 个:", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weekly.forEach { e -> AssistChip(onClick = { onOpen(e.anchor) }, label = { Text(e.title) }) }
            }
            Draft(wText, "…", minLines = 6) { wText = it; prefs.edit().putString(wKey, it).apply() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val n = words(wText)
                Text(
                    "$n 词" + if (n in 80..100) " ✓" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                CopyButton {
                    clipboard.setText(AnnotatedString(
                        "【本周段落】目标表达:${weekly.joinToString(" · ") { it.title }}。请按 ❌ / ⚠️ / ✅ 批改,并说说段落衔接:\n\n${wText.trim()}"
                    ))
                }
            }
        }
    }
}

private fun words(t: String) = Regex("[A-Za-z]+(?:['’-][A-Za-z]+)*").findAll(t).count()

@Composable
private fun Task(title: String, meta: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun Draft(value: String, placeholder: String, minLines: Int = 3, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        minLines = minLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CopyButton(copy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    Button(onClick = { copy(); copied = true }) { Text(if (copied) "已复制 ✓" else "复制去批改") }
}
