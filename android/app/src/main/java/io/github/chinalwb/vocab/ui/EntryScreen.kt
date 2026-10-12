package io.github.chinalwb.vocab.ui

import androidx.compose.runtime.remember
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
            // The facts sit on the page; the entry itself is a card with two sides, like a coin:
            // the notes (a carousel) and, while it's in 自测, the quiz. Moving it into or out of
            // 自测 flips the card; once I've answered I can flip over to the notes and back.
            val selfTest = entry.level == "SELFTEST"
            val quiz = stage == Stage.Test
            // kept out here so flipping to the notes and back doesn't lose my attempt; a new try
            // (再试一次) resets them, and for a moved entry asks its next example
            var tryNo by rememberSaveable(entry.anchor, stage) { mutableStateOf(attempts.size) }
            val ex = remember(entry.anchor, tryNo) { if (selfTest) null else entry.quizExample(tryNo) }
            var draft by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf("") }
            var revealed by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf(false) }
            var graded by rememberSaveable(entry.anchor, stage, tryNo) { mutableStateOf<Boolean?>(null) }
            var notesUp by rememberSaveable(entry.anchor, stage) { mutableStateOf(false) }
            val retry = { tryNo = maxOf(tryNo + 1, attempts.size); notesUp = false }
            FlipCard(
                back = quiz && !notesUp,
                color = levelColor(entry.level),
                front = {
                    EntryNotes(entry, onXref, inCard = true)
                    if (quiz) FlipButton("回到自测") { notesUp = false }
                },
                backSide = {
                    SideHeading("自测")
                    SelfTestStats(attempts)
                    if (!revealed) {
                        // a moved entry asks one of its examples (中→英); with none, like a review card
                        if (!selfTest) {
                            if (ex != null) ExamplePrompt(ex) else ReviewFront(entry, kindOf(entry, inTest = true))
                        }
                        SelfTestInput(draft, { draft = it }) {
                            when {
                                selfTest -> onAttempt(draft)
                                ex != null -> onGrade(draft, draft.isNotBlank() && bestDiff(draft, ex.en).same)
                                draft.isBlank() -> onGrade("", false)
                            }
                            revealed = true
                        }
                    } else {
                        if (selfTest) SelfTestResult(draft, entry.selfTestAnswer)
                        else MovedResult(ex, draft, graded) { graded = it; onGrade(draft, it) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RetryButton(retry)
                            FlipButton("翻面看笔记") { notesUp = true }
                        }
                        SelfTestHistory(attempts)
                    }
                },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * A card with two sides that turns over in 3D when [back] changes: rotates about its vertical
 * axis, dips a little at the half-way point (where the side shown swaps), and eases its height
 * to the new side.
 */
@Composable
private fun FlipCard(
    back: Boolean,
    color: androidx.compose.ui.graphics.Color,
    front: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    backSide: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val angle by animateFloatAsState(
        if (back) 180f else 0f,
        tween(650, easing = FastOutSlowInEasing),
        label = "flip",
    )
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 16f * density
                val dip = 1f - 0.08f * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
                scaleX = dip; scaleY = dip
            }
            .shadow(2.dp, shape)
            .background(color, shape)
            .animateContentSize(tween(350))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // the far side is drawn mirrored back, so it reads the right way round
                .graphicsLayer { if (angle > 90f) rotationY = 180f }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = if (angle > 90f) backSide else front,
        )
    }
}

/** "自测 ———", matching the notes side's 笔记 heading. */
@Composable
private fun SideHeading(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 12.dp).weight(1f), color = MaterialTheme.colorScheme.outline)
    }
}

/** Turns the card over: an outlined button with the ↻ glyph. */
@Composable
private fun FlipButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp)) {
        Icon(androidx.compose.material.icons.Icons.Default.Refresh, null, Modifier.padding(end = 6.dp))
        Text(label)
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
