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
