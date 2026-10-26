package com.doublebasshero.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doublebasshero.data.notificationPrefs
import com.doublebasshero.engine.CapRules
import com.doublebasshero.engine.DayState
import com.doublebasshero.engine.DayType
import com.doublebasshero.engine.Exercise
import com.doublebasshero.engine.FormRating
import com.doublebasshero.engine.Levels
import com.doublebasshero.engine.NotificationType
import com.doublebasshero.engine.Quest
import com.doublebasshero.engine.SessionBuilder
import com.doublebasshero.engine.WeekPlanner
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// ===================== Calendar =====================

@Composable
fun CalendarScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val d = data ?: return
    val month by vm.calendarMonth.collectAsState()
    val sel by vm.calendarDay.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(Icons.back, "Previous month", 44.dp) { vm.calendarMonth.value = month.minusMonths(1) }
            Hud("${month.month.getDisplayName(TextStyle.FULL, Locale.US)} ${month.year}", 26.sp, modifier = Modifier.weight(1f), align = TextAlign.Center)
            IconBox(Icons.forward, "Next month", 44.dp) { vm.calendarMonth.value = month.plusMonths(1) }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        val pending = d.calendar.currentWeek.makeupPending
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                "MTWTFSS".forEach { Text("$it", color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
            }
            cells.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..6).forEach { i ->
                        val date = row.getOrNull(i)
                        if (date == null) {
                            Box(Modifier.weight(1f).height(52.dp))
                        } else {
                            val info = d.calendar.day(date)
                            val look = tileLook(info, pending)
                            val ring = when { date == sel -> 3.dp; date == d.today -> 1.5.dp; else -> 0.dp }
                            Column(
                                Modifier.weight(1f).height(52.dp).alpha(look.alpha)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(look.bg)
                                    .let { if (look.dashed) it.dashedBorder(look.border, RoundedCornerShape(8.dp), 1.5.dp) else it.border(1.5.dp, look.border, RoundedCornerShape(8.dp)) }
                                    .let { if (ring > 0.dp) it.border(ring, C.text, RoundedCornerShape(8.dp)) else it }
                                    .clickable(role = Role.Button) { vm.calendarDay.value = date }
                                    .semantics { contentDescription = "${date.label()}, ${info.type.dayName()}" },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("${date.dayOfMonth}", color = look.fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(look.text, style = hud(18.sp, color = look.fg))
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(DayType.SPEED, DayType.HYBRID, DayType.ENDURANCE).forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(t.color()))
                    HGap(5.dp)
                    Text(t.title, color = C.textSoft, fontSize = 12.sp)
                }
            }
            Text("Dashed = deload", color = C.textSoft, fontSize = 12.sp)
        }
        val info = d.calendar.day(sel)
        Card(border = if (info.inProgram) info.type.color() else C.line) {
            Row {
                Label(sel.label(), Modifier.weight(1f))
                val (status, sc) = when {
                    !info.inProgram -> "Before the program" to C.muted
                    info.session?.result?.partial == true -> "Partial" to C.endurance
                    info.session != null -> "Done" to C.hybrid
                    info.state == DayState.SKIPPED -> "Skipped" to C.bad
                    info.state == DayState.UNAVAILABLE -> "Can't train" to C.muted
                    info.type == DayType.REST -> "Rest" to Color(0xFFC9C3B3)
                    sel == d.today -> "Today · to do" to C.text
                    else -> "Planned" to C.muted
                }
                Text(status.uppercase(), style = hud(14.sp, FontWeight.Bold, sc))
            }
            Hud(if (info.inProgram) info.type.dayName() else "—", 30.sp, if (info.inProgram) info.type.color() else C.muted, italic = true)
            when {
                info.type == DayType.BENCHMARK -> Body("Benchmark at ${d.level.benchmarkBpm}: the next fresh day after it unlocked.", color = C.hybrid, size = 13.sp)
                info.converted -> Body("Every exercise hit the cap on ${info.slot.title} days, so this is an Endurance day.", color = C.endurance, size = 13.sp)
                info.deload && info.inProgram -> Body("Deload week (week 4 of the cycle): half the sets, no ramp.", color = C.warm, size = 13.sp)
            }
            if (info.session == null && info.canStart && info.type in DayType.PROGRESSING) {
                d.calendar.buildSession(sel, d.startBpms)?.let { p ->
                    Body("${p.exercises.size} exercises · ${p.exercises.sumOf { it.sets }} sets · ~${(SessionBuilder.estimatedDurationSec(p) + 30) / 60} min")
                }
            }
            info.session?.let { SessionRows(it) }
        }
    }
}

