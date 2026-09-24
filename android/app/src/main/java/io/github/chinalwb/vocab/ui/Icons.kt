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
