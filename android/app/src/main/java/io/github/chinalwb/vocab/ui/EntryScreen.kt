package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.chinalwb.vocab.data.Entry

/**
 * One entry. Cross-references push another EntryScreen on the nav back stack,
 * so the system back gesture walks back through the chain like the page's ← 返回.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(entry: Entry?, onBack: () -> Unit, onXref: (String) -> Unit, onSeen: (String) -> Unit) {
    if (entry != null) LaunchedEffect(entry.anchor) { onSeen(entry.anchor) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.let { levelStyle(it.level).name } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = entry?.let { levelColor(it.level) } ?: androidx.compose.ui.graphics.Color.Unspecified),
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (entry == null) {
                Text("找不到这个条目,可能已在词库里改名或删除。")
                return@Column
            }
            EntryHeader(entry)
            Spacer(Modifier.height(20.dp))
            EntryBody(entry, onXref)
            Spacer(Modifier.height(32.dp))
        }
    }
}
