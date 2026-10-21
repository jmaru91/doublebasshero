package com.doublebasshero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doublebasshero.engine.Levels

private val WD_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val WD_LETTER = listOf("M", "T", "W", "T", "F", "S", "S")
val WD_FULL = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

fun hm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
fun OnboardingScreen(vm: AppViewModel, requestNotifications: () -> Unit) {
    val o by vm.onboarding.collectAsState()
    val steps = if (o.retest) 1 else 4
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!o.retest) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(steps) { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= o.step) C.speed else C.line))
                }
            }
        }
        when (o.step) {
            0 -> Pillars(vm)
            1 -> TrueBpm(vm, o)
            2 -> DaysOff(vm, o)
            else -> ReminderStep(vm, o, requestNotifications)
        }
    }
}

@Composable
private fun Pillars(vm: AppViewModel) {
    Hud("Double Bass Hero", 44.sp, C.speed, italic = true)
    Body("Get faster on double bass, one level at a time. Metronome, session guide and calendar in one place.", size = 16.sp)
    Card {
        Label("The three pillars")
        Pillar("03", C.speed, "Firing rate", "How fast your nervous system fires your muscles from a cold start")
        Pillar("02", C.endurance, "Endurance", "The aerobic base that refills your freshness faster")
        Pillar("01", C.text, "Form", "The roots. Without form nothing else grows.")
    }
    Hud("Form is king and decides always.", 26.sp, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    PrimaryButton("Next", { vm.updateOnboarding { it.copy(step = 1) } })
}

@Composable
private fun Pillar(n: String, color: Color, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Hud(n, 40.sp, color, Modifier.width(56.dp))
        Column {
            Hud(title, 22.sp)
            Body(text, color = C.muted, size = 13.sp)
        }
    }
}

