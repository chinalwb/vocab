package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch

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
fun EntryNotes(
    entry: Entry,
    onXref: (String) -> Unit,
    /** on the entry page's card: no card per page, and the notes fill [modifier]'s height, scrolling inside */
    inCard: Boolean = false,
    modifier: Modifier = Modifier,
    /** on the entry page: which examples 自测 may ask, and the tap that toggles one */
    quiz: ExampleQuiz? = null,
    /** on the entry page: the card's expand button, at the end of the 笔记 heading */
    trailing: @Composable () -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("notes", Context.MODE_PRIVATE) }
    var all by remember { mutableStateOf(prefs.getBoolean("all", false)) }
    val sections = entry.sections
    val carousel = sections.size > 1 && !all
    Column(modifier) {
        NotesHeading(
            if (sections.size > 1) (if (all) "分卡显示" else "全部展开") else null,
            trailing,
        ) { all = !all; prefs.edit().putBoolean("all", all).apply() }
        when {
            carousel -> NotesCarousel(entry.anchor, sections, onXref, inCard, if (inCard) Modifier.weight(1f) else Modifier, quiz)
            inCard -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { EntryBody(entry, onXref, quiz) }
            else -> EntryBody(entry, onXref)
        }
    }
}

/** "笔记 ——— 全部展开": where the answer starts. */
@Composable
private fun NotesHeading(action: String?, trailing: @Composable () -> Unit, onAction: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Text("笔记", style = MaterialTheme.typography.labelLarge, color = muted)
        HorizontalDivider(Modifier.padding(horizontal = 12.dp).weight(1f), color = MaterialTheme.colorScheme.outline)
        if (action != null) Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            color = muted,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(horizontal = 4.dp, vertical = 6.dp),
        )
        trailing()
    }
}

// a section heading that is only its label ("**常见搭配:**") — the card's tab already says it
private val LABEL_ONLY = Regex("""^\*\*[^*]+[:：]\s*\*\*$""")

@Composable
private fun NotesCarousel(anchor: String, sections: List<Section>, onXref: (String) -> Unit, inCard: Boolean, modifier: Modifier, quiz: ExampleQuiz?) {
    val pager = rememberPagerState { sections.size }
    val tabs = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ink = MaterialTheme.colorScheme.onSurface
    LaunchedEffect(pager.currentPage) { tabs.animateScrollToItem((pager.currentPage - 1).coerceAtLeast(0)) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            // on the entry card the pager takes the card's remaining height and each page scrolls;
            // elsewhere it is as tall as the page in view, so a short one leaves no gap below
            modifier = if (inCard) Modifier.weight(1f).fillMaxWidth() else Modifier.animateContentSize(),
        ) { page ->
            val blocks = sections[page].blocks.let { b ->
                if ((b.firstOrNull() as? Block.Para)?.text?.let(LABEL_ONLY::matches) == true) b.drop(1) else b
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (inCard) Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 8.dp)
                        else Modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp)
                    )
            ) { Blocks(blocks, onXref, quiz) }
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

/** Which examples 自测 may ask ([excluded]: the plain English of the ones opted out) and the tap that toggles one. */
class ExampleQuiz(val excluded: Set<String>, val onToggle: (String) -> Unit)

/** Renders the blocks build.py produced; xref taps are handed to [onXref]. */
@Composable
fun EntryBody(entry: Entry, onXref: (String) -> Unit, quiz: ExampleQuiz? = null) = Blocks(entry.blocks, onXref, quiz)

@Composable
private fun Blocks(blocks: List<Block>, onXref: (String) -> Unit, quiz: ExampleQuiz? = null) {
    val link = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
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
                block.items.forEachIndexed { i, ex ->
                    // every translated example is a 自测 question unless I tap it out: the number
                    // sits in a filled dot while it's in, a hollow one (and dimmed text) when out
                    val asks = quiz != null && ex.quizable
                    val out = asks && plain(ex.en) in quiz!!.excluded
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (asks) Modifier.clip(RoundedCornerShape(8.dp)).clickable { quiz!!.onToggle(plain(ex.en)) } else Modifier)
                            .alpha(if (out) 0.45f else 1f),
                    ) {
                        Box(Modifier.width(22.dp).padding(top = 4.dp)) {
                            if (asks) Box(
                                Modifier
                                    .size(18.dp)
                                    .then(if (out) Modifier.border(1.5.dp, muted, CircleShape) else Modifier.background(ink, CircleShape)),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (out) muted else MaterialTheme.colorScheme.surface) }
                            else Text("${i + 1}", style = body, color = muted)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(inline(ex.en, link, onXref), style = body.copy(fontFamily = FontFamily.Serif, fontSize = 17.sp))
                            if (ex.zh.isNotEmpty()) {
                                Text(inline(ex.zh, link, onXref), style = MaterialTheme.typography.bodyMedium, color = muted)
                            }
                        }
                        SpeakButton(plain(ex.en), Modifier.padding(start = 4.dp), size = 18.dp)
                    }
                }
                if (quiz != null && block.items.any { it.quizable }) Text(
                    "● 自测会考这些例句,点一句可以排除(至少留一句)",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(start = 22.dp),
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
