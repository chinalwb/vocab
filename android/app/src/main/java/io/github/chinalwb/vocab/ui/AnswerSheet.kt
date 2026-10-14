package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Block
import io.github.chinalwb.vocab.data.Entry

/*
 * The V2 answer sheet (ui/claude-code-v2, 用户 2026-10-14): the question on a card in the entry's
 * level colour, joined to a panel underneath that holds the word chips and 显示答案 — one sheet
 * of paper. The 你的句子 box sits right under the question in both 选词 and 打字, the same size in
 * the same place, so neither switching modes nor the keyboard moves anything; the keyboard only
 * covers the empty card below it. ⌫ next to the chips takes back the last word without reaching
 * up. The 复习 / 考核 runs and the entry page's 自测 both use it.
 */

/** The sheet's own colours, light and dark: the box on the card, the panel, the keys, the diff marks. */
@Immutable
class SheetColors(
    val sub: Color,
    val well: Color,
    val panel: Color,
    val key: Color,
    val keyBg: Color,
    val faint: Color,
    val err: Color,
    val warn: Color,
)

@Composable
fun sheetColors(): SheetColors {
    val ink = MaterialTheme.colorScheme.onSurface
    return if (isSystemInDarkTheme()) SheetColors(
        sub = ink.copy(alpha = 0.76f), well = Color.Black.copy(alpha = 0.24f), panel = Color(0xFF232320),
        key = Color(0xFF45433E), keyBg = Color(0xFF2A2926), faint = ink.copy(alpha = 0.16f),
        err = Color(0xFFF08A78), warn = Color(0xFFE6B35C).copy(alpha = 0.36f),
    ) else SheetColors(
        sub = ink.copy(alpha = 0.72f), well = Color.White.copy(alpha = 0.62f), panel = Color.White,
        key = Color(0xFFD9D6CC), keyBg = Color.White, faint = ink.copy(alpha = 0.14f),
        err = Color(0xFFC2412D), warn = Color(0xFFB7791F).copy(alpha = 0.3f),
    )
}

/** A run's top row: ✕, the progress bar, "3 / 32". The page has no other bars while a run is on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunTopBar(onClose: () -> Unit, progress: Float, label: String, closeLabel: String = "结束这一轮") {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .height(52.dp)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, closeLabel) }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = sheetColors().faint,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The sheet: [card] on the level colour (it scrolls when the keyboard leaves it short), [panel]
 * joined underneath it. Takes the height [modifier] gives it.
 */
@Composable
fun AnswerSheet(
    color: Color,
    modifier: Modifier = Modifier,
    scroll: ScrollState = androidx.compose.foundation.rememberScrollState(),
    card: @Composable ColumnScope.() -> Unit,
    panel: @Composable ColumnScope.() -> Unit,
) {
    val c = sheetColors()
    val line = MaterialTheme.colorScheme.outline
    Column(modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(color)
                .verticalScroll(scroll)
                .padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = card,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(c.panel)
                .drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = panel,
        )
    }
}

/** "make use of · 例句 1 / 3 · 中译英" with the 选词 | 打字 switch at its end. */
@Composable
fun SheetMetaRow(meta: String, toggle: (@Composable () -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            meta,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = sheetColors().sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        toggle?.invoke()
    }
}

