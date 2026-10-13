package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.sync.SyncStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The 复习 tab itself is QuizScreen now (a stage's list, then a run through it). What stays
// here is what a card asks, shared by the runs and the entry page, and the 同步 page.

@Composable
internal fun Stat(label: String, value: Int, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("$value", fontSize = 34.sp, fontFamily = AppFont, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What a card asks — the same rules as kind() in template.html. */
enum class Kind(val writes: Boolean) { SelfTest(true), Sentence(true), Translate(true), Grammar(false), Produce(true), Recognize(false) }

/** [inTest]: it's being quizzed, so a 认识 entry is produced too. */
fun kindOf(e: Entry, inTest: Boolean = false): Kind = when {
    e.level == "SELFTEST" -> Kind.SelfTest   // title is the Chinese prompt, the quote is the answer
    e.level == "SENTENCE" && e.originalSentence != null -> Kind.Sentence
    e.title.startsWith("翻译") -> Kind.Translate
    e.level == "GRAMMAR" -> Kind.Grammar
    e.writes || inTest -> Kind.Produce   // 会写 or quizzed: 中→英
    else -> Kind.Recognize         // 认识: 英→中
}

/** A 中→英 prompt must not give the answer away: blank out the headword's English words. */
fun maskTitle(text: String, title: String): String =
    Regex("[A-Za-z]{3,}").findAll(title).map { it.value.lowercase() }.distinct()
        .fold(text) { t, w -> t.replace(Regex("\\b$w\\w*", RegexOption.IGNORE_CASE), "＿＿") }

/** The prompt side of a card, for an entry without an example sentence to ask. */
@Composable
fun ReviewFront(entry: Entry, kind: Kind) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Chip(levelStyle(entry.level).short, levelColor(entry.level))
        val label: @Composable (String) -> Unit = {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val prompt: @Composable (String) -> Unit = { Text(it, style = MaterialTheme.typography.titleMedium, lineHeight = 26.sp) }
        when (kind) {
            Kind.SelfTest -> {
                label("自测 · 中译英")
                prompt(entry.title.replace(Regex("^自测[::]\\s*"), ""))
                Hint("不看笔记,直接写出英文。")
            }
            Kind.Sentence -> {
                label("我的原句")
                Quote(entry.originalSentence.orEmpty().lines(), MaterialTheme.colorScheme.primary) {}
                Hint("哪里不对、哪里不地道?先自己改一遍,再看答案。")
            }
            Kind.Translate -> {
                label("中译英")
                prompt(plain(entry.gloss).replace(Regex("^中译英练习[——-]*"), ""))
                Hint("先写出你的英文,再和几种译法对照。")
            }
            Kind.Produce -> {
                label("写出英文" + if (entry.pos.isNotEmpty()) " · ${entry.pos}" else "")
                prompt(maskTitle(plain(entry.gloss), entry.title))
                Hint("想出这个英文表达,再用它造一个句子。")
            }
            Kind.Grammar -> {
                Text(entry.title, fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp)
                Hint("回想这条规则,以及正反例句。")
            }
            Kind.Recognize -> {
                Text(entry.title, fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp)
                if (entry.ipa.isNotEmpty()) Text(entry.ipa, style = MaterialTheme.typography.bodyLarge)
                Hint("它是什么意思?什么语感?")
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** ⋮ → 同步与数据: the GitHub sync panel and 重置复习进度. */
@Composable
fun SyncScreen(
    sync: SyncStatus,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmReset by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SyncPanel(sync, onConnect, onDisconnect, onSyncNow)
        Text(
            "复习的排期(每条下次什么时候到期)只在这台手机和同步的 progress 分支里。重置会清掉所有排期,从头开始;进度阶段和答题记录不受影响。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.align(Alignment.End)) { Text("重置复习排期") }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("重置复习排期?") },
        text = { Text("所有条目的复习排期都会清空,从头开始。词库、进度阶段和答题记录不受影响。") },
        confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }) { Text("重置") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
    )
}

private val SYNCED_AT = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())

/** 同步到 GitHub — the same panel as the page's #sync. The token stays on this phone. */
@Composable
private fun SyncPanel(sync: SyncStatus, onConnect: (String) -> Unit, onDisconnect: () -> Unit, onSyncNow: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("同步到 GitHub", style = MaterialTheme.typography.titleSmall)
        if (!sync.connected) {
            var token by remember { mutableStateOf("") }
            val uri = LocalUriHandler.current
            Text(
                "进度、复习记录和自测结果存进本仓库的 progress 分支,网页和 App 共用一份。自测里写的句子原文不上传。" +
                    "需要一个 fine-grained token:Repository access 只选 chinalwb/vocab,Contents 设为 Read and write。token 只存在这台手机上。",
                style = MaterialTheme.typography.bodySmall, color = muted,
            )
            TextButton(onClick = { uri.openUri("https://github.com/settings/personal-access-tokens/new") }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("去 GitHub 生成 token")
            }
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                placeholder = { Text("github_pat_…") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onConnect(token) }, enabled = token.isNotBlank()) { Text("连接") }
        } else {
            Text("chinalwb/vocab · progress 分支。改动攒 15 秒提交一次,离开 App 时立即提交。", style = MaterialTheme.typography.bodySmall, color = muted)
            Text(
                when {
                    sync.busy -> "同步中…"
                    sync.error != null -> "同步失败:${sync.error}"
                    sync.ok > 0 -> "已同步 · ${SYNCED_AT.format(Instant.ofEpochMilli(sync.ok))}"
                    else -> "还没同步过"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (sync.error != null && !sync.busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onSyncNow, enabled = !sync.busy) { Text("立即同步") }
                TextButton(onClick = onDisconnect) { Text("断开") }
            }
        }
    }
}
