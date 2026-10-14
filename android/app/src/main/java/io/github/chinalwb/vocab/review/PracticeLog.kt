package io.github.chinalwb.vocab.review

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * One sentence practised on a day (用户 2026-10-14): the Chinese prompt, what I wrote ("" = I
 * looked without writing), and the answer it was checked against. From the entry page's 自测,
 * 复习 and 考核 — anything with an answer to pair with; what I grade myself isn't recorded.
 */
data class PracticeItem(
    val anchor: String,
    /** the entry's title, for the record */
    val title: String,
    val zh: String,
    val mine: String,
    val answer: String,
    val ok: Boolean,
    /** "entry" (条目页), "review" (复习), "test" (考核) — as in Attempt.via */
    val via: String,
    /** HH:mm */
    val time: String,
) {
    /** The same question asked again that day replaces this one: an entry's sentence, by its Chinese. */
    val key: String get() = "$anchor|$zh"
}

data class PracticeDay(val date: LocalDate, val items: List<PracticeItem>)

private val VIA = mapOf("entry" to "条目页", "review" to "复习", "test" to "考核")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

private fun oneLine(s: String) = s.replace(Regex("\\s*\\n\\s*"), " ").trim()

/**
 * The day as Markdown — this is the file itself, readable on its own:
 *
 *     ## 我们应该利用现有的工具。
 *
 *     - 条目:make use of(make-use-of)
 *     - 来源:考核
 *     - 时间:22:41
 *     - 结果:没对
 *     - 我写的:We should make use of the tool we already have.
 *     - 答案:We should make use of the tools we already have.
 */
fun renderPractice(day: PracticeDay): String = buildString {
    append("# 练习 · ${day.date}\n\n")
    append("> 同一句一天里练了几次,只留最后一次。\n")
    day.items.forEach { i ->
        append("\n## ${oneLine(i.zh)}\n\n")
        append("- 条目:${oneLine(i.title)}(${i.anchor})\n")
        append("- 来源:${VIA[i.via] ?: i.via}\n")
        append("- 时间:${i.time}\n")
        append("- 结果:${when { i.mine.isBlank() -> "没写"; i.ok -> "答对"; else -> "没对" }}\n")
        append("- 我写的:${if (i.mine.isBlank()) "(没写)" else oneLine(i.mine)}\n")
        append("- 答案:${oneLine(i.answer)}\n")
    }
}

// "- 条目:deserted(vs desert / dessert)(deserted)": the anchor is in the last brackets
private val ENTRY_LINE = Regex("""^- 条目:(.*)\((.*)\)$""")

/** Reads back what [renderPractice] wrote; anything it doesn't recognise is skipped. */
fun parsePractice(text: String, date: LocalDate): PracticeDay {
    val items = mutableListOf<PracticeItem>()
    var zh: String? = null
    val f = mutableMapOf<String, String>()
    fun flush() {
        val q = zh ?: return
        val m = ENTRY_LINE.find("- 条目:" + (f["条目"] ?: ""))
        val anchor = m?.groupValues?.get(2).orEmpty()
        val result = f["结果"].orEmpty()
        if (anchor.isNotEmpty() && f["答案"] != null) items += PracticeItem(
            anchor = anchor,
            title = m?.groupValues?.get(1).orEmpty(),
            zh = q,
            mine = if (result == "没写") "" else f["我写的"].orEmpty(),
            answer = f["答案"].orEmpty(),
            ok = result == "答对",
            via = VIA.entries.firstOrNull { it.value == f["来源"] }?.key ?: f["来源"].orEmpty(),
            time = f["时间"].orEmpty(),
        )
        zh = null
        f.clear()
    }
    text.lines().forEach { line ->
        when {
            line.startsWith("## ") -> { flush(); zh = line.removePrefix("## ").trim() }
            line.startsWith("- ") && zh != null -> {
                val body = line.removePrefix("- ")
                val at = body.indexOf(':')
                if (at > 0) f[body.substring(0, at)] = body.substring(at + 1)
            }
        }
    }
    flush()
    return PracticeDay(date, items)
}

/**
 * The practice record, one Markdown file a day (filesDir/practice/2026-10-15.md). Only on this
 * phone — 同步 leaves it alone, like the text of 自测 attempts — until I export or share it.
 */
class PracticeLog(context: Context) {
    private val dir = File(context.filesDir, "practice")
    private val mutex = Mutex()
    private val _today = MutableStateFlow(PracticeDay(LocalDate.now(), emptyList()))
    /** Today's sentences, oldest first. */
    val today: StateFlow<PracticeDay> = _today.asStateFlow()

    fun file(date: LocalDate) = File(dir, "$date.md")

    private fun read(date: LocalDate): PracticeDay =
        runCatching { parsePractice(file(date).readText(), date) }.getOrDefault(PracticeDay(date, emptyList()))

    /** Loads today's file — again after midnight, when the day has moved on. */
    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { _today.value = read(LocalDate.now()) }
    }

    /** Adds [item] to today; one asked before today keeps only this, the latest, at the end. */
    suspend fun record(item: PracticeItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = LocalDate.now()
            val day = _today.value.takeIf { it.date == now } ?: read(now)
            val next = PracticeDay(now, day.items.filter { it.key != item.key } + item)
            dir.mkdirs()
            file(now).writeText(renderPractice(next))
            _today.value = next
        }
    }

    companion object {
        fun now(): String = LocalTime.now().format(TIME)
    }
}
