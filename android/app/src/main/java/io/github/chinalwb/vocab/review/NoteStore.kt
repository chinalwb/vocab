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

/** My note on an entry; a cleared one is kept (text = "") so 同步 carries the clear too. */
@Serializable
data class Note(val text: String, val t: Long)

/** {anchor: Note} — the page keeps the same map in vk.notes. */
typealias Notes = Map<String, Note>

/** The notes by anchor, without the cleared ones. */
fun Notes.written(): Map<String, String> = mapValues { it.value.text }.filterValues { it.isNotEmpty() }

/**
 * 我的笔记 (用户 2026-10-24): what I write on an entry myself, the last tab of its notes card.
 * Lives on this device (filesDir/notes.json); 同步 copies it to GitHub, newest wins per entry.
 */
class NoteStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "notes.json")
    private val mutex = Mutex()
    private val _data = MutableStateFlow<Notes>(emptyMap())
    val data: StateFlow<Notes> = _data.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = runCatching { json.decodeFromString<Notes>(file.readText()) }.getOrNull() ?: emptyMap()
        }
    }

    /** false when nothing changed (the same text), so there is nothing to sync */
    suspend fun set(anchor: String, text: String): Boolean {
        val t = text.trimEnd()
        if (t == _data.value[anchor]?.text.orEmpty()) return false
        replace { it + (anchor to Note(t, System.currentTimeMillis())) }
        return true
    }

    suspend fun replace(merge: (Notes) -> Notes) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = merge(_data.value)
            file.writeText(json.encodeToString(_data.value))
        }
    }
}