/** The 你的句子 box's top line: its name, and 清空 once there's something to clear. */
@Composable
private fun BoxHeader(onClear: (() -> Unit)?) {
    val c = sheetColors()
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("你的句子", style = MaterialTheme.typography.labelMedium, color = c.sub, modifier = Modifier.weight(1f))
        if (onClear != null) Text(
            "清空",
            style = MaterialTheme.typography.labelLarge,
            color = c.sub,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClear).padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

private val BOX_MIN = 140.dp

/** 选词: the picked words in the box, the last one outlined — tap it to take it back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsAnswerBox(st: WordBankState) {
    val c = sheetColors()
    val ink = MaterialTheme.colorScheme.onSurface
    val picked = st.picked
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = BOX_MIN)
            .clip(RoundedCornerShape(16.dp))
            .background(c.well)
            .border(1.5.dp, Color.Transparent, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxHeader(if (picked.isEmpty()) null else st::clear)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (picked.isEmpty()) Text("点下面的词,一个一个拼出英文…", color = c.sub, modifier = Modifier.padding(vertical = 8.dp))
            picked.forEachIndexed { i, w ->
                val last = i == picked.lastIndex
                Box(
                    Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.keyBg)
                        .then(if (last) Modifier.border(2.dp, ink, RoundedCornerShape(10.dp)).clickable { st.undo() } else Modifier)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(w, fontFamily = AppFont, fontSize = 17.sp) }
            }
            if (picked.isNotEmpty()) Box(Modifier.padding(start = 2.dp, top = 7.dp).size(2.dp, 24.dp).background(ink))
        }
        if (picked.isNotEmpty()) Text("点最后一个词或右下角的 ⌫ 撤回", style = MaterialTheme.typography.labelSmall, color = c.sub)
    }
}

/** 打字: the same box with a cursor in it, outlined; a long answer grows it downward. */
@Composable
fun TypingAnswerBox(value: String, onChange: (String) -> Unit) {
    val c = sheetColors()
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = BOX_MIN)
            .clip(RoundedCornerShape(16.dp))
            .background(c.well)
            .border(1.5.dp, ink, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BoxHeader(if (value.isEmpty()) null else ({ onChange("") }))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = AppFont, fontSize = 19.sp, lineHeight = 28.sp, color = ink),
            cursorBrush = SolidColor(ink),
            minLines = 2,
            modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text("不看答案,写出英文…", fontSize = 19.sp, lineHeight = 28.sp, color = c.sub)
                    field()
                }
            },
        )
    }
}

