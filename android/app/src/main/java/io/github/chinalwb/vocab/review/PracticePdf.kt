package io.github.chinalwb.vocab.review

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import io.github.chinalwb.vocab.ui.bestDiff
import java.io.File

/*
 * 导出 PDF (用户 2026-10-14): a day's practice as one A4 file in three parts, each from a new
 * page — 一、答案 (the Chinese and the English), 二、对照 (what I wrote against the answer, extra or
 * wrong words struck through in red, missed ones highlighted), 三、只有中文 (the Chinese with lines
 * to write the English by hand). Drawn with the system fonts, so the Chinese needs nothing bundled.
 */

private const val PAGE_W = 595 // A4 in points
private const val PAGE_H = 842
private const val MARGIN_X = 54f
private const val MARGIN_TOP = 60f
private const val MARGIN_BOTTOM = 64f
private const val INDENT = 22f

private val INK = Color.rgb(0x1F, 0x1F, 0x1E)
private val MUTED = Color.rgb(0x6B, 0x69, 0x5F)
private val RULE = Color.rgb(0xC9, 0xC6, 0xBC)
private val ERR = Color.rgb(0xC2, 0x41, 0x2D)
private val MISSED = Color.argb(0x66, 0xE6, 0xB3, 0x5C)

private fun paint(size: Float, bold: Boolean = false, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    this.color = color
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}

/** Lays pages out top to bottom, starting a new one when the next block doesn't fit. */
private class Pages(private val doc: PdfDocument, private val date: String) {
    private val width = PAGE_W - MARGIN_X * 2
    private var page: PdfDocument.Page? = null
    private var no = 0
    var y = 0f
        private set
    val canvas: Canvas get() = page!!.canvas

    fun newPage() {
        page?.let { finish(it) }
        no++
        page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, no).create())
        y = MARGIN_TOP
    }

    private fun finish(p: PdfDocument.Page) {
        val foot = paint(8.5f, color = MUTED)
        p.canvas.drawText("VoCab 练习 · $date", MARGIN_X, PAGE_H - 32f, foot)
        val n = "$no"
        p.canvas.drawText(n, PAGE_W - MARGIN_X - foot.measureText(n), PAGE_H - 32f, foot)
        doc.finishPage(p)
    }

    fun close() { page?.let { finish(it) }; page = null }

    fun layout(text: CharSequence, p: TextPaint, indent: Float = 0f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, (width - indent).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.25f)
            .setIncludePad(false)
            .build()

    /** Draws [rows] (each a layout at an indent) together on one page, then leaves [after] below. */
    fun block(rows: List<Pair<StaticLayout, Float>>, extra: Float = 0f, after: Float = 14f, draw: (Canvas, Float) -> Unit = { _, _ -> }) {
        val h = rows.sumOf { it.first.height.toDouble() }.toFloat() + extra
        if (page == null || (y + h > PAGE_H - MARGIN_BOTTOM && y > MARGIN_TOP)) newPage()
        rows.forEach { (l, indent) ->
            canvas.save()
            canvas.translate(MARGIN_X + indent, y)
            l.draw(canvas)
            canvas.restore()
            y += l.height
        }
        draw(canvas, y)
        y += extra + after
    }
}

