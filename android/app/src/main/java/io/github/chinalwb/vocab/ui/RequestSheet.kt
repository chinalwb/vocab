package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.review.REQUEST_NOTE_MAX
import io.github.chinalwb.vocab.review.REQUEST_WORD_MAX
import io.github.chinalwb.vocab.review.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 待补充 (用户 2026-11-18): the 新建 sheet and the pending cards on top of 浏览. Same as the page's
// reqSheet() / paintReqs() in template.html — keep the two in step.

private fun ymd(t: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))
private fun whenAdded(t: Long) = SimpleDateFormat("M/d HH:mm", Locale.US).format(Date(t))

/** 新建 ([item] null) or one pending item: the word, the note, 提交 / 保存, and 删除 for an item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestSheet(
    item: Request?,
    /** 同步 connected: without it the item stays on this phone, and Claude can't see it */
    connected: Boolean,
    onSave: (word: String, note: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var word by rememberSaveable { mutableStateOf(item?.w.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(item?.note.orEmpty()) }
    var sure by remember { mutableStateOf(false) }
    val wordFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { (if (item == null) wordFocus else noteFocus).requestFocus() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // like the 筛选 sheet: its own window, so no haze — nearly opaque instead
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
        scrimColor = Color.Black.copy(alpha = 0.25f),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (item == null) "新建条目" else "待补充", style = MaterialTheme.typography.titleLarge, fontFamily = AppFont)
            OutlinedTextField(
                value = word,
                onValueChange = { word = it.replace("\n", " ").take(REQUEST_WORD_MAX) },
                label = { Text("想学的词或短语") },
                placeholder = { Text("比如 get over sth") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { noteFocus.requestFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(wordFocus),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(REQUEST_NOTE_MAX) },
                label = { Text("补充(可选)") },
                placeholder = { Text("在哪儿看到的原句、想问什么") },
                minLines = 4,
                maxLines = 8,
                supportingText = { Text("${note.length} / $REQUEST_NOTE_MAX", Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                modifier = Modifier.fillMaxWidth().focusRequester(noteFocus),
            )
            Text(
                (if (item == null) "提交后放在「浏览」最上面的「待补充」里。" else "${whenAdded(item.c)} 记下的。") +
                    "回到电脑让 Claude 补全,发布后换成正式条目。" +
                    if (connected) "会原样同步到 GitHub 的公开仓库。" else "还没连接同步:先存在这台手机上,到「同步与数据」连上 GitHub 后才会上传,Claude 才看得到。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // two taps, so a stray one doesn't lose the item
                if (item != null) TextButton(onClick = { if (sure) onDelete() else sure = true }) {
                    Text(if (sure) "再点一次删除" else "删除", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(word, note) }, enabled = word.isNotBlank()) { Text(if (item == null) "提交" else "保存") }
            }
        }
    }
}

/** A pending item on the list: a dashed outline instead of a level colour — it has no level yet. */
@Composable
fun PendingCard(item: Request, onClick: () -> Unit, tile: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    val edge = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    edge, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (tile) 16.dp else 18.dp, vertical = if (tile) 14.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(item.w, color = ink, fontSize = if (tile) 17.sp else 19.sp, lineHeight = if (tile) 23.sp else 25.sp,
            fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (item.note.isNotEmpty()) Text(item.note, color = ink.copy(alpha = 0.75f), fontSize = if (tile) 14.sp else 15.sp,
            lineHeight = if (tile) 20.sp else 21.sp, maxLines = if (tile) 5 else 3, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("等 Claude 补全", style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
            Text(ymd(item.c), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.55f))
        }
    }
}