// ===================== Progress =====================

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val d = data ?: return
    val level = d.level
    val history = d.calendar.history.filter { it.dayType != DayType.BENCHMARK }
    val series = history.filter { it.dayType == DayType.ENDURANCE }.mapNotNull { s -> s.of(Exercise.PEDAL_NORMAL)?.topBpm?.let { s.date to it } }
    val minutes = d.sessions.sumOf { (it.updatedAt - it.startedAt).coerceIn(0, 3 * 3600_000L) } / 60000
    val programStart = LocalDate.ofEpochDay(d.profile.programStart)
    val planned = generateSequence(programStart) { it.plusDays(1) }.takeWhile { it <= d.today }
        .count { val i = d.calendar.day(it); i.type.isTraining && (it < d.today || i.session != null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Hud("Progress", 30.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${history.size}", "Sessions", Modifier.weight(1f))
            Stat("${minutes / 60}h ${minutes % 60}m", "Trained", Modifier.weight(1f))
            Stat(if (planned > 0) "${(history.size * 100 / planned).coerceAtMost(100)}%" else "—", "Adherence", Modifier.weight(1f))
        }
        Card {
            Row(verticalAlignment = Alignment.Bottom) {
                Label("Pedal Normal · Endurance top set", Modifier.weight(1f))
                Hud(series.lastOrNull()?.second?.let { "$it BPM" } ?: "—", 22.sp, C.endurance)
            }
            if (series.size < 2) {
                Body("The chart fills in after a couple of Endurance sessions.", color = C.muted, size = 13.sp)
            } else {
                Chart(series.map { it.second }, level.benchmarkBpm)
                Row {
                    Text(series.first().first.short(), color = C.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(series.last().first.short(), color = C.muted, fontSize = 11.sp)
                }
            }
        }
        Card {
            Label("Exercises at the cap (${level.cap}) per day type")
            DayType.PROGRESSING.forEach { t ->
                val n = CapRules.atCap(level, t, history).size
                val full = n == level.exercises.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Hud(t.title, 17.sp, t.color(), Modifier.weight(1f))
                    if (full) { Chip(if (t == DayType.ENDURANCE) "Benchmark" else "→ Endurance", t.color(), size = 12.sp); HGap(8.dp) }
                    Hud("$n/${level.exercises.size}", 18.sp)
                }
                ProgressBar(maxOf(0.04f, n.toFloat() / level.exercises.size), t.color())
            }
            Body("Speed or Hybrid days turn into Endurance days when every exercise is at the cap. The benchmark unlocks when every exercise is at the cap on Endurance days.", size = 13.sp)
        }
        Card {
            Label("Form per exercise · last 2 weeks")
            val recent = d.calendar.recentRatings()
            val (plan, weak) = com.doublebasshero.engine.WeaknessBalancer.balance(level, recent)
            level.exercises.forEach { ex ->
                val r = recent[ex].orEmpty()
                val clean = if (r.isEmpty()) null else r.count { it == FormRating.CLEAN }.toFloat() / r.size
                Row {
                    Text(ex.title, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        (clean?.let { "${(it * 100).toInt()}% clean sets" } ?: "no data yet") +
                            (if (d.profile.prioritizeWeak && weak != null && plan[ex] != level.setsPerExercise) " · ${level.setsPerExercise}→${plan[ex]} sets" else ""),
                        color = C.muted, fontSize = 12.sp,
                    )
                }
                ProgressBar(clean ?: 0f, when { clean == null -> C.line; clean >= 0.75f -> C.hybrid; clean >= 0.6f -> C.endurance; else -> C.bad })
            }
        }
    }
}

