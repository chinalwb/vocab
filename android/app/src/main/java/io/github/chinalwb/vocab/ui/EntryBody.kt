package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Block
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.data.Section
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.launch

@Composable
fun EntryHeader(entry: Entry) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            entry.title,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            modifier = Modifier.sharedEntryTitle(entry.anchor),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(levelStyle(entry.level).short, levelColor(entry.level))
            if (entry.no > 0) Chip("#${entry.no}", MaterialTheme.colorScheme.surfaceContainerHigh)
            listOf(entry.ipa, entry.pos, if (entry.writes) "会写" else "认识", entry.date.takeIf { it.isNotEmpty() }?.let { "收录 $it" } ?: "")
                .filter { it.isNotEmpty() }
                .forEach { Chip(it, MaterialTheme.colorScheme.surfaceContainerHigh) }
        }
    }
}

@Composable
fun Chip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * The notes under a 笔记 heading: one section per card in a pager (a tab per section, swipe or
 * tap, never auto-advancing), so a glance takes in one thing. 全部展开 stacks them back into one
 * page and is remembered. Entries without sections show as before.
 */
@Composable
fun EntryNotes(entry: Entry, onXref: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("notes", Context.MODE_PRIVATE) }
    var all by remember { mutableStateOf(prefs.getBoolean("all", false)) }
    val sections = entry.sections
    val carousel = sections.size > 1 && !all
    Column {
        NotesHeading(
            if (sections.size > 1) (if (all) "分卡显示" else "全部展开") else null,
        ) { all = !all; prefs.edit().putBoolean("all", all).apply() }
        if (carousel) NotesCarousel(entry.anchor, sections, onXref) else EntryBody(entry, onXref)
    }
}

/** "笔记 ——— 全部展开": where the answer starts. */
@Composable
private fun NotesHeading(action: String?, onAction: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Text("笔记", style = MaterialTheme.typography.labelLarge, color = muted)
        HorizontalDivider(Modifier.padding(horizontal = 12.dp).weight(1f), color = MaterialTheme.colorScheme.outline)
        if (action != null) Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            color = muted,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

// a section heading that is only its label ("**常见搭配:**") — the card's tab already says it
private val LABEL_ONLY = Regex("""^\*\*[^*]+[:：]\s*\*\*$""")

@Composable
private fun NotesCarousel(anchor: String, sections: List<Section>, onXref: (String) -> Unit) {
    val pager = rememberPagerState { sections.size }
    val tabs = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ink = MaterialTheme.colorScheme.onSurface
    LaunchedEffect(pager.currentPage) { tabs.animateScrollToItem((pager.currentPage - 1).coerceAtLeast(0)) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(state = tabs, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(sections) { i, s ->
                val on = i == pager.currentPage
                Text(
                    s.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (on) Modifier.background(ink)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        )
                        .clickable { scope.launch { pager.animateScrollToPage(i) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        HorizontalPager(
            state = pager,
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            key = { "$anchor/$it" },
            // the pager is as tall as the card in view, so a short card leaves no gap below
            modifier = Modifier.animateContentSize(),
        ) { page ->
            val blocks = sections[page].blocks.let { b ->
                if ((b.firstOrNull() as? Block.Para)?.text?.let(LABEL_ONLY::matches) == true) b.drop(1) else b
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) { Blocks(blocks, onXref, fold = true) }
        }
        if (sections.size > 1) Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            sections.indices.forEach { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier
                        .height(5.dp)
                        .width(if (on) 14.dp else 5.dp)
                        .background(ink.copy(alpha = if (on) 0.6f else 0.2f), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

/** Renders the blocks build.py produced; xref taps are handed to [onXref]. */
@Composable
fun EntryBody(entry: Entry, onXref: (String) -> Unit) = Blocks(entry.blocks, onXref)

/** [fold]: an example list shows its first sentence, the rest behind 更多例句. */
@Composable
private fun Blocks(blocks: List<Block>, onXref: (String) -> Unit, fold: Boolean = false) {
    val link = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (block in blocks) when (block) {
            is Block.Para -> Text(inline(block.text, link, onXref), style = body)
            is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.items.forEach { item ->
                    Row {
                        Text("•", style = body, color = muted, modifier = Modifier.width(18.dp))
                        Text(inline(item, link, onXref), style = body)
                    }
                }
            }
            is Block.Examples -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                var open by remember(block) { mutableStateOf(!fold) }
                val shown = if (open) block.items else block.items.take(1)
                shown.forEachIndexed { i, ex ->
                    Row {
                        Text("${i + 1}", style = body, color = muted, modifier = Modifier.width(22.dp))
                        Column {
                            Text(inline(ex.en, link, onXref), style = body.copy(fontFamily = FontFamily.Serif, fontSize = 17.sp))
                            if (ex.zh.isNotEmpty()) {
                                Text(inline(ex.zh, link, onXref), style = MaterialTheme.typography.bodyMedium, color = muted)
                            }
                        }
                    }
                }
                if (shown.size < block.items.size) Text(
                    "更多例句(${block.items.size - shown.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = muted,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { open = true }.padding(start = 22.dp, top = 2.dp, bottom = 2.dp),
                )
            }
            is Block.Quote -> Quote(block.lines, link, onXref)
        }
    }
}

@Composable
fun Quote(lines: List<String>, link: androidx.compose.ui.graphics.Color, onXref: (String) -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
        )
        Column(Modifier.padding(start = 12.dp)) {
            lines.forEach {
                Text(
                    inline(it, link, onXref),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 26.sp),
                )
            }
        }
    }
}
