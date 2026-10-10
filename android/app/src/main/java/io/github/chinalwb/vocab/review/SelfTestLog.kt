package io.github.chinalwb.vocab.review

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One reveal of a 自测 entry. A blank [text] is a 偷看 (looked at the answer without writing). */
@Serializable
data class Attempt(val t: Long, val text: String, val ok: Boolean, /** "entry", "review" or "selftest" (the 自测 tab) */ val via: String)

data class AttemptSummary(val tries: Int, val ok: Int, val peeks: Int, val last: Attempt?)

fun List<Attempt>.summary(): AttemptSummary {
    val tries = filter { it.text.isNotEmpty() }
    return AttemptSummary(tries.size, tries.count { it.ok }, size - tries.size, lastOrNull())
}

const val MAX_PER_ENTRY = 50

/**
 * 自测 attempt history, keyed by anchor — the same record the page keeps in localStorage
 * (vk.selftest). Lives only on this device (filesDir/selftest.json), never in the repo.
 */
class SelfTestLog(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "selftest.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow<Map<String, List<Attempt>>>(emptyMap())
    val state: StateFlow<Map<String, List<Attempt>>> = _state.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.value = runCatching { json.decodeFromString<Map<String, List<Attempt>>>(file.readText()) }
                .getOrDefault(emptyMap())
        }
    }

    /** 同步: swap in the merged log. */
    suspend fun replace(merge: (Map<String, List<Attempt>>) -> Map<String, List<Attempt>>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.value = merge(_state.value)
            file.writeText(json.encodeToString(_state.value))
        }
    }

    suspend fun record(anchor: String, attempt: Attempt) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = (_state.value[anchor].orEmpty() + attempt).takeLast(MAX_PER_ENTRY)
            _state.value = _state.value + (anchor to list)
            file.writeText(json.encodeToString(_state.value))
        }
    }
}
