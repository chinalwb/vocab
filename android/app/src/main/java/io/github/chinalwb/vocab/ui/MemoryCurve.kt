package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chinalwb.vocab.review.MemoryPoint
import io.github.chinalwb.vocab.review.RECALL_AT_DUE
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.recallOn
import io.github.chinalwb.vocab.review.today
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private fun fmtDay(day: Long) = LocalDate.ofEpochDay(day).let { "${it.monthValue}/${it.dayOfMonth}" }

/**
 * The 记忆 card after an entry's notes (用户 2026-10-15): how much of it I probably still remember,
 * when it's next due, the estimated forgetting curve, and every graded day — the same card as
 * memorySlide() in template.html. [points] come from review/Memory.kt; [due] is the SM-2 card's
 * next day while the entry is in 复习; [ring] is the card's colour (the dots' gap ring).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryCard(points: List<MemoryPoint>, stage: Stage, due: Long?, ring: Color) {
    val ink = MaterialTheme.colorScheme.onSurface
    val c = sheetColors()
    val note = TextStyle(fontSize = 13.sp, lineHeight = 21.sp, color = c.sub)
    if (points.isEmpty()) {
        Text("还没在复习或考核里做过这一条。做过一次之后,这里会画出它的遗忘曲线:每复习一次回到顶上,答对了往后掉得更慢。", style = note)
        return
    }
    val today = today()
    val now = (points.recallOn(today.toDouble()) * 100).roundToInt()
    val whenText = when {
        due == null -> when (stage) { Stage.Test -> "考核中,不按日子排"; Stage.Done -> "已掌握,不再排期"; else -> null }
        due > today -> "下次复习:${fmtDay(due)}(${due - today} 天后)"
        due == today -> "今天到期"
        else -> "已经过了 ${today - due} 天"
    }
    var picked by remember(points) { mutableStateOf<Long?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text("约 $now%", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.alignByBaseline())
            Text("  现在大概还记得", style = note, modifier = Modifier.alignByBaseline())
        }
        if (whenText != null) Text(whenText, style = note)
        // the readout for a tapped day; a hint until then (one line either way, so nothing jumps)
        val p = picked
        Text(
            if (p == null) AnnotatedString("点曲线上的某一天,看那天大概还记得多少")
            else buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append("约 ${(points.recallOn(p.toDouble()) * 100).roundToInt()}%") }
                append(" · ${if (p == today) "今天" else fmtDay(p)}")
                val graded = points.firstOrNull { it.day == p }
                if (graded != null) append(if (graded.ok) " · 复习答对" else " · 复习没对")
                else if (p > today) append(" · 不复习的话")
            },
            style = note,
            modifier = Modifier.padding(top = 8.dp),
        )
        MemoryChart(points, today, due, ink, c.sub, c.err, ring, picked, onPick = { picked = it },
            Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = "遗忘曲线:现在约 $now%" })
        Text("复习 ${points.size} 天 · 忘过 ${points.count { !it.ok }} 次", fontWeight = FontWeight.Bold, color = ink, fontSize = 14.sp)
        // the table view: every graded day
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            points.forEach {
                Text(buildAnnotatedString {
                    append(fmtDay(it.day) + " ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (it.ok) ink else c.err)) { append(if (it.ok) "✓" else "✗") }
                }, style = note)
            }
        }
        Text("按每次复习 / 考核的对错估算:SM-2 在估计还记得 90% 左右时安排下一次复习,答对一次,下一段就掉得更慢。只是模型推算,不是测出来的;条目页的自测不算。", style = note)
    }
}

/**
 * The curve itself, the same drawing as paintMemory() in template.html: solid up to today, dashed
 * after (if I don't review it), a dot per graded day on the top line (✗ in the error colour),
 * today's dot on the curve. A tap picks the nearest day and draws a hairline there.
 */
