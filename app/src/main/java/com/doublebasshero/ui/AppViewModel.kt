package com.doublebasshero.ui

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doublebasshero.app
import com.doublebasshero.data.AppData
import com.doublebasshero.data.SessionStatus
import com.doublebasshero.data.decodeRunner
import com.doublebasshero.data.epoch
import com.doublebasshero.engine.BatteryRule
import com.doublebasshero.engine.DayType
import com.doublebasshero.engine.Levels
import com.doublebasshero.engine.ResumeAction
import com.doublebasshero.engine.ResumePolicy
import com.doublebasshero.engine.Retest
import com.doublebasshero.engine.SessionBuilder
import com.doublebasshero.engine.StartKey
import com.doublebasshero.engine.WeekPlanner
import com.doublebasshero.notify.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

sealed interface Screen {
    data object Onboarding : Screen
    data object Today : Screen
    data object Calendar : Screen
    data object Progress : Screen
    data object Levels : Screen
    data object Settings : Screen
    data object Review : Screen
    data object EditWeek : Screen
    data object Player : Screen
    data class LevelUp(val from: Int, val to: Int) : Screen
}

data class OnboardingState(
    val step: Int = 0,
    val trueBpm: Int = 100,
    /** Older pick first; a new pick replaces it. */
    val restDays: List<Int> = listOf(3, 6),
    val reminderMinutes: List<Int> = List(7) { 1080 },
    val message: String = "",
    /** Redoing the placement from the level map (only step 1 is shown). */
    val retest: Boolean = false,
    val retestCap: Int = 0,
    val verifiedBpm: Int? = null,
)

/** A start that waits for the low-battery warning. */
data class BatteryWarning(val percent: Int, val minutes: Int, val action: () -> Unit)

