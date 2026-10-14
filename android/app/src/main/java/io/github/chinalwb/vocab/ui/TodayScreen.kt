package io.github.chinalwb.vocab.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.chinalwb.vocab.review.PracticeDay
import io.github.chinalwb.vocab.review.PracticeItem
import io.github.chinalwb.vocab.review.savePdfToDownloads
import io.github.chinalwb.vocab.review.writePracticePdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 今天的练习 (⋮ menu, 用户 2026-10-14): every sentence I answered today — on an entry's 自测, in
 * 复习 or in 考核 — as a pair, what I wrote and the answer, the last try only. 导出 PDF writes the
 * three-part file (答案 / 对照 / 只有中文) and hands it to the share sheet: save, print or send.
 */
@Composable
fun TodayScreen(day: PracticeDay, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val items = day.items
    val ok = items.count { it.mine.isNotBlank() && it.ok }
    // 保存到「下载」 or 分享…: either way the PDF is written fresh from today's record
    val export: (save: Boolean) -> Unit = { save ->
        busy = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = writePracticePdf(ctx, day)
                    if (save && android.os.Build.VERSION.SDK_INT >= 29) file to savePdfToDownloads(ctx, file) else file to null
                }
            }
            busy = false
            result.onSuccess { (file, saved) ->
                if (saved != null) {
                    android.widget.Toast.makeText(ctx, "已保存到「下载」:$saved", android.widget.Toast.LENGTH_LONG).show()
                    return@onSuccess
                }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "VoCab 练习 · ${day.date}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(send, "导出今天的练习"))
            }.onFailure {
                android.widget.Toast.makeText(ctx, "导出失败:${it.message ?: it.javaClass.simpleName}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${day.date}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (items.isEmpty()) "还没有练习" else "共 ${items.size} 句 · 答对 $ok 句",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.foundation.layout.Box {
                        Button(onClick = { menu = true }, enabled = items.isNotEmpty() && !busy, modifier = Modifier.height(48.dp)) {
                            Icon(PdfIcon, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (busy) "正在导出…" else "导出 PDF", fontWeight = FontWeight.Bold)
                        }
                        androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                            if (android.os.Build.VERSION.SDK_INT >= 29) androidx.compose.material3.DropdownMenuItem(
                                text = { Text("保存到「下载」") },
                                onClick = { menu = false; export(true) },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("分享…(打印、发送、存到别的 App)") },
                                onClick = { menu = false; export(false) },
                            )
                        }
                    }
                }
                Text(
                    if (items.isEmpty()) "在条目页自测、复习或考核里答题,答过的句子就会记在这里:我写的和答案一对一对,同一句只留最后一次。"
                    else "PDF 里有三份:答案(中英)、对照(我写的和答案)、只有中文(留了横线,可以再练一遍)。同一句今天练了几次,只留最后一次。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(items.asReversed(), key = { _, it -> it.key }) { i, it -> PracticeRow(items.size - i, it) { onOpen(it.anchor) } }
    }
}

/** One pair: the Chinese, what I wrote (with the verdict), the answer, and where it came from. */
@Composable
private fun PracticeRow(no: Int, item: PracticeItem, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val c = sheetColors()
    val verdict = when { item.mine.isBlank() -> "没写"; item.ok -> "答对"; else -> "没对" }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("$no. ${item.zh}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = muted)) { append("我写的 · ") }
                withStyle(SpanStyle(color = if (verdict == "答对") muted else c.err, fontWeight = FontWeight.Bold)) { append(verdict) }
                append("\n")
                append(if (item.mine.isBlank()) "(没写就看了答案)" else item.mine)
            },
            fontFamily = AppFont, fontSize = 16.sp, lineHeight = 23.sp,
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = muted)) { append("答案\n") }
                append(item.answer)
            },
            fontFamily = AppFont, fontSize = 16.sp, lineHeight = 23.sp,
        )
        Text(
            listOf(item.title, mapOf("entry" to "条目页", "review" to "复习", "test" to "考核")[item.via] ?: item.via, item.time)
                .filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = muted,
        )
    }
}