@Composable
private fun MemoryChart(
    points: List<MemoryPoint>, today: Long, due: Long?,
    ink: Color, sub: Color, err: Color, ring: Color,
    picked: Long?, onPick: (Long) -> Unit, modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = AppFont, fontSize = 11.sp, color = sub)
    val last = points.last()
    val horizon = maxOf(today, last.day + last.interval, due ?: 0L)
    val x0 = points.first().day
    val x1 = max(x0 + 4, horizon + max(1L, ceil((horizon - x0) * 0.15).toLong()))
    // samples: every quarter day, plus each graded day's moment before (the bottom of the tooth)
    val xs = remember(points, x1) { generateSequence(x0.toDouble()) { it + 0.25 }.takeWhile { it <= x1 }.toList() }
    val pre = remember(points) { points.drop(1).map { it.day.toDouble() to points.recallOn(it.day - 1e-6) } }
    val lo = min(0.5, floor(min(xs.minOf { points.recallOn(it) }, pre.minOfOrNull { it.second } ?: 1.0) * 10) / 10)
    Canvas(modifier.pointerInput(points, x1) {
        detectTapGestures { o ->
            val l = 40.dp.toPx(); val r = 14.dp.toPx()
            onPick((x0 + (o.x - l) / (size.width - l - r) * (x1 - x0)).roundToLong().coerceIn(x0, x1))
        }
    }) {
        val l = 40.dp.toPx(); val r = 14.dp.toPx(); val t = 12.dp.toPx(); val b = 26.dp.toPx()
        fun x(d: Double) = (l + (d - x0) / (x1 - x0) * (size.width - l - r)).toFloat()
        fun y(v: Double) = (t + (1 - v) / (1 - lo) * (size.height - t - b)).toFloat()
        fun path(from: Double, to: Double): Path {
            val p = Path()
            var started = false
            for (d in (xs.filter { it in from..to } + to).distinct()) {
                pre.firstOrNull { it.first > from && abs(it.first - d) < 1e-9 }?.let { p.lineTo(x(d), y(it.second)) }
                if (started) p.lineTo(x(d), y(points.recallOn(d))) else { p.moveTo(x(d), y(points.recallOn(d))); started = true }
            }
            return p
        }
        val hair = 1.dp.toPx()
        listOf(1.0 to "100%", RECALL_AT_DUE to "90%", lo to "${(lo * 100).roundToInt()}%").forEach { (v, s) ->
            drawLine(ink.copy(alpha = 0.14f), Offset(l, y(v)), Offset(size.width - r, y(v)), hair)
            val m = measurer.measure(s, label)
            drawText(m, topLeft = Offset(l - 6.dp.toPx() - m.size.width, y(v) - m.size.height / 2f))
        }
        val end = min(today, x1).toDouble()
        val past = path(x0.toDouble(), end)
        drawPath(Path().apply { addPath(past); lineTo(x(end), y(lo)); lineTo(x(x0.toDouble()), y(lo)); close() }, ink.copy(alpha = 0.07f))
        val line = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(past, ink, style = line)
        if (today < x1) drawPath(
            path(today.toDouble(), x1.toDouble()), ink.copy(alpha = 0.5f),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))),
        )
        drawLine(ink.copy(alpha = 0.3f), Offset(x(today.toDouble()), t), Offset(x(today.toDouble()), size.height - b), hair)
        picked?.let { drawLine(ink.copy(alpha = 0.55f), Offset(x(it.toDouble()), t), Offset(x(it.toDouble()), size.height - b), hair) }
        // dots carry a ring in the card's colour, so they stay clear where they sit on the line
        fun dot(at: Offset, fill: Color, radius: Float) { drawCircle(ring, radius + 2.dp.toPx(), at); drawCircle(fill, radius, at) }
        points.forEach { dot(Offset(x(it.day.toDouble()), y(1.0)), if (it.ok) ink else err, 4.5.dp.toPx()) }
        dot(Offset(x(today.toDouble()), y(points.recallOn(today.toDouble()))), ink, 5.dp.toPx())
        // x labels: today first, then the due date, then the first day — each dropped if it would crowd one kept already
        val kept = mutableListOf<Long>()
        listOfNotNull(today to "今天", due?.takeIf { it > today }?.let { it to fmtDay(it) }, x0 to fmtDay(x0)).forEach { (d, s) ->
            if (kept.all { abs(x(it.toDouble()) - x(d.toDouble())) > 44.dp.toPx() }) {
                kept += d
                val m = measurer.measure(s, label)
                drawText(m, topLeft = Offset(x(d.toDouble()) - m.size.width / 2f, size.height - b + 6.dp.toPx()))
            }
        }
    }
}
