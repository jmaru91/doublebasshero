package com.doublebasshero.session

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.doublebasshero.audio.Metronome
import com.doublebasshero.audio.Speaker
import com.doublebasshero.data.ProfileEntity
import com.doublebasshero.data.Repository
import com.doublebasshero.data.SessionEntity
import com.doublebasshero.data.SessionStatus
import com.doublebasshero.data.encodeRunner
import com.doublebasshero.data.encodeSets
import com.doublebasshero.data.epoch
import com.doublebasshero.engine.FormRating
import com.doublebasshero.engine.RestKind
import com.doublebasshero.engine.RunnerState
import com.doublebasshero.engine.SessionPlan
import com.doublebasshero.engine.SessionRunner
import com.doublebasshero.engine.Step
import com.doublebasshero.engine.Warmup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Outcome { COMPLETE, PARTIAL, BENCHMARK }

data class LiveSession(
    val sessionId: Long,
    val date: LocalDate,
    val runner: RunnerState,
    /** Placement test (onboarding / retest): not saved to history. */
    val placement: Boolean,
    val startedAt: Long,
    val paused: Boolean = false,
    /** Elapsed ms in the current step (timed steps: after the count-in; rests: since the rest began). */
    val elapsedMs: Long = 0,
    val countingIn: Boolean = false,
    val restCued: Boolean = false,
    val outcome: Outcome? = null,
    val tick: Long = 0,
) {
    val step: Step? get() = runner.step
    val stepSeconds: Int get() = step?.let { SessionRunner.hintSeconds(runner.plan, it) } ?: 0
}

/**
 * Runs a session: steps on the clock, metronome, voice and cues, and a
 * crash-safe snapshot in the database after every step change.
 */
