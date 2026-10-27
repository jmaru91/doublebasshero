package com.doublebasshero.engine

import kotlinx.serialization.Serializable

@Serializable
data class PlannedExercise(
    val exercise: Exercise,
    val sets: Int,
    val startBpm: Int,
    val weak: Boolean = false,
    /**
     * Fewer sets than normal (weakness balancing took some away): the start moves up
     * so the last set still lands where the normal ramp would arrive.
     */
    val raisedStart: Boolean = false,
)

@Serializable
data class SessionPlan(
    val level: Int,
    val dayType: DayType,
    /** The week slot this session fills (before any Speed/Hybrid → Endurance conversion). */
    val slot: DayType,
    val deload: Boolean,
    val warmupBpm: Int,
    val withWarmup: Boolean,
    val exercises: List<PlannedExercise>,
    /** Only for BENCHMARK sessions. */
    val benchmarkBpm: Int? = null,
    /** Floor exercises only (no-pedals mode). */
    val noPedals: Boolean = false,
    /** Drives the warm-up tempo ramp (0 in snapshots from older versions). */
    val trueBpm: Int = 0,
    /** Gets an extra Solo Leg pass in the warm-up. */
    val weakFoot: Foot? = null,
    /** Which Solo Leg the warm-up plays today (1 or 2; 0 = both, older sessions). */
    val soloLeg: Int = 0,
) {
    val warmup: List<WarmupExercise> get() = Warmup.exercises(soloLeg)

    /** Warm-up tempo for exercise [index]; older snapshots fall back to their single tempo. */
    fun warmupBpm(index: Int): Int = if (trueBpm > 0) Warmup.bpm(trueBpm, index) else warmupBpm

    val cap: Int get() = Levels.byNumber(level).cap
    val setDurationSec: Int get() = Levels.byNumber(level).setDurationSec
    val isBenchmark: Boolean get() = dayType == DayType.BENCHMARK
}

object SessionBuilder {
    /**
     * A normal session: warm-up, then the level's exercises in order (Pedal Normal
     * last) played with the day type's pattern. Deload: half the sets, no ramp.
     */
    fun build(
        level: Level,
        dayType: DayType,
        slot: DayType,
        trueBpm: Int,
        startBpms: Map<StartKey, Int>,
        recentRatings: Map<Exercise, List<FormRating>>,
        prioritizeWeak: Boolean,
        deload: Boolean,
        noPedals: Boolean = false,
        weakFoot: Foot? = null,
        soloLeg: Int = 1,
    ): SessionPlan {
        require(dayType in DayType.PROGRESSING)
        val base = if (noPedals) NoPedals.baseSets(level) else level.exercises.associateWith { level.setsPerExercise }
        val (sets, weak) = if (prioritizeWeak) WeaknessBalancer.balance(base, recentRatings) else base to null
        return SessionPlan(
            level = level.number,
            dayType = dayType,
            slot = slot,
            deload = deload,
            warmupBpm = Warmup.bpm(trueBpm, 0),
            withWarmup = true,
            noPedals = noPedals,
            trueBpm = trueBpm,
            weakFoot = weakFoot,
            soloLeg = soloLeg,
            exercises = base.keys.map { ex ->
                val n = sets.getValue(ex)
                val start = StartBpms.get(startBpms, level, trueBpm, ex, dayType)
                // A strong exercise that gave sets away must still be challenging: start higher so the
                // last set lands where the normal ramp would arrive, and the result is recorded at that speed.
                val missing = if (deload) 0 else (base.getValue(ex) - n).coerceAtLeast(0)
                PlannedExercise(
                    exercise = ex,
                    sets = if (deload) (n + 1) / 2 else n,
                    startBpm = (start + Ramp.DEFAULT_STEP * missing).coerceAtMost(level.cap),
                    weak = ex == weak,
                    raisedStart = missing > 0,
                )
            },
        )
    }

    /** Warm-up (unless it's the placement test), then 4:00 at [bpm]. */
    fun benchmark(
        level: Level,
        bpm: Int,
        trueBpm: Int,
        placement: Boolean,
        slot: DayType = DayType.SPEED,
        weakFoot: Foot? = null,
        soloLeg: Int = 1,
    ) = SessionPlan(
        level = level.number,
        dayType = DayType.BENCHMARK,
        slot = slot,
        deload = false,
        warmupBpm = Warmup.bpm(trueBpm, 0),
        withWarmup = !placement,
        exercises = emptyList(),
        benchmarkBpm = bpm,
        trueBpm = trueBpm,
        weakFoot = weakFoot,
        soloLeg = soloLeg,
    )

