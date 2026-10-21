package com.doublebasshero.engine

import java.time.LocalDate

/** A session already saved, with the week slot it used. */
data class SavedSession(val slot: DayType, val result: SessionResult)

/** Changes to the current week only. */
data class WeekOverrides(
    val weekStart: LocalDate,
    val skipped: Set<LocalDate> = emptySet(),
    val unavailable: Set<LocalDate> = emptySet(),
    val makeupAccepted: Boolean? = null,
)

data class Profile(
    val trueBpm: Int,
    val restDays: Set<Int>,
    val programStart: LocalDate,
    val cycleStart: LocalDate,
    val benchmarkUnlocked: Boolean,
    val prioritizeWeak: Boolean = true,
) {
    val level: Level get() = Levels.forTrueBpm(trueBpm)
}

data class DayInfo(
    val date: LocalDate,
    /** The week slot (S/H/E/R) before conversion. */
    val slot: DayType,
    /** What is actually played that day. */
    val type: DayType,
    val state: DayState,
    val inProgram: Boolean,
    val deload: Boolean,
    val cycleWeek: Int,
    /** A Speed/Hybrid slot played as Endurance because every exercise hit the cap. */
    val converted: Boolean,
    val session: SavedSession?,
) {
    val canStart: Boolean get() = type.isTraining && (state == DayState.PLANNED || state == DayState.MAKEUP)
}

/**
 * Combines profile, saved sessions and this week's overrides into the calendar the
 * screens show: what each day is, what was done, deload weeks, Speed/Hybrid days
 * turned into Endurance, and the benchmark day.
 */
class TrainingCalendar(
    val today: LocalDate,
    val profile: Profile,
    sessions: List<SavedSession>,
    val overrides: WeekOverrides?,
) {
    private val byDate: Map<LocalDate, SavedSession> = sessions.associateBy { it.result.date }
    val history: List<SessionResult> = sessions.map { it.result }
    val basePlan: List<DayType> = WeekPlanner.recommend(profile.restDays)
    val level: Level = profile.level
    private val thisWeek: LocalDate = today.weekStart()

    val currentWeek: WeekPlan by lazy { planFor(thisWeek) }

    private fun planFor(weekStart: LocalDate): WeekPlan {
        val ov = overrides?.takeIf { it.weekStart == weekStart }
        val done = (0..6).mapNotNull { i -> byDate[weekStart.plusDays(i.toLong())]?.let { i to it.slot } }.toMap()
        val todayIndex = if (weekStart == thisWeek) today.dayIndex() else if (weekStart < thisWeek) 7 else 0
        val first = if (profile.programStart.weekStart() == weekStart) profile.programStart.dayIndex() else if (profile.programStart > weekStart) 7 else 0
        return WeekPlanner.replan(
            base = basePlan,
            todayIndex = todayIndex,
            done = done,
            skipped = ov?.skipped.orEmpty().map { it.dayIndex() }.toSet(),
            unavailable = ov?.unavailable.orEmpty().map { it.dayIndex() }.toSet(),
            // The first, partial week keeps the usual days off: no make-up offers.
            makeupAccepted = ov?.makeupAccepted ?: if (weekStart == profile.programStart.weekStart()) false else null,
            firstDayIndex = first,
        )
    }

    private val weekCache = HashMap<LocalDate, WeekPlan>()

    private fun weekPlan(weekStart: LocalDate): WeekPlan =
        if (weekStart == thisWeek) currentWeek else weekCache.getOrPut(weekStart) { planFor(weekStart) }

    /** Day info without the benchmark-day override. */
    fun rawDay(date: LocalDate): DayInfo {
        val inProgram = date >= profile.programStart
        val planned = weekPlan(date.weekStart()).days[date.dayIndex()]
        val deload = Cycle.isDeload(profile.cycleStart, date)
        val saved = byDate[date]
        var type = saved?.result?.dayType ?: planned.type
        var converted = false
        if (saved == null && date >= today && CapRules.converts(level, planned.type, history)) {
            type = DayType.ENDURANCE
            converted = true
        }
        return DayInfo(
            date = date,
            slot = planned.type,
            type = if (inProgram) type else DayType.REST,
            state = if (inProgram) planned.state else DayState.REST,
            inProgram = inProgram,
            deload = deload,
            cycleWeek = Cycle.weekIndex(profile.cycleStart, date),
            converted = converted,
            session = saved,
        )
    }

    /** The first fresh training day (right after a rest day, not in a deload week) once unlocked. */
    val benchmarkDay: LocalDate? by lazy {
        if (!profile.benchmarkUnlocked || level.isTop) return@lazy null
        (0L until 28L).map { today.plusDays(it) }.firstOrNull { d ->
            val info = rawDay(d)
            val prev = rawDay(d.minusDays(1))
            info.canStart && !info.deload && !prev.type.isTraining
        }
    }

    fun day(date: LocalDate): DayInfo {
        val raw = rawDay(date)
        return if (date == benchmarkDay) raw.copy(type = DayType.BENCHMARK, converted = false) else raw
    }

    fun week(weekStart: LocalDate): List<DayInfo> = (0..6).map { day(weekStart.plusDays(it.toLong())) }

    fun isDeload(date: LocalDate): Boolean = Cycle.isDeload(profile.cycleStart, date)

    fun sessionsIn(weekStart: LocalDate): List<SessionResult> =
        history.filter { it.date >= weekStart && it.date < weekStart.plusWeeks(1) }

    /** Ratings of the last two weeks, per exercise, for weakness balancing. */
    fun recentRatings(): Map<Exercise, List<FormRating>> =
        history.filter { it.date >= today.minusDays(14) && it.level == level.number }
            .flatMap { it.exercises }
            .groupBy({ it.exercise }, { r -> r.sets.mapNotNull { it.rating } })
            .mapValues { (_, v) -> v.flatten() }

    fun buildSession(date: LocalDate, startBpms: Map<StartKey, Int>): SessionPlan? {
        val info = day(date)
        return when {
            info.type == DayType.BENCHMARK -> SessionBuilder.benchmark(level, level.benchmarkBpm, profile.trueBpm, placement = false, slot = info.slot)
            info.type in DayType.PROGRESSING -> SessionBuilder.build(
                level, info.type, info.slot, profile.trueBpm, startBpms, recentRatings(), profile.prioritizeWeak, info.deload,
            )
            else -> null
        }
    }
}
