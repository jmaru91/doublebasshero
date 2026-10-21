package com.doublebasshero.engine

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** One finished set: its BPM, the user's rating, and the ramp step chosen after it. */
@Serializable
data class SetResult(val bpm: Int, val rating: FormRating? = null, val rampDelta: Int? = null)

data class ExerciseResult(val exercise: Exercise, val sets: List<SetResult>) {
    val rating: FormRating? get() = Ratings.average(sets.mapNotNull { it.rating })
    val topBpm: Int get() = sets.maxOfOrNull { it.bpm } ?: 0
}

/** A saved session, as the rules see it. [dayType] is what was actually played. */
data class SessionResult(
    val date: LocalDate,
    val dayType: DayType,
    val level: Int,
    val deload: Boolean,
    val partial: Boolean,
    val exercises: List<ExerciseResult>,
) {
    fun of(exercise: Exercise): ExerciseResult? = exercises.firstOrNull { it.exercise == exercise }
}

object Ratings {
    /** Average of set ratings (Clean 2, Shaky 1, Broken 0): ≥1.5 Clean, ≥0.75 Shaky, else Broken. */
    fun average(ratings: List<FormRating>): FormRating? {
        if (ratings.isEmpty()) return null
        val avg = ratings.sumOf { it.score }.toDouble() / ratings.size
        return when {
            avg >= 1.5 -> FormRating.CLEAN
            avg >= 0.75 -> FormRating.SHAKY
            else -> FormRating.BROKEN
        }
    }

    /** Share of clean-equivalent score, 0..1, used to find the weakest exercise. */
    fun formScore(ratings: List<FormRating>): Double? =
        if (ratings.isEmpty()) null else ratings.sumOf { it.score }.toDouble() / (2.0 * ratings.size)
}

/** Start BPMs are kept per exercise and per progressing day type. */
@Serializable
data class StartKey(val exercise: Exercise, val dayType: DayType)

object StartBpms {
    /**
     * Placement defaults: every day type starts so that the default ramp
     * (+2 per set) ends on the True BPM, never above the cap.
     */
    fun defaults(level: Level, trueBpm: Int): Map<StartKey, Int> {
        val start = (trueBpm - Ramp.DEFAULT_STEP * (level.setsPerExercise - 1)).coerceIn(30, level.cap)
        return level.exercises.flatMap { ex -> DayType.PROGRESSING.map { StartKey(ex, it) to start } }.toMap()
    }

    fun get(map: Map<StartKey, Int>, level: Level, trueBpm: Int, exercise: Exercise, dayType: DayType): Int {
        val type = if (dayType in DayType.PROGRESSING) dayType else DayType.ENDURANCE
        return (map[StartKey(exercise, type)] ?: defaults(level, trueBpm).getValue(StartKey(exercise, type))).coerceAtMost(level.cap)
    }
}

sealed interface ReviewReason {
    data object NoSession : ReviewReason
    data class Broken(val date: LocalDate) : ReviewReason
    data class Shaky(val date: LocalDate) : ReviewReason
    data class HeldBack(val date: LocalDate) : ReviewReason
    data class AllClean(val sessions: Int) : ReviewReason
    data class AllCleanFeelingGood(val plusFours: Int, val sessions: Int) : ReviewReason
}

data class Recommendation(
    val exercise: Exercise,
    val dayType: DayType,
    val from: Int,
    val delta: Int,
    val reason: ReviewReason,
) {
    fun to(cap: Int, chosenDelta: Int = delta): Int = (from + chosenDelta).coerceAtMost(cap)
}

object WeeklyReview {
    /**
     * Recommends next week's start BPMs from one week of sessions:
     * any Broken → −2; any Shaky or a +0 ramp choice → 0;
     * all Clean with +4 in most sessions → +4; all Clean → +2.
     */
    fun recommend(level: Level, trueBpm: Int, startBpms: Map<StartKey, Int>, week: List<SessionResult>): List<Recommendation> =
        DayType.PROGRESSING.flatMap { type ->
            val sessions = week.filter { it.dayType == type && !it.deload && it.level == level.number }.sortedBy { it.date }
            level.exercises.map { ex ->
                val from = StartBpms.get(startBpms, level, trueBpm, ex, type)
                val results = sessions.mapNotNull { s -> s.of(ex)?.takeIf { it.sets.isNotEmpty() }?.let { s to it } }
                if (results.isEmpty()) return@map Recommendation(ex, type, from, 0, ReviewReason.NoSession)
                val broken = results.firstOrNull { it.second.rating == FormRating.BROKEN }
                val shaky = results.firstOrNull { it.second.rating == FormRating.SHAKY }
                val heldBack = results.firstOrNull { (_, r) -> r.sets.dropLast(1).any { it.rampDelta == 0 } }
                val plusFours = results.count { (_, r) -> r.sets.any { it.rampDelta == 4 } }
                when {
                    broken != null -> Recommendation(ex, type, from, -2, ReviewReason.Broken(broken.first.date))
                    shaky != null -> Recommendation(ex, type, from, 0, ReviewReason.Shaky(shaky.first.date))
                    heldBack != null -> Recommendation(ex, type, from, 0, ReviewReason.HeldBack(heldBack.first.date))
                    plusFours * 2 > results.size -> Recommendation(ex, type, from, 4, ReviewReason.AllCleanFeelingGood(plusFours, results.size))
                    else -> Recommendation(ex, type, from, 2, ReviewReason.AllClean(results.size))
                }
            }
        }

