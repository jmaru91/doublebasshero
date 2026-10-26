package com.doublebasshero.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doublebasshero.engine.BarKind
import com.doublebasshero.engine.DayType
import com.doublebasshero.engine.FormRating
import com.doublebasshero.engine.RestKind
import com.doublebasshero.engine.SessionRunner
import com.doublebasshero.engine.Step
import com.doublebasshero.engine.Warmup
import com.doublebasshero.session.LiveSession
import com.doublebasshero.session.Outcome

fun mmss(sec: Long) = "${sec / 60}:${"%02d".format(sec % 60)}"

@Composable
fun PlayerScreen(vm: AppViewModel) {
    val live by vm.live.collectAsState()
    val s = live ?: return
    when (s.outcome) {
        Outcome.BENCHMARK -> BenchmarkResult(vm, s)
        Outcome.COMPLETE, Outcome.PARTIAL -> Summary(vm, s)
        null -> Running(vm, s)
    }
}

@Composable
private fun Running(vm: AppViewModel, s: LiveSession) {
    var confirmClose by remember { mutableStateOf(false) }
    val c = vm.controller
    // Beat position straight from the audio clock, refreshed every frame.
    var pos by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var eighth by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            pos = c.metronome.position()
            eighth = c.metronome.eighthPosition()
        }
    }
    BackHandler { c.pauseForDialog(); confirmClose = true }

    val r = s.runner
    val step = s.step ?: return
    val dayColor = r.plan.dayType.color()
    val color = when (step) {
        is Step.Rest -> C.muted
        is Step.Warmup -> C.warm
        else -> dayColor
    }
    val muted = c.metronome.muted

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(color))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBox(Icons.close, "Close session", 44.dp) { c.pauseForDialog(); confirmClose = true }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Hud(
                        when (step) {
                            is Step.Warmup -> "Warm-up"
                            is Step.Rest -> "Rest"
                            is Step.BenchmarkTest -> "Benchmark"
                            else -> r.plan.dayType.dayName() + if (r.plan.deload) " · deload" else ""
                        },
                        15.sp, color,
                    )
                    val left = r.steps.drop(r.index).sumOf { SessionRunner.hintSeconds(r.plan, it) } - s.elapsedMs / 1000
                    Text("Step ${r.index + 1}/${r.steps.size} · ~${(left.coerceAtLeast(0) + 30) / 60} min left", color = C.muted, fontSize = 12.sp)
                }
                IconBox(if (muted) Icons.mute else Icons.sound, if (muted) "Sound off" else "Sound on", 44.dp) { c.setMuted(!muted) }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StepHeader(s, step)
                if (step is Step.Rest) RestView(vm, s, step) else PlayView(s, step, color, pos, eighth)
            }

            Column(Modifier.fillMaxWidth().background(C.night).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (SessionRunner.isTimed(step)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Hud(
                            when {
                                s.paused -> "Paused"
                                s.countingIn -> "Count-in"
                                else -> mmss(s.stepSeconds - s.elapsedMs / 1000)
                            },
                            30.sp, modifier = Modifier.weight(1f),
                        )
                        Text("of ${mmss(s.stepSeconds.toLong())}", color = C.muted, fontSize = 13.sp)
                    }
                    ProgressBar(s.elapsedMs / 1000f / s.stepSeconds, color, height = 6.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (SessionRunner.isTimed(step)) {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(color).clickable(role = Role.Button) { c.togglePause() }
                                .semantics { contentDescription = if (s.paused) "Resume" else "Pause" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (s.paused) Icons.play else Icons.pause, null, tint = C.ink, modifier = Modifier.size(28.dp)) }
                        HGap(12.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Label("Up next")
                        Text(nextLabel(s), color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconBox(Icons.skip, "Skip step", 52.dp) { c.advance() }
                }
            }
        }

        if (confirmClose) CloseDialog(vm, s) { confirmClose = false }
    }
}

