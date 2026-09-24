package io.github.chinalwb.vocab.review

import android.content.Context
import io.github.chinalwb.vocab.data.Entry
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
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

const val NEW_PER_DAY = 10

/** SM-2 scheduling state for one entry. Days are LocalDate epoch days. */
@Serializable
data class Card(
    val ease: Double = 2.5,
    val interval: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val due: Long = 0,
)

@Serializable
data class ReviewData(
    val cards: Map<String, Card> = emptyMap(),
    /** How many never-seen entries were introduced on [newDay]. */
    val newDay: Long = 0,
    val newToday: Int = 0,
)

enum class Grade(val q: Int, val label: String) {
    Again(1, "忘了"),
    Hard(3, "模糊"),
    Good(4, "记得"),
    Easy(5, "轻松"),
}

/**
 * Review progress lives only on this device (filesDir/review.json) — it is
 * never written back to the public repo.
 */
class ReviewStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "review.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow(ReviewData())
    val state: StateFlow<ReviewData> = _state.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.value = runCatching { json.decodeFromString<ReviewData>(file.readText()) }
                .getOrDefault(ReviewData())
        }
    }

    suspend fun grade(anchor: String, grade: Grade) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val today = today()
            val s = _state.value
            val existing = s.cards[anchor]
            val newCount = if (s.newDay == today) s.newToday else 0
            _state.value = s.copy(
                cards = s.cards + (anchor to schedule(existing ?: Card(), grade, today)),
                newDay = today,
                newToday = if (existing == null) newCount + 1 else newCount,
            )
            file.writeText(json.encodeToString(_state.value))
        }
    }

    suspend fun reset() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.value = ReviewData()
            file.delete()
        }
    }
}

fun today(): Long = LocalDate.now().toEpochDay()

fun schedule(c: Card, grade: Grade, today: Long): Card {
    val q = grade.q
    val ease = max(1.3, c.ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
    return if (q < 3) {
        c.copy(ease = ease, interval = 1, reps = 0, lapses = c.lapses + 1, due = today + 1)
    } else {
        val reps = c.reps + 1
        val interval = when (reps) {
            1 -> 1
            2 -> if (q == 5) 4 else 3
            else -> max(c.interval + 1, (c.interval * ease).roundToInt())
        }
        c.copy(ease = ease, interval = interval, reps = reps, due = today + interval)
    }
}

data class ReviewPlan(val due: List<Entry>, val fresh: List<Entry>) {
    val all get() = due + fresh
}

/**
 * Today's queue: everything due, then up to the day's remaining quota of
 * never-seen entries, most recently 收录 first — so what just arrived in a
 * fetch is what gets introduced next.
 */
fun planToday(entries: List<Entry>, review: ReviewData): ReviewPlan {
    val today = today()
    val due = entries.filter { e -> review.cards[e.anchor]?.let { it.due <= today } == true }
        .sortedBy { review.cards[it.anchor]!!.due }
    val quota = NEW_PER_DAY - if (review.newDay == today) review.newToday else 0
    val fresh = entries.filter { it.anchor !in review.cards }
        .sortedByDescending { it.date.removePrefix("~") }
        .take(max(0, quota))
    return ReviewPlan(due, fresh)
}
