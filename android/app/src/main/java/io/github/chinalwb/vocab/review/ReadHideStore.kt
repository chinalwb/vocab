package io.github.chinalwb.vocab.review

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** {anchor: Fav(on = removed, t)} — a restore is kept (on = false) so 同步 carries it too. */
typealias ReadsHidden = Map<String, Fav>

/** The 阅读 items removed from the list. */
fun ReadsHidden.removed(): Set<String> = filterValues { it.on }.keys

/**
 * 阅读 items I've removed from the list (用户 2026-10-23). readlist.md keeps them — the app can't
 * edit the repo — so this only hides them, and 恢复 brings one back. Lives on this device
 * (filesDir/reads_hidden.json); 同步 copies it to GitHub, newest wins per item, the same as ⭐.
 */
class ReadHideStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "reads_hidden.json")
    private val mutex = Mutex()
    private val _data = MutableStateFlow<ReadsHidden>(emptyMap())
    val data: StateFlow<ReadsHidden> = _data.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = runCatching { json.decodeFromString<ReadsHidden>(file.readText()) }.getOrNull() ?: emptyMap()
        }
    }

    suspend fun set(anchor: String, removed: Boolean) = replace { it + (anchor to Fav(removed, System.currentTimeMillis())) }

    suspend fun replace(merge: (ReadsHidden) -> ReadsHidden) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = merge(_data.value)
            file.writeText(json.encodeToString(_data.value))
        }
    }
}
