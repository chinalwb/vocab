package io.github.chinalwb.vocab.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.chinalwb.vocab.VocabApp
import io.github.chinalwb.vocab.data.CheckResult
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.review.Grade
import io.github.chinalwb.vocab.review.planToday
import io.github.chinalwb.vocab.sync.describe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class Session(
    val queue: List<Entry>,
    val index: Int = 0,
    val revealed: Boolean = false,
    /** Entries graded 忘了 once already this session — they come back only once. */
    val requeued: Set<String> = emptySet(),
    val done: Int = 0,
) {
    val current get() = queue.getOrNull(index)
}

class VocabViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as VocabApp).repository
    private val reviews = (app as VocabApp).reviews

    val library = repo.state
    val review = reviews.state

    private val prefs = app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
    private val _tiles = MutableStateFlow(prefs.getBoolean("tiles", false))
    /** Browse layout: false = list, true = Keep-style tiles. Remembered across launches. */
    val tiles = _tiles.asStateFlow()

    fun toggleTiles() {
        _tiles.value = !_tiles.value
        prefs.edit().putBoolean("tiles", _tiles.value).apply()
    }

    private var busy = false
    private val _checking = MutableStateFlow(false)
    /** Only true for checks the user asked for — the launch-time check runs without a spinner. */
    val checking = _checking.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session = _session.asStateFlow()

    init {
        viewModelScope.launch {
            repo.load()
            reviews.load()
            check(quiet = true)
        }
    }

    fun check(quiet: Boolean = false) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            _checking.value = !quiet
            val msg = when (val r = repo.check()) {
                is CheckResult.Updated -> describe(r)
                CheckResult.UpToDate -> if (quiet) null else "已是最新"
                is CheckResult.Failed -> if (quiet) null else r.message
            }
            _checking.value = false
            busy = false
            msg?.let { _messages.send(it) }
        }
    }

    fun markSeen(anchor: String) = viewModelScope.launch { repo.markSeen(anchor) }
    fun markAllSeen() = viewModelScope.launch { repo.markAllSeen() }

    fun startReview() {
        val lib = library.value
        val entries = lib.data?.entries ?: return
        val plan = planToday(entries, review.value)
        _session.value = if (plan.all.isEmpty()) null else Session(plan.all)
    }

    fun reveal() {
        _session.value = _session.value?.copy(revealed = true)
    }

    fun grade(g: Grade) {
        val s = _session.value ?: return
        val entry = s.current ?: return
        viewModelScope.launch { reviews.grade(entry.anchor, g) }
        val again = g == Grade.Again && entry.anchor !in s.requeued
        _session.value = s.copy(
            queue = if (again) s.queue + entry else s.queue,
            requeued = if (again) s.requeued + entry.anchor else s.requeued,
            index = s.index + 1,
            revealed = false,
            done = s.done + 1,
        )
    }

    fun endReview() {
        _session.value = null
    }

    fun resetReview() = viewModelScope.launch { reviews.reset() }
}