@Composable
private fun TrueBpm(vm: AppViewModel, o: OnboardingState) {
    val level = Levels.forTrueBpm(o.trueBpm)
    Hud(if (o.retest) "Redo the True BPM test" else "Your True BPM", 34.sp)
    Body("The BPM at which you can hold 4 minutes of non-stop 16th notes with clean form. It is your starting point and sets your level.", size = 15.sp)
    Card(padding = 20.dp) {
        Hud("${o.trueBpm}", 96.sp, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        Label("BPM · 16th notes", Modifier.fillMaxWidth().padding(start = 0.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(-5, -1, 1, 5).forEach { d ->
                GhostButton(if (d > 0) "+$d" else "−${-d}", {
                    vm.updateOnboarding { it.copy(trueBpm = (it.trueBpm + d).coerceIn(40, 230), message = "") }
                }, Modifier.weight(1f))
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().border(1.dp, C.speed, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tile("LV ${level.number}", C.speed, C.ink, 72.dp, 36.dp, size = 22.sp)
        HGap(14.dp)
        Column {
            Hud("${level.minBpm}–${level.maxBpm} BPM", 18.sp)
            Body("Quest ${level.quest.number} — ${level.quest.title}", color = C.muted, size = 13.sp)
        }
    }
    if (o.message.isNotEmpty()) Body(o.message, color = C.hybrid, weight = FontWeight.SemiBold)
    GhostButton("Take the test now (4:00 at ${o.trueBpm})", vm::placementTest, Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (o.retest) {
            GhostButton("Cancel", { vm.updateOnboarding { OnboardingState() }; vm.go(Screen.Levels) }, Modifier.width(120.dp))
            PrimaryButton("Save", { vm.finishOnboarding() }, Modifier.weight(1f))
        } else {
            GhostButton("Back", { vm.updateOnboarding { it.copy(step = 0) } }, Modifier.width(110.dp))
            PrimaryButton("Next", { vm.updateOnboarding { it.copy(step = 2, message = "") } }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DaysOff(vm: AppViewModel, o: OnboardingState) {
    val plan = vm.recommendedWeek(o.restDays.toSet())
    Hud("Your week", 34.sp)
    Body("Tap your two usual days off. We'll plan the rest. You can change any single week later.", size = 15.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).forEach { i ->
            val off = i in o.restDays
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(WD_SHORT[i], color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (off) C.rest else Color.Transparent)
                        .border(1.5.dp, if (off) C.rest else C.lineStrong, CircleShape)
                        .clickable(role = Role.Checkbox) {
                            // Replaces the older of the two picks.
                            if (!off) vm.updateOnboarding { it.copy(restDays = listOf(it.restDays[1], i)) }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(if (off) "OFF" else "TRAIN", style = hud(12.sp, color = if (off) C.ink else C.muted)) }
            }
        }
    }
    Card {
        Label("Recommended plan")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            plan.forEachIndexed { i, t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Tile("${t.letter}", t.color(), C.ink, 38.dp, 46.dp)
                    Text(WD_LETTER[i], color = C.muted, fontSize = 11.sp)
                }
            }
        }
        Body("1 Speed · 1 Hybrid · 3 Endurance · 2 Rest\nSpeed comes right after a rest day, so you start fresh.\nSpeed → Hybrid → Endurance on consecutive days.\nEvery 4th week is a deload week.", size = 13.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton("Back", { vm.updateOnboarding { it.copy(step = 1) } }, Modifier.width(110.dp))
        PrimaryButton("Next", { vm.updateOnboarding { it.copy(step = 3) } }, Modifier.weight(1f))
    }
}

@Composable
private fun ReminderStep(vm: AppViewModel, o: OnboardingState, requestNotifications: () -> Unit) {
    val plan = vm.recommendedWeek(o.restDays.toSet())
    Hud("Reminders", 34.sp)
    Body("When should we remind you to train? Same time every day, or change single days (e.g. mornings at the weekend).", size = 15.sp)
    ReminderEditor(o.reminderMinutes, plan.map { if (it.isTraining) it.dayName() else "Usual day off · no reminder" }) { mins ->
        vm.updateOnboarding { it.copy(reminderMinutes = mins) }
    }
    Body("We'll also tell you when your week review is ready, when the benchmark unlocks, and when a deload week starts. Nothing on rest days.", color = C.muted, size = 13.sp)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton("Back", { vm.updateOnboarding { it.copy(step = 2) } }, Modifier.width(110.dp))
        PrimaryButton("Allow and start", { requestNotifications(); vm.finishOnboarding() }, Modifier.weight(1f))
    }
}

/** Reminder time per weekday with an "every day" row that shifts them all. */
@Composable
fun ReminderEditor(minutes: List<Int>, kinds: List<String>, onChange: (List<Int>) -> Unit) {
    fun clamp(m: Int) = m.coerceIn(300, 1380)
    val same = minutes.all { it == minutes[0] }
    Card(padding = 12.dp) {
        TimeRow("Every day", if (same) "Same time for all days" else "Shifts every day", if (same) hm(minutes[0]) else "Mixed", bold = true,
            onMinus = { onChange(minutes.map { clamp(it - 30) }) }, onPlus = { onChange(minutes.map { clamp(it + 30) }) })
        (0..6).forEach { i ->
            TimeRow(WD_FULL[i], kinds[i], hm(minutes[i]),
                onMinus = { onChange(minutes.toMutableList().also { it[i] = clamp(it[i] - 30) }) },
                onPlus = { onChange(minutes.toMutableList().also { it[i] = clamp(it[i] + 30) }) })
        }
    }
}

@Composable
fun TimeRow(title: String, subtitle: String, value: String, bold: Boolean = false, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 15.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
            Text(subtitle, color = C.muted, fontSize = 12.sp)
        }
        SquareButton("−", "$title 30 minutes earlier", onMinus)
        Hud(value, 21.sp, modifier = Modifier.width(66.dp), align = TextAlign.Center)
        SquareButton("+", "$title 30 minutes later", onPlus)
    }
}

@Composable
fun SquareButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, C.lineStrong, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = C.textSoft, fontSize = 20.sp) }
}
