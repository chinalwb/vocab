package io.github.chinalwb.vocab

import android.app.Application
import io.github.chinalwb.vocab.data.VocabRepository
import io.github.chinalwb.vocab.review.ReviewStore
import io.github.chinalwb.vocab.sync.UpdateWorker

class VocabApp : Application() {
    val repository by lazy { VocabRepository(this) }
    val reviews by lazy { ReviewStore(this) }

    override fun onCreate() {
        super.onCreate()
        UpdateWorker.schedule(this)
    }
}
