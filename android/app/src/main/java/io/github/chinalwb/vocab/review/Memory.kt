package io.github.chinalwb.vocab.review

import java.time.Instant
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln

/**
 * 记忆曲线 (用户 2026-10-15): an entry's estimated forgetting curve (Ebbinghaus), replayed from its
 * 复习 / 考核 history — the entry page's 自测 doesn't count, as it doesn't grade SM-2 either. An
 * estimate, not a measurement: SM-2 sets the next review for when recall would be down to about
 * [RECALL_AT_DUE], so after each graded day the memory's stability is S = interval / ln(1 / RECALL_AT_DUE)
 * and recall decays as e^(−t/S). A day counts as right only if every sentence answered that day was.
 * Same model as memoryOf() in template.html.
 */
const val RECALL_AT_DUE = 0.9

/** One graded day (LocalDate epoch day); [interval] is what SM-2 gave it, in days. */
data class MemoryPoint(val day: Long, val ok: Boolean, val interval: Int)

fun memoryOf(attempts: List<Attempt>, resetAt: Long): List<MemoryPoint> {
    val days = sortedMapOf<Long, Boolean>()
    attempts.filter { it.via != "entry" && it.t >= resetAt }.forEach {
        val d = Instant.ofEpochMilli(it.t).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        days[d] = (days[d] ?: true) && it.ok
    }
    var c = Card()
    return days.map { (day, ok) ->
        c = schedule(c, if (ok) Grade.Good else Grade.Again, day)
        MemoryPoint(day, ok, c.interval)
    }
}

/** Estimated recall on [day] (fractional days allowed); 1 before the first graded day. */
fun List<MemoryPoint>.recallOn(day: Double): Double {
    val p = lastOrNull { it.day <= day } ?: return 1.0
    return exp(-(day - p.day) / (p.interval / ln(1 / RECALL_AT_DUE)))
}
