package io.github.chinalwb.vocab.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** A state that writes every change to SharedPreferences [file]/[key] (null removes the key). */
private class PrefState<T>(initial: T, private val save: (T) -> Unit) : MutableState<T> {
    private val s = mutableStateOf(initial)
    override var value: T
        get() = s.value
        set(v) { if (v != s.value) { s.value = v; save(v) } }
    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }
}

/** A remembered String? setting, kept across launches. */
@Composable
fun rememberPrefString(file: String, key: String, default: String?): MutableState<String?> {
    val prefs = LocalContext.current.getSharedPreferences(file, Context.MODE_PRIVATE)
    return remember(file, key) {
        PrefState(prefs.getString(key, default)) { v -> prefs.edit().apply { if (v == null) remove(key) else putString(key, v) }.apply() }
    }
}

/** A remembered Boolean setting, kept across launches. */
@Composable
fun rememberPrefBool(file: String, key: String, default: Boolean): MutableState<Boolean> {
    val prefs = LocalContext.current.getSharedPreferences(file, Context.MODE_PRIVATE)
    return remember(file, key) { PrefState(prefs.getBoolean(key, default)) { v -> prefs.edit().putBoolean(key, v).apply() } }
}
