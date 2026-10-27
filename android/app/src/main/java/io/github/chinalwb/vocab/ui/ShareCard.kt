package io.github.chinalwb.vocab.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.chinalwb.vocab.R
import io.github.chinalwb.vocab.data.Entry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * 分享图片 (用户 2026-10-27): the entry's notes drawn as one long picture, to send to a WeChat
 * friend or save to the gallery (Moments only takes pictures from the gallery — WeChat stopped
 * accepting them from other apps). The sheet shows the picture as it will be; what's shared is
 * that same composable, recorded into a graphics layer and written out as a PNG.
 */

private const val WECHAT = "com.tencent.mm"
// WeChat's 发送给朋友 screen; other apps have sent pictures straight to it for years
private const val WECHAT_SHARE_IMG = "com.tencent.mm.ui.tools.ShareImgUI"

/** The picture: level, headword, part of speech and IPA, every note block, and a footer. */
@Composable
private fun ShareCard(entry: Entry, modifier: Modifier = Modifier) = LightTheme {
    val style = levelStyle(entry.level)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // contentColor spelled out: surfaceVariant is white too, so the default would pick its grey
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Chip("${style.short} · ${style.name}", style.light)
            Text(entry.title, fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp)
            val pos = entry.pos.substringBefore('(').substringBefore('(').trim()
            listOf(pos, entry.ipa).filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = muted)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            StillBlocks(entry.anchor, entry.blocks)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Footer(entry, muted)
        }
    }
}

// the user's own motto (2026-10-26), next to the app icon at the foot of every picture
private const val SLOGAN = "Get it right. Then make it better."

/** The app icon, VoCab and the slogan beside it; the entry's number and date on the right. */
@Composable
private fun Footer(entry: Entry, muted: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // the launcher foreground has a 9dp frame round the photo on its 108dp canvas: scaling by
        // 108 / 90 crops it away, the way the launcher's mask does
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))) {
            Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.2f))
        }
        Column(Modifier.weight(1f)) {
            Text("VoCab", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(SLOGAN, style = MaterialTheme.typography.bodySmall, color = muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (entry.no > 0) Text("#${entry.no}", style = MaterialTheme.typography.labelMedium, color = muted)
            if (entry.date.isNotBlank()) Text(entry.date, style = MaterialTheme.typography.labelMedium, color = muted)
        }
    }
}

/** 分享图片: the picture as a preview, then 发给微信好友 / 保存到相册 / 其他 App…. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareImageSheet(entry: Entry, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    val wechat = remember { ctx.hasWeChat() }
    // every button writes the picture fresh from the preview, then hands it on
    val run: (suspend (File) -> String?) -> Unit = { then ->
        busy = true
        scope.launch {
            val result = runCatching {
                val file = writePng(ctx, entry, layer)
                then(file)
            }
            busy = false
            result.onSuccess { done -> if (done != null) { Toast.makeText(ctx, done, Toast.LENGTH_LONG).show(); onDismiss() } }
                .onFailure { Toast.makeText(ctx, "没能生成图片:${it.message ?: it.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("分享图片", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ShareCard(
                entry,
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .recordInto(layer),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (wechat) Button(
                    onClick = { run { ctx.sendToWeChat(it); null } },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("发给微信好友", fontWeight = FontWeight.Bold) }
                if (Build.VERSION.SDK_INT >= 29) OutlinedButton(
                    onClick = { run { withContext(Dispatchers.IO) { saveToGallery(ctx, it) } } },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("保存到相册") }
                OutlinedButton(
                    onClick = { run { ctx.startActivity(Intent.createChooser(ctx.sendIntent(it), "分享图片")); null } },
                    enabled = !busy,
                    modifier = Modifier.height(48.dp),
                ) { Text("其他…") }
            }
            Text(
                "发朋友圈:先「保存到相册」,再在微信里从相册选这张图。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Draws as usual and records the same drawing into [layer], so it can be turned into a bitmap. */
private fun Modifier.recordInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/** The recorded card as a PNG in the cache (share/), which FileProvider hands to other apps. */
private suspend fun writePng(ctx: Context, entry: Entry, layer: GraphicsLayer): File {
    // a hardware bitmap can't be compressed; copy it into ordinary memory first
    val bitmap = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    return withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "vocab-${entry.anchor}.png").also { f ->
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}

private fun Context.sendIntent(file: File) = Intent(Intent.ACTION_SEND).apply {
    type = "image/png"
    putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@sendIntent, "$packageName.files", file))
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** Straight to WeChat's 发送给朋友; if that screen ever moves, WeChat's own share options instead. */
private fun Context.sendToWeChat(file: File) {
    val send = sendIntent(file)
    try {
        startActivity(Intent(send).setComponent(ComponentName(WECHAT, WECHAT_SHARE_IMG)))
    } catch (_: ActivityNotFoundException) {
        startActivity(send.setPackage(WECHAT))
    } catch (_: SecurityException) {
        startActivity(send.setPackage(WECHAT))
    }
}

private fun Context.hasWeChat(): Boolean = packageManager.getLaunchIntentForPackage(WECHAT) != null

/** 保存到相册 (Android 10+, no permission needed): Pictures/VoCab; returns the toast to show. */
@androidx.annotation.RequiresApi(29)
private fun saveToGallery(ctx: Context, file: File): String {
    val resolver = ctx.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VoCab")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("没能在相册里建文件")
    resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return "已保存到相册(Pictures/VoCab)"
}
