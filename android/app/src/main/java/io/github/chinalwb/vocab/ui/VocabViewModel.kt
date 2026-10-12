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
import io.github.chinalwb.vocab.review.next
import io.github.chinalwb.vocab.review.stageOf
import io.github.chinalwb.vocab.sync.describe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * A run through one stage's entries: 复习 (the notes first, then a question) or 考核 (the
 * question, the notes only after). Each card asks one of the entry's examples (中→英), or a
 * 自测题's own question; an entry with nothing to check against is graded by me.
 */
data class QuizSession(
    val stage: Stage,
    val queue: List<Entry>,
    val index: Int = 0,
    /** 复习 only: the notes have been read, on to the question */
    val studied: Boolean = false,
    val revealed: Boolean = false,
    val done: Int = 0,
    /** answered (not peeked) this run, and how many of those were right */
    val checked: Int = 0,
    val ok: Int = 0,
    /** moved on to the next stage this run */
    val advanced: Int = 0,
    /** my own verdict on an entry that can't be checked automatically */
    val graded: Boolean? = null,
    /** the example sentence this card asks, fixed when the card comes up */
    val example: QuizExample? = null,
) {
    val current get() = queue.getOrNull(index)

    fun withExample(log: Map<String, List<Attempt>>, optOut: Map<String, Set<String>>) =
        copy(example = current?.takeIf { it.level != "SELFTEST" }?.let { it.quizExample(log[it.anchor]?.size ?: 0, optOut[it.anchor].orEmpty()) })
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

    // Example sentences I've opted out of 自测, by anchor: the plain English of each. Lives on this
    // device only (SharedPreferences "quizopt"); at least one example per entry always stays in.
    private val optPrefs = app.getSharedPreferences("quizopt", android.content.Context.MODE_PRIVATE)
    private val _quizOptOut = MutableStateFlow(optPrefs.all.mapValues { (_, v) -> (v as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty() })
    val quizOptOut = _quizOptOut.asStateFlow()

    /** Tap on an example in the notes: toggles whether 自测 may ask it. Refused when it is the last one in. */
    fun toggleQuizExample(entry: Entry, en: String) {
        val cur = _quizOptOut.value[entry.anchor].orEmpty()
        val next = if (en in cur) cur - en else {
            // a toast, not _messages: the snackbar host is on Home and this happens on the entry page
            if (entry.quizExamples(cur).size <= 1) { android.widget.Toast.makeText(getApplication(), "至少要留一句例句用于自测", android.widget.Toast.LENGTH_SHORT).show(); return }
            cur + en
        }
        _quizOptOut.value = _quizOptOut.value + (entry.anchor to next)
        optPrefs.edit().apply { if (next.isEmpty()) remove(entry.anchor) else putStringSet(entry.anchor, next) }.apply()
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

    private val _quiz = MutableStateFlow<QuizSession?>(null)
    /** The run going on in the 复习 or 考核 tab, if any. */
    val quiz = _quiz.asStateFlow()
    /** When each entry was last moved (ms): attempts before that don't count towards the next stage. */
    val stageTimes = stageStore.data

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

    /**
     * 下一个 on the entry page: the list an entry was opened from, as it stood then (moving the
     * entry out of the filter doesn't lose its place), and the entry opened from it — cross-
     * references don't move it. Opened from anywhere else (错题本, 练习), there's no list.
     */
    private val _openList = MutableStateFlow(emptyList<String>() to "")
    val openList = _openList.asStateFlow()
    fun openedFrom(list: List<String>, anchor: String) { _openList.value = list to anchor }

    fun setStage(anchor: String, stage: Stage) = viewModelScope.launch { stageStore.set(anchor, stage); progress.soon() }

    fun connectSync(token: String) = progress.connect(token)
    fun disconnectSync() = progress.disconnect()
    fun syncNow() = progress.now()

    fun resetReview() = viewModelScope.launch { reviews.reset(); progress.soon() }

    /** 开始复习 / 开始考核: the stage's entries, in the order the tab lists them. */
    fun startQuiz(stage: Stage) {
        val entries = library.value.data?.entries ?: return
        val q = quizQueue(stage, entries, stages.value, selfTests.value, review.value)
        _quiz.value = if (q.isEmpty()) null else QuizSession(stage, q).withExample(selfTests.value, quizOptOut.value)
    }

    /** 复习: the notes are read, on to the question. */
    fun studiedQuiz() {
        _quiz.value = _quiz.value?.copy(studied = true)
    }

    /** 显示答案: a 自测题 is checked against its 答案, an example against its English; both are logged. */
    fun revealQuiz(draft: String) {
        val s = _quiz.value ?: return
        val e = s.current ?: return
        val via = s.stage.key
        val tried = draft.isNotBlank()
        val ex = s.example
        when {
            e.level == "SELFTEST" -> {
                val ok = tried && bestDiff(draft.trim(), e.selfTestAnswer).same
                recordSelfTest(e, draft, via)
                schedule(s, e, ok)
                _quiz.value = s.copy(revealed = true, checked = s.checked + if (tried) 1 else 0, ok = s.ok + if (ok) 1 else 0)
            }
            ex != null -> {
                val ok = tried && bestDiff(draft.trim(), ex.en).same
                recordAttempt(e, draft, ok, via, ex.en)
                schedule(s, e, ok)
                _quiz.value = s.copy(revealed = true, checked = s.checked + if (tried) 1 else 0, ok = s.ok + if (ok) 1 else 0)
            }
            // nothing to check against: a blank reveal is a peek, anything else waits for my verdict
            !tried -> { recordAttempt(e, "", false, via); schedule(s, e, false); _quiz.value = s.copy(revealed = true) }
            else -> _quiz.value = s.copy(revealed = true)
        }
    }

    /** 我写对了 / 没写对 on an entry that has no answer to check against. */
    fun gradeQuiz(draft: String, ok: Boolean) {
        val s = _quiz.value ?: return
        val e = s.current ?: return
        recordAttempt(e, draft, ok, s.stage.key)
        schedule(s, e, ok)
        _quiz.value = s.copy(graded = ok, checked = s.checked + 1, ok = s.ok + if (ok) 1 else 0)
    }

    // 复习 keeps its spaced schedule, graded by the quiz: right → Good (the interval grows), wrong → Again (tomorrow)
    private fun schedule(s: QuizSession, e: Entry, ok: Boolean) {
        if (s.stage == Stage.Review) viewModelScope.launch { reviews.grade(e.anchor, if (ok) Grade.Good else Grade.Again); progress.soon() }
    }

    /** 下一题 — [advance]: move this entry on to the next stage first (offered once all its questions are right). */
    fun nextQuiz(advance: Boolean) {
        val s = _quiz.value ?: return
        if (advance) s.current?.let { e -> s.stage.next?.let { setStage(e.anchor, it) } }
        _quiz.value = s.copy(index = s.index + 1, studied = false, revealed = false, graded = null, done = s.done + 1, advanced = s.advanced + if (advance) 1 else 0)
            .withExample(selfTests.value, quizOptOut.value)
    }

    fun endQuiz() {
        _quiz.value = null
    }

    /** One attempt with the verdict already known; [ex] is the example it answered (plain English). */
    fun recordAttempt(entry: Entry, text: String, ok: Boolean, via: String, ex: String = "") = viewModelScope.launch {
        selfTestLog.record(entry.anchor, Attempt(System.currentTimeMillis(), text.trim(), ok && text.isNotBlank(), via, ex))
        progress.soon()
    }

    /** Records one reveal of a 自测 entry; ok = the words match the answer. */
    fun recordSelfTest(entry: Entry, text: String, via: String) = viewModelScope.launch {
        val t = text.trim()
        selfTestLog.record(entry.anchor, Attempt(System.currentTimeMillis(), t, t.isNotEmpty() && bestDiff(t, entry.selfTestAnswer).same, via))
        progress.soon()
    }
}