/** An in-progress session found after the app was killed or the phone died. */
data class Interrupted(val sessionId: Long, val action: ResumeAction, val minutesAgo: Long, val title: String, val setsDone: Int)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val ctx = application
    private val repo = ctx.app.repository
    val controller = ctx.app.controller

    val data: StateFlow<AppData?> = repo.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profile = ctx.app.profile
    val live = controller.state

    val screen = MutableStateFlow<Screen>(Screen.Today)
    val onboarding = MutableStateFlow(OnboardingState())
    val selectedDay = MutableStateFlow(LocalDate.now())
    val calendarMonth = MutableStateFlow(YearMonth.now())
    val calendarDay = MutableStateFlow(LocalDate.now())
    val batteryWarning = MutableStateFlow<BatteryWarning?>(null)
    val reviewPicks = MutableStateFlow<Map<StartKey, Int>>(emptyMap())
    val flash = MutableStateFlow("")
    val confirmRetest = MutableStateFlow(false)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    init {
        viewModelScope.launch {
            val p = repo.profileNow()
            if (!p.onboarded) screen.value = Screen.Onboarding
            repo.rollover()
            handleInterrupted()
            _ready.value = true
        }
    }

    fun onResume() {
        repo.refreshToday()
        selectedDay.value = selectedDay.value.coerceAtLeast(LocalDate.now().minusDays(6))
        viewModelScope.launch {
            repo.rollover()
            handleInterrupted()
        }
        Reminders.reschedule(ctx)
    }

    fun go(s: Screen) {
        flash.value = ""
        screen.value = s
    }

    // ---------- interrupted sessions ----------

    val interrupted = MutableStateFlow<Interrupted?>(null)

    /** Applies the resume rules to an in-progress session left behind by a dead phone or a killed app. */
    private suspend fun handleInterrupted() {
        if (controller.state.value != null) return
        val e = repo.inProgressNow() ?: run { interrupted.value = null; return }
        val runner = decodeRunner(e.snapshot ?: "") ?: run { repo.deleteSession(e.id); return }
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.updatedAt), ZoneId.systemDefault())
        val now = LocalDateTime.now()
        when (val action = ResumePolicy.decide(runner.plan, at, now)) {
            ResumeAction.SAVE_PARTIAL -> {
                if (runner.setsDone == 0) repo.deleteSession(e.id)
                else repo.saveInProgress(e.copy(status = SessionStatus.PARTIAL, snapshot = null))
                if (runner.setsDone > 0) {
                    repo.setNotice("${e.date.let { LocalDate.ofEpochDay(it).dayOfWeek.name.lowercase().replaceFirstChar { c -> c.uppercase() } }}'s session was interrupted: ${runner.setsDone} of ${runner.setsTotal} sets saved as a partial session.")
                }
                interrupted.value = null
            }
            ResumeAction.DROP_BENCHMARK -> {
                repo.deleteSession(e.id)
                repo.setNotice("The benchmark was interrupted, so it doesn't count. It stays available on the next fresh day.")
                interrupted.value = null
            }
            else -> {
                val step = runner.step
                val title = when (step) {
                    is com.doublebasshero.engine.Step.Work -> "${step.exercise.title}, set ${step.set}/${step.of}"
                    is com.doublebasshero.engine.Step.Warmup -> "the warm-up"
                    else -> "a rest"
                }
                interrupted.value = Interrupted(e.id, action, java.time.Duration.between(at, now).toMinutes(), title, runner.setsDone)
            }
        }
    }

    fun resumeInterrupted() = guardBattery(30) {
        viewModelScope.launch {
            val it = interrupted.value ?: return@launch
            val e = repo.inProgressNow() ?: return@launch
            val runner = decodeRunner(e.snapshot ?: "") ?: return@launch
            interrupted.value = null
            controller.resume(e, runner, rewarm = it.action == ResumeAction.REWARM_THEN_RESUME)
            screen.value = Screen.Player
        }
    }

    fun saveInterrupted() = viewModelScope.launch {
        val e = repo.inProgressNow() ?: return@launch
        val runner = decodeRunner(e.snapshot ?: "")
        if (runner == null || runner.setsDone == 0) repo.deleteSession(e.id)
        else repo.saveInProgress(e.copy(status = SessionStatus.PARTIAL, snapshot = null))
        interrupted.value = null
    }

    fun discardInterrupted() = viewModelScope.launch {
        interrupted.value?.let { repo.deleteSession(it.sessionId) }
        interrupted.value = null
    }

    fun clearNotice() = viewModelScope.launch { repo.setNotice("") }

    // ---------- battery ----------

    private fun battery(): Pair<Int, Boolean> {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100 to true
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return level * 100 / scale to plugged
    }

    private fun guardBattery(minutes: Int, action: () -> Unit) {
        val (pct, charging) = battery()
        if (BatteryRule.warn(pct, charging)) batteryWarning.value = BatteryWarning(pct, minutes, action)
        else action()
    }

    fun batteryContinue() {
        val w = batteryWarning.value ?: return
        batteryWarning.value = null
        w.action()
    }

    // ---------- sessions ----------

    fun startDay(date: LocalDate) {
        val d = data.value ?: return
        val plan = d.calendar.buildSession(date, d.startBpms) ?: return
        guardBattery((SessionBuilder.estimatedDurationSec(plan) + 30) / 60) {
            controller.start(plan, date)
            screen.value = Screen.Player
        }
    }

    fun skipToday() = viewModelScope.launch {
        repo.skipDay(LocalDate.now())
        Reminders.cancelPosted(ctx)
        Reminders.reschedule(ctx)
    }

    fun unskipToday() = viewModelScope.launch {
        repo.unskipDay(LocalDate.now())
        Reminders.reschedule(ctx)
    }

    fun benchmarkResult(passed: Boolean) = viewModelScope.launch {
        val s = live.value ?: return@launch
        val plan = s.runner.plan
        val bpm = plan.benchmarkBpm ?: return@launch
        controller.dismiss()
        if (s.placement) {
            val o = onboarding.value
            onboarding.value = if (passed) {
                o.copy(trueBpm = bpm, verifiedBpm = bpm, message = "Test passed: True BPM $bpm confirmed.")
            } else {
                val lower = (bpm - 10).coerceAtLeast(40)
                o.copy(trueBpm = lower, message = "No problem: try again at $lower BPM.")
            }
            screen.value = Screen.Onboarding
            return@launch
        }
        val before = Levels.forTrueBpm(profile.value?.trueBpm ?: bpm).number
        repo.benchmarkResult(s.date, passed, bpm, plan.slot, s.startedAt)
        Reminders.reschedule(ctx)
        if (passed) {
            screen.value = Screen.LevelUp(before, Levels.forTrueBpm(bpm).number)
        } else {
            screen.value = Screen.Levels
            flash.value = "Benchmark not passed. It locks again until every exercise is back at the cap on Endurance days."
        }
    }

    fun finishSummary() {
        controller.dismiss()
        screen.value = Screen.Today
        Reminders.reschedule(ctx)
    }

    // ---------- onboarding / retest ----------

    fun updateOnboarding(f: (OnboardingState) -> OnboardingState) {
        onboarding.value = f(onboarding.value)
    }

    fun placementTest() {
        val o = onboarding.value
        val level = Levels.forTrueBpm(o.trueBpm)
        controller.start(SessionBuilder.benchmark(level, o.trueBpm, o.trueBpm, placement = true), LocalDate.now(), placement = true)
        screen.value = Screen.Player
    }

    fun finishOnboarding() = viewModelScope.launch {
        val o = onboarding.value
        if (o.retest) {
            if (!Retest.accepts(o.trueBpm, Levels.forTrueBpm(o.retestCap), o.verifiedBpm)) {
                onboarding.value = o.copy(message = "Above ${o.retestCap} you have to pass the test in the app. Tap \"Take the test now\".")
                return@launch
            }
            repo.retest(o.trueBpm)
        } else {
            repo.completeOnboarding(o.trueBpm, o.restDays.toSet(), o.reminderMinutes)
        }
        onboarding.value = OnboardingState()
        selectedDay.value = LocalDate.now()
        screen.value = Screen.Today
        Reminders.reschedule(ctx)
    }

    fun startRetest() {
        val p = profile.value ?: return
        confirmRetest.value = false
        onboarding.value = OnboardingState(step = 1, trueBpm = p.trueBpm, retest = true, retestCap = Levels.forTrueBpm(p.trueBpm).cap)
        screen.value = Screen.Onboarding
    }

    fun recommendedWeek(rest: Set<Int>) = WeekPlanner.recommend(rest)

    // ---------- week ----------

    fun acceptReview() = viewModelScope.launch {
        repo.acceptReview(reviewPicks.value)
        reviewPicks.value = emptyMap()
        screen.value = Screen.Today
        Reminders.reschedule(ctx)
    }

    fun setMakeup(accepted: Boolean) = viewModelScope.launch { repo.setMakeup(LocalDate.now(), accepted) }
    fun toggleUnavailable(date: LocalDate) = viewModelScope.launch { repo.toggleUnavailable(date); Reminders.reschedule(ctx) }
    fun resetWeek() = viewModelScope.launch { repo.resetWeek(LocalDate.now()); Reminders.reschedule(ctx) }

    // ---------- settings ----------

    fun updateSettings(f: (com.doublebasshero.data.ProfileEntity) -> com.doublebasshero.data.ProfileEntity) = viewModelScope.launch {
        repo.updateProfile(f)
        Reminders.reschedule(ctx)
    }

    fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Reminders.ACTION_START -> if (controller.state.value == null) startDay(LocalDate.now())
            Reminders.ACTION_REVIEW -> screen.value = Screen.Review
        }
    }

    val today: LocalDate get() = data.value?.today ?: LocalDate.now()

    fun isPast(date: LocalDate) = date.epoch() < today.epoch()
}
