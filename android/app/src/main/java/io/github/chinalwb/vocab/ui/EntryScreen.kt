package io.github.chinalwb.vocab.ui

import androidx.compose.runtime.remember
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
    /** an attempt on an entry moved to 自测, with its verdict */
    onGrade: (String, Boolean) -> Unit = { _, _ -> },
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
            // Three zones, kept visibly apart: what the entry is (title, labels, 进度 — above), my
            // attempt (a card), and the answer (the notes, under their own 笔记 heading).
            var notesShown = true
            if (entry.level != "SELFTEST" && stage == Stage.Test) {
                // moved to 自测: quizzed on one of its example sentences like a 自测题 (Chinese → English,
                // compared word by word and logged); with no example, asked like a review card and
                // graded by me. The example is fixed for this try; 再试一次 moves to the next one.
                var tryNo by rememberSaveable(entry.anchor, stage) { mutableStateOf(attempts.size) }
                val ex = remember(entry.anchor, tryNo) { entry.quizExample(tryNo) }
                var draft by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf("") }
                var revealed by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf(false) }
                var graded by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf<Boolean?>(null) }
                QuizCard {
                    SelfTestStats(attempts)
                    if (!revealed) {
                        if (ex != null) ExamplePrompt(ex) else ReviewFront(entry, kindOf(entry, inTest = true))
                        SelfTestInput(draft, { draft = it }) {
                            when {
                                ex != null -> onGrade(draft, draft.isNotBlank() && bestDiff(draft, ex.en).same)
                                draft.isBlank() -> onGrade("", false)
                            }
                            revealed = true
                        }
                    } else {
                        MovedResult(ex, draft, graded) { graded = it; onGrade(draft, it) }
                        androidx.compose.material3.TextButton(
                            onClick = { tryNo = attempts.size },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text("再试一次") }
                        SelfTestHistory(attempts)
                    }
                }
                notesShown = revealed
            }
            if (entry.level == "SELFTEST") {
                // 自测: the answer stays hidden until I've written my own attempt
                var draft by rememberSaveable(entry.anchor) { mutableStateOf("") }
                var revealed by rememberSaveable(entry.anchor) { mutableStateOf(false) }
                QuizCard {
                    SelfTestStats(attempts)
                    if (!revealed) SelfTestInput(draft, { draft = it }) { onAttempt(draft); revealed = true }
                    else {
                        SelfTestResult(draft, entry.selfTestAnswer) { draft = ""; revealed = false }
                        SelfTestHistory(attempts)
                    }
                }
                notesShown = revealed
            }
            if (!notesShown) return@Column
            NotesHeading()
            EntryBody(entry, onXref)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** My attempt, on a card of its own between the entry's facts and its notes. */
@Composable
private fun QuizCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
    Spacer(Modifier.height(24.dp))
}

/** "笔记 ———": where the answer starts. */
@Composable
private fun NotesHeading() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
        Text("笔记", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 12.dp).weight(1f), color = MaterialTheme.colorScheme.outline)
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