@Composable
private fun Chart(values: List<Int>, bench: Int) {
    val lo = (minOf(values.min(), bench) - 4).toFloat()
    val hi = (maxOf(values.max(), bench) + 4).toFloat()
    Box(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Endurance BPM over time, from ${values.first()} to ${values.last()}" }) {
        Canvas(Modifier.fillMaxSize()) {
            fun y(v: Float) = size.height * (1 - (v - lo) / (hi - lo))
            val by = y(bench.toFloat())
            drawLine(C.speed, Offset(0f, by), Offset(size.width, by), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f)))
            val step = size.width / (values.size - 1)
            val path = Path()
            values.forEachIndexed { i, v ->
                val p = Offset(i * step, y(v.toFloat()))
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, C.endurance, style = Stroke(width = 6f))
            values.forEachIndexed { i, v -> drawCircle(C.endurance, 9f, Offset(i * step, y(v.toFloat()))) }
        }
        Text("Benchmark $bench", color = C.speedSoft, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd))
    }
}

// ===================== Levels =====================

@Composable
fun LevelsScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val d = data ?: return
    val flash by vm.flash.collectAsState()
    val confirm by vm.confirmRetest.collectAsState()
    var open by remember { mutableStateOf(d.level.number) }
    val level = d.level
    val history = d.calendar.history
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Hud("Main quest", 34.sp, italic = true)
                Body("Benchmark: 4 minutes of non-stop 16ths with clean form.", color = C.muted)
            }
            if (flash.isNotEmpty()) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.warnBg).padding(12.dp)) { Body(flash, color = C.endurance) }
            if (!level.isTop) {
                val unlocked = d.profile.benchmarkUnlocked
                val atCap = CapRules.atCap(level, DayType.ENDURANCE, history)
                val day = d.calendar.benchmarkDay
                Card(border = if (unlocked) C.hybrid else C.line) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Hud("Benchmark · ${level.benchmarkBpm} BPM", 22.sp, modifier = Modifier.weight(1f))
                        Chip(if (unlocked) "Unlocked" else "Locked", if (unlocked) C.hybrid else C.dim, size = 13.sp)
                    }
                    val noPedalsNow = d.calendar.day(d.today).noPedals
                    Body(
                        if (unlocked && noPedalsNow && day == null) "Unlocked. It's played on pedals, so it waits until no-pedals mode is off."
                        else if (unlocked) (day?.let { "Every exercise is at the cap on Endurance days. Suggested for ${it.label()}, the next fresh day." } ?: "Unlocked. It will be scheduled on the next fresh day.")
                        else "${atCap.size} of ${level.exercises.size} exercises at the cap (${level.cap}) on Endurance days, rated clean. It unlocks when all of them get there.",
                        size = 13.sp,
                    )
                    level.exercises.forEach { ex ->
                        val ok = ex in atCap
                        val last = history.filter { it.dayType == DayType.ENDURANCE && it.level == level.number }.maxByOrNull { it.date }?.of(ex)?.topBpm
                        Row {
                            Text(ex.title, color = C.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(if (ok) "AT CAP" else "${last ?: "—"} / ${level.cap}", style = hud(16.sp, FontWeight.Bold, if (ok) C.hybrid else C.muted))
                        }
                    }
                    if (unlocked && day != null) PrimaryButton("Go to benchmark day", { vm.selectedDay.value = day; vm.go(Screen.Today) })
                }
            }
            Quest.entries.forEach { q ->
                val ls = Levels.all.filter { it.quest == q }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Hud(if (q == Quest.ENDGAME) q.title else "Quest ${q.number} — ${q.title}", 20.sp, modifier = Modifier.weight(1f))
                        Text("LVL ${ls.first().number}" + if (ls.size > 1) "–${ls.last().number}" else "", style = hud(14.sp, FontWeight.Bold, C.muted))
                    }
                    ls.forEach { l ->
                        val cur = l.number == level.number
                        val done = l.number < level.number
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.surface)
                                .border(1.dp, if (cur) C.speed else C.line, RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { open = if (open == l.number) -1 else l.number },
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Tile("${l.number}", if (cur) C.speed else if (done) Color(0xFF173323) else C.restTile, if (cur) C.ink else if (done) C.hybrid else C.dim, 48.dp, 38.dp)
                                HGap(12.dp)
                                Hud("${l.minBpm}–${l.maxBpm} BPM", 20.sp, modifier = Modifier.weight(1f))
                                Text((if (cur) "Current" else if (done) "Cleared" else "Locked").uppercase(), style = hud(13.sp, FontWeight.Bold, if (cur) C.speed else if (done) C.hybrid else C.dim))
                            }
                            if (open == l.number) {
                                Column(Modifier.padding(start = 72.dp, end = 12.dp, bottom = 12.dp)) {
                                    Text(l.exercises.joinToString(" · ") { it.title }, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    val sample = SessionBuilder.build(l, DayType.ENDURANCE, DayType.ENDURANCE, l.minBpm, emptyMap(), emptyMap(), false, false)
                                    Text("${l.setsPerExercise} × ${l.setDurationSec / 60} min ramp · session ~${(SessionBuilder.estimatedDurationSec(sample) + 30) / 60} min", color = C.muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            GhostButton("Redo the True BPM test", { vm.confirmRetest.value = true }, Modifier.fillMaxWidth())
        }
        if (confirm) {
            Modal({ vm.confirmRetest.value = false }) {
                Hud("Redo the True BPM test?", 26.sp)
                Body("Your new True BPM places you on a level again. It's meant for coming back after a long break or an injury, not for skipping the benchmark.")
                Body("Resets: your level, every start BPM, and the current cycle (back to week 1).", color = C.bad)
                Body("Kept: calendar, session history and progress charts.", color = C.hybrid)
                Body("To move up a level you still have to take the test in the app; you can't type a higher number.", color = C.endurance)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Cancel", { vm.confirmRetest.value = false }, Modifier.weight(1f))
                    PrimaryButton("Reset and test", vm::startRetest, Modifier.weight(1f), C.bad)
                }
            }
        }
    }
}

