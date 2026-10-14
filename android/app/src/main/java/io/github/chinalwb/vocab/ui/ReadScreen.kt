package io.github.chinalwb.vocab.ui

import io.github.chinalwb.vocab.data.LibraryState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 阅读 (⋮ → 阅读, 用户 2026-10-14): the same thing said two ways, a common version and a C1 one,
 * with what makes the second one C1. Read only — no quiz, no 进度, no 错题本. Data: readlist.md →
 * data.json "reads", newest first.
 */
@Composable
fun ReadScreen(lib: LibraryState, onXref: (String) -> Unit, modifier: Modifier = Modifier) {
    val data = lib.data
    if (data == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "同一个意思的常用说法和 C1 说法,读一读,看差别在哪。只读,不考。",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
        if (data.reads.isEmpty()) item {
            Text("还没有内容。", style = MaterialTheme.typography.bodyLarge, color = muted, modifier = Modifier.padding(top = 24.dp))
        }
        items(data.reads, key = { it.anchor }) { r ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (r.date.isNotEmpty()) Text(r.date, style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                    NoteBlocks(r.anchor, r.blocks, onXref)
                }
            }
        }
    }
}
