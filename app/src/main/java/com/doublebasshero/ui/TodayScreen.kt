package com.doublebasshero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doublebasshero.data.AppData
import com.doublebasshero.engine.BarKind
import com.doublebasshero.engine.DayInfo
import com.doublebasshero.engine.DayState
import com.doublebasshero.engine.DayType
import com.doublebasshero.engine.Patterns
import com.doublebasshero.engine.ResumeAction
import com.doublebasshero.engine.SavedSession
import com.doublebasshero.engine.SessionBuilder
import com.doublebasshero.engine.weekStart
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun LocalDate.label(): String = "${dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)}, ${month.getDisplayName(TextStyle.SHORT, Locale.US)} $dayOfMonth"
fun LocalDate.short(): String = "${month.getDisplayName(TextStyle.SHORT, Locale.US)} $dayOfMonth"

/** How a day looks as a calendar tile. */
data class TileLook(val text: String, val bg: Color, val fg: Color, val border: Color, val dashed: Boolean, val alpha: Float = 1f)

fun tileLook(info: DayInfo, makeupPending: Boolean): TileLook {
    val c = info.type.color()
    return when {
        !info.inProgram -> TileLook("", Color.Transparent, C.faint, C.line, false)
        info.state == DayState.SKIPPED -> TileLook("✕", C.restTile, C.dim, C.lineStrong, false, 0.75f)
        info.state == DayState.UNAVAILABLE -> TileLook("—", C.restTile, C.dim, C.lineStrong, false)
        info.session != null -> TileLook("${info.type.letter}", c, C.ink, c, false)
        info.type == DayType.REST -> TileLook("R", C.restTile, Color(0xFFC9C3B3), C.lineStrong, false)
        else -> TileLook("${info.type.letter}", Color.Transparent, c, c, info.deload || (info.state == DayState.MAKEUP && makeupPending))
    }
}

@Composable
fun TodayScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val d = data ?: return
    val sel by vm.selectedDay.collectAsState()
    val interrupted by vm.interrupted.collectAsState()
    val battery by vm.batteryWarning.collectAsState()
    val level = d.level
    val p = d.profile
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hud("Double Bass Hero", 20.sp, C.speed, Modifier.weight(1f), italic = true)
                Text(d.today.label().uppercase(), style = hud(14.sp, FontWeight.SemiBold, C.muted))
                HGap(10.dp)
                IconBox(Icons.bell, "Settings") { vm.go(Screen.Settings) }
            }

            // Level, True BPM, cycle
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tile("LV ${level.number}", C.speed, C.ink, 86.dp, 42.dp, size = 28.sp)
                    HGap(14.dp)
                    Column {
                        Label("Quest ${level.quest.number}")
                        Hud(level.quest.title, 21.sp)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Body("True BPM", Modifier.weight(1f), C.muted)
                    Hud("${p.trueBpm}", 28.sp)
                }
                ProgressBar((p.trueBpm - level.minBpm).toFloat() / (level.benchmarkBpm - level.minBpm), C.speed)
                Row {
                    Text("${level.minBpm}", color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(if (level.isTop) "Top level · ${level.maxBpm}" else "Benchmark at ${level.benchmarkBpm} → LV ${level.number + 1}", color = C.muted, fontSize = 12.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                CycleRow(d)
            }

            interrupted?.let { InterruptedCard(vm, it) }

            if (p.notice.isNotEmpty()) {
                Card(border = C.endurance) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Body(p.notice, Modifier.weight(1f))
                        Text("OK", style = hud(16.sp, color = C.endurance), modifier = Modifier.clickable(role = Role.Button) { vm.clearNotice() }.padding(8.dp))
                    }
                }
            }

            d.review?.let { (week, recs) ->
                val changes = recs.count { it.delta != 0 }
                Card(border = C.speed, onClick = { vm.go(Screen.Review) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Hud("Week review ready", 20.sp, C.speed)
                            Body("${week.short()} – ${week.plusDays(6).short()} · $changes start BPM${if (changes == 1) "" else "s"} to change")
                        }
                        Icon(Icons.forward, null, tint = C.speed)
                    }
                }
            }

            MakeupCard(vm, d)

            // Week strip
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("This week", Modifier.weight(1f))
                    Text("EDIT THIS WEEK", style = hud(15.sp, FontWeight.Bold, C.speed), modifier = Modifier.clickable(role = Role.Button) { vm.go(Screen.EditWeek) }.padding(vertical = 6.dp, horizontal = 4.dp))
                }
                WeekStrip(d, sel) { vm.selectedDay.value = it }
            }

            DayCard(vm, d, sel)
        }
        battery?.let { w ->
            Modal({ vm.batteryWarning.value = null }) {
                Hud("Battery ${w.percent}%", 26.sp, C.endurance)
                Body("This session takes ~${w.minutes} min with the metronome running and the screen off. Plug in your phone so it doesn't die mid-set.")
                PrimaryButton("I'll plug it in", { vm.batteryWarning.value = null })
                GhostButton("Start anyway", vm::batteryContinue, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, size: androidx.compose.ui.unit.Dp = 40.dp, tint: Color = C.textSoft, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).border(1.dp, C.lineStrong, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun CycleRow(d: AppData) {
    val info = d.calendar.day(d.today)
    val pos = info.cycleWeek
    val sessions = d.calendar.currentWeek.sessionCount
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..4).forEach { i ->
                Box(Modifier.size(22.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(
                    when { i < pos -> C.speed; i == pos -> if (i == 4) C.warm else C.speedSoft; else -> C.line },
                ))
            }
        }
        HGap(10.dp)
        Body(
            when {
                pos == 4 -> "Deload week · half the sets, no ramp"
                sessions < 3 -> "Only $sessions sessions this week: it will count as a deload"
                else -> "Cycle · week $pos of 3" + if (pos == 3) " · deload next week" else ""
            },
            size = 13.sp,
        )
    }
}

@Composable
private fun InterruptedCard(vm: AppViewModel, it: Interrupted) {
    Card(border = C.bad) {
        Hud("Session interrupted", 20.sp, C.bad)
        Body(
            if (it.setsDone == 0) "Stopped during ${it.title} · ${it.minutesAgo} min ago. No sets were finished yet: you'll start again from the warm-up."
            else "Stopped at ${it.title} · ${it.minutesAgo} min ago. ${it.setsDone} finished sets are safe; the interrupted set will be redone.",
        )
        PrimaryButton(if (it.action == ResumeAction.REWARM_THEN_RESUME && it.setsDone > 0) "Warm up and resume" else "Resume", vm::resumeInterrupted, height = 50.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (it.setsDone > 0) GhostButton("End and save", { vm.saveInterrupted() }, Modifier.weight(1f))
            GhostButton("Discard", { vm.discardInterrupted() }, Modifier.weight(1f), color = C.bad)
        }
    }
}

@Composable
private fun MakeupCard(vm: AppViewModel, d: AppData) {
    val w = d.calendar.currentWeek
    if (!w.makeupPending) return
    val ws = d.today.weekStart()
    val missed = (0..6).filter { w.days[it].state == DayState.SKIPPED || w.days[it].state == DayState.UNAVAILABLE }
        .filter { ws.plusDays(it.toLong()) >= d.profile.programStart.let { e -> LocalDate.ofEpochDay(e) } }
        .joinToString(" and ") { WD_FULL[it] }
    val names = w.makeupCandidates.joinToString(" and ") { WD_FULL[it] }
    Card(border = C.endurance) {
        Hud("Make up a session?", 20.sp, C.endurance)
        Body("You can't train on $missed. Train on $names instead to keep every session this week?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Yes, add " + w.makeupCandidates.joinToString(" + ") { WD_FULL[it].take(3) }, { vm.setMakeup(true) }, Modifier.weight(1f), C.endurance, 48.dp)
            GhostButton("No, drop one", { vm.setMakeup(false) }, Modifier.weight(1f))
        }
    }
}