    /** Estimated length with the rest hints, in seconds. */
    fun estimatedDurationSec(plan: SessionPlan): Int = SessionRunner.steps(plan).sumOf { SessionRunner.hintSeconds(plan, it) }

    /** Highest BPM the default ramp reaches for an exercise. */
    fun projectedTop(plan: SessionPlan, ex: PlannedExercise): Int =
        if (plan.deload) ex.startBpm else (ex.startBpm + Ramp.DEFAULT_STEP * (ex.sets - 1)).coerceAtMost(plan.cap)
}

@Serializable
enum class RestKind { SHORT, LONG, SWITCH_EXERCISE, FINAL }

@Serializable
sealed class Step {
    @Serializable
    data class Warmup(val index: Int, val rewarm: Boolean = false) : Step()

    @Serializable
    data class Work(val exercise: Exercise, val set: Int, val of: Int, val exerciseIndex: Int, val exerciseCount: Int) : Step()

    /** Rest after a set: rate it; between sets of one exercise, also choose the next BPM. */
    @Serializable
    data class Rest(val kind: RestKind, val ratedExercise: Exercise, val nextSetOf: Exercise?) : Step()

    @Serializable
    data class BenchmarkTest(val bpm: Int) : Step()
}

/** Everything needed to resume a session after the app was killed: saved after every step. */
@Serializable
data class RunnerState(
    val plan: SessionPlan,
    val steps: List<Step>,
    val index: Int,
    val bpm: Map<Exercise, Int>,
    val nextBpm: Map<Exercise, Int>,
    val sets: Map<Exercise, List<SetResult>>,
) {
    val step: Step? get() = steps.getOrNull(index)
    val finished: Boolean get() = index >= steps.size
    val setsDone: Int get() = sets.values.sumOf { it.size }
    val setsTotal: Int get() = steps.count { it is Step.Work }
}

object SessionRunner {
    fun steps(plan: SessionPlan): List<Step> {
        val out = mutableListOf<Step>()
        if (plan.withWarmup) plan.warmup.indices.forEach { out += Step.Warmup(it) }
        if (plan.isBenchmark) {
            out += Step.BenchmarkTest(plan.benchmarkBpm!!)
            return out
        }
        val n = plan.exercises.size
        plan.exercises.forEachIndexed { i, ex ->
            for (s in 1..ex.sets) {
                out += Step.Work(ex.exercise, s, ex.sets, i + 1, n)
                out += when {
                    s < ex.sets -> Step.Rest(
                        if (plan.dayType == DayType.SPEED) RestKind.LONG else RestKind.SHORT,
                        ex.exercise,
                        if (plan.deload) null else ex.exercise,
                    )
                    i < n - 1 -> Step.Rest(RestKind.SWITCH_EXERCISE, ex.exercise, null)
                    else -> Step.Rest(RestKind.FINAL, ex.exercise, null)
                }
            }
        }
        return out
    }

    fun start(plan: SessionPlan): RunnerState = RunnerState(
        plan = plan,
        steps = steps(plan),
        index = 0,
        bpm = plan.exercises.associate { it.exercise to it.startBpm },
        nextBpm = emptyMap(),
        sets = emptyMap(),
    )

    /** Exact length of a timed step in ms (warm-up: whole passes of the pattern). */
    fun stepMillis(plan: SessionPlan, step: Step): Long = when (step) {
        is Step.Warmup -> Warmup.millis(plan.warmup[step.index], plan.warmupBpm(step.index), plan.weakFoot)
        else -> hintSeconds(plan, step) * 1000L
    }

    /** Seconds a timed step lasts, or the rest hint for a rest step. */
    fun hintSeconds(plan: SessionPlan, step: Step): Int = when (step) {
        is Step.Warmup -> ((stepMillis(plan, step) + 999) / 1000).toInt()
        is Step.Work -> plan.setDurationSec
        is Step.BenchmarkTest -> Benchmark.DURATION_SEC
        is Step.Rest -> when (step.kind) {
            RestKind.SHORT -> Rests.SHORT_SEC
            RestKind.LONG, RestKind.SWITCH_EXERCISE -> Rests.LONG_SEC
            RestKind.FINAL -> 0
        }
    }

    /** Rest steps wait for the user; everything else runs on the clock. */
    fun isTimed(step: Step): Boolean = step !is Step.Rest

