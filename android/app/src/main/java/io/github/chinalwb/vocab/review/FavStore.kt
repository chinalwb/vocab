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

/** One example sentence's ⭐: [on] = starred; an unstar is kept (on = false) so 同步 carries it too. */
@Serializable
data class Fav(val on: Boolean, val t: Long)

/** {anchor: {plain English of the example: Fav}} — the page keeps the same map in vk.favs. */
typealias Favs = Map<String, Map<String, Fav>>

/** The starred sentences by anchor, without the unstars. */
fun Favs.starred(): Map<String, Set<String>> =
    mapValues { (_, m) -> m.filterValues { it.on }.keys }.filterValues { it.isNotEmpty() }

/**
 * Example sentences I've starred (long-press → 收藏). Lives on this device (filesDir/favs.json);
 * 同步 copies it to GitHub, newest wins per sentence.
 */
class FavStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "favs.json")
    private val mutex = Mutex()
    private val _data = MutableStateFlow<Favs>(emptyMap())
    val data: StateFlow<Favs> = _data.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = runCatching { json.decodeFromString<Favs>(file.readText()) }.getOrNull() ?: emptyMap()
        }
    }

    suspend fun toggle(anchor: String, en: String) = replace { all ->
        val m = all[anchor].orEmpty()
        all + (anchor to (m + (en to Fav(m[en]?.on != true, System.currentTimeMillis()))))
    }

    suspend fun replace(merge: (Favs) -> Favs) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _data.value = merge(_data.value)
            file.writeText(json.encodeToString(_data.value))
        }
    }
}
