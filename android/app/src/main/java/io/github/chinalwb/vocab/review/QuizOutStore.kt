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

/** {anchor: {plain English of the example: Fav(on = left out, t)}} — 恢复 is kept (on = false) so 同步 carries it. */
typealias QuizOut = Map<String, Map<String, Fav>>

/** The examples left out, by anchor: the shape quizExamples(excluded) takes. */
fun QuizOut.leftOut(): Map<String, Set<String>> =
    mapValues { (_, m) -> m.filterValues { it.on }.keys }.filterValues { it.isNotEmpty() }

/**
 * 不再考这句 (long-press on an example; 用户 2026-10-12 as 移出自测, 2026-10-23 renamed and synced):
 * the examples no stage asks any more. Lives on this device (filesDir/quizopt.json); 同步 copies
 * it to GitHub, newest wins per sentence, the same as ⭐ (template.html keeps vk.quizout).
 * The first load takes over what SharedPreferences "quizopt" held before.
 */
class QuizOutStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "quizopt.json")
    private val mutex = Mutex()
    private val _data = MutableStateFlow<QuizOut>(emptyMap())
    val data: StateFlow<QuizOut> = _data.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = runCatching { json.decodeFromString<QuizOut>(file.readText()) }.getOrNull() ?: migrate()
        }
    }

    // the old store: {anchor: set of plain English}, no times — t = 1, so any later change wins
    private fun migrate(): QuizOut {
        val prefs = context.getSharedPreferences("quizopt", Context.MODE_PRIVATE)
        val old = prefs.all.mapValues { (_, v) -> (v as? Set<*>)?.filterIsInstance<String>().orEmpty().associateWith { Fav(true, 1) } }
            .filterValues { it.isNotEmpty() }
        if (old.isNotEmpty()) file.writeText(json.encodeToString(old))
        return old
    }

    suspend fun set(anchor: String, en: String, out: Boolean) = replace { all ->
        all + (anchor to (all[anchor].orEmpty() + (en to Fav(out, System.currentTimeMillis()))))
    }

    suspend fun replace(merge: (QuizOut) -> QuizOut) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = merge(_data.value)
            file.writeText(json.encodeToString(_data.value))
        }
    }
}
