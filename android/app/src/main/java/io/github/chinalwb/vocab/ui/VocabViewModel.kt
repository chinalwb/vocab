package io.github.chinalwb.vocab.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.chinalwb.vocab.VocabApp
import io.github.chinalwb.vocab.data.CheckResult
import io.github.chinalwb.vocab.data.Entry
import io.github.chinalwb.vocab.review.Attempt
import io.github.chinalwb.vocab.review.Grade
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.planToday
import io.github.chinalwb.vocab.review.stageOf
import io.github.chinalwb.vocab.sync.describe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class TestSession(
    val queue: List<Entry>,
    val index: Int = 0,
    val revealed: Boolean = false,
    val done: Int = 0,
    /** 自测题 answered (not peeked) this run, and how many of those matched */
    val checked: Int = 0,
    val ok: Int = 0,
    val mastered: Int = 0,
    /** my own verdict on a moved entry that can't be checked automatically */
    val graded: Boolean? = null,
    /** the example sentence this card asks (moved entries), fixed when the card comes up */
    val example: QuizExample? = null,
) {
    val current get() = queue.getOrNull(index)

    fun withExample(log: Map<String, List<Attempt>>) =
        copy(example = current?.takeIf { it.level != "SELFTEST" }?.let { it.quizExample(log[it.anchor]?.size ?: 0) })
}

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
    private val selfTestLog = (app as VocabApp).selfTests
    /** 自测 attempt history by anchor. */
    val selfTests = selfTestLog.state
    private val stageStore = (app as VocabApp).stages
    /** 进度 moves by anchor — read through stageOf(). */
    val stages = stageStore.state
    private val progress = (app as VocabApp).progress
    /** 同步 to GitHub: connected / last success / error. */
    val syncStatus = progress.status

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

    private val updater = (app as VocabApp).updater
    private val _appUpdate = MutableStateFlow<io.github.chinalwb.vocab.update.AppUpdateState>(io.github.chinalwb.vocab.update.AppUpdateState.Idle)
    val appUpdate = _appUpdate.asStateFlow()

    /** ⋮ → 检查 App 更新: compare with the APK on main. */
    fun checkAppUpdate() = viewModelScope.launch {
        _appUpdate.value = io.github.chinalwb.vocab.update.AppUpdateState.Checking
        try {
            val remote = updater.latest()
            if (remote.versionCode > io.github.chinalwb.vocab.BuildConfig.VERSION_CODE) {
                _appUpdate.value = io.github.chinalwb.vocab.update.AppUpdateState.Available(remote)
            } else {
                _appUpdate.value = io.github.chinalwb.vocab.update.AppUpdateState.Idle
                _messages.send("已是最新版本 v${io.github.chinalwb.vocab.BuildConfig.VERSION_NAME}")
            }
        } catch (e: Exception) {
            _appUpdate.value = io.github.chinalwb.vocab.update.AppUpdateState.Idle
            _messages.send("检查 App 更新失败:${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun installAppUpdate() = viewModelScope.launch {
        val s = _appUpdate.value as? io.github.chinalwb.vocab.update.AppUpdateState.Available ?: return@launch
        try {
            _appUpdate.value = io.github.chinalwb.vocab.update.AppUpdateState.Downloading(s.remote, 0f)
            val file = updater.download { p -> _appUpdate.value = io.github.chinalwb.vocab.update.AppUpdateState.Downloading(s.remote, p) }
            _appUpdate.value = io.github.chinalwb.vocab.update.AppUpdateState.Idle
            updater.install(file)
        } catch (e: Exception) {
            _appUpdate.value = s
            _messages.send("下载失败:${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun dismissAppUpdate() {
        if (_appUpdate.value is io.github.chinalwb.vocab.update.AppUpdateState.Available) _appUpdate.value = io.github.chinalwb.vocab.update.AppUpdateState.Idle
    }

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session = _session.asStateFlow()
    private val _test = MutableStateFlow<TestSession?>(null)
    /** The 自测 tab's run through everything in 自测中. */
    val test = _test.asStateFlow()

    init {
        viewModelScope.launch {
            repo.load()
            reviews.load()
            selfTestLog.load()
            stageStore.load()
            progress.ready.complete(Unit)
            progress.now()
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
        // 已掌握 never comes back on its own
        val plan = planToday(entries.filter { stages.value.stageOf(it) != Stage.Done }, review.value)
        _session.value = if (plan.all.isEmpty()) null else Session(plan.all)
    }

    fun reveal() {
        _session.value = _session.value?.copy(revealed = true)
    }

    fun grade(g: Grade) {
        val s = _session.value ?: return
        val entry = s.current ?: return
        viewModelScope.launch { reviews.grade(entry.anchor, g); progress.soon() }
        val again = g == Grade.Again && entry.anchor !in s.requeued
        _session.value = s.copy(
            queue = if (again) s.queue + entry else s.queue,
            requeued = if (again) s.requeued + entry.anchor else s.requeued,
            index = s.index + 1,
            revealed = false,
            done = s.done + 1,
        )
    }

    fun setStage(anchor: String, stage: Stage) = viewModelScope.launch { stageStore.set(anchor, stage); progress.soon() }

    fun connectSync(token: String) = progress.connect(token)
    fun disconnectSync() = progress.disconnect()
    fun syncNow() = progress.now()

    /** 已掌握 on a review card: out of review for good, and on to the next card. */
    fun markMastered() {
        val s = _session.value ?: return
        val entry = s.current ?: return
        setStage(entry.anchor, Stage.Done)
        _session.value = s.copy(
            // drop a 忘了 repeat still waiting later in the queue
            queue = s.queue.filterIndexed { i, e -> i <= s.index || e.anchor != entry.anchor },
            index = s.index + 1,
            revealed = false,
            done = s.done + 1,
        )
    }

    fun endReview() {
        _session.value = null
    }

    fun resetReview() = viewModelScope.launch { reviews.reset(); progress.soon() }

    fun startTest() {
        val entries = library.value.data?.entries ?: return
        val q = testQueue(entries, stages.value, selfTests.value)
        _test.value = if (q.isEmpty()) null else TestSession(q).withExample(selfTests.value)
    }

    /** 显示答案 in the 自测 tab: a 自测题 is logged and checked against its 答案. */
    fun revealTest(draft: String) {
        val s = _test.value ?: return
        val e = s.current ?: return
        if (e.level == "SELFTEST") {
            recordSelfTest(e, draft, "selftest")
            val tried = draft.isNotBlank()
            _test.value = s.copy(
                revealed = true,
                checked = s.checked + if (tried) 1 else 0,
                ok = s.ok + if (tried && bestDiff(draft.trim(), e.selfTestAnswer).same) 1 else 0,
            )
        } else {
            // moved to 自测: checked against this card's example sentence; with none, I grade it myself
            val ex = s.example
            when {
                ex != null -> {
                    val tried = draft.isNotBlank()
                    val ok = tried && bestDiff(draft.trim(), ex.en).same
                    recordAttempt(e, draft, ok, "selftest")
                    _test.value = s.copy(revealed = true, checked = s.checked + if (tried) 1 else 0, ok = s.ok + if (ok) 1 else 0)
                }
                draft.isBlank() -> { recordAttempt(e, "", false, "selftest"); _test.value = s.copy(revealed = true) }
                else -> _test.value = s.copy(revealed = true)
            }
        }
    }

    /** 我写对了 / 没写对 on a moved entry that has no answer to check against. */
    fun gradeTest(draft: String, ok: Boolean) {
        val s = _test.value ?: return
        val e = s.current ?: return
        recordAttempt(e, draft, ok, "selftest")
        _test.value = s.copy(graded = ok, checked = s.checked + 1, ok = s.ok + if (ok) 1 else 0)
    }

    /** 下一题, or 已掌握 then 下一题. */
    fun nextTest(mastered: Boolean) {
        val s = _test.value ?: return
        if (mastered) s.current?.let { setStage(it.anchor, Stage.Done) }
        _test.value = s.copy(index = s.index + 1, revealed = false, graded = null, done = s.done + 1, mastered = s.mastered + if (mastered) 1 else 0)
            .withExample(selfTests.value)
    }

    fun endTest() {
        _test.value = null
    }

    /** One attempt with the verdict already known (an entry moved to 自测). */
    fun recordAttempt(entry: Entry, text: String, ok: Boolean, via: String) = viewModelScope.launch {
        selfTestLog.record(entry.anchor, Attempt(System.currentTimeMillis(), text.trim(), ok && text.isNotBlank(), via))
        progress.soon()
    }

    /** Records one reveal of a 自测 entry; ok = the words match the answer. */
    fun recordSelfTest(entry: Entry, text: String, via: String) = viewModelScope.launch {
        val t = text.trim()
        selfTestLog.record(entry.anchor, Attempt(System.currentTimeMillis(), t, t.isNotEmpty() && bestDiff(t, entry.selfTestAnswer).same, via))
        progress.soon()
    }
}
