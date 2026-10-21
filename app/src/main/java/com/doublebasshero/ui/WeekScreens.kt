package com.doublebasshero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doublebasshero.engine.DayState
import com.doublebasshero.engine.ReviewReason
import com.doublebasshero.engine.StartKey
import com.doublebasshero.engine.weekStart

@Composable
fun ScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBox(Icons.back, "Back", 44.dp, onClick = onBack)
        HGap(12.dp)
        Column {
            Hud(title, 28.sp)
            Text(subtitle, color = C.muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun ReviewScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val picks by vm.reviewPicks.collectAsState()
    val d = data ?: return
    val review = d.review
    if (review == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.go(Screen.Today) }
        return
    }
    val (week, recs) = review
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Week review", "${week.short()} – ${week.plusDays(6).short()}") { vm.go(Screen.Today) }
        Body("Start BPMs for this week, recommended from last week's sessions. Accept them all or adjust any of them: you are the judge of your form.")
        recs.groupBy { it.dayType }.forEach { (type, rows) ->
            val color = type.color()
            val sessions = d.calendar.sessionsIn(week).count { it.dayType == type }
            Card(border = color) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Hud(type.title, 20.sp, color, Modifier.weight(1f))
                    Text("$sessions session${if (sessions == 1) "" else "s"}", color = C.muted, fontSize = 12.sp)
                }
                rows.forEach { rec ->
                    val key = StartKey(rec.exercise, rec.dayType)
                    val chosen = picks[key] ?: rec.delta
                    val (reason, rc) = when (val rs = rec.reason) {
                        ReviewReason.NoSession -> "No completed session: unchanged" to C.muted
                        is ReviewReason.Broken -> "Broken on ${WD_FULL[rs.date.dayOfWeek.value - 1].take(3)}" to C.bad
                        is ReviewReason.Shaky -> "Shaky on ${WD_FULL[rs.date.dayOfWeek.value - 1].take(3)}" to C.endurance
                        is ReviewReason.HeldBack -> "+0 chosen on ${WD_FULL[rs.date.dayOfWeek.value - 1].take(3)}" to C.endurance
                        is ReviewReason.AllClean -> "All clean (${rs.sessions}/${rs.sessions})" to C.hybrid
                        is ReviewReason.AllCleanFeelingGood -> "All clean, +4 in ${rs.plusFours}/${rs.sessions}" to C.hybrid
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rec.exercise.title, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(reason, color = rc, fontSize = 12.sp)
                        }
                        Text("${rec.from} → ", style = hud(20.sp))
                        Text("${rec.to(d.level.cap, chosen)}", style = hud(20.sp, color = color))
                    }
                    ChoiceRow(listOf(-2, 0, 2, 4).map { Triple(if (it > 0) "+$it" else "$it", color, it == chosen) }, { i ->
                        vm.reviewPicks.value = picks + (key to listOf(-2, 0, 2, 4)[i])
                    }, height = 36.dp)
                }
            }
        }
        PrimaryButton("Accept and start the week", { vm.acceptReview() })
    }
}

@Composable
fun EditWeekScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val d = data ?: return
    val ws = d.today.weekStart()
    val w = d.calendar.currentWeek
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Edit this week", "${ws.short()} – ${ws.plusDays(6).short()} · this week only") { vm.go(Screen.Today) }
        Card {
            Label("Plan")
            WeekStrip(d, null) { }
            Body(
                "${w.sessionCount} sessions · ${7 - w.sessionCount} rest days" +
                    (if (w.sessionCount < 3) " · counts as a deload week" else "") +
                    (if (w.makeupPending) " · make-up suggested on " + w.makeupCandidates.joinToString(" and ") { WD_FULL[it] } else ""),
                size = 13.sp,
            )
        }
        Column {
            (0..6).forEach { i ->
                val date = ws.plusDays(i.toLong())
                val info = d.calendar.day(date)
                val look = tileLook(info, w.makeupPending)
                val unavailable = info.state == DayState.UNAVAILABLE
                val editable = date >= d.today && info.session == null && info.state != DayState.SKIPPED && info.inProgram
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tile(look.text, look.bg, look.fg, 34.dp, 38.dp, border = look.border, dashed = look.dashed, size = 18.sp)
                    HGap(12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(date.label(), color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                !info.inProgram -> "Before you started"
                                info.type == com.doublebasshero.engine.DayType.BENCHMARK -> "Benchmark day"
                                info.session != null -> "Done"
                                info.state == DayState.SKIPPED -> "Skipped · counts as rest"
                                unavailable -> "Can't train · counts as rest"
                                info.state == DayState.MAKEUP -> if (w.makeupPending) "Suggested make-up session" else "Make-up session"
                                info.type == com.doublebasshero.engine.DayType.REST -> "Rest"
                                else -> info.type.dayName()
                            },
                            color = C.muted, fontSize = 12.sp,
                        )
                    }
                    if (editable) {
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (unavailable) C.text else Color.Transparent)
                                .border(1.dp, if (unavailable) C.text else C.lineStrong, RoundedCornerShape(10.dp))
                                .clickable(role = Role.Switch) { vm.toggleUnavailable(date) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) { Text(if (unavailable) "AVAILABLE" else "CAN'T TRAIN", style = hud(15.sp, FontWeight.Bold, if (unavailable) C.ink else C.textSoft)) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            }
        }
        Body(
            "A day you can't train counts as a rest day.\nIf a day off can make up for it, we'll suggest it.\n" +
                "If the week is too short: S H E E E → S H E E → S H E → H E → E.\nFewer than 3 sessions turns the week into a deload.",
            color = C.muted, size = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Reset", { vm.resetWeek() }, Modifier.width(120.dp))
            PrimaryButton("Done", { vm.go(Screen.Today) }, Modifier.weight(1f))
        }
    }
}