/** A key on the panel: a 3dp lip along the bottom so it reads as something to press. */
@Composable
private fun Key(modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = sheetColors()
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(c.key)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 1.dp, end = 1.dp, top = 1.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(c.keyBg),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The four chips two by two, ⌫ beside them; once the sentence is complete, a note instead of chips. */
@Composable
fun WordKeys(st: WordBankState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            if (st.done) Text(
                "拼完了,点「显示答案」对照",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().height(116.dp).padding(top = 46.dp),
            ) else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                st.options.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { w ->
                            Key(Modifier.weight(1f).height(54.dp), onClick = { st.pick(w) }) { Text(w, fontFamily = AppFont, fontSize = 19.sp) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Key(Modifier.width(56.dp).height(116.dp), enabled = st.picked.isNotEmpty(), onClick = { st.undo() }) {
            Icon(BackspaceIcon, "撤回最后一个词", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** The filled button at the bottom of the panel (显示答案, 下一题). */
@Composable
fun SheetButton(label: String, modifier: Modifier = Modifier.fillMaxWidth(), filled: Boolean = true, onClick: () -> Unit) {
    if (filled) androidx.compose.material3.Button(onClick = onClick, modifier = modifier.height(52.dp)) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    } else androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, sheetColors().key),
    ) {
        Text(label, fontSize = 16.sp)
    }
}

private fun marked(words: List<String>, keep: Set<Int>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    words.forEachIndexed { i, w ->
        if (i > 0) append(' ')
        if (i in keep) append(w) else withStyle(style) { append(w) }
    }
}

/**
 * The reveal: one verdict line (what happened, and this sentence's stats under it), then what I
 * wrote — extra or wrong words struck through — and the answer, with what I missed highlighted.
 * [answer] may hold several acceptable lines; the closest one is compared, as before.
 */
@Composable
fun RevealVerdict(mine: String, answer: String, stats: String) {
    val c = sheetColors()
    val d = bestDiff(mine, answer)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            mine.isBlank() -> {
                VerdictBar(VerdictIcon.Peek, "没写就看了答案", stats)
                answer.lines().filter { it.isNotBlank() }.forEach { AnswerWell("答案", AnnotatedString(it), say = it) }
            }
            d.same -> {
                VerdictBar(VerdictIcon.Good, "完全正确", stats)
                Mine("我写的", AnnotatedString(mine.trim()))
                AnswerWell("答案", AnnotatedString(d.answer.joinToString(" ")), say = d.answer.joinToString(" "))
            }
            else -> {
                val extra = d.mine.size - d.keepMine.size
                val missing = d.answer.size - d.keepAnswer.size
                val parts = listOfNotNull(if (extra > 0) "多了 $extra 个词" else null, if (missing > 0) "漏了 $missing 个词" else null)
                VerdictBar(VerdictIcon.Warn, (listOf(if (extra + missing <= 2) "差一点" else "还不对") + parts).joinToString(" · "), stats)
                Mine("我写的(划掉 = 多出来或写错的)", marked(d.mine, d.keepMine, SpanStyle(color = c.err, textDecoration = TextDecoration.LineThrough)))
                AnswerWell("答案(高亮 = 漏掉或写错的)", marked(d.answer, d.keepAnswer, SpanStyle(background = c.warn)), say = d.answer.joinToString(" "))
                Text("逐词对照只看字面:意思对、换了说法也可能没问题。", style = MaterialTheme.typography.bodySmall, color = c.sub)
            }
        }
    }
}

enum class VerdictIcon { Good, Warn, Peek }

/** The verdict as one inverted bar: an icon in a coloured dot, the verdict, and a second line. */
@Composable
fun VerdictBar(icon: VerdictIcon, title: String, sub: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val ground = MaterialTheme.colorScheme.background
    val (dot, glyph) = when (icon) {
        VerdictIcon.Good -> Color(0xFF8FD19E) to "✓"
        VerdictIcon.Warn -> Color(0xFFF2A65A) to "!"
        VerdictIcon.Peek -> Color(0xFFB9B6AC) to "–"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ink).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).background(dot, CircleShape), contentAlignment = Alignment.Center) {
            Text(glyph, color = Color(0xFF1F1F1E), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = ground, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
            if (sub.isNotEmpty()) Text(sub, color = ground.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Mine(label: String, text: AnnotatedString) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = sheetColors().sub)
        Text(text, fontFamily = AppFont, fontSize = 18.sp, lineHeight = 28.sp)
    }
}

@Composable
private fun AnswerWell(label: String, text: AnnotatedString, say: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(sheetColors().well).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = sheetColors().sub)
            Text(text, fontFamily = AppFont, fontSize = 18.sp, lineHeight = 28.sp)
        }
        SpeakButton(say)
    }
}

/**
 * Under a reveal: the notes for what was just asked — the section the example sentence comes
 * from, that sentence highlighted — or, for a 自测题 or an entry without one, all the notes.
 * [onAll] goes to the whole notes (the entry page, or its notes side).
 */
@Composable
fun NotesForQuestion(entry: Entry, ex: QuizExample?, onXref: (String) -> Unit, onAll: (() -> Unit)?) {
    val c = sheetColors()
    val section = ex?.let { q ->
        entry.sections.firstOrNull { s -> s.blocks.any { b -> b is Block.Examples && b.items.any { plain(it.en) == q.en } } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = c.faint)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (section != null) "笔记里的这一句 · ${section.label}" else "笔记",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.sub,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (onAll != null) Text(
                "全部笔记 ›",
                style = MaterialTheme.typography.labelLarge, color = c.sub,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAll).padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
        if (section != null) NoteBlocks(entry.anchor, sectionBlocks(section), onXref, highlight = ex.en)
        else EntryBody(entry, onXref)
    }
}

/** The entry's sentences as short bars (the one asked filled), up to 10 — more and they'd be specks. */
@Composable
fun SentenceBars(at: Int, of: Int) {
    if (of !in 2..10) return
    val ink = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(of) { i -> Box(Modifier.size(16.dp, 5.dp).background(if (i == at) ink else ink.copy(alpha = 0.18f), RoundedCornerShape(3.dp))) }
    }
}
