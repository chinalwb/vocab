package io.github.chinalwb.vocab

import android.app.Application
import io.github.chinalwb.vocab.data.VocabRepository
import io.github.chinalwb.vocab.review.ReviewStore
import io.github.chinalwb.vocab.review.SelfTestLog
import io.github.chinalwb.vocab.review.StageStore
import io.github.chinalwb.vocab.sync.ProgressSync
import io.github.chinalwb.vocab.sync.UpdateWorker

class VocabApp : Application() {
    val repository by lazy { VocabRepository(this) }
    val reviews by lazy { ReviewStore(this) }
    val selfTests by lazy { SelfTestLog(this) }
    val stages by lazy { StageStore(this) }
    val favs by lazy { io.github.chinalwb.vocab.review.FavStore(this) }
    /** 今天的练习: the day's sentences, one Markdown file a day, on this phone only. */
    val practice by lazy { io.github.chinalwb.vocab.review.PracticeLog(this) }
    /** 学习历史: the last 100 entries I've been on. */
    val history by lazy { io.github.chinalwb.vocab.review.HistoryStore(this) }
    /** 阅读 items removed from the list (hidden, synced). */
    val readsHidden by lazy { io.github.chinalwb.vocab.review.ReadHideStore(this) }
    val progress by lazy { ProgressSync(this, reviews, selfTests, stages, favs, history, readsHidden) }
    val updater by lazy { io.github.chinalwb.vocab.update.AppUpdater(this) }
    val speaker by lazy { io.github.chinalwb.vocab.ui.Speaker(this) }

    override fun onCreate() {
        super.onCreate()
        UpdateWorker.schedule(this)
    }
}