private fun nextLabel(s: LiveSession): String {
    val r = s.runner
    val next = r.steps.drop(r.index + 1).firstOrNull { it !is Step.Rest } ?: return "Summary"
    return when (next) {
        is Step.Work -> "${next.exercise.title} · Set ${next.set}/${next.of}"
        is Step.Warmup -> Warmup.exercises[next.index].title
        is Step.BenchmarkTest -> "Benchmark"
        else -> ""
    }
}

@Composable
private fun StepHeader(s: LiveSession, step: Step) {
    val (label, title, sub) = when (step) {
        is Step.Warmup -> Triple((if (step.rewarm) "Re-warm-up " else "") + "${step.index + 1}/${Warmup.exercises.size}", Warmup.exercises[step.index].title, Warmup.exercises[step.index].subtitle)
        is Step.Work -> Triple("Exercise ${step.exerciseIndex}/${step.exerciseCount} · Set ${step.set}/${step.of}", step.exercise.title, step.exercise.description)
        is Step.BenchmarkTest -> Triple("4:00 · non-stop 16ths", "Benchmark", "Clean form for all four minutes")
        is Step.Rest -> Triple(
            "", when (step.kind) { RestKind.FINAL -> "Last set done"; RestKind.SWITCH_EXERCISE -> "Switch exercise"; else -> "Rest" },
            nextLabel(s).let { if (step.kind == RestKind.FINAL) "Rate it, then see your summary." else "Next: $it" },
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotEmpty()) Label(label)
        if (step !is Step.Rest) Hud(title, 34.sp, align = TextAlign.Center)
        Body(sub, color = C.muted, align = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayView(s: LiveSession, step: Step, color: Color, pos: Pair<Int, Int>?, eighth: Long?) {
    val r = s.runner
    val bpm = SessionRunner.bpm(r)
    Row(verticalAlignment = Alignment.Bottom) {
        Hud("$bpm", 120.sp, color)
        HGap(8.dp)
        Hud("BPM", 20.sp, C.muted, Modifier.padding(bottom = 18.dp))
    }
    if (step is Step.Work) {
        val prev = r.sets[step.exercise].orEmpty()
        if (prev.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("Sets", Modifier.align(Alignment.CenterVertically))
                prev.forEach { set ->
                    val c = set.rating?.color() ?: C.dim
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                        Text("${set.bpm}", style = hud(16.sp, FontWeight.Bold, c))
                    }
                }
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text("$bpm", style = hud(16.sp, FontWeight.Bold, C.ink))
                }
            }
        }
    }
    val active = !s.paused && pos != null
    Row(Modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        (0..3).forEach { i ->
            val on = active && pos!!.first == i
            Dot(if (on) (if (i == 0) Color.White else color) else C.line, if (i == 0) 20.dp else 14.dp)
        }
    }
    if (step is Step.Warmup) {
        StepGrid(step.index, if (active) eighth else null)
    } else {
        val pattern = SessionRunner.pattern(r)
        val bar = if (active) pos!!.second else -1
        val dayType = if (step is Step.BenchmarkTest) DayType.BENCHMARK else r.plan.dayType
        PatternCells(dayType, if (bar >= 0) bar % 4 else -1, 40.dp)
        val kind = if (bar >= 0) pattern[bar % pattern.size] else pattern.first()
        Hud(
            when {
                s.countingIn -> "Get ready"
                dayType == DayType.SPEED -> if (kind == BarKind.REST) "Rest" else "Play!"
                dayType == DayType.HYBRID -> if (kind == BarKind.EIGHTHS) "8ths" else "16ths"
                else -> "Non-stop 16ths"
            },
            40.sp, if (kind == BarKind.REST && !s.countingIn) C.dim else color, italic = true,
        )
    }
}

