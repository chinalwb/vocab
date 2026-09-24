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
    val entries: List<Entry>,
) {
    @Transient
    val byAnchor: Map<String, Entry> = entries.associateBy { it.anchor }
}

@Serializable
data class Entry(
    val anchor: String,
    val title: String,
    /** A1–C2, TERM, GRAMMAR or SENTENCE — see detect_level in build.py. */
    val level: String,
    val ipa: String = "",
    val pos: String = "",
    val cefr: String = "",
    val date: String = "",
    val gloss: String = "",
    val blocks: List<Block> = emptyList(),
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
