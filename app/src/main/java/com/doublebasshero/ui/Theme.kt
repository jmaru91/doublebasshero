package com.doublebasshero.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doublebasshero.engine.DayType
import com.doublebasshero.engine.FormRating

object C {
    val night = Color(0xFF0F141C)
    val navBar = Color(0xFF121821)
    val surface = Color(0xFF171F2C)
    val surfaceDeep = Color(0xFF111823)
    val line = Color(0xFF263247)
    val lineStrong = Color(0xFF33415A)
    val text = Color(0xFFE8EDF5)
    val textSoft = Color(0xFFC5CFDD)
    val muted = Color(0xFF9AA8BC)
    val dim = Color(0xFF8A97AB)
    val faint = Color(0xFF5B677A)
    val ink = Color(0xFF0B0F16)
    val speed = Color(0xFF4C9BFF)
    val speedSoft = Color(0xFF8BBDFF)
    val hybrid = Color(0xFF37C77F)
    val endurance = Color(0xFFF0B840)
    val rest = Color(0xFFE9E4D4)
    val restTile = Color(0xFF222B39)
    val warm = Color(0xFFB79BFF)
    val bad = Color(0xFFFF8A7F)
    val left = Color(0xFFFF9F43)
    val warnBg = Color(0xFF2A2414)
    val goodBg = Color(0xFF13301F)
}

fun DayType.color(): Color = when (this) {
    DayType.SPEED -> C.speed
    DayType.HYBRID -> C.hybrid
    DayType.ENDURANCE -> C.endurance
    DayType.REST, DayType.BENCHMARK -> C.rest
}

fun DayType.dayName(): String = when (this) {
    DayType.REST -> "Rest day"
    DayType.BENCHMARK -> "Benchmark day"
    else -> "$title day"
}

fun FormRating.color(): Color = when (this) {
    FormRating.CLEAN -> C.hybrid
    FormRating.SHAKY -> C.endurance
    FormRating.BROKEN -> C.bad
}

/** Condensed uppercase "HUD" type, like the course graphics (Roboto Condensed ships with Android). */
private val condensed = FontFamily(
    androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)),
)

fun hud(size: TextUnit, weight: FontWeight = FontWeight.ExtraBold, color: Color = C.text, italic: Boolean = false) = TextStyle(
    fontFamily = condensed,
    fontWeight = weight,
    fontSize = size,
    color = color,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    letterSpacing = 0.3.sp,
)

private val scheme: ColorScheme = darkColorScheme(
    primary = C.speed,
    onPrimary = C.ink,
    background = C.night,
    onBackground = C.text,
    surface = C.surface,
    onSurface = C.text,
    error = C.bad,
)

@Composable
fun DbhTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

@Composable
fun Hud(text: String, size: TextUnit, color: Color = C.text, modifier: Modifier = Modifier, italic: Boolean = false, align: TextAlign? = null) =
    Text(text.uppercase(), style = hud(size, color = color, italic = italic), modifier = modifier, textAlign = align, lineHeight = size * 1.0)

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = C.muted) =
    Text(text.uppercase(), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = modifier)

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = C.textSoft, size: TextUnit = 14.sp, weight: FontWeight = FontWeight.Normal, align: TextAlign? = null) =
    Text(text, color = color, fontSize = size, fontWeight = weight, lineHeight = size * 1.4, modifier = modifier, textAlign = align)

@Composable
fun Card(
    modifier: Modifier = Modifier,
    border: Color = C.line,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.surface)
            .border(1.dp, border, shape)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.speed, height: Dp = 56.dp, enabled: Boolean = true) =
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = C.ink),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) { Hud(text, 21.sp, C.ink) }

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.textSoft, border: Color = C.lineStrong, height: Dp = 48.dp) =
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, border),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) { Hud(text, 17.sp, color, weight = FontWeight.Bold) }

@Composable
private fun Hud(text: String, size: TextUnit, color: Color, weight: FontWeight) =
    Text(text.uppercase(), style = hud(size, weight, color), maxLines = 1)

/** Slanted HUD tile, like the course's day-type cards. */
@Composable
fun Tile(
    text: String,
    bg: Color,
    fg: Color,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    border: Color = bg,
    dashed: Boolean = false,
    size: TextUnit = 22.sp,
) {
    Box(
        modifier
            .size(width, height)
            .clip(Slant)
            .background(bg)
            .let { if (dashed) it.dashedBorder(border, Slant) else it.border(2.dp, border, Slant) },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = hud(size, color = fg)) }
}

/** Dashed outline, used for deload days and suggested make-up days. */
fun Modifier.dashedBorder(color: Color, shape: androidx.compose.ui.graphics.Shape, width: Dp = 2.dp): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline, color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = width.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
    )
}

/** The course's slanted card shape (like skewX(-10deg)), text stays upright. */
val Slant = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val k = size.height * 0.176f / 2f
    moveTo(k * 2, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - k * 2, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = C.muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.hybrid, checkedThumbColor = C.text, uncheckedTrackColor = C.lineStrong),
        )
    }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color = C.ink, modifier: Modifier = Modifier, size: TextUnit = 14.sp) =
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 7.dp, vertical = 1.dp)) {
        Text(text.uppercase(), style = hud(size, FontWeight.Bold, fg))
    }

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(C.line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

@Composable
fun Dot(color: Color, size: Dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

/** Big, foot-friendly choice buttons (ratings, ramp steps). */
@Composable
fun ChoiceRow(options: List<Triple<String, Color, Boolean>>, onPick: (Int) -> Unit, height: Dp = 52.dp, sub: List<String>? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, (label, color, selected) ->
            Column(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) color else Color.Transparent)
                    .border(2.dp, color, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onPick(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(label.uppercase(), style = hud(18.sp, color = if (selected) C.ink else color))
                sub?.getOrNull(i)?.let { Text(it, fontSize = 11.sp, color = if (selected) C.ink else C.muted, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun Modal(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xC005070A))
            .clickable(onClick = onDismiss, indication = null, interactionSource = remember { MutableInteractionSource() })
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(C.surface)
                .border(1.dp, C.lineStrong, RoundedCornerShape(18.dp))
                .clickable(enabled = true, indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}
