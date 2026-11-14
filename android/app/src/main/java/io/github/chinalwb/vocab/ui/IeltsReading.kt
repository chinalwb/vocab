package io.github.chinalwb.vocab.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.chinalwb.vocab.data.Block
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.data.Section

/** Rows remain ordinary Markdown tables for older clients and exported notes. */
internal data class IeltsExpression(
    val term: String, val priority: String, val meaning: String, val original: String = "",
    val example: String = "", val prompt: String = "", val contrast: String = "", val reference: String = "",
)
internal fun ieltsExpressions(entry: Entry): List<IeltsExpression> {
    val blocks = entry.sections.firstOrNull { it.label == "表达卡片" }?.blocks.orEmpty()
    val table = blocks.filterIsInstance<Block.Table>().firstOrNull { it.head.firstOrNull() == "表达" }
    if (table != null) return table.rows.mapNotNull { row ->
        val c = row + List(8) { "" }
        if (c[0].isBlank()) null else IeltsExpression(plain(c[0]), c[1], plain(c[2]), plain(c[3]),
            plain(c[4]), plain(c[5]), plain(c[6]), Regex("""\]\(#([^)]+)\)""").find(c[7])?.groupValues?.get(1).orEmpty())
    }
    return blocks.filterIsInstance<Block.Bullets>().flatMap { it.items }.mapNotNull { text ->
        val m = Regex("""^\*\*(.+?)\*\*\s*[—:：-]*\s*(.*)$""").find(text) ?: return@mapNotNull null
        IeltsExpression(m.groupValues[1], "理解并积累", plain(m.groupValues[2]))
    }
}

