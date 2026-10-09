package io.github.chinalwb.vocab.review

import android.content.Context
import io.github.chinalwb.vocab.data.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

/** 进度: 学习中 → 自测 → 已掌握, like 百词斩's 斩. [key] is what stage.json / vk.stage store. */
enum class Stage(val key: String, val label: String) {
    Learn("learn", "学习中"),
    Test("test", "自测"),
    Done("done", "已掌握"),
}

/** Only moves are stored: a 自测 entry starts in 自测, everything else in 学习中 — same as stageOf() in template.html. */
fun Map<String, String>.stageOf(e: Entry): Stage =
    Stage.entries.firstOrNull { it.key == this[e.anchor] } ?: if (e.level == "SELFTEST") Stage.Test else Stage.Learn

/** stage.json: the moves, and when each was made (ms) so 同步 can let the newest win. */
@Serializable
data class StageData(val stages: Map<String, String> = emptyMap(), val t: Map<String, Long> = emptyMap())

/**
 * Where I've moved each entry, keyed by anchor — the page keeps the same maps in localStorage
 * (vk.stage / vk.stage.t). Lives on this device (filesDir/stage.json); 同步 copies it to GitHub.
 */
class StageStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "stage.json")
    private val mutex = Mutex()
    private val _data = MutableStateFlow(StageData())
    val data: StateFlow<StageData> = _data.asStateFlow()
    private val _state = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Just the moves, for stageOf(). */
    val state: StateFlow<Map<String, String>> = _state.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val text = runCatching { file.readText() }.getOrNull()
            _data.value = text?.let {
                runCatching {
                    // v1.9 wrote the bare {anchor: stage} map; ignoreUnknownKeys would read that as empty
                    if (json.parseToJsonElement(it).jsonObject.containsKey("stages")) json.decodeFromString<StageData>(it)
                    else StageData(json.decodeFromString<Map<String, String>>(it))
                }.getOrNull()
            } ?: StageData()
            _state.value = _data.value.stages
        }
    }

    suspend fun set(anchor: String, stage: Stage) = replace {
        StageData(it.stages + (anchor to stage.key), it.t + (anchor to System.currentTimeMillis()))
    }

    suspend fun replace(merge: (StageData) -> StageData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = merge(_data.value)
            _state.value = _data.value.stages
            file.writeText(json.encodeToString(_data.value))
        }
    }
}