/** What I wrote with the extra or wrong words struck through in red; the answer with the missed ones highlighted. */
private fun marked(mine: String, answer: String): Pair<CharSequence, CharSequence> {
    val d = bestDiff(mine, answer)
    fun build(words: List<String>, keep: Set<Int>, span: () -> List<Any>): CharSequence {
        val b = SpannableStringBuilder()
        words.forEachIndexed { i, w ->
            if (i > 0) b.append(' ')
            val start = b.length
            b.append(w)
            if (i !in keep) span().forEach { b.setSpan(it, start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
        return b
    }
    return build(d.mine, d.keepMine) { listOf(StrikethroughSpan(), ForegroundColorSpan(ERR)) } to
        build(d.answer, d.keepAnswer) { listOf(BackgroundColorSpan(MISSED)) }
}

private fun labelled(label: String, text: CharSequence): CharSequence =
    SpannableStringBuilder().apply {
        append(label)
        setSpan(ForegroundColorSpan(MUTED), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(text)
    }

/** Writes [day] as a PDF into the cache (exports/) and returns the file, ready to share. */
fun writePracticePdf(context: Context, day: PracticeDay): File {
    val date = day.date.toString()
    val doc = PdfDocument()
    val pages = Pages(doc, date)
    val title = paint(20f, bold = true)
    val sub = paint(10.5f, color = MUTED)
    val part = paint(15f, bold = true)
    val zh = paint(12.5f, bold = true)
    val en = paint(12f)
    val items = day.items
    val ok = items.count { it.mine.isNotBlank() && it.ok }
    val num = { i: Int -> "${i + 1}. " }

    fun partHead(name: String, note: String, first: Boolean = false) {
        if (!first) pages.newPage()
        if (first) {
            pages.block(listOf(pages.layout("VoCab 练习 · $date", title) to 0f), after = 4f)
            pages.block(listOf(pages.layout("共 ${items.size} 句 · 答对 $ok 句 · 同一句一天里练了几次,只留最后一次", sub) to 0f), after = 22f)
        }
        pages.block(listOf(pages.layout(name, part) to 0f), after = 4f)
        pages.block(listOf(pages.layout(note, sub) to 0f), after = 16f)
    }

    // 一、答案: the Chinese, the English under it
    partHead("一、答案(${items.size} 句)", "中文和正确的英文。", first = true)
    items.forEachIndexed { i, it ->
        pages.block(listOf(pages.layout(num(i) + it.zh, zh) to 0f, pages.layout(it.answer, en, INDENT) to INDENT))
    }

    // 二、对照: what I wrote against the answer
    partHead("二、对照", "我写的(红色划掉 = 多出来或写错的)和答案(高亮 = 漏掉或写错的)。")
    items.forEachIndexed { i, it ->
        val verdict = when { it.mine.isBlank() -> "没写"; it.ok -> "答对"; else -> "没对" }
        val (mine, answer) = if (it.mine.isBlank()) ("(没写就看了答案)" as CharSequence) to (it.answer as CharSequence)
        else if (it.ok) (it.mine as CharSequence) to (it.answer as CharSequence)
        else marked(it.mine, it.answer)
        val head = SpannableStringBuilder(num(i) + it.zh + "  ").apply {
            val at = length
            append(verdict)
            setSpan(ForegroundColorSpan(if (verdict == "答对") MUTED else ERR), at, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.NORMAL), at, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        pages.block(
            listOf(
                pages.layout(head, zh) to 0f,
                pages.layout(labelled("我写的:", mine), en, INDENT) to INDENT,
                pages.layout(labelled("答  案:", answer), en, INDENT) to INDENT,
            ),
        )
    }

    // 三、只有中文: the Chinese, with lines to write the English on
    partHead("三、只有中文", "不看答案,把英文写在横线上。")
    val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RULE; strokeWidth = 0.8f }
    items.forEachIndexed { i, it ->
        val lines = (it.answer.length / 60 + 1).coerceIn(1, 3) + 1
        val step = 24f
        pages.block(listOf(pages.layout(num(i) + it.zh, zh) to 0f), extra = lines * step, after = 10f) { c, top ->
            for (k in 1..lines) {
                val ly = top + k * step
                c.drawLine(MARGIN_X + INDENT, ly, PAGE_W - MARGIN_X, ly, rule)
            }
        }
    }
    pages.close()

    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val out = File(dir, "vocab-practice-$date.pdf")
    out.outputStream().use { doc.writeTo(it) }
    doc.close()
    return out
}

/** 保存到「下载」 (Android 10+, no permission needed): a copy of [file] in the Downloads folder; returns its name there. */
@androidx.annotation.RequiresApi(29)
fun savePdfToDownloads(context: Context, file: File): String {
    val resolver = context.contentResolver
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, file.name)
        put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
        put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("没能在「下载」里建文件")
    resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    values.clear()
    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return file.name
}
