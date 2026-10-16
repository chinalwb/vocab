package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chinalwb.vocab.data.LibraryState
import io.github.chinalwb.vocab.review.HISTORY_MAX
import io.github.chinalwb.vocab.review.HistoryData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val VIA = mapOf("entry" to "打开", "review" to "复习", "test" to "考核")

/** 今天 / 昨天 / 10 月 14 日 · 周二 — the heading over a day's rows, same as histDay() in template.html. */
private fun dayLabel(t: Long): String {
    val d = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (ChronoUnit.DAYS.between(d, LocalDate.now())) {
        0L -> "今天"
        1L -> "昨天"
        else -> "${d.monthValue} 月 ${d.dayOfMonth} 日 · 周${"一二三四五六日"[d.dayOfWeek.value - 1]}"
    }
}

private fun hhmm(t: Long): String {
    val z = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
    return "%02d:%02d".format(z.hour, z.minute)
}

/**
 * 学习历史 (⋮ menu, 用户 2026-10-16): the last [HISTORY_MAX] entries I've been on, newest first,
 * under a heading per day; each row says when and where (打开 / 复习 / 考核). 下一个 on the entry
 * page walks this list. Same as renderHistory() in template.html.
 */
@Composable
fun HistoryScreen(lib: LibraryState, history: HistoryData, onOpen: (String, List<String>) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val data = lib.data
    if (data == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // an entry gone from the notes drops out
    val rows = remember(history, data) { history.visits.filter { it.a in data.byAnchor } }
    val days = remember(rows) { rows.groupBy { dayLabel(it.t) }.toList() }
    val order = rows.map { it.a }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                (if (rows.isEmpty()) "还没有看过的条目。" else "最近看过的 ${rows.size} 条,最新的在最上面。") +
                    "打开一条、或在复习 / 考核里做到它,都会记在这里;同一条再看会移到最上面,最多留 $HISTORY_MAX 条。开了同步的话,网页和 App 共用这份历史。",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        days.forEach { (day, list) ->
            item(key = "h:$day") {
                Text(
                    "$day · ${list.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = muted,
                    modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
                )
            }
            items(list, key = { it.a }) { v ->
                val e = data.byAnchor.getValue(v.a)
                val gloss = if (e.level == "SELFTEST") "先说出英文,再点开对答案" else cardGloss(e.gloss)
                StageRow(e, listOf("${hhmm(v.t)} ${VIA[v.via] ?: "打开"}", gloss).filter { it.isNotEmpty() }.joinToString(" · ")) { onOpen(v.a, order) }
            }
        }
        if (rows.isNotEmpty()) item {
            TextButton(onClick = { confirm = true }, modifier = Modifier.padding(top = 8.dp)) { Text("清空学习历史") }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("清空学习历史?") },
        text = { Text("开了同步的话,其他设备同步后也会清空。") },
        confirmButton = { TextButton(onClick = { confirm = false; onClear() }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
    )
}
