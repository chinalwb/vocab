package io.github.chinalwb.vocab.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette lifted from template.html so the app and the page read as one thing.
private val Light = lightColorScheme(
    background = Color(0xFFF7F6F2), surface = Color(0xFFF7F6F2),
    onBackground = Color(0xFF1F1F1E), onSurface = Color(0xFF1F1F1E),
    onSurfaceVariant = Color(0xFF77756C), surfaceVariant = Color(0xFFFFFFFF),
    outline = Color(0xFFE2E0D8), outlineVariant = Color(0xFFE2E0D8),
    primary = Color(0xFF1F1F1E), onPrimary = Color(0xFFF7F6F2),
    secondaryContainer = Color(0xFF1F1F1E), onSecondaryContainer = Color(0xFFF7F6F2),
    surfaceContainer = Color(0xFFEFEDE7), surfaceContainerHigh = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF1F1F1E), inverseOnSurface = Color(0xFFF7F6F2),
)
private val Dark = darkColorScheme(
    background = Color(0xFF1B1B19), surface = Color(0xFF1B1B19),
    onBackground = Color(0xFFECEAE2), onSurface = Color(0xFFECEAE2),
    onSurfaceVariant = Color(0xFF928F84), surfaceVariant = Color(0xFF2A2926),
    outline = Color(0xFF343330), outlineVariant = Color(0xFF343330),
    primary = Color(0xFFECEAE2), onPrimary = Color(0xFF1B1B19),
    secondaryContainer = Color(0xFFECEAE2), onSecondaryContainer = Color(0xFF1B1B19),
    surfaceContainer = Color(0xFF232320), surfaceContainerHigh = Color(0xFF2A2926),
    inverseSurface = Color(0xFFECEAE2), inverseOnSurface = Color(0xFF1B1B19),
)

data class LevelStyle(val short: String, val name: String, val light: Color, val dark: Color)

val GROUP_ORDER = listOf("A1", "A2", "B1", "B2", "C1", "C2", "TERM", "GRAMMAR", "SENTENCE")

val LEVELS = mapOf(
    "A1" to LevelStyle("A1", "入门", Color(0xFFDCECD4), Color(0xFF2E4A2A)),
    "A2" to LevelStyle("A2", "基础", Color(0xFFCBE7E1), Color(0xFF1D4A45)),
    "B1" to LevelStyle("B1", "进阶", Color(0xFFD2E3F4), Color(0xFF254460)),
    "B2" to LevelStyle("B2", "中高级", Color(0xFFFBF0BD), Color(0xFF57501C)),
    "C1" to LevelStyle("C1", "高级", Color(0xFFFBDCC0), Color(0xFF5A3A1D)),
    "C2" to LevelStyle("C2", "精通", Color(0xFFF7CFC9), Color(0xFF5B2B28)),
    "TERM" to LevelStyle("术语", "术语与固定搭配", Color(0xFFDDD9F2), Color(0xFF3B3364)),
    "GRAMMAR" to LevelStyle("语法", "语法与语域笔记", Color(0xFFF2D5E4), Color(0xFF562A44)),
    "SENTENCE" to LevelStyle("句子", "整句订正", Color(0xFFE8D7C3), Color(0xFF4A3626)),
)

fun levelStyle(level: String) = LEVELS[level] ?: LEVELS.getValue("TERM")

@Composable
fun levelColor(level: String): Color =
    levelStyle(level).let { if (isSystemInDarkTheme()) it.dark else it.light }

@Composable
fun VocabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
