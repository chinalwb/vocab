package io.github.chinalwb.vocab.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.chinalwb.vocab.VocabApp
import io.github.chinalwb.vocab.data.Entry
import java.util.Locale

/**
 * 朗读, with the phone's own text-to-speech engine (Samsung's or Google's): free, offline, no
 * key. One instance for the app's life; the engine binds in the background, and a tap before
 * it's ready is simply dropped.
 */
class Speaker(context: Context) {
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) ready = true
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        // the engine keeps its own default voice; US English unless that isn't installed
        tts.language = Locale.US
        tts.setSpeechRate(0.95f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vocab")
    }
}

@Composable
fun speaker(): Speaker = (LocalContext.current.applicationContext as VocabApp).speaker

/** 🔊 — reads [text] aloud. */
@Composable
fun SpeakButton(text: String, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val sp = speaker()
    IconButton(onClick = { sp.speak(text) }, modifier = modifier.size(size + 12.dp)) {
        Icon(VolumeUpIcon, "朗读", Modifier.size(size), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What 🔊 on an entry reads: its headword, or a 自测题's first answer (its title is Chinese). */
fun Entry.speakText(): String {
    if (level == "SELFTEST") return selfTestAnswer.lines().firstOrNull { it.isNotBlank() }.orEmpty()
    val t = Regex("""^(.+?)\s*[((].+[))]$""").find(title.trim())?.groupValues?.get(1) ?: title
    return if (Regex("[A-Za-z]").containsMatchIn(t)) t else ""
}
