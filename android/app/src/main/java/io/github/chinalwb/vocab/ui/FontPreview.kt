package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.R

/**
 * 字体对比 (用户 2026-10-13, temporary): the same piece of an entry in each candidate font, to pick
 * one before the real change. Fonts come from Google's downloadable-fonts provider (Play services),
 * so the APK doesn't grow; offline on first open they fall back to the system font. Chinese has no
 * glyphs in these fonts and stays the system's.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun google(name: String) = GoogleFont(name).let { f ->
    FontFamily(
        Font(f, provider, FontWeight.Normal),
        Font(f, provider, FontWeight.Medium),
        Font(f, provider, FontWeight.SemiBold),
        Font(f, provider, FontWeight.Normal, FontStyle.Italic),
    )
}

private val CANDIDATES = listOf(
    Triple("现在 · 系统衬线体", "手机自带(三星上是 Noto Serif 一类)", FontFamily.Serif),
    Triple("Literata", "Google 为 Play 图书设计的屏幕阅读衬线体(推荐)", google("Literata")),
    Triple("Source Serif 4", "Adobe 的中性衬线体,偏技术文档", google("Source Serif 4")),
    Triple("Inter", "无衬线,更现代、更像工具类 App", google("Inter")),
)

@Composable
fun FontPreviewScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                "同一段 worth 条目,四种英文字体;中文都是系统字体。第一次打开要联网下载字体,没加载出来时会显示成系统字体,稍等或重进一次。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CANDIDATES.forEach { (name, note, family) ->
            item(key = name) { Sample(name, note, family) }
        }
    }
}

@Composable
private fun Sample(name: String, note: String, family: FontFamily) {
    val ink = Color(0xFF1F2A24)
    val muted = ink.copy(alpha = 0.7f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFDCE6D5), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("worth", fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = ink)
            Text("英 /wɜːθ/  美 /wɝːθ/  ·  adj. / n.  ·  B1", fontFamily = family, fontSize = 15.sp, color = muted)
            Text(
                "having a particular value, or good or useful enough to justify the time, money or effort spent on it.",
                fontFamily = family, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 23.sp, color = ink,
            )
            Text("值(多少钱);值得(花时间、花钱、花力气)。", fontFamily = family, fontSize = 15.sp, lineHeight = 22.sp, color = muted)
            Example(family, ink, muted, "1", "Is it worth upgrading to the new version?", "值得升级到新版本吗?")
            Example(family, ink, muted, "2", "For what it's worth, I think you made the right call.", "不管有没有用,我觉得你的决定是对的。")
            Text("“It's worth a try” — 0123456789", fontFamily = family, fontSize = 15.sp, color = muted)
        }
    }
}

@Composable
private fun Example(family: FontFamily, ink: Color, muted: Color, n: String, en: String, zh: String) {
    Column {
        Text("$n.  $en", fontFamily = family, fontSize = 17.sp, lineHeight = 25.sp, color = ink)
        Text(zh, fontSize = 15.sp, lineHeight = 22.sp, color = muted, modifier = Modifier.padding(start = 22.dp))
    }
}
