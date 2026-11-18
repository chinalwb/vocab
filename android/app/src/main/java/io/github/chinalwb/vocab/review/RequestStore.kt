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

/**
 * One 待补充 item: [w] the word or phrase (required), [note] anything else (≤ [REQUEST_NOTE_MAX]),
 * [c] when it was added, [t] its last change. A delete is kept (del = true) so 同步 carries it too.
 */
@Serializable
data class Request(val w: String, val note: String = "", val c: Long, val t: Long, val del: Boolean = false)

/** {id: Request} — the page keeps the same map in vk.requests. */
typealias Requests = Map<String, Request>

const val REQUEST_WORD_MAX = 200
const val REQUEST_NOTE_MAX = 600

/** The items still waiting, newest first: not deleted, and no entry carries their id yet. */
fun Requests.pending(answered: Set<String>): List<Pair<String, Request>> =
    filter { (id, r) -> r.w.isNotBlank() && !r.del && id !in answered }.toList().sortedByDescending { it.second.c }

/** Base-36 time + 4 random characters, like the page's: a–z 0–9, what build.py accepts in `- 请求:`. */
fun newRequestId(): String =
    java.lang.Long.toString(System.currentTimeMillis(), 36) + (1..4).map { "0123456789abcdefghijklmnopqrstuvwxyz".random() }.joinToString("")

/**
 * 待补充 (用户 2026-11-18): a word I meet away from my desk. 新建 adds it with a note; it lists on
 * top of 浏览 and syncs; back at the Mac, Claude reads progress.json and writes one entry per item
 * with a `- 请求:<id>` line. Once that entry is published (Entry.req) the item leaves the list.
 * Lives on this device (filesDir/requests.json); 同步 copies it to GitHub, newest wins per item.
 */
class RequestStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "requests.json")
    private val mutex = Mutex()
    private val _data = MutableStateFlow<Requests>(emptyMap())
    val data: StateFlow<Requests> = _data.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = runCatching { json.decodeFromString<Requests>(file.readText()) }.getOrNull() ?: emptyMap()
        }
    }

    /** A new item ([id] null) or an edit; false when nothing changed, so there is nothing to sync. */
    suspend fun put(id: String?, word: String, note: String): Boolean {
        val w = word.trim().replace(Regex("\\s+"), " ").take(REQUEST_WORD_MAX)
        val n = note.trim().take(REQUEST_NOTE_MAX)
        if (w.isEmpty()) return false
        val old = id?.let { _data.value[it] }
        if (old != null && old.w == w && old.note == n) return false
        val now = System.currentTimeMillis()
        replace { it + ((id ?: newRequestId()) to Request(w, n, old?.c ?: now, now)) }
        return true
    }

    suspend fun delete(id: String): Boolean {
        val old = _data.value[id] ?: return false
        replace { it + (id to old.copy(t = System.currentTimeMillis(), del = true)) }
        return true
    }

    suspend fun replace(merge: (Requests) -> Requests) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = merge(_data.value)
            file.writeText(json.encodeToString(_data.value))
        }
    }
}
