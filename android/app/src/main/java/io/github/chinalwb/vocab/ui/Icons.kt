package io.github.chinalwb.vocab.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Material's "grid_view" — icons-core doesn't ship it and icons-extended is huge for one icon.
val GridViewIcon: ImageVector = ImageVector.Builder("GridView", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M3,3v8h8V3H3zM9,9H5V5h4V9zM3,13v8h8v-8H3zM9,19H5v-4h4V19z" +
            "M13,3v8h8V3H13zM19,9h-4V5h4V9zM13,13v8h8v-8H13zM19,19h-4v-4h4V19z"
    ),
    fill = SolidColor(Color.Black),
).build()

// Material's "open_in_full" / "close_fullscreen": the entry card grows to the whole page and back.
val OpenInFullIcon: ImageVector = ImageVector.Builder("OpenInFull", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes("M21,11V3h-8l3.29,3.29l-10,10L3,13v8h8l-3.29,-3.29l10,-10z"),
    fill = SolidColor(Color.Black),
).build()

val CloseFullscreenIcon: ImageVector = ImageVector.Builder("CloseFullscreen", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M22,3.41L16.71,8.7L20,12h-8V4l3.29,3.29L20.59,2L22,3.41z" +
            "M3.41,22l5.29,-5.29L12,20v-8H4l3.29,3.29L2,20.59L3.41,22z"
    ),
    fill = SolidColor(Color.Black),
).build()

// Material's "volume_up": 朗读.
val VolumeUpIcon: ImageVector = ImageVector.Builder("VolumeUp", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02z" +
            "M14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"
    ),
    fill = SolidColor(Color.Black),
).build()

// Material's "content_copy" and "star_border": the long-press menu on an example sentence.
val ContentCopyIcon: ImageVector = ImageVector.Builder("ContentCopy", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1z" +
            "M19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z"
    ),
    fill = SolidColor(Color.Black),
).build()

val StarBorderIcon: ImageVector = ImageVector.Builder("StarBorder", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M22,9.24l-7.19,-0.62L12,2L9.19,8.63L2,9.24l5.46,4.73L5.82,21L12,17.27L18.18,21l-1.63,-7.03L22,9.24z" +
            "M12,15.4l-3.76,2.27l1,-4.28l-3.32,-2.88l4.38,-0.38L12,6.1l1.71,4.04l4.38,0.38l-3.32,2.88l1,4.28L12,15.4z"
    ),
    fill = SolidColor(Color.Black),
).build()

// Material's "menu_book" (outline only): the 阅读 page.
val MenuBookIcon: ImageVector = ImageVector.Builder("MenuBook", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5c-1.95,0 -4.05,0.4 -5.5,1.5c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65" +
            "c0,0.25 0.25,0.5 0.5,0.5c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5" +
            "c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.65,0 3.35,0.3 4.75,1.05c0.1,0.05 0.15,0.05 0.25,0.05c0.25,0 0.5,-0.25 0.5,-0.5V6" +
            "C22.4,5.55 21.75,5.25 21,5zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5c-1.7,0 -4.15,0.65 -5.5,1.5V8" +
            "c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.2,0 2.4,0.15 3.5,0.5V18.5z"
    ),
    fill = SolidColor(Color.Black),
).build()

// Material's "backspace" (outlined): ⌫ in the word-chip panel takes back the last word.
val BackspaceIcon: ImageVector = ImageVector.Builder("Backspace", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M22,3H7c-0.69,0 -1.23,0.35 -1.59,0.88L0,12l5.41,8.11C5.77,20.64 6.31,21 7,21h15c1.1,0 2,-0.9 2,-2V5C24,3.9 23.1,3 22,3z" +
            "M22,19H7.07L2.4,12l4.66,-7H22V19z" +
            "M10.41,17L14,13.41L17.59,17L19,15.59L15.41,12L19,8.41L17.59,7L14,10.59L10.41,7L9,8.41L12.59,12L9,15.59z"
    ),
    fill = SolidColor(Color.Black),
).build()

// Material's "picture_as_pdf": 导出 PDF.
val PdfIcon: ImageVector = ImageVector.Builder("PictureAsPdf", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M20,2H8C6.9,2 6,2.9 6,4v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4C22,2.9 21.1,2 20,2z" +
            "M20,16H8V4h12V16z" +
            "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6z" +
            "M16,12V9c0,-0.55 -0.45,-1 -1,-1h-2v5h2C15.55,13 16,12.55 16,12z" +
            "M14,9h1v3h-1V9z" +
            "M18,11h1v-1h-1V9h1V8h-2v5h1V11z" +
            "M10,11h1c0.55,0 1,-0.45 1,-1V9c0,-0.55 -0.45,-1 -1,-1H9v5h1V11z" +
            "M10,9h1v1h-1V9z"
    ),
    fill = SolidColor(Color.Black),
).build()

/** Material "history": 学习历史 in the ⋮ menu. */
val HistoryIcon: ImageVector = ImageVector.Builder("History", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z" +
            "M12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z"
    ),
    fill = SolidColor(Color.Black),
).build()

// Material's "screen_rotation": a table opens on its own page, turned to landscape (用户 2026-10-27,
// a different icon from the card's ⤢ so the two buttons aren't confused).
val ScreenRotationIcon: ImageVector = ImageVector.Builder("ScreenRotation", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M16.48,2.52c3.27,1.55 5.61,4.72 5.97,8.48h1.5C23.44,4.84 18.29,0 12,0l-0.66,0.03l3.81,3.81l1.33,-1.32z" +
            "M10.23,1.75c-0.59,-0.59 -1.54,-0.59 -2.12,0L1.75,8.11c-0.59,0.59 -0.59,1.54 0,2.12l12.02,12.02" +
            "c0.59,0.59 1.54,0.59 2.12,0l6.36,-6.36c0.59,-0.59 0.59,-1.54 0,-2.12L10.23,1.75z" +
            "M14.83,21.19L2.81,9.17l6.36,-6.36l12.02,12.02l-6.36,6.36z" +
            "M7.52,21.48C4.25,19.94 1.91,16.76 1.55,13L0.05,13C0.56,19.16 5.71,24 12,24l0.66,-0.03l-3.81,-3.81l-1.33,1.32z"
    ),
    fill = SolidColor(Color.Black),
).build()
