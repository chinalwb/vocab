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