    /** BPM the metronome plays during [step]. */
    fun bpm(state: RunnerState, step: Step? = state.step): Int = when (step) {
        is Step.Warmup -> state.plan.warmupBpm(step.index)
        is Step.Work -> state.bpm.getValue(step.exercise)
        is Step.BenchmarkTest -> step.bpm
        else -> 0
    }

    fun pattern(state: RunnerState, step: Step? = state.step): List<BarKind> = when (step) {
        is Step.Warmup -> state.plan.warmup[step.index].bars.map { BarKind.EIGHTHS }
        is Step.Work -> Patterns.forDay(state.plan.dayType)
        is Step.BenchmarkTest -> Patterns.forDay(DayType.BENCHMARK)
        else -> emptyList()
    }

    /** Moves past the current step: logs a finished set, applies the chosen ramp. */
    fun advance(state: RunnerState): RunnerState {
        val step = state.step ?: return state
        var sets = state.sets
        var bpm = state.bpm
        var next = state.nextBpm
        when (step) {
            is Step.Work -> {
                sets = sets + (step.exercise to (sets[step.exercise].orEmpty() + SetResult(bpm.getValue(step.exercise))))
            }
            is Step.Rest -> step.nextSetOf?.let { ex ->
                val chosen = next[ex] ?: defaultNext(state, ex, null)
                val log = sets.getValue(ex)
                sets = sets + (ex to log.dropLast(1) + log.last().copy(rampDelta = chosen - bpm.getValue(ex)))
                bpm = bpm + (ex to chosen)
                next = next - ex
            }
            else -> Unit
        }
        return state.copy(index = state.index + 1, sets = sets, bpm = bpm, nextBpm = next)
    }

    /** Rates the set just played; on a between-sets rest the rating sets the next BPM. */
    fun rate(state: RunnerState, rating: FormRating): RunnerState {
        val step = state.step as? Step.Rest ?: return state
        val ex = step.ratedExercise
        val log = state.sets[ex] ?: return state
        val sets = state.sets + (ex to log.dropLast(1) + log.last().copy(rating = rating))
        val next = step.nextSetOf?.let { state.nextBpm + (it to defaultNext(state, it, rating)) } ?: state.nextBpm
        return state.copy(sets = sets, nextBpm = next)
    }

    /**
     * Shaky or Broken → +0; Clean (or not rated) → +2; Clean after a Clean set of
     * the same exercise → +4. Capped. The user doesn't choose.
     */
    fun defaultNext(state: RunnerState, ex: Exercise, rating: FormRating?): Int {
        val previous = state.sets[ex].orEmpty().dropLast(1).lastOrNull()?.rating
        val step = when {
            rating == FormRating.CLEAN && previous == FormRating.CLEAN -> Ramp.STRONG_STEP
            rating == null || rating == FormRating.CLEAN -> Ramp.DEFAULT_STEP
            else -> 0
        }
        return (state.bpm.getValue(ex) + step).coerceAtMost(state.plan.cap)
    }

    fun nextBpm(state: RunnerState): Int? {
        val ex = (state.step as? Step.Rest)?.nextSetOf ?: return null
        return state.nextBpm[ex] ?: defaultNext(state, ex, null)
    }

    /** Rating of the set the current rest step is about. */
    fun pendingRating(state: RunnerState): FormRating? {
        val step = state.step as? Step.Rest ?: return null
        return state.sets[step.ratedExercise]?.lastOrNull()?.rating
    }

    /**
     * Resumes after an interruption: the interrupted set was never logged, so the
     * runner restarts the current step. With [rewarm] the warm-up is played again
     * right before the next set (after a rest that was in progress). If the
     * interruption hit the warm-up itself, the warm-up simply starts over.
     */
    fun resume(state: RunnerState, rewarm: Boolean): RunnerState {
        if (state.step is Step.Warmup) {
            val first = state.steps.indexOfFirst { it is Step.Warmup }
            return state.copy(index = first)
        }
        if (!rewarm) return state
        val at = (state.index until state.steps.size).firstOrNull { state.steps[it] !is Step.Rest } ?: return state
        val warm = state.plan.warmup.indices.map { Step.Warmup(it, rewarm = true) }
        return state.copy(steps = state.steps.take(at) + warm + state.steps.drop(at))
    }

    fun toResult(state: RunnerState, date: java.time.LocalDate, partial: Boolean): SessionResult = SessionResult(
        date = date,
        dayType = state.plan.dayType,
        level = state.plan.level,
        deload = state.plan.deload,
        partial = partial,
        exercises = state.plan.exercises.mapNotNull { pe -> state.sets[pe.exercise]?.let { ExerciseResult(pe.exercise, it) } },
    )
}