@Composable
fun WeekStrip(d: AppData, selected: LocalDate?, onPick: (LocalDate) -> Unit) {
    val ws = d.today.weekStart()
    val pending = d.calendar.currentWeek.makeupPending
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).forEach { i ->
            val date = ws.plusDays(i.toLong())
            val info = d.calendar.day(date)
            val look = tileLook(info, pending)
            Column(
                Modifier.width(44.dp).clickable(role = Role.Button) { onPick(date) }.semantics { contentDescription = "${date.label()}, ${info.type.dayName()}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("MTWTFSS"[i].toString(), color = C.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier.alpha(look.alpha).let { if (date == selected) it.border(2.dp, C.text, Slant) else it },
                ) { Tile(look.text, look.bg, look.fg, 40.dp, 50.dp, border = look.border, dashed = look.dashed, size = 24.sp) }
                Text("${date.dayOfMonth}", color = if (date == d.today) C.text else C.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Dot(if (date == d.today) C.text else Color.Transparent, 4.dp)
            }
        }
    }
}

@Composable
fun PatternCells(type: DayType, active: Int = -1, height: androidx.compose.ui.unit.Dp = 30.dp) {
    val pattern = Patterns.forDay(type)
    if (pattern.isEmpty()) return
    val color = type.color()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..3).forEach { i ->
            val k = pattern[i % pattern.size]
            val bg = when (k) { BarKind.SIXTEENTHS -> color; BarKind.EIGHTHS -> Color.Transparent; BarKind.REST -> C.restTile }
            val fg = when (k) { BarKind.SIXTEENTHS -> C.ink; BarKind.EIGHTHS -> color; BarKind.REST -> C.dim }
            val border = if (k == BarKind.REST) C.lineStrong else color
            Box(
                Modifier.weight(1f).height(height).alpha(if (active < 0 || active == i) 1f else 0.45f)
                    .clip(RoundedCornerShape(7.dp)).background(bg)
                    .border(if (active == i) 3.dp else 1.dp, if (active == i) C.text else border, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(when (k) { BarKind.SIXTEENTHS -> "16THS"; BarKind.EIGHTHS -> "8THS"; BarKind.REST -> "REST" }, style = hud(13.sp, FontWeight.Bold, fg)) }
        }
    }
}

