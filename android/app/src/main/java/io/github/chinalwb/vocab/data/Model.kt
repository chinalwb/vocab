package io.github.chinalwb.vocab.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator

/** The data.json schema this app understands; must match SCHEMA in build.py. */
const val SUPPORTED_SCHEMA = 1

/** meta.json — polled to decide whether data.json needs downloading. */
@Serializable
data class Meta(
    val schema: Int,
    val hash: String,
    val count: Int,
    val generated: String,
)

@Serializable
data class VocabData(
    val schema: Int,
    val hash: String,
    val count: Int,
    val generated: String,
    /** 错题本 categories in display order — MISTAKE_TYPES in build.py. */
    val mistakeTypes: List<MistakeType> = emptyList(),
    val entries: List<Entry>,
    /** 阅读 (readlist.md): common vs C1 phrasing, read only; newest first. Absent in older data. */
    val reads: List<Read> = emptyList(),
) {
    @Transient
    val byAnchor: Map<String, Entry> = entries.associateBy { it.anchor }
}

@Serializable
data class Entry(
    val anchor: String,
    /** Its number in the ## 目录 list — shown as #12 and searchable. 0 in data from before it existed. */
    val no: Int = 0,
    val title: String,
    /** A1–C2, TERM, GRAMMAR or SENTENCE — see detect_level in build.py. */
    val level: String,
    val ipa: String = "",
    val pos: String = "",
    val cefr: String = "",
    val date: String = "",
    /** "write" (会写: practise producing it) or "read" (认识) — see mastery() in build.py. */
    val mastery: String = "read",
    val gloss: String = "",
    val blocks: List<Block> = emptyList(),
    /** The user's own mistakes in this entry — the tagged ❌/⚠️ bullets. */
    val mistakes: List<Mistake> = emptyList(),
    val hash: String = "",
) {
    @Transient
    val searchText: String = buildString {
        append(title).append('\n').append(anchor).append('\n').append(gloss)
        blocks.forEach { b ->
            when (b) {
                is Block.Para -> append('\n').append(b.text)
                is Block.Bullets -> b.items.forEach { append('\n').append(it) }
                is Block.Examples -> b.items.forEach { append('\n').append(it.en).append('\n').append(it.zh) }
                is Block.Quote -> b.lines.forEach { append('\n').append(it) }
            }
        }
    }.lowercase()

    /** For 句子 entries: the user's original sentence, shown as the review prompt. */
    val originalSentence: String?
        get() = blocks.firstNotNullOfOrNull { (it as? Block.Quote)?.lines?.joinToString("\n") }

    val writes: Boolean get() = mastery == "write"

    /** The notes split at their bold labels, one card each — see [splitSections]. */
    @Transient
    val sections: List<Section> = splitSections(blocks)
}

/** One 阅读 item: [title] is the meaning in Chinese; [blocks] hold 常用说法 / C1 说法 / 差别. */
@Serializable
data class Read(
    val anchor: String,
    val title: String,
    val date: String = "",
    val blocks: List<Block> = emptyList(),
    val hash: String = "",
)

/** One card of the notes carousel: [label] is its tab ("释义", "义项一", "常见搭配"). */
data class Section(val label: String, val blocks: List<Block>)

// A bold label opens a section: "**常见搭配:**", "**含义:** text", "**义项一:…**", "**race**(动词)".
private val SECTION_RE = Regex("""^\*\*([^*]+?)[:：]\s*\*\*|^\*\*([^*]+?)\*\*\s*(?:[(（][^)）]*[)）])?$""")
// consecutive definition sections share one card
private val DEFINITION = setOf("English definition", "含义", "释义", "整体释义")

private fun sectionLabel(text: String): String? {
    val m = SECTION_RE.find(text) ?: return null
    val label = (m.groups[1] ?: m.groups[2])!!.value.replace("⚠️", "").trim()
    return label.split(Regex("[:：(（—]"))[0].trim()
}

/**
 * Mirrors sections() in build.py — keep the two in step. Whatever comes before the first label
 * is 概要; English definition + 含义 become one 释义 card; long labels are cut to 10 characters.
 */
fun splitSections(blocks: List<Block>): List<Section> {
    val out = mutableListOf<Pair<String, MutableList<Block>>>()
    for (b in blocks) {
        var label = (b as? Block.Para)?.let { sectionLabel(it.text) }
        if (label != null && label in DEFINITION) label = if (out.lastOrNull()?.first == "释义") null else "释义"
        if (label != null || out.isEmpty()) {
            val l = label ?: "概要"
            out += (if (l.length > 11) l.take(10) + "…" else l) to mutableListOf()
        }
        out.last().second += b
    }
    return out.map { Section(it.first, it.second) }
}

/** Mirrors parse_blocks in build.py. Text fields keep their inline markdown. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("t")
sealed interface Block {
    @Serializable @SerialName("p")
    data class Para(val text: String) : Block

    @Serializable @SerialName("ul")
    data class Bullets(val items: List<String>) : Block

    @Serializable @SerialName("ol")
    data class Examples(val items: List<Example>) : Block

    @Serializable @SerialName("quote")
    data class Quote(val lines: List<String>) : Block
}

@Serializable
data class Example(val en: String, val zh: String = "")

@Serializable
data class MistakeType(val key: String, val desc: String = "", /** 发送前自检 question */ val check: String = "")

/** One tagged bullet: [sev] is "error" (❌) or "warn" (⚠️); [text] keeps its inline markdown. */
@Serializable
data class Mistake(
    val sev: String,
    val type: String,
    val text: String,
    /** The 我的原句 quote the bullet belongs to; empty when the entry has none. */
    val original: String = "",
)