class SessionController(
    private val context: Context,
    private val repo: Repository,
    private val scope: CoroutineScope,
    private val prefs: () -> ProfileEntity?,
) {
    private val _state = MutableStateFlow<LiveSession?>(null)
    val state: StateFlow<LiveSession?> = _state

    val metronome = Metronome()
    private val speaker by lazy { Speaker(context) }
    private var ticker: Job? = null

    /** Wall-clock (elapsedRealtime) when the current step's timer started, adjusted for pauses. */
    private var stepStart = 0L

    private val voice get() = prefs()?.voicePrompts != false
    private val restCue get() = prefs()?.restCue != false

    fun start(plan: SessionPlan, date: LocalDate, placement: Boolean = false) {
        if (_state.value?.outcome == null && _state.value != null) return
        val runner = SessionRunner.start(plan)
        val now = System.currentTimeMillis()
        _state.value = LiveSession(0, date, runner, placement, now)
        scope.launch {
            if (!placement) {
                val id = repo.saveInProgress(entity(runner, date, now, 0))
                _state.value = _state.value?.copy(sessionId = id)
            }
        }
        launchService()
    }

    /** Continues an interrupted session; [rewarm] plays the warm-up first. */
    fun resume(saved: SessionEntity, runner: RunnerState, rewarm: Boolean) {
        val r = SessionRunner.resume(runner, rewarm)
        _state.value = LiveSession(saved.id, LocalDate.ofEpochDay(saved.date), r, false, saved.startedAt)
        persist()
        launchService()
    }

    /** Audio first, then the first step's clock, then the ticker that reads it. */
    private fun launchService() {
        metronome.muted = false
        metronome.start()
        enterStep(announce = true)
        ContextCompat.startForegroundService(context, Intent(context, SessionService::class.java))
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                tick()
                delay(50)
            }
        }
    }

    private fun entity(r: RunnerState, date: LocalDate, startedAt: Long, id: Long, status: String = SessionStatus.IN_PROGRESS) = SessionEntity(
        id = id,
        date = date.epoch(),
        slot = r.plan.slot.name,
        dayType = r.plan.dayType.name,
        level = r.plan.level,
        deload = r.plan.deload,
        status = status,
        startedAt = startedAt,
        updatedAt = System.currentTimeMillis(),
        setsJson = encodeSets(r.sets),
        snapshot = if (status == SessionStatus.IN_PROGRESS) encodeRunner(r) else null,
    )

    private fun persist() {
        val s = _state.value ?: return
        if (s.placement || s.sessionId == 0L || s.outcome != null) return
        val e = entity(s.runner, s.date, s.startedAt, s.sessionId)
        scope.launch { repo.saveInProgress(e) }
    }

    private fun enterStep(announce: Boolean) {
        val s = _state.value ?: return
        val step = s.step
        if (step == null) {
            finish(partial = false)
            return
        }
        if (SessionRunner.isTimed(step)) {
            val countIn = when (step) {
                is Step.Work, is Step.BenchmarkTest -> 4
                is Step.Warmup -> if (step.index == 0) 4 else 0
                else -> 0
            }
            val delayMs = metronome.play(SessionRunner.bpm(s.runner), SessionRunner.pattern(s.runner), countIn)
            stepStart = SystemClock.elapsedRealtime() + delayMs
            _state.value = s.copy(elapsedMs = 0, countingIn = countIn > 0, restCued = false, paused = false)
        } else {
            metronome.silence()
            stepStart = SystemClock.elapsedRealtime()
            _state.value = s.copy(elapsedMs = 0, countingIn = false, restCued = false, paused = false)
        }
        if (announce) announce(step, s.runner)
    }

    private fun announce(step: Step, r: RunnerState) {
        if (!voice) return
        speakerSay(
            when (step) {
                is Step.Warmup -> (if (step.rewarm) "Re-warm-up. " else "") + Warmup.exercises[step.index].title + "."
                is Step.Work -> "${step.exercise.title}. Set ${step.set} of ${step.of}. ${SessionRunner.bpm(r, step)}."
                is Step.BenchmarkTest -> "Benchmark. Four minutes at ${step.bpm}."
                is Step.Rest -> when (step.kind) {
                    RestKind.SHORT -> "Rest."
                    RestKind.LONG, RestKind.SWITCH_EXERCISE -> "Long rest."
                    RestKind.FINAL -> "Done. Rate your last set."
                }
            },
        )
    }

    private fun speakerSay(text: String) {
        if (metronome.muted) return
        speaker.say(text)
    }

    private fun tick() {
        val s = _state.value ?: return
        if (s.outcome != null || s.paused) return
        val step = s.step ?: return
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - stepStart).coerceAtLeast(0)
        if (SessionRunner.isTimed(step)) {
            val counting = now < stepStart
            if (!counting && elapsed >= s.stepSeconds * 1000L) {
                advance()
                return
            }
            _state.value = s.copy(elapsedMs = if (counting) 0 else elapsed, countingIn = counting, tick = s.tick + 1)
        } else {
            var cued = s.restCued
            if (!cued && step is Step.Rest && step.kind != RestKind.FINAL && elapsed >= s.stepSeconds * 1000L) {
                cued = true
                if (restCue && !metronome.muted) {
                    metronome.restCue()
                    speaker.buzz()
                    if (voice) speakerSay("Rest is up.")
                }
            }
            _state.value = s.copy(elapsedMs = elapsed, restCued = cued, tick = s.tick + 1)
        }
    }

    // ---------- user actions ----------

    fun advance() {
        val s = _state.value ?: return
        if (s.outcome != null) return
        val finishedWork = s.step is Step.Work || s.step is Step.BenchmarkTest
        val r = SessionRunner.advance(s.runner)
        _state.value = s.copy(runner = r)
        if (finishedWork) metronome.chime()
        persist()
        enterStep(announce = true)
    }

    /** Headset single press / main button: pause-resume while playing, Ready during a rest. */
    fun primary() {
        val step = _state.value?.step ?: return
        if (step is Step.Rest) advance() else togglePause()
    }

    fun togglePause() {
        val s = _state.value ?: return
        val step = s.step ?: return
        if (!SessionRunner.isTimed(step)) return
        if (s.paused) {
            val delayMs = metronome.play(SessionRunner.bpm(s.runner), SessionRunner.pattern(s.runner), 4)
            stepStart = SystemClock.elapsedRealtime() + delayMs - s.elapsedMs
            _state.value = s.copy(paused = false, countingIn = true)
        } else {
            metronome.silence()
            _state.value = s.copy(paused = true)
        }
    }

    fun rate(rating: FormRating) {
        val s = _state.value ?: return
        _state.value = s.copy(runner = SessionRunner.rate(s.runner, rating))
        persist()
    }

    fun chooseNext(delta: Int) {
        val s = _state.value ?: return
        _state.value = s.copy(runner = SessionRunner.chooseNext(s.runner, delta))
        persist()
    }

    fun setMuted(muted: Boolean) {
        metronome.muted = muted
        _state.value = _state.value?.copy(tick = (_state.value?.tick ?: 0) + 1)
    }

    /** Pauses the clock while the close dialog is open. */
    fun pauseForDialog() {
        val s = _state.value ?: return
        if (s.step?.let { SessionRunner.isTimed(it) } == true && !s.paused) togglePause()
    }

    fun endAndSave() = finish(partial = true)

    fun discard() {
        val s = _state.value ?: return
        stopAudio()
        _state.value = null
        if (s.sessionId != 0L) scope.launch { repo.deleteSession(s.sessionId) }
    }

    private fun finish(partial: Boolean) {
        val s = _state.value ?: return
        stopAudio()
        if (s.runner.plan.isBenchmark) {
            _state.value = s.copy(outcome = Outcome.BENCHMARK)
            if (s.sessionId != 0L) scope.launch { repo.deleteSession(s.sessionId) }
            return
        }
        _state.value = s.copy(outcome = if (partial) Outcome.PARTIAL else Outcome.COMPLETE)
        if (s.placement) return
        val status = if (partial) SessionStatus.PARTIAL else SessionStatus.COMPLETE
        scope.launch {
            if (partial && s.runner.setsDone == 0) repo.deleteSession(s.sessionId)
            else repo.saveInProgress(entity(s.runner, s.date, s.startedAt, s.sessionId, status))
        }
    }

    private fun stopAudio() {
        ticker?.cancel()
        ticker = null
        metronome.stop()
    }

    /** The summary or result screen is done with the finished session. */
    fun dismiss() {
        _state.value = null
    }
}