/** Optional recall is deliberately separate from attempts, quiz grading and stage movement. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IeltsSection(
    entry: Entry, section: Section, index: Int, visit: Int, onIndex: (Int) -> Unit,
    onWord: (Int) -> Unit, onXref: (String) -> Unit,
) {
    val cards = remember(entry) { ieltsExpressions(entry) }
    val blocks = section.blocks.dropWhile { (it as? Block.Para)?.text == "**${section.label}**" }
    if (section.label == "原文") {
        val color = MaterialTheme.colorScheme.primary
        blocks.forEach { block ->
            if (block is Block.Quote && cards.isNotEmpty()) {
                val text = block.lines.joinToString("\n")
                val terms = cards.mapIndexedNotNull { i, c ->
                    val term = c.term.replace(Regex("""\s+(sth|sb)\b.*$"""), "").trimEnd('…', '.').trim()
                    if (term.isBlank()) null else term to i
                }.sortedByDescending { it.first.length }
                val pattern = Regex("\\b(" + terms.joinToString("|") { Regex.escape(it.first) } + ")\\b", RegexOption.IGNORE_CASE)
                Text(buildAnnotatedString {
                    var last = 0
                    pattern.findAll(text).forEach { m ->
                        append(text.substring(last, m.range.first))
                        val target = terms.first { it.first.equals(m.value, ignoreCase = true) }.second
                        withLink(LinkAnnotation.Clickable("ielts-$target", TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))) { onWord(target) }) { append(m.value) }
                        last = m.range.last + 1
                    }
                    append(text.substring(last))
                }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 10.dp))
            } else if (block is Block.Para) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .08f)) {
                    Text(plain(block.text), Modifier.padding(14.dp))
                }
            } else NoteBlocks(entry.anchor, listOf(block), onXref)
        }
        return
    }
    if (section.label != "表达卡片") { NoteBlocks(entry.anchor, blocks, onXref); return }
    val prefs = LocalContext.current.getSharedPreferences("ielts-expressions", Context.MODE_PRIVATE)
    var selectedVersion by remember { mutableStateOf(0) }
    var filter by rememberSaveable(entry.anchor, visit) { mutableStateOf("全部表达") }
    var recall by rememberSaveable(entry.anchor, visit) { mutableStateOf(false) }
    var revealed by rememberSaveable(entry.anchor, index, recall, filter) { mutableStateOf(false) }
    var draft by rememberSaveable(entry.anchor, index, recall, filter) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val selected: (IeltsExpression) -> Boolean = { prefs.getBoolean("${entry.anchor}:${it.term}", false) }
    val visible = remember(cards, filter, selectedVersion) {
        cards.withIndex().filter { filter == "全部表达" || if (filter == "我选的表达") selected(it.value) else it.value.priority == filter }
    }
    // An original-text tap always opens the requested card, even after filtering the deck.
    LaunchedEffect(index) { if (visible.none { it.index == index }) filter = "全部表达" }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("全部表达", "重点会用", "理解并积累", "按需查看", "我选的表达").forEach { label ->
            FilterChip(selected = filter == label, onClick = { filter = label; revealed = false; draft = "" }, label = { Text(label) })
        }
    }
    FilterChip(selected = recall, onClick = { recall = !recall }, label = { Text("回忆模式") })
    val at = visible.indexOfFirst { it.index == index }.coerceAtLeast(0)
    val current = visible.getOrNull(at)
    if (current == null) { Text("这里还没有表达，可以切换学习范围，或选择想学的表达。"); return }
    val c = current.value
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = .35f), RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(c.priority, style = MaterialTheme.typography.labelMedium)
        if (recall && !revealed) {
            Text("试着回忆英文表达", style = MaterialTheme.typography.titleLarge)
            Text(c.prompt.ifBlank { c.meaning })
            OutlinedTextField(draft, { draft = it }, label = { Text("写下你记得的英文") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { revealed = true }) { Text("揭晓表达与例句") }
        } else {
            Row { Text(c.term, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); SpeakButton(c.term) }
            Text(c.meaning)
            if (c.original.isNotBlank()) { Text("原文中的用法", style = MaterialTheme.typography.labelMedium); Text(c.original) }
            if (c.example.isNotBlank()) {
                Text("新语境 · 自拟例句", style = MaterialTheme.typography.labelMedium)
                Row { Text(c.example, Modifier.weight(1f)); SpeakButton(c.example) }
                Text(c.prompt)
            }
            if (c.contrast.isNotBlank()) Text(c.contrast)
            if (recall && draft.isNotBlank()) { Text("刚才的回忆 · 自行对照", style = MaterialTheme.typography.labelMedium); Text(draft) }
        }
        FilterChip(selected = selected(c), onClick = {
            prefs.edit().putBoolean("${entry.anchor}:${c.term}", !selected(c)).apply(); selectedVersion++
        }, label = { Text(if (selected(c)) "已选为想学的表达 ✓" else "选为想学的表达") })
        if (c.reference.isNotBlank()) TextButton(onClick = { onXref(c.reference) }) { Text("打开已有条目 →") }
        else {
            var copied by remember(c.term) { mutableStateOf(false) }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString("请把下面的表达整理为独立学习条目，先查现有笔记并复用，避免重复，补上双向链接。\n来源条目：${entry.title} (#${entry.anchor})\n来源标签：${entry.tags.joinToString(" · ")}\n表达：${c.term}\n含义：${c.meaning}\n原文：${c.original}\n新语境（自拟）：${c.example}\n中文：${c.prompt}\n辨析：${c.contrast}\n目标：会写，进入现有复习流程。")); copied = true
            }) { Text(if (copied) "已复制 ✓" else "复制学习条目保存请求") }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(enabled = at > 0, onClick = { onIndex(visible[at - 1].index) }) { Text("← 上一张") }
        Text("${at + 1} / ${visible.size}", Modifier.padding(top = 12.dp))
        TextButton(enabled = at < visible.lastIndex, onClick = { onIndex(visible[at + 1].index) }) { Text("下一张 →") }
    }
    Text("先回忆，再揭晓；换一天再试，并用自己的话造句。选择仅保存在此设备；独立学习条目沿用现有复习流程。", style = MaterialTheme.typography.bodySmall)
}
