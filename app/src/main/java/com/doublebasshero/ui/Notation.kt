package com.doublebasshero.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doublebasshero.engine.WarmupExercise

/**
 * Warm-up patterns as drum notation on a 5-line staff, two bars per row:
 * - feet: kick in the first space, stems down, 8ths beamed in pairs (one pair
 *   per beat), 8th rests where the foot is silent, R/L under each note;
 * - hands (Solo Leg, as in the PDF): hi-hat as an x above the staff and snare in
 *   the third space, stems up.
 * The 8th being played is highlighted.
 */
@Composable
fun WarmupNotation(ex: WarmupExercise, activeBar: Int?, activeCell: Int?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val rows = ex.bars.indices.chunked(2)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            val description = row.joinToString(" | ") { ex.bars[it].replace('-', '·') }
            Canvas(Modifier.fillMaxWidth().height(112.dp).semantics { contentDescription = description }) {
                drawRow(ex, row, activeBar, activeCell, measurer)
            }
        }
    }
}

private fun DrawScope.drawRow(ex: WarmupExercise, row: List<Int>, activeBar: Int?, activeCell: Int?, measurer: TextMeasurer) {
    val pad = 6.dp.toPx()
    val barW = (size.width - pad * 2) / 2
    val slotW = barW / 8
    val space = 7.dp.toPx()
    val top = 30.dp.toPx()
    val bottom = top + 4 * space
    val kickY = bottom - space / 2 // first space (F4)
    val snareY = top + 2.5f * space // third space from the bottom (C5)
    val hatY = top - space / 2 // just above the top line (G5)
    val headW = minOf(slotW * 0.56f, 11.dp.toPx())
    val headH = headW * 0.75f
    val stroke = 1.6.dp.toPx()
    val handStemTop = 8.dp.toPx()
    val footStemEnd = bottom + 24.dp.toPx()

    // Staff lines across the row.
    for (l in 0 until 5) {
        val y = top + l * space
        drawLine(C.lineStrong, Offset(pad, y), Offset(pad + barW * row.size, y), 1.dp.toPx())
    }

    row.forEachIndexed { slot, barIndex ->
        val bar = ex.bars[barIndex]
        val hats = ex.hats?.get(barIndex)
        val snare = ex.snare?.get(barIndex)
        val left = pad + slot * barW
        val alpha = if (activeBar != null && activeBar != barIndex) 0.45f else 1f
        listOf(left, left + barW).forEach { x -> drawLine(C.dim.copy(alpha = alpha), Offset(x, top), Offset(x, bottom), 1.2.dp.toPx()) }

        fun cx(i: Int) = left + (i + 0.5f) * slotW
        val footStemX = { i: Int -> cx(i) - headW / 2 + stroke / 2 }
        val handStemX = { i: Int -> cx(i) + headW / 2 - stroke / 2 }
        val ink = C.text.copy(alpha = 0.85f * alpha)

        for (i in 0 until 8) {
            val active = activeBar == barIndex && activeCell == i
            if (active) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(left + i * slotW + 1.dp.toPx(), 2.dp.toPx()),
                    size = Size(slotW - 2.dp.toPx(), size.height - 4.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            // Hands: hi-hat x and/or snare head on one up-stem.
            val hat = hats?.get(i) == 'x'
            val sn = snare?.get(i) == 's'
            if (hat || sn) {
                val handColor = (if (active) Color.White else C.textSoft).copy(alpha = alpha)
                if (hat) drawX(cx(i), hatY, headW * 0.42f, handColor)
                if (sn) drawOval(handColor, topLeft = Offset(cx(i) - headW / 2, snareY - headH / 2), size = Size(headW, headH))
                val from = if (sn) snareY else hatY
                drawLine(handColor, Offset(handStemX(i), from), Offset(handStemX(i), handStemTop), stroke)
            }

            // Feet.
            val ch = bar[i]
            val foot = when (ch) { 'R' -> C.speed; 'L' -> C.left; else -> C.dim }
            val color = (if (active) Color.White else foot).copy(alpha = alpha)
            if (ch == '-') {
                drawEighthRest(cx(i), kickY + 6.dp.toPx(), color)
                continue
            }
            drawOval(color, topLeft = Offset(cx(i) - headW / 2, kickY - headH / 2), size = Size(headW, headH))
            drawLine(color, Offset(footStemX(i), kickY), Offset(footStemX(i), footStemEnd), stroke)
            val label = measurer.measure("$ch", hud(13.sp, color = color))
            drawText(label, topLeft = Offset(cx(i) - label.size.width / 2f, footStemEnd + 4.dp.toPx()))
        }

        // Feet: one beam per beat when both 8ths are played, a flag when only one is.
        for (beat in 0 until 4) {
            val a = beat * 2
            val b = a + 1
            val aNote = bar[a] != '-'
            val bNote = bar[b] != '-'
            val beamH = 4.dp.toPx()
            when {
                aNote && bNote -> drawRect(ink, Offset(footStemX(a) - stroke / 2, footStemEnd - beamH), Size(footStemX(b) - footStemX(a) + stroke, beamH))
                aNote -> drawFlagDown(footStemX(a), footStemEnd, ink)
                bNote -> drawFlagDown(footStemX(b), footStemEnd, ink)
            }
            // Hands: beam pairs too (in this warm-up they are quarters, so no beams).
            if (hats != null || snare != null) {
                val ha = hats?.get(a) == 'x' || snare?.get(a) == 's'
                val hb = hats?.get(b) == 'x' || snare?.get(b) == 's'
                if (ha && hb) drawRect(ink, Offset(handStemX(a) - stroke / 2, handStemTop), Size(handStemX(b) - handStemX(a) + stroke, beamH))
            }
        }
    }
}

private fun DrawScope.drawX(x: Float, y: Float, r: Float, color: Color) {
    val w = 1.8.dp.toPx()
    drawLine(color, Offset(x - r, y - r), Offset(x + r, y + r), w, StrokeCap.Round)
    drawLine(color, Offset(x - r, y + r), Offset(x + r, y - r), w, StrokeCap.Round)
}

/** Flag on a down-stem, curling up to the right from the stem's end. */
private fun DrawScope.drawFlagDown(x: Float, end: Float, color: Color) {
    val p = Path().apply {
        moveTo(x, end)
        quadraticBezierTo(x + 3.dp.toPx(), end - 6.dp.toPx(), x + 7.dp.toPx(), end - 9.dp.toPx())
        quadraticBezierTo(x + 6.dp.toPx(), end - 12.dp.toPx(), x + 4.dp.toPx(), end - 15.dp.toPx())
    }
    drawPath(p, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
}

/** The 8th-rest glyph: a dot with a hooked tail. */
private fun DrawScope.drawEighthRest(x: Float, y: Float, color: Color) {
    val dot = Offset(x - 2.5.dp.toPx(), y - 6.dp.toPx())
    drawCircle(color, 2.6.dp.toPx(), dot)
    val p = Path().apply {
        moveTo(dot.x, dot.y + 1.dp.toPx())
        quadraticBezierTo(x + 1.dp.toPx(), y - 3.dp.toPx(), x + 3.5.dp.toPx(), y - 8.dp.toPx())
        lineTo(x - 1.dp.toPx(), y + 9.dp.toPx())
    }
    drawPath(p, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
}
