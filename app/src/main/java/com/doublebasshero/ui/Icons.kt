package com.doublebasshero.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons (24×24), drawn like the prototype's inline SVGs. */
object Icons {
    private fun stroke(vararg paths: String, filled: Boolean = false) = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = if (filled) null else SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    val home = stroke("M3 11l9-7 9 7", "M5 10v10h14V10")
    val calendar = stroke("M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M3 10h18M8 3v4M16 3v4")
    val chart = stroke("M3 20h18", "M5 16l4-5 4 3 6-8")
    val layers = stroke("M12 3l9 5-9 5-9-5 9-5z", "M3 13l9 5 9-5")
    val bell = stroke("M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9", "M13.7 21a2 2 0 0 1-3.4 0")
    val close = stroke("M6 6l12 12M18 6L6 18")
    val back = stroke("M15 5l-7 7 7 7")
    val forward = stroke("M9 5l7 7-7 7")
    val play = stroke("M7 4l13 8-13 8z", filled = true)
    val pause = stroke("M6 4h4v16H6z", "M14 4h4v16h-4z", filled = true)
    val skip = stroke("M5 5l10 7-10 7z", "M19 5v14")
    val sound = stroke("M4 9h4l5-4v14l-5-4H4z", "M17 9a4 4 0 0 1 0 6")
    val mute = stroke("M4 9h4l5-4v14l-5-4H4z", "M17 9l5 6M22 9l-5 6")
}
