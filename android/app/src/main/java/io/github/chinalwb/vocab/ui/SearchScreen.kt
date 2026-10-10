package io.github.chinalwb.vocab.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.data.LibraryState
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.stageOf

private const val RECENT_MAX = 8

/**
 * The search page: searches every entry, whatever the browse filters are (looking something up
 * shouldn't be blocked by a filter I forgot about). "12" or "#12" jumps to entry #12. With an
 * empty box it shows my recent searches, kept on the phone.
 */
@Composable
fun SearchScreen(
    lib: LibraryState,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    selfTests: Map<String, List<Attempt>> = emptyMap(),
    stages: Map<String, String> = emptyMap(),
) {
    val prefs = LocalContext.current.getSharedPreferences("search", Context.MODE_PRIVATE)
    var recent by remember { mutableStateOf(prefs.getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() }) }
    val saveRecent = { q: String ->
        val t = q.trim()
        if (t.isNotEmpty()) {
            recent = (listOf(t) + recent.filter { it != t }).take(RECENT_MAX)
            prefs.edit().putString("recent", recent.joinToString("\n")).apply()
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val entries = lib.data?.entries.orEmpty()
    val results = remember(entries, query) {
        val q = query.trim().lowercase()
        val numQ = Regex("^#?(\\d+)$").find(q)?.groupValues?.get(1)?.toInt()
        if (q.isEmpty()) emptyList()
        else entries.filter { e -> if (numQ != null) e.no == numQ else q in e.searchText }
    }
    val foot = { e: Entry -> listOfNotNull(selfTests.stFoot(e), stageFoot(e, stages.stageOf(e))).joinToString(" · ").ifEmpty { null } }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            SearchField(query, { query = it }, Modifier.weight(1f).focusRequester(focus), onSearch = { saveRecent(query) })
        }
        if (query.isBlank()) {
            if (recent.isNotEmpty()) Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("最近搜索", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { recent = emptyList(); prefs.edit().remove("recent").apply() }) { Text("清除") }
                }
                recent.forEach { r ->
                    Text(r, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { query = r }.padding(vertical = 10.dp))
                }
            } else Text(
                "搜索单词、释义、例句,或输入编号 #12。搜的是全部条目,不受浏览页的筛选影响。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        } else {
            Text(
                if (results.isEmpty()) "没有匹配的条目" else "${results.size} 条",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyColumn(contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.anchor }) { e ->
                    // no shared bounds here: the browse page's cards carry the same keys
                    EntryCard(e, null, foot(e), shared = false) { saveRecent(query); onOpen(e.anchor) }
                }
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}