@Composable
private fun DayCard(vm: AppViewModel, d: AppData, sel: LocalDate) {
    val info = d.calendar.day(sel)
    val color = info.type.color()
    val isToday = sel == d.today
    val plan = if (info.canStart) d.calendar.buildSession(sel, d.startBpms) else null
    Card(border = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("${if (isToday) "Today" else if (sel < d.today) "Past" else "Planned"} · ${sel.label()}", Modifier.weight(1f))
            val (status, sc) = when {
                info.session?.result?.partial == true -> "Partial" to C.endurance
                info.session != null -> "Done" to C.hybrid
                info.state == DayState.SKIPPED -> "Skipped" to C.bad
                info.state == DayState.UNAVAILABLE -> "Can't train" to C.muted
                info.type == DayType.REST -> "Rest" to Color(0xFFC9C3B3)
                isToday -> "To do" to C.text
                else -> "Planned" to C.muted
            }
            Text(status.uppercase(), style = hud(14.sp, FontWeight.Bold, sc))
        }
        Hud(info.type.dayName() + if (info.deload && info.type in DayType.PROGRESSING) " · deload" else "", 36.sp, color, italic = true)
        Body(
            when (info.type) {
                DayType.SPEED -> "1 bar of 16ths, then 3 bars of rest. Fresh, zero fatigue, long rests."
                DayType.HYBRID -> "1 bar of 8ths, then 1 bar of 16ths, repeating."
                DayType.ENDURANCE -> "Non-stop 16ths. Little rest, fatigue on purpose."
                DayType.BENCHMARK -> "Warm-up, then 4:00 of non-stop 16ths at ${d.level.benchmarkBpm}. That is the whole session."
                DayType.REST -> "No leg work at all. Recovery is part of training: the next cycle starts fresh."
            },
        )
        val note = when {
            info.type == DayType.BENCHMARK -> "Unlocked: every exercise reached the cap on Endurance days. Today is fresh, after a rest day." to C.hybrid
            info.converted -> "Every exercise hit the cap (${d.level.cap}) on ${info.slot.title} days, so this is an Endurance day until you pass the benchmark." to C.endurance
            info.deload && info.type.isTraining -> "Deload week: half the sets, every set at the start BPM." to C.warm
            info.state == DayState.MAKEUP -> "Make-up session on one of your usual days off." to C.endurance
            else -> null
        }
        note?.let { (t, c) -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C2433)).padding(10.dp)) { Body(t, color = c, size = 13.sp) } }

        if (info.type in DayType.PROGRESSING && info.session == null && info.state != DayState.SKIPPED && info.state != DayState.UNAVAILABLE) PatternCells(info.type)

        if (plan != null && !plan.isBenchmark) {
            val minutes = (SessionBuilder.estimatedDurationSec(plan) + 30) / 60
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("~$minutes′", "Duration", Modifier.weight(1f))
                Stat("${plan.exercises.sumOf { it.sets }}", "Sets", Modifier.weight(1f))
                Stat("${plan.exercises.size}", "Exercises", Modifier.weight(1f))
            }
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Body("Warm-up + solo leg", Modifier.weight(1f))
                    Text("~2′", style = hud(16.sp, FontWeight.Bold, C.muted))
                }
                plan.exercises.forEach { e ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.exercise.title, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                if (e.weak) { HGap(8.dp); Chip("Weak spot", C.bad, size = 11.sp) }
                            }
                            val top = SessionBuilder.projectedTop(plan, e)
                            Text(
                                "${e.sets} × ${plan.setDurationSec / 60} min · " + when {
                                    plan.deload -> "no ramp"
                                    top > e.startBpm -> "+2 per set, up to $top"
                                    else -> "at the cap"
                                },
                                color = C.muted, fontSize = 12.sp,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Hud("${e.startBpm}", 22.sp, color)
                            Label("Start", color = C.muted)
                        }
                    }
                }
            }
            ToggleRow("Prioritize weak spots", "More sets for your worst exercise, taken from your best", d.profile.prioritizeWeak) { on ->
                vm.updateSettings { it.copy(prioritizeWeak = on) }
            }
        }
        if (plan != null && isToday) {
            PrimaryButton(if (plan.isBenchmark) "Start benchmark" else "Start session", { vm.startDay(sel) }, color = color)
            GhostButton("I can't train today", { vm.skipToday() }, Modifier.fillMaxWidth())
        }
        info.session?.let { SessionRows(it) }
    }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(C.surfaceDeep).padding(10.dp)) {
        Hud(value, 24.sp)
        Label(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionRows(s: SavedSession) {
    val r = s.result
    if (r.dayType == DayType.BENCHMARK) {
        Body("Benchmark attempt")
        return
    }
    Column {
        r.exercises.forEach { x ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            Column(Modifier.padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(x.exercise.title, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val rt = x.rating
                    Text((rt?.title ?: "Not rated").uppercase(), style = hud(13.sp, FontWeight.Bold, rt?.color() ?: C.muted))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    x.sets.forEach { set -> Chip("${set.bpm}", set.rating?.color() ?: C.faint) }
                }
            }
        }
    }
}