/** Warm-up step grid: one cell per 8th note, R/L per foot, empty = rest; cursor follows the metronome. */
@Composable
private fun StepGrid(index: Int, eighth: Long?) {
    val bars = Warmup.exercises[index].bars
    val activeBar = eighth?.let { ((it / 8) % bars.size).toInt() }
    val activeCell = eighth?.let { (it % 8).toInt() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        bars.forEachIndexed { bi, bar ->
            Row(
                Modifier.fillMaxWidth().alpha(if (bars.size == 1 || activeBar == null || activeBar == bi) 1f else 0.55f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                (0..3).forEach { beat ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        (0..1).forEach { c ->
                            val i = beat * 2 + c
                            val ch = bar[i]
                            val bg = when (ch) { 'R' -> C.speed; 'L' -> C.left; else -> Color.Transparent }
                            val on = activeBar == bi && activeCell == i
                            Box(
                                Modifier.size(38.dp, 28.dp).clip(RoundedCornerShape(4.dp)).background(bg)
                                    .border(if (on) 2.dp else 1.dp, if (on) C.text else if (ch == '-') Color(0xFF3A475E) else bg, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center,
                            ) { if (ch != '-') Text("$ch", style = hud(15.sp, color = C.ink)) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
            Legend(C.speed, "R right foot")
            Legend(C.left, "L left foot")
            Legend(Color.Transparent, "rest", Color(0xFF3A475E))
        }
    }
}

@Composable
private fun Legend(color: Color, text: String, border: Color = color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color).border(1.dp, border, RoundedCornerShape(2.dp)))
        HGap(5.dp)
        Text(text, color = C.muted, fontSize = 12.sp)
    }
}

@Composable
private fun RestView(vm: AppViewModel, s: LiveSession, step: Step.Rest) {
    val c = vm.controller
    val r = s.runner
    val long = step.kind != RestKind.SHORT
    val (name, hint, col) = when (step.kind) {
        RestKind.FINAL -> Triple("Done!", "Last set of the session", C.hybrid)
        RestKind.SHORT -> Triple("Short rest", "A few seconds", C.textSoft)
        else -> Triple("Long rest", "A few minutes", C.warm)
    }
    Hud(name, 60.sp, col, italic = true, align = TextAlign.Center)
    Hud(hint + if (s.restCued) " · rest is up" else "", 20.sp, C.muted, align = TextAlign.Center)

    val set = r.sets[step.ratedExercise]?.lastOrNull()
    if (set != null) {
        val rating = set.rating
        Card {
            Text("How was that set?  ", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("${step.ratedExercise.title} · ${set.bpm} BPM", color = C.muted, fontSize = 13.sp)
            ChoiceRow(FormRating.entries.map { Triple(it.title, it.color(), rating == it) }, { c.rate(FormRating.entries[it]) })
        }
    }
    if (step.nextSetOf != null) {
        val cur = r.bpm.getValue(step.nextSetOf!!)
        val next = SessionRunner.nextBpm(r) ?: cur
        val rating = set?.rating
        Card {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Next set", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Hud("$next BPM", 26.sp, r.plan.dayType.color())
            }
            Body(
                when {
                    next >= r.plan.cap && next > cur -> "+${next - cur} · level cap reached (${r.plan.cap})."
                    next >= r.plan.cap -> "Level cap reached (${r.plan.cap})."
                    rating == null -> "Clean → +2 (+4 after two clean sets in a row) · Shaky or Broken → same BPM."
                    rating == FormRating.CLEAN && next - cur == 4 -> "+4 · two clean sets in a row."
                    rating == FormRating.CLEAN -> "+2 · clean set."
                    else -> "Same BPM · ${rating.title.lowercase()} set."
                },
                color = C.muted, size = 13.sp,
            )
        }
    }
    Body("Go when you're ready · resting ${mmss(s.elapsedMs / 1000)}", color = C.muted, size = 13.sp, align = TextAlign.Center)
    PrimaryButton(if (step.kind == RestKind.FINAL) "See summary" else "Ready", { c.advance() }, color = C.text)
    if (long) Spacer(Modifier.height(4.dp))
}

@Composable
private fun CloseDialog(vm: AppViewModel, s: LiveSession, dismiss: () -> Unit) {
    val c = vm.controller
    val bench = s.runner.plan.isBenchmark
    val done = s.runner.setsDone
    val keepGoing = { dismiss(); if (s.paused) c.togglePause() }
    Modal(keepGoing) {
        Hud(if (bench) "Stop the test?" else "End this session?", 26.sp)
        Body(
            when {
                bench -> "The test only counts if you play the full 4 minutes."
                done > 0 -> "You played $done of ${s.runner.setsTotal} sets. Save them as a partial session (they count toward the week review), or discard them."
                else -> "You haven't finished a set yet. Nothing to save."
            },
        )
        PrimaryButton("Keep going", keepGoing)
        if (!bench && done > 0 && !s.placement) GhostButton("End and save $done set${if (done == 1) "" else "s"}", { dismiss(); c.endAndSave() }, Modifier.fillMaxWidth(), C.text, C.speed)
        GhostButton(
            if (bench) "Stop the test" else if (done > 0) "Discard session" else "Leave session",
            { dismiss(); c.discard(); vm.go(if (s.placement) Screen.Onboarding else Screen.Today) },
            Modifier.fillMaxWidth(), C.bad, Color(0xFF5A2E2E),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(vm: AppViewModel, s: LiveSession) {
    val r = s.runner
    val partial = s.outcome == Outcome.PARTIAL
    BackHandler { vm.finishSummary() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(if (partial) "Partial session · saved" else "Session complete · saved")
            Hud(r.plan.dayType.dayName() + if (r.plan.deload) " · deload" else "", 40.sp, r.plan.dayType.color(), italic = true)
            val minutes = (System.currentTimeMillis() - s.startedAt) / 60000
            Body("$minutes min · ${r.setsDone}${if (partial) " of ${r.setsTotal}" else ""} sets · LV ${r.plan.level}", color = C.muted)
        }
        Body("Each exercise's rating is the average of its sets. It feeds next week's review, where the app recommends your new start BPMs.")
        r.plan.exercises.forEach { pe ->
            val sets = r.sets[pe.exercise] ?: return@forEach
            val rating = com.doublebasshero.engine.Ratings.average(sets.mapNotNull { it.rating })
            Card {
                Row {
                    Text(pe.exercise.title, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text((rating?.title ?: "Not rated").uppercase(), style = hud(16.sp, color = rating?.color() ?: C.muted))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sets.forEach { Chip("${it.bpm}", it.rating?.color() ?: C.faint, size = 16.sp) }
                }
            }
        }
        PrimaryButton("Done", vm::finishSummary)
    }
}

@Composable
private fun BenchmarkResult(vm: AppViewModel, s: LiveSession) {
    val bpm = s.runner.plan.benchmarkBpm ?: 0
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Label("Benchmark · $bpm BPM")
        Hud("4:00 done", 44.sp)
        Body("Did your form stay clean for all 4 minutes of 16ths? No tension, no twitching, even legs. Be honest: form decides.", size = 17.sp)
        Spacer(Modifier.weight(1f))
        PrimaryButton("Yes, clean form", { vm.benchmarkResult(true) }, color = C.hybrid)
        GhostButton("No, it broke down", { vm.benchmarkResult(false) }, Modifier.fillMaxWidth())
    }
}

@Composable
fun LevelUpScreen(vm: AppViewModel, s: Screen.LevelUp) {
    val level = com.doublebasshero.engine.Levels.byNumber(s.to)
    val from = com.doublebasshero.engine.Levels.byNumber(s.from)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Hud("Level up!", 30.sp, C.endurance, italic = true)
        Tile("LV ${level.number}", C.speed, C.ink, 200.dp, 96.dp, size = 72.sp)
        Hud("${level.minBpm}–${level.maxBpm} BPM", 24.sp)
        if (from.quest != level.quest) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(C.goodBg).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("New quest unlocked: ${level.quest.title}", color = C.hybrid, fontWeight = FontWeight.Bold)
            }
        }
        Body("The cap went up: Speed and Hybrid days are back.", align = TextAlign.Center)
        Card {
            Label("New session")
            Text(level.exercises.joinToString(" · ") { it.title }, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("${level.setsPerExercise} × ${level.setDurationSec / 60} min ramp", color = C.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", { vm.go(Screen.Today) })
    }
}
