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
