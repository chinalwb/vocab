package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.Stage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * One entry. Cross-references push another EntryScreen on the nav back stack,
 * so the system back gesture walks back through the chain like the page's ← 返回.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    entry: Entry?,
    onBack: () -> Unit,
    onXref: (String) -> Unit,
    onSeen: (String) -> Unit,
    attempts: List<Attempt> = emptyList(),
    onAttempt: (String) -> Unit = {},
    stage: Stage = Stage.Learn,
    onStage: (Stage) -> Unit = {},
) {
    if (entry != null) LaunchedEffect(entry.anchor) { onSeen(entry.anchor) }
    Scaffold(
        topBar = {
            TopAppBar(
                // Stable padding: the status bar may be re-showing while this page flies in.
                windowInsets = WindowInsets.statusBarsIgnoringVisibility,
                title = { Text(entry?.let { levelStyle(it.level).name } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = entry?.let { levelColor(it.level) } ?: androidx.compose.ui.graphics.Color.Unspecified),
            )
        }
    ) { pad ->
        // Only the body grows out of the tapped card; the top bar fades in on its own,
        // so the flying title never has to pass underneath it.
        Column(
            Modifier
                .padding(pad)
                // the keyboard takes room from the page instead of panning the whole window
                // (which pushed the top bar under the status bar and left nothing to scroll)
                .consumeWindowInsets(pad)
                .imePadding()
                .fillMaxSize()
                .then(if (entry != null) Modifier.sharedEntryContainer(entry.anchor) else Modifier)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (entry == null) {
                Text("找不到这个条目,可能已在词库里改名或删除。")
                return@Column
            }
            EntryHeader(entry)
            Spacer(Modifier.height(12.dp))
            StageBar(stage, selfTest = entry.level == "SELFTEST", onStage)
            Spacer(Modifier.height(16.dp))
            if (entry.level != "SELFTEST" && stage == Stage.Test) {
                // moved to 自测: asked like a review card before the notes open
                var draft by rememberSaveable(entry.anchor, stage) { mutableStateOf("") }
                var revealed by rememberSaveable(entry.anchor, stage) { mutableStateOf(false) }
                if (!revealed) {
                    ReviewFront(entry, kindOf(entry, inTest = true))
                    Spacer(Modifier.height(16.dp))
                    SelfTestInput(draft, { draft = it }) { revealed = true }
                    return@Column
                }
                if (draft.isNotBlank()) {
                    Text("你写的", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        draft.trim(),
                        fontFamily = FontFamily.Serif,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .padding(top = 6.dp, bottom = 8.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    )
                }
                Text("对照下面的笔记,自己判断写得对不对。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
            if (entry.level == "SELFTEST") {
                // 自测: the answer stays hidden until I've written my own attempt
                var draft by rememberSaveable(entry.anchor) { mutableStateOf("") }
                var revealed by rememberSaveable(entry.anchor) { mutableStateOf(false) }
                SelfTestStats(attempts)
                Spacer(Modifier.height(12.dp))
                if (!revealed) {
                    SelfTestInput(draft, { draft = it }) { onAttempt(draft); revealed = true }
                    return@Column
                }
                SelfTestResult(draft, entry.selfTestAnswer) { draft = ""; revealed = false }
                SelfTestHistory(attempts)
                Spacer(Modifier.height(16.dp))
            }
            EntryBody(entry, onXref)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 进度 and the moves out of it — the same buttons as the page's .stage row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageBar(stage: Stage, selfTest: Boolean, onStage: (Stage) -> Unit) {
    // Where it is and what I can do are kept apart: the stepper only shows the stage, and the
    // moves are verb-labelled outlined / text buttons, so the page's one filled button stays
    // the real next step (显示答案). A filled "已掌握" read as a status, not an action.
    val moves = when (stage) {
        Stage.Learn -> listOf(Stage.Test to "移到自测")
        Stage.Test -> listOf(Stage.Done to "标为已掌握") + if (selfTest) emptyList() else listOf(Stage.Learn to "移回学习中")
        Stage.Done -> listOf(Stage.Test to "移回自测")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("进度  ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stage.entries.forEachIndexed { i, s ->
                if (i > 0) Text("  →  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                val here = s == stage
                Text(
                    s.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (here) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = if (here) Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp) else Modifier,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            moves.forEachIndexed { i, (to, label) ->
                if (i == 0) OutlinedButton(onClick = { onStage(to) }, contentPadding = PaddingValues(horizontal = 16.dp)) { Text(label) }
                else TextButton(onClick = { onStage(to) }) { Text(label) }
            }
        }
    }
}
