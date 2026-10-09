package io.github.chinalwb.vocab.sync

import android.content.Context
import android.util.Base64
import io.github.chinalwb.vocab.BuildConfig
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.Card
import io.github.chinalwb.vocab.review.MAX_PER_ENTRY
import io.github.chinalwb.vocab.review.ReviewData
import io.github.chinalwb.vocab.review.ReviewStore
import io.github.chinalwb.vocab.review.SelfTestLog
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.StageData
import io.github.chinalwb.vocab.review.StageStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** progress.json on the repo's orphan `progress` branch — the same file the page reads and writes. */
@Serializable
data class Progress(
    val v: Int = 1,
    val stages: Map<String, PStage> = emptyMap(),
    val cards: Map<String, Card> = emptyMap(),
    val newDay: Long = 0,
    val newToday: Int = 0,
    val resetAt: Long = 0,
    val attempts: Map<String, List<PAttempt>> = emptyMap(),
)

@Serializable
data class PStage(val s: String, val t: Long)

/** A 自测 attempt without what I typed — the text never leaves the device. */
@Serializable
data class PAttempt(val t: Long, val ok: Boolean, val peek: Boolean, val via: String)

/** What a synced attempt shows here: the result came from the other device, the text didn't. */
const val ELSEWHERE = "(在另一台设备上写的)"

data class SyncStatus(val connected: Boolean = false, val busy: Boolean = false, val ok: Long = 0, val error: String? = null)

private const val API = BuildConfig.PROGRESS_API

/**
 * 同步: pull progress.json, merge it into the local stores, push back if this device knew
 * something GitHub didn't. Same format and merge rules as syncNow() in template.html:
 * newest wins per entry, a reset drops older cards, 自测 attempts are a union.
 */
class ProgressSync(
    context: Context,
    private val reviews: ReviewStore,
    private val selfTests: SelfTestLog,
    private val stages: StageStore,
) {
    // encodeDefaults: the page compares field by field, so every field must be written
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pending: Job? = null
    /** Completed once the local stores are loaded — nothing syncs before that. */
    val ready = CompletableDeferred<Unit>()

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private fun token() = prefs.getString("token", "").orEmpty()
    private fun readStatus() = SyncStatus(token().isNotEmpty(), ok = prefs.getLong("ok", 0), error = prefs.getString("error", null))

    fun connect(token: String) {
        prefs.edit().putString("token", token.trim()).remove("ok").remove("error").apply()
        _status.value = readStatus()
        now()
    }

    fun disconnect() {
        pending?.cancel()
        prefs.edit().clear().apply()
        _status.value = SyncStatus()
    }

    /** Changes are batched: one commit per burst of activity, not per tap. */
    fun soon() {
        if (token().isEmpty()) return
        pending?.cancel()
        pending = scope.launch { delay(15_000); run() }
    }

    /** Leaving the app: push what's waiting right away. */
    fun flush() {
        if (pending?.isActive == true) now()
    }

    fun now() {
        if (token().isEmpty()) return
        pending?.cancel()
        pending = scope.launch { run() }
    }

    private suspend fun run() {
        ready.await()
        mutex.withLock {
            _status.value = _status.value.copy(busy = true)
            val error = try {
                sync(); null
            } catch (e: Exception) {
                e.message ?: e.toString()
            }
            prefs.edit().apply {
                if (error == null) putLong("ok", System.currentTimeMillis()).remove("error") else putString("error", error)
            }.apply()
            _status.value = readStatus()
        }
    }

    private suspend fun sync() {
        repeat(3) {
            val (sha, remote) = get()
            merge(remote)
            val out = snapshot()
            if (out == remote) return
            if (put(out, sha)) return
        }
        throw IOException("连续冲突,稍后再试")
    }

    private suspend fun merge(r: Progress) {
        reviews.replace { l ->
            val resetAt = maxOf(l.resetAt, r.resetAt)
            val cards = (l.cards.keys + r.cards.keys).mapNotNull { a ->
                val mine = l.cards[a]
                val theirs = r.cards[a]
                val c = if (theirs == null || (mine != null && mine.t >= theirs.t)) mine!! else theirs
                if (c.t >= resetAt) a to c else null
            }.toMap()
            val (day, count) = when {
                r.newDay > l.newDay -> r.newDay to r.newToday
                l.newDay > r.newDay -> l.newDay to l.newToday
                else -> l.newDay to maxOf(l.newToday, r.newToday)
            }
            ReviewData(cards, day, count, resetAt)
        }
        stages.replace { l ->
            val newer = r.stages.filter { (a, o) -> Stage.entries.any { it.key == o.s } && o.t > (l.t[a] ?: 0) }
            StageData(l.stages + newer.mapValues { it.value.s }, l.t + newer.mapValues { it.value.t })
        }
        selfTests.replace { l ->
            l + r.attempts.map { (a, list) ->
                val mine = l[a].orEmpty()
                val seen = mine.map { it.t }.toSet()
                a to (mine + list.filter { it.t !in seen }.map { Attempt(it.t, if (it.peek) "" else ELSEWHERE, it.ok, it.via) })
                    .sortedBy { it.t }.takeLast(MAX_PER_ENTRY)
            }
        }
    }

    private fun snapshot(): Progress {
        val r = reviews.state.value
        val s = stages.data.value
        return Progress(
            stages = s.stages.mapValues { (a, st) -> PStage(st, s.t[a] ?: 0) },
            cards = r.cards,
            newDay = r.newDay,
            newToday = r.newToday,
            resetAt = r.resetAt,
            attempts = selfTests.state.value.mapValues { (_, l) -> l.map { PAttempt(it.t, it.ok, it.text.isEmpty(), it.via) } },
        )
    }

    private fun get(): Pair<String, Progress> {
        val body = request("GET", "$API?ref=progress", null) ?: throw IOException("GitHub 冲突")
        val o = json.decodeFromString<JsonObject>(body)
        val content = String(Base64.decode(o["content"]!!.jsonPrimitive.content, Base64.DEFAULT), Charsets.UTF_8)
        return o["sha"]!!.jsonPrimitive.content to json.decodeFromString<Progress>(content)
    }

    /** false = someone wrote first; pull again. */
    private fun put(p: Progress, sha: String): Boolean {
        val content = Base64.encodeToString((json.encodeToString(p) + "\n").toByteArray(), Base64.NO_WRAP)
        val body = buildJsonObject {
            put("message", "sync: progress from app")
            put("branch", "progress")
            put("sha", sha)
            put("content", content)
        }
        return request("PUT", API, body.toString()) != null
    }

    private fun request(method: String, url: String, body: String?): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.useCaches = false
        conn.setRequestProperty("Authorization", "Bearer ${token()}")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            return when (val code = conn.responseCode) {
                200, 201 -> conn.inputStream.bufferedReader().use { it.readText() }
                409, 422 -> null
                401 -> throw IOException("token 无效或已过期")
                403, 404 -> throw IOException("token 没有 chinalwb/vocab 的读写权限")
                else -> throw IOException("GitHub 返回 $code")
            }
        } finally {
            conn.disconnect()
        }
    }
}
