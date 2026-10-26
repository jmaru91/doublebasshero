package com.doublebasshero.engine

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarTest {
    private val today = LocalDate.of(2026, 10, 21) // Wednesday
    private val l4 = Levels.byNumber(4)
    private val profile = Profile(
        trueBpm = 112, restDays = setOf(3, 6),
        programStart = LocalDate.of(2026, 10, 5), cycleStart = LocalDate.of(2026, 10, 5),
        benchmarkUnlocked = false,
    )

    private fun saved(date: LocalDate, type: DayType, top: Int = 112) = SavedSession(
        slot = type,
        result = SessionResult(date, type, 4, false, false, l4.exercises.map { ExerciseResult(it, listOf(SetResult(top - 2, FormRating.CLEAN, 2), SetResult(top, FormRating.CLEAN))) }),
    )

    private val thisWeek = listOf(saved(LocalDate.of(2026, 10, 19), DayType.SPEED), saved(LocalDate.of(2026, 10, 20), DayType.HYBRID))

    private fun cal(sessions: List<SavedSession> = thisWeek, p: Profile = profile, ov: WeekOverrides? = null) =
        TrainingCalendar(today, p, sessions, ov)

    @Test fun `current week shows done days and today's Endurance`() {
        val c = cal()
        val week = c.week(today.weekStart())
        assertEquals("SHEREER", week.map { it.type.letter }.joinToString(""))
        assertEquals(DayState.DONE, week[0].state)
        assertTrue(c.day(today).canStart)
        assertEquals(3, c.day(today).cycleWeek)
        assertEquals(1, c.buildSession(today, emptyMap())!!.soloLeg, "2 sessions before today → Solo Leg 1")
        assertEquals(1, cal(sessions = emptyList()).buildSession(today, emptyMap())!!.soloLeg)
        assertEquals(2, cal(sessions = thisWeek.take(1)).buildSession(today, emptyMap())!!.soloLeg)
    }

    @Test fun `past training days without a session are skipped`() {
        val c = cal(sessions = emptyList())
        assertEquals(DayState.SKIPPED, c.day(LocalDate.of(2026, 10, 13)).state)
        assertEquals(DayState.REST, c.day(LocalDate.of(2026, 10, 15)).state)
        assertEquals(DayState.REST, c.day(LocalDate.of(2026, 10, 1)).state, "before the program")
    }

    @Test fun `next week is a deload`() {
        val c = cal()
        assertTrue(c.day(LocalDate.of(2026, 10, 28)).deload)
        val plan = c.buildSession(LocalDate.of(2026, 10, 28), emptyMap())!!
        assertTrue(plan.deload)
        assertTrue(plan.exercises.all { it.sets == 2 })
    }

    @Test fun `maxed Speed turns future Speed days into Endurance`() {
        val c = cal(sessions = listOf(saved(LocalDate.of(2026, 10, 19), DayType.SPEED, top = 119), saved(LocalDate.of(2026, 10, 20), DayType.HYBRID)))
        val nextMonday = c.day(LocalDate.of(2026, 10, 26))
        assertEquals(DayType.SPEED, nextMonday.slot)
        assertEquals(DayType.ENDURANCE, nextMonday.type)
        assertTrue(nextMonday.converted)
        assertEquals(DayType.SPEED, c.day(LocalDate.of(2026, 10, 19)).type, "past days keep what was played")
    }

    @Test fun `unlocked benchmark goes on the next fresh day`() {
        val c = cal(p = profile.copy(benchmarkUnlocked = true))
        assertEquals(LocalDate.of(2026, 10, 23), c.benchmarkDay)
        assertEquals(DayType.BENCHMARK, c.day(LocalDate.of(2026, 10, 23)).type)
        assertNull(cal().benchmarkDay)
    }

    @Test fun `skipping today suggests Thursday`() {
        val ov = WeekOverrides(today.weekStart(), skipped = setOf(today))
        val c = cal(ov = ov)
        assertTrue(c.currentWeek.makeupPending)
        assertEquals(DayState.MAKEUP, c.day(LocalDate.of(2026, 10, 22)).state)
        assertEquals(DayType.ENDURANCE, c.day(LocalDate.of(2026, 10, 22)).type)
    }

    @Test fun `notifications merge into one morning message plus the evening check`() {
        val day = NotificationDay(
            date = LocalDate.of(2026, 10, 26), type = DayType.SPEED, estimatedMinutes = 38, pedalStartBpm = 110,
            sessionDone = false, weekReviewPending = true, reviewChanges = 3, benchmarkJustUnlockedFor = null,
            deloadStarts = true, cycleStarts = false,
        )
        val prefs = NotificationPrefs(reminderTimes = List(7) { if (it >= 5) LocalTime.of(9, 0) else LocalTime.of(18, 0) })
        val n = NotificationPlanner.plan(day, prefs)
        assertEquals(2, n.size)
        assertEquals("Week review ready · Speed day · ~38 min", n[0].title)
        assertEquals(LocalTime.of(18, 0), n[0].time)
        assertEquals(LocalTime.of(21, 0), n[1].time)
        val sat = NotificationPlanner.plan(day.copy(date = LocalDate.of(2026, 10, 31), weekReviewPending = false, deloadStarts = false), prefs)
        assertEquals(LocalTime.of(9, 0), sat[0].time)
        val rest = NotificationPlanner.plan(day.copy(type = DayType.REST, weekReviewPending = false, deloadStarts = false), prefs)
        assertTrue(rest.isEmpty(), "rest-day notifications are off by default")
        val done = NotificationPlanner.plan(day.copy(sessionDone = true, weekReviewPending = false, deloadStarts = false), prefs)
        assertTrue(done.isEmpty())
    }
}
