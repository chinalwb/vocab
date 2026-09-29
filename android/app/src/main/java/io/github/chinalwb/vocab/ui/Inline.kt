package io.github.chinalwb.vocab.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

// 错题本 type tags, e.g. `[介词]` — keep in sync with MISTAKE_TYPES in build.py.
val MISTAKE_TAGS = listOf("冠词", "介词", "搭配", "动词", "结构", "词形", "标点", "逻辑", "直译", "语气")

// Same inline syntax build.py's inline() understands.
// The tag alternative goes first so a tag is never swallowed into a later `[text](#anchor)`.
private val TOKEN = Regex(
    """\[(""" + MISTAKE_TAGS.joinToString("|") + """)](?!\()|""" +
        """\*\*(.+?)\*\*|~~(.+?)~~|`(.+?)`|\[(.+?)]\(#([^)]+)\)|(?<![\w*])\*([^*\n]+)\*(?![\w*])"""
)

/** Renders inline markdown; `[text](#anchor)` becomes a tappable cross-reference. */
fun inline(text: String, linkColor: Color, onXref: (String) -> Unit): AnnotatedString =
    buildAnnotatedString { append(text, linkColor, onXref) }

private fun AnnotatedString.Builder.append(text: String, linkColor: Color, onXref: (String) -> Unit) {
    var last = 0
    for (m in TOKEN.findAll(text)) {
        append(text.substring(last, m.range.first))
        val g = m.groups
        when {
            g[1] != null -> withStyle(SpanStyle(fontSize = 0.8.em, background = linkColor.copy(alpha = 0.10f))) {
                append("\u2009${g[1]!!.value}\u2009")
            }
            g[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g[2]!!.value, linkColor, onXref) }
            g[3] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[3]!!.value, linkColor, onXref) }
            g[4] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = linkColor.copy(alpha = 0.10f))) { append(g[4]!!.value) }
            g[5] != null -> {
                val anchor = g[6]!!.value
                val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                withLink(LinkAnnotation.Clickable(anchor, style) { onXref(anchor) }) {
                    append(g[5]!!.value, linkColor, onXref)
                }
            }
            g[7] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[7]!!.value, linkColor, onXref) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** Plain text with the markdown markers dropped, for one-line previews. */
fun plain(text: String): String = TOKEN.replace(text) { m ->
    m.groups[1]?.value?.let { "[$it]" } ?: m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value
        ?: m.groups[5]?.value ?: m.groups[7]?.value ?: ""
}