    /** Applies the chosen deltas (defaults to the recommendations). */
    fun apply(
        startBpms: Map<StartKey, Int>,
        recommendations: List<Recommendation>,
        cap: Int,
        chosen: Map<StartKey, Int> = emptyMap(),
    ): Map<StartKey, Int> {
        val out = startBpms.toMutableMap()
        for (r in recommendations) {
            val key = StartKey(r.exercise, r.dayType)
            out[key] = r.to(cap, chosen[key] ?: r.delta).coerceAtLeast(30)
        }
        return out
    }
}

object CapRules {
    /**
     * A Speed or Hybrid day turns into an Endurance day once every exercise of the
     * level reached the cap in the latest session of that type at this level.
     */
    fun converts(level: Level, type: DayType, history: List<SessionResult>): Boolean {
        if (type != DayType.SPEED && type != DayType.HYBRID) return false
        val latest = history.filter { it.dayType == type && it.level == level.number && !it.deload }.maxByOrNull { it.date } ?: return false
        return level.exercises.all { ex -> (latest.of(ex)?.topBpm ?: 0) >= level.cap }
    }

    /** Exercises at the cap in the latest session of [type] at this level. */
    fun atCap(level: Level, type: DayType, history: List<SessionResult>): Set<Exercise> {
        val latest = history.filter { it.dayType == type && it.level == level.number && !it.deload }.maxByOrNull { it.date }
            ?: return emptySet()
        return level.exercises.filter { (latest.of(it)?.topBpm ?: 0) >= level.cap }.toSet()
    }

    /**
     * The benchmark unlocks when, in one week's Endurance sessions, every exercise
     * reached the cap and was rated Clean every time it was played.
     */
    fun benchmarkUnlocked(level: Level, week: List<SessionResult>): Boolean {
        if (level.isTop) return false
        val endurance = week.filter { it.dayType == DayType.ENDURANCE && it.level == level.number && !it.deload }
        if (endurance.isEmpty()) return false
        return level.exercises.all { ex ->
            val played = endurance.mapNotNull { it.of(ex)?.takeIf { r -> r.sets.isNotEmpty() } }
            played.isNotEmpty() && played.any { it.topBpm >= level.cap } && played.all { it.rating == FormRating.CLEAN }
        }
    }
}

object WeaknessBalancer {
    const val MIN_RATED_SETS = 6
    const val MIN_GAP = 0.15

    /**
     * Gives the weakest exercise up to 2× its sets, taken from the strongest ones
     * (never below 1), so the session keeps the same total. Pedal Normal never
     * loses sets. Needs enough rated sets to judge.
     */
    fun balance(level: Level, ratings: Map<Exercise, List<FormRating>>): Pair<Map<Exercise, Int>, Exercise?> {
        val plan = level.exercises.associateWith { level.setsPerExercise }.toMutableMap()
        val candidates = level.exercises.filter { it != Exercise.PEDAL_NORMAL }
        if (candidates.size < 2) return plan to null
        val scores = candidates.associateWith { ex ->
            ratings[ex]?.takeIf { it.size >= MIN_RATED_SETS }?.let { Ratings.formScore(it) }
        }
        if (scores.values.any { it == null }) return plan to null
        val sorted = candidates.sortedBy { scores.getValue(it) }
        val weak = sorted.first()
        if (scores.getValue(sorted.last())!! - scores.getValue(weak)!! < MIN_GAP) return plan to null
        var need = level.setsPerExercise
        for (ex in sorted.drop(1).reversed()) {
            if (need == 0) break
            val give = minOf(plan.getValue(ex) - 1, need)
            plan[ex] = plan.getValue(ex) - give
            plan[weak] = plan.getValue(weak) + give
            need -= give
        }
        return plan to weak
    }
}

object Retest {
    /**
     * Redoing the True BPM test: anything up to the current cap can be typed in;
     * a higher value is accepted only after passing the test in the app.
     */
    fun accepts(newTrueBpm: Int, currentLevel: Level, verifiedBpm: Int?): Boolean =
        newTrueBpm <= currentLevel.cap || verifiedBpm == newTrueBpm
}
