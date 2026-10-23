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
    /** this card was answered wrong or peeked at: 考核 sends it back to 复习 on 下一题 */
    val missed: Boolean = false,
    /** what this run moved by itself (复习 → 考核 earned, 考核 → 复习 missed), for 撤销 on the last page */
    val promoted: List<String> = emptyList(),
    val demoted: List<String> = emptyList(),
    /** my own verdict on an entry that can't be checked automatically */
    val graded: Boolean? = null,
    /** the example sentence this card asks, fixed when the card comes up */
    val example: QuizExample? = null,
    /** an entry asks all its sentences (用户 2026-10-15): which one, and its step (1 选词, 2 打字) */
    val k: Int = 0,
    val step: Int = 1,
    /** how ① went, and ② once revealed (helped: revealed from the 选词 hint) */
    val bankOk: Boolean = false,
    val typedOk: Boolean = false,
    val helped: Boolean = false,
    /** every sentence so far right: grades the SM-2 card and the moves after the last one */
    val entryOk: Boolean = true,
    /** 算我对: entryOk before this sentence and the SM-2 card before its grade, to work them out again */
    val entryOkBefore: Boolean = true,
    val cardBefore: io.github.chinalwb.vocab.review.Card? = null,
    val righted: Boolean = false,
    /** the sentences this run asks of the entry (indexes into quizExamples()), and ① skipped for this one */
    val qs: List<Int> = emptyList(),
    val skipped: Boolean = false,
) {
    val current get() = queue.getOrNull(index)

    /** the entry's last sentence this run (a 自测题 or an entry without examples has one question) */
    val lastQ get() = example == null || k + 1 >= qs.size

    /**
     * A sentence starts: on the entry's first, the sentences this run asks ([qs], runSentences());
     * then this one — straight to ② 打字 if it has been answered right before ([skipped]).
     */
    fun withExample(optOut: Map<String, Set<String>>, attempts: Map<String, List<Attempt>>, since: Map<String, Long>): QuizSession {
        val e = current ?: return copy(example = null)
        val mine = attempts[e.anchor].orEmpty()
        val asks = e.level != "SELFTEST"
        val qs = if (!asks) emptyList() else if (k == 0) e.runSentences(mine, since[e.anchor] ?: 0L, optOut[e.anchor].orEmpty()) else qs
        val ex = if (asks) qs.getOrNull(k)?.let { e.quizExample(it, optOut[e.anchor].orEmpty()) } else null
        val skip = (if (asks) ex != null else e.selfTestAnswer.isNotBlank()) && mine.rightBefore(ex?.en ?: "")
        return copy(qs = qs, example = ex, step = if (skip) 2 else 1, bankOk = skip, skipped = skip)
    }
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
    private val favStore = (app as VocabApp).favs
    /** ⭐ example sentences: {anchor: {plain English: Fav}}, unstars included — read through starred(). */
    val favs = favStore.data
    private val readHideStore = (app as VocabApp).readsHidden
    /** 阅读 items removed from the list: {anchor: Fav(on = removed, t)} — read through removed(). */
    val readsHidden = readHideStore.data
    private val historyStore = (app as VocabApp).history
    /** 学习历史: newest first, one row per entry. */
    val history = historyStore.data
    private val progress = (app as VocabApp).progress
    private val practice = (app as VocabApp).practice
    /** 今天的练习: today's practised sentences, each with what I wrote and the answer. */
    val practiceToday = practice.today
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
            favStore.load()
            historyStore.load()
            readHideStore.load()
            practice.load()
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

    /** Long-press → 收藏 / 取消收藏 on an example sentence. */
    fun toggleFav(anchor: String, en: String) = viewModelScope.launch { favStore.toggle(anchor, en); progress.soon() }

    fun connectSync(token: String) = progress.connect(token)
    fun disconnectSync() = progress.disconnect()
    fun syncNow() = progress.now()

    fun resetReview() = viewModelScope.launch { reviews.reset(); progress.soon() }

    /** 学习历史: I'm on [anchor] — opened ("entry"), or met in a run ("review" / "test"). */
    fun visit(anchor: String, via: String) = viewModelScope.launch { historyStore.visit(anchor, via); progress.soon() }
    fun clearHistory() = viewModelScope.launch { historyStore.clear(); progress.soon() }

    /** 阅读: remove an item from the list ([removed] = true) or bring it back. */
    fun setReadRemoved(anchor: String, removed: Boolean) = viewModelScope.launch { readHideStore.set(anchor, removed); progress.soon() }

    /** Sets the run, and logs the entry it's now on in 学习历史 (once per entry). */
    private fun setRun(s: QuizSession?) {
        _quiz.value = s
        s?.current?.let { visit(it.anchor, s.stage.key) }
    }

    /** 开始复习 / 开始考核: the stage's entries, in the order the tab lists them. */
    fun startQuiz(stage: Stage) {
        val entries = library.value.data?.entries ?: return
        val q = quizQueue(stage, entries, stages.value, selfTests.value, review.value)
        setRun(if (q.isEmpty()) null else QuizSession(stage, q).withExample(quizOptOut.value, selfTests.value, stageTimes.value.t))
    }

    /**
     * 复习这一条 (用户 2026-10-16): an entry due for 复习 today, reviewed from its own page as a run of
     * one — the same rules as 开始复习 (every sentence, the SM-2 card graded once, the moves) — where
     * the page's own 自测 never grades. Its notes were just on screen, so it starts on the question.
     * Same as reviewOne() in template.html.
     */
    fun startReviewOf(e: Entry) {
        setRun(QuizSession(Stage.Review, listOf(e), studied = true).withExample(quizOptOut.value, selfTests.value, stageTimes.value.t))
        _tabRequest.value = 1
    }

    /** The home tab to switch to (复习, for a run started from an entry page); cleared once shown. */
    private val _tabRequest = MutableStateFlow<Int?>(null)
    val tabRequest = _tabRequest.asStateFlow()
    fun tabShown() { _tabRequest.value = null }

    /** 复习: the notes are read, on to the question. */
    fun studiedQuiz() {
        _quiz.value = _quiz.value?.copy(studied = true)
    }

    /**
     * 显示答案: a 自测题 is checked against its 答案, an example against its English. ① 选词 is only
     * checked; ② 打字 is logged, right only if ① was too ([helped]: revealed from the 选词 hint).
     * The entry's SM-2 grade and its miss come after its last sentence, right only if all were.
     */
    fun revealQuiz(draft: String, helped: Boolean = false) {
        val s = _quiz.value ?: return
        val e = s.current ?: return
        val via = s.stage.key
        val tried = draft.isNotBlank()
        val ex = s.example
        val answer = if (e.level == "SELFTEST") e.selfTestAnswer else ex?.en
        when {
            answer != null && s.step == 1 ->
                _quiz.value = s.copy(revealed = true, bankOk = tried && bestDiff(draft.trim(), answer).same)
            answer != null -> {
                val typedOk = !helped && tried && bestDiff(draft.trim(), answer).same
                val ok = s.bankOk && typedOk
                if (ex == null) recordSelfTest(e, draft, via, ok) else recordAttempt(e, draft, ok, via, ex.en)
                val all = s.entryOk && ok
                val before = review.value.cards[e.anchor]          // read before schedule() grades it
                if (s.lastQ) schedule(s, e, all)
                _quiz.value = s.copy(
                    revealed = true, typedOk = typedOk, helped = helped, entryOk = all, missed = s.lastQ && !all,
                    entryOkBefore = s.entryOk, cardBefore = before,
                    checked = s.checked + if (tried) 1 else 0, ok = s.ok + if (ok) 1 else 0,
                )
            }
            // nothing to check against: a blank reveal is a peek, anything else waits for my verdict
            !tried -> { recordAttempt(e, "", false, via); schedule(s, e, false); _quiz.value = s.copy(revealed = true, missed = true) }
            else -> _quiz.value = s.copy(revealed = true)
        }
    }

    /** Within the entry: ① → ② 打字, or ([nextSentence]) on to its next sentence's ①. */
    fun onwardQuiz(nextSentence: Boolean) {
        val s = _quiz.value ?: return
        _quiz.value = if (nextSentence) s.copy(k = s.k + 1, step = 1, revealed = false, righted = false).withExample(quizOptOut.value, selfTests.value, stageTimes.value.t)
        else s.copy(step = 2, revealed = false, righted = false)
    }

    /**
     * 算我对 in a run: this sentence was right after all — the entry's result, and on its last sentence
     * the SM-2 grade (from the card as it was before) and the 考核 miss, are worked out again.
     * Same as the #st-right handler in renderQuizCard() (template.html).
     */
    fun rightQuiz() {
        val s = _quiz.value ?: return
        val e = s.current ?: return
        if (!s.revealed || s.righted) return
        markRight(e.anchor)
        val all = s.entryOkBefore
        if (s.lastQ && s.stage == Stage.Review) viewModelScope.launch {
            reviews.replace { r -> r.copy(cards = s.cardBefore?.let { r.cards + (e.anchor to it) } ?: (r.cards - e.anchor)) }
            reviews.grade(e.anchor, if (all) Grade.Good else Grade.Again)
            progress.soon()
        }
        _quiz.value = s.copy(righted = true, ok = s.ok + 1, entryOk = all, missed = s.lastQ && !all)
    }

    // the last 今天的练习 pair per entry, so 算我对 can mark it right too
    private val lastPractice = mutableMapOf<String, io.github.chinalwb.vocab.review.PracticeItem>()

    /**
     * 算我对 (用户 2026-10-19): a reveal judged wrong for a difference that isn't a mistake — a tense
     * the Chinese doesn't give away, a contraction, a synonym — counts as right: the last attempt
     * (a written one) turns ok. Same as markRight() in template.html.
     */
    fun markRight(anchor: String) = viewModelScope.launch {
        selfTestLog.replace { m ->
            val l = m[anchor].orEmpty()
            val a = l.lastOrNull()
            if (a == null || a.text.isEmpty()) m else m + (anchor to (l.dropLast(1) + a.copy(ok = true)))
        }
        progress.soon()
        lastPractice[anchor]?.let { practice.record(it.copy(ok = true)) }
    }

    /** 我写对了 / 没写对 on an entry that has no answer to check against. */
    fun gradeQuiz(draft: String, ok: Boolean) {
        val s = _quiz.value ?: return
        val e = s.current ?: return
        recordAttempt(e, draft, ok, s.stage.key)
        schedule(s, e, ok)
        _quiz.value = s.copy(graded = ok, checked = s.checked + 1, ok = s.ok + if (ok) 1 else 0, missed = !ok)
    }

    // 复习 keeps its spaced schedule, graded by the quiz: right → Good (the interval grows), wrong → Again (tomorrow)
    private fun schedule(s: QuizSession, e: Entry, ok: Boolean) {
        if (s.stage == Stage.Review) viewModelScope.launch { reviews.grade(e.anchor, if (ok) Grade.Good else Grade.Again); progress.soon() }
    }

    /**
     * 下一题 — [advance]: move this entry on to the next stage first (offered once all its questions
     * are right). The run also moves entries by itself: in 复习, one that has earned 考核
     * (advanceReady, with its SM-2 streak) goes on; in 考核, one answered wrong or peeked at goes back
     * to 复习 and starts its schedule again. Both can be undone on the last page.
     */
    fun nextQuiz(advance: Boolean) {
        val s = _quiz.value ?: return
        val e = s.current
        var promoted = s.promoted
        var demoted = s.demoted
        val auto = e != null && s.revealed && s.stage == Stage.Review && advanceReady(
            e, s.stage, selfTests.value[e.anchor].orEmpty(), stageTimes.value.t[e.anchor] ?: 0L,
            quizOptOut.value[e.anchor].orEmpty(), review.value.cards[e.anchor]?.reps ?: 0,
        )
        val moved = e != null && (advance || auto)
        if (e != null && moved) s.stage.next?.let { setStage(e.anchor, it) }
        if (e != null && moved && s.stage == Stage.Review) promoted = promoted + e.anchor
        if (e != null && !moved && s.stage == Stage.Test && s.revealed && s.missed) {
            demoted = demoted + e.anchor
            viewModelScope.launch {
                stageStore.set(e.anchor, Stage.Review)
                reviews.grade(e.anchor, Grade.Again)   // its streak starts over: due tomorrow
                progress.soon()
            }
        }
        setRun(s.copy(
            index = s.index + 1, studied = false, revealed = false, graded = null, missed = false, done = s.done + 1,
            advanced = s.advanced + if (moved) 1 else 0, promoted = promoted, demoted = demoted,
            k = 0, step = 1, bankOk = false, typedOk = false, helped = false, entryOk = true, righted = false,
        ).withExample(quizOptOut.value, selfTests.value, stageTimes.value.t))
    }

    /** 撤销 on the last page: put back what this run moved by itself (the schedule stays as graded). */
    fun undoQuizMoves() {
        val s = _quiz.value ?: return
        viewModelScope.launch {
            s.promoted.forEach { stageStore.set(it, Stage.Review) }
            s.demoted.forEach { stageStore.set(it, Stage.Test) }
            progress.soon()
        }
        _quiz.value = s.copy(advanced = s.advanced - s.promoted.size, promoted = emptyList(), demoted = emptyList())
    }

    fun endQuiz() {
        _quiz.value = null
    }

    /** One attempt with the verdict already known; [ex] is the example it answered (plain English). */
    fun recordAttempt(entry: Entry, text: String, ok: Boolean, via: String, ex: String = "") = viewModelScope.launch {
        val t = text.trim()
        selfTestLog.record(entry.anchor, Attempt(System.currentTimeMillis(), t, ok && t.isNotEmpty(), via, ex))
        progress.soon()
        // an example sentence goes into 今天的练习 too; what I grade myself has no answer to pair with
        val zh = entry.quizExamples().firstOrNull { plain(it.en) == ex }?.let { plain(it.zh) }
        if (ex.isNotEmpty() && zh != null) practiceItem(entry, zh, t, ex, ok && t.isNotEmpty(), via).let { lastPractice[entry.anchor] = it; practice.record(it) }
    }

    /** Records one reveal of a 自测 entry; ok = the words match the answer, unless [okAs] says (两步). */
    fun recordSelfTest(entry: Entry, text: String, via: String, okAs: Boolean? = null) = viewModelScope.launch {
        val t = text.trim()
        val d = bestDiff(t, entry.selfTestAnswer)
        val ok = t.isNotEmpty() && (okAs ?: d.same)
        selfTestLog.record(entry.anchor, Attempt(System.currentTimeMillis(), t, ok, via))
        progress.soon()
        // the answer it was checked against: the closest of the accepted ones, or the first after a peek
        val answer = if (t.isEmpty()) entry.selfTestAnswer.lines().firstOrNull { it.isNotBlank() }.orEmpty() else d.answer.joinToString(" ")
        if (answer.isNotEmpty()) practiceItem(entry, entry.title.removePrefix("自测:").removePrefix("自测:"), t, answer, ok, via).let { lastPractice[entry.anchor] = it; practice.record(it) }
    }

    private fun practiceItem(entry: Entry, zh: String, mine: String, answer: String, ok: Boolean, via: String) =
        io.github.chinalwb.vocab.review.PracticeItem(
            anchor = entry.anchor, title = entry.title, zh = zh, mine = mine, answer = answer, ok = ok, via = via,
            time = io.github.chinalwb.vocab.review.PracticeLog.now(),
        )

    /** 今天的练习 opened: if the day has turned since the last answer, start on the new one. */
    fun refreshToday() = viewModelScope.launch { practice.load() }
}
