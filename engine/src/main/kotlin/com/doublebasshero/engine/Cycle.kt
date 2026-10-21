package com.doublebasshero.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** 0 = Monday … 6 = Sunday. */
fun LocalDate.dayIndex(): Int = dayOfWeek.value - 1

/** 3 hard weeks + 1 deload week, repeating from [cycleStart] (a Monday). */
object Cycle {
    const val LENGTH = 4
    const val MIN_SESSIONS = 3

    fun weekIndex(cycleStart: LocalDate, date: LocalDate): Int {
        val weeks = ChronoUnit.WEEKS.between(cycleStart, date.weekStart())
        return if (weeks < 0) 1 else (weeks % LENGTH).toInt() + 1
    }

    fun isDeload(cycleStart: LocalDate, date: LocalDate): Boolean = weekIndex(cycleStart, date) == LENGTH

    /**
     * Called once a week has ended. A hard week with fewer than 3 sessions was an
     * unplanned deload: the cycle restarts the following week.
     */
    fun rollover(cycleStart: LocalDate, endedWeekStart: LocalDate, sessionsDone: Int): LocalDate =
        if (!isDeload(cycleStart, endedWeekStart) && sessionsDone < MIN_SESSIONS) endedWeekStart.plusWeeks(1) else cycleStart

    /** BPMs change only after a real hard week. */
    fun reviewApplies(cycleStart: LocalDate, endedWeekStart: LocalDate, sessionsDone: Int): Boolean =
        !isDeload(cycleStart, endedWeekStart) && sessionsDone >= MIN_SESSIONS
}

enum class ResumeAction { RESUME, REWARM_THEN_RESUME, SAVE_PARTIAL, DROP_BENCHMARK }

object ResumePolicy {
    const val REWARM_AFTER_MIN = 15L

    /**
     * After a session was interrupted (battery, app killed): same day → resume the
     * interrupted set, with a re-warm-up after 15 minutes or on a Speed day; a later
     * day → save the finished sets as a partial session. A benchmark never resumes.
     */
    fun decide(plan: SessionPlan, interruptedAt: LocalDateTime, now: LocalDateTime): ResumeAction = when {
        plan.isBenchmark -> ResumeAction.DROP_BENCHMARK
        now.toLocalDate() != interruptedAt.toLocalDate() -> ResumeAction.SAVE_PARTIAL
        plan.dayType == DayType.SPEED -> ResumeAction.REWARM_THEN_RESUME
        ChronoUnit.MINUTES.between(interruptedAt, now) >= REWARM_AFTER_MIN -> ResumeAction.REWARM_THEN_RESUME
        else -> ResumeAction.RESUME
    }
}

object BatteryRule {
    const val LOW_PERCENT = 20

    fun warn(percent: Int, charging: Boolean): Boolean = !charging && percent < LOW_PERCENT
}