// ===================== Settings =====================

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val profile by vm.profile.collectAsState()
    val p = profile ?: return
    val prefs = p.notificationPrefs()
    val minutes = p.reminderMinutes.split(',').map { it.trim().toInt() }
    val plan = WeekPlanner.recommend(setOf(p.restDay1, p.restDay2))
    fun toggle(t: NotificationType, on: Boolean) = vm.updateSettings {
        val disabled = it.disabledNotifications.split(',').filter { s -> s.isNotBlank() }.toMutableSet()
        if (on) disabled -= t.name else disabled += t.name
        it.copy(disabledNotifications = disabled.joinToString(","))
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Settings", "Reminders and session cues") { vm.go(Screen.Today) }
        Label("Training reminders")
        ReminderEditor(minutes, plan.map { if (it.isTraining) it.dayName() else "Usual day off · no reminder" }) { m ->
            vm.updateSettings { it.copy(reminderMinutes = m.joinToString(",")) }
        }
        Card(padding = 12.dp) {
            TimeRow("Missed-session check", "Still counts if you train later that night", hm(p.missedMinutes),
                onMinus = { vm.updateSettings { it.copy(missedMinutes = (it.missedMinutes - 30).coerceIn(300, 1380)) } },
                onPlus = { vm.updateSettings { it.copy(missedMinutes = (it.missedMinutes + 30).coerceIn(300, 1380)) } })
        }
        Label("Notifications")
        Card(padding = 12.dp) {
            val rows = listOf(
                NotificationType.TRAINING_REMINDER to ("Training reminder" to "Training days, at the times above"),
                NotificationType.MISSED_SESSION to ("Missed session" to "If you haven't trained by ${hm(p.missedMinutes)}"),
                NotificationType.WEEK_REVIEW to ("Week review ready" to "First day of the week"),
                NotificationType.BENCHMARK_UNLOCKED to ("Benchmark unlocked" to "When every exercise hits the cap"),
                NotificationType.BENCHMARK_DAY to ("Benchmark day" to "Morning of the benchmark"),
                NotificationType.CYCLE to ("Deload / new cycle" to "Start of week 4 and of week 1"),
                NotificationType.REST_DAY to ("Rest day" to "Rest days"),
            )
            rows.forEach { (t, labels) -> ToggleRow(labels.first, labels.second, t in prefs.enabled) { toggle(t, it) } }
        }
        Label("During a session")
        Card(padding = 12.dp) {
            ToggleRow("End-of-rest cue", "Sound and vibration when the suggested rest is up", p.restCue) { on -> vm.updateSettings { it.copy(restCue = on) } }
            ToggleRow("Voice prompts", "Announces exercise, set and BPM", p.voicePrompts) { on -> vm.updateSettings { it.copy(voicePrompts = on) } }
        }
        Label("Training")
        Card(padding = 12.dp) {
            ToggleRow("Prioritize weak spots", "More sets for your worst exercise, taken from your best", p.prioritizeWeak) { on -> vm.updateSettings { it.copy(prioritizeWeak = on) } }
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weaker foot", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Gets one extra Solo Leg pass in the warm-up", color = C.muted, fontSize = 12.sp)
                val options = listOf("NONE" to "None", "LEFT" to "Left", "RIGHT" to "Right")
                ChoiceRow(options.map { (k, label) -> Triple(label, C.speed, p.weakFoot == k) }, { i ->
                    vm.updateSettings { it.copy(weakFoot = options[i].first) }
                }, height = 42.dp)
            }
            ToggleRow(
                "No-pedals mode",
                "Floor exercises only, for when you're away from your pedals (e.g. on holiday). Same session length.",
                p.noPedals,
            ) { on -> vm.updateSettings { it.copy(noPedals = on, noPedalsUntil = -1) } }
            if (p.noPedals) {
                val today = LocalDate.now()
                val until = p.noPedalsUntil.takeIf { it >= 0 }?.let { LocalDate.ofEpochDay(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Back with pedals", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (until == null) "No end date: turn it off by hand" else "The mode switches off by itself that day", color = C.muted, fontSize = 12.sp)
                    }
                    SquareButton("−", "One day earlier") {
                        val next = until?.minusDays(1)
                        vm.updateSettings { it.copy(noPedalsUntil = if (next == null || next <= today) -1 else next.toEpochDay()) }
                    }
                    Text(until?.short()?.uppercase() ?: "NONE", style = hud(19.sp), modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
                    SquareButton("+", "One day later") {
                        val next = until?.plusDays(1) ?: today.plusDays(7)
                        vm.updateSettings { it.copy(noPedalsUntil = next.toEpochDay()) }
                    }
                }
                if (until != null) {
                    Text("CLEAR DATE", style = hud(14.sp, FontWeight.Bold, C.speed), modifier = Modifier.clickable(role = Role.Button) { vm.updateSettings { it.copy(noPedalsUntil = -1) } }.padding(vertical = 6.dp))
                }
            }
        }
        Body("Your usual days off: ${WD_FULL[p.restDay1]} and ${WD_FULL[p.restDay2]}. To change a single week, use \"Edit this week\" on Today.", color = C.muted, size = 13.sp)
    }
}
