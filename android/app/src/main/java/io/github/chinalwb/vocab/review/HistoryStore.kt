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

/** How many entries 学习历史 keeps — the page's HIST_MAX. */
const val HISTORY_MAX = 100

/** I was on entry [a] at [t]: opened it ("entry"), or met it in a 复习 / 考核 run ("review" / "test"). */
@Serializable
data class Visit(val a: String, val t: Long, val via: String)

/** The visits, newest first, one per entry; [clearAt]: 清空 — anything older is dropped on every device. */
@Serializable
data class HistoryData(val visits: List<Visit> = emptyList(), val clearAt: Long = 0)

/** Newest first, one row per entry, nothing from before [clearAt], at most [HISTORY_MAX]. */
fun trimHistory(visits: List<Visit>, clearAt: Long): List<Visit> =
    visits.filter { it.t > clearAt }.sortedByDescending { it.t }.distinctBy { it.a }.take(HISTORY_MAX)

/**
 * 学习历史 (用户 2026-10-16): the last [HISTORY_MAX] entries I've been on. Lives on this device
 * (filesDir/history.json); 同步 copies it to GitHub, newest wins per entry. Same as vk.hist in template.html.
 */
class HistoryStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "history.json")
    private val mutex = Mutex()
    private val _data = MutableStateFlow(HistoryData())
    val data: StateFlow<HistoryData> = _data.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = runCatching { json.decodeFromString<HistoryData>(file.readText()) }.getOrNull() ?: HistoryData()
        }
    }

    suspend fun visit(anchor: String, via: String) = replace { h ->
        h.copy(visits = trimHistory(listOf(Visit(anchor, System.currentTimeMillis(), via)) + h.visits, h.clearAt))
    }

    suspend fun clear() = replace { HistoryData(clearAt = System.currentTimeMillis()) }

    suspend fun replace(merge: (HistoryData) -> HistoryData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = merge(_data.value)
            file.writeText(json.encodeToString(_data.value))
        }
    }
}
