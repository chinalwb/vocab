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
import kotlinx.serialization.json.Json
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

/**
 * Where I've moved each entry, keyed by anchor — the page keeps the same map in localStorage
 * (vk.stage). Lives only on this device (filesDir/stage.json), never in the repo.
 */
class StageStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "stage.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow<Map<String, String>>(emptyMap())
    val state: StateFlow<Map<String, String>> = _state.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.value = runCatching { json.decodeFromString<Map<String, String>>(file.readText()) }
                .getOrDefault(emptyMap())
        }
    }

    suspend fun set(anchor: String, stage: Stage) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.value = _state.value + (anchor to stage.key)
            file.writeText(json.encodeToString(_state.value))
        }
    }
}
