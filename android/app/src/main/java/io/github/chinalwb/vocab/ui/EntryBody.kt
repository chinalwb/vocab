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
            listOf(entry.ipa, entry.pos, entry.date.takeIf { it.isNotEmpty() }?.let { "收录 $it" } ?: "")
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

/** Renders the blocks build.py produced; xref taps are handed to [onXref]. */
@Composable
fun EntryBody(entry: Entry, onXref: (String) -> Unit) {
    val link = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (block in entry.blocks) when (block) {
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
