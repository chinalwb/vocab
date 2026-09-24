package io.github.chinalwb.vocab.data

import android.content.Context
import io.github.chinalwb.vocab.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val BASE_URL = BuildConfig.VOCAB_URL

data class LibraryState(
    val data: VocabData? = null,
    /** Entries added since they were last opened — cleared as they're viewed. */
    val newAnchors: Set<String> = emptySet(),
    /** Entries whose content changed in a fetch — cleared as they're viewed. */
    val updatedAnchors: Set<String> = emptySet(),
    val lastChecked: Long? = null,
    val lastUpdated: Long? = null,
    val loadError: String? = null,
)

sealed interface CheckResult {
    data object UpToDate : CheckResult
    data class Updated(val added: Int, val changed: Int, val removed: Int) : CheckResult
    data class Failed(val message: String) : CheckResult
}

/** Pages answered 404 — build.py's JSON hasn't been deployed from main yet. */
private class NotPublished : IOException()

@Serializable
private data class SyncState(
    val newAnchors: Set<String> = emptySet(),
    val updatedAnchors: Set<String> = emptySet(),
    val lastChecked: Long? = null,
    val lastUpdated: Long? = null,
)

/**
 * Holds the vocabulary and keeps it in step with what GitHub Pages serves.
 *
 * A check fetches meta.json (a few dozen bytes) and only downloads data.json
 * when its hash differs from the local copy's, then diffs per-entry hashes to
 * work out what was added or edited since the last fetch.
 */
class VocabRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val dataFile = File(context.filesDir, "data.json")
    private val syncFile = File(context.filesDir, "sync.json")

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked() }
    }

    private fun loadLocked() {
        if (_state.value.data != null) return
        val data = runCatching {
            val text = if (dataFile.exists()) dataFile.readText()
            else context.assets.open("data.json").bufferedReader().use { it.readText() }
            json.decodeFromString<VocabData>(text)
        }
        val sync = runCatching { json.decodeFromString<SyncState>(syncFile.readText()) }
            .getOrDefault(SyncState())
        _state.value = LibraryState(
            data = data.getOrNull(),
            newAnchors = sync.newAnchors,
            updatedAnchors = sync.updatedAnchors,
            lastChecked = sync.lastChecked,
            lastUpdated = sync.lastUpdated,
            loadError = data.exceptionOrNull()?.let { "本地数据读取失败:${it.message}" },
        )
    }

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            try {
                checkLocked()
            } catch (e: NotPublished) {
                CheckResult.Failed("服务器上还没有词库数据(需要先把 dev 合并到 main)")
            } catch (e: IOException) {
                CheckResult.Failed("网络错误:${e.message ?: e.javaClass.simpleName}")
            } catch (e: Exception) {
                CheckResult.Failed("数据解析失败:${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun checkLocked(): CheckResult {
        val now = System.currentTimeMillis()
        val current = _state.value.data
        val meta = json.decodeFromString<Meta>(get("meta.json"))
        if (meta.schema > SUPPORTED_SCHEMA) {
            return CheckResult.Failed("词库格式已升级,请重新构建并安装新版 App")
        }
        if (current != null && meta.hash == current.hash) {
            _state.update { it.copy(lastChecked = now) }
            saveSync()
            return CheckResult.UpToDate
        }

        val text = get("data.json")
        val fresh = json.decodeFromString<VocabData>(text)
        val old = current?.entries?.associate { it.anchor to it.hash } ?: emptyMap()
        val freshAnchors = fresh.entries.mapTo(HashSet()) { it.anchor }
        val added = fresh.entries.filter { it.anchor !in old }.map { it.anchor }
        val changed = fresh.entries.filter { old[it.anchor] != null && old[it.anchor] != it.hash }.map { it.anchor }
        val removed = old.keys.count { it !in freshAnchors }

        val tmp = File(context.filesDir, "data.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(dataFile)) throw IOException("无法写入本地缓存")

        _state.update { s ->
            val newSet = (s.newAnchors + added).intersect(freshAnchors)
            s.copy(
                data = fresh,
                newAnchors = newSet,
                updatedAnchors = (s.updatedAnchors + changed).intersect(freshAnchors) - newSet,
                lastChecked = now,
                lastUpdated = now,
            )
        }
        saveSync()
        return if (added.isEmpty() && changed.isEmpty() && removed == 0) CheckResult.UpToDate
        else CheckResult.Updated(added.size, changed.size, removed)
    }

    suspend fun markSeen(anchor: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val s = _state.value
            if (anchor !in s.newAnchors && anchor !in s.updatedAnchors) return@withLock
            _state.value = s.copy(newAnchors = s.newAnchors - anchor, updatedAnchors = s.updatedAnchors - anchor)
            saveSync()
        }
    }

    suspend fun markAllSeen() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.update { it.copy(newAnchors = emptySet(), updatedAnchors = emptySet()) }
            saveSync()
        }
    }

    private fun saveSync() {
        val s = _state.value
        syncFile.writeText(
            json.encodeToString(SyncState(s.newAnchors, s.updatedAnchors, s.lastChecked, s.lastUpdated))
        )
    }

    private fun get(name: String): String {
        // Pages sits behind a CDN with a 10-minute max-age; the query string skips it.
        val conn = URL("$BASE_URL$name?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.useCaches = false
        conn.setRequestProperty("Cache-Control", "no-cache")
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                throw NotPublished()
            }
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code($name)")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
