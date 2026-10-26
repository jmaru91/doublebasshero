package com.doublebasshero.engine

import com.doublebasshero.engine.Exercise.FLOOR_ELEVATED_TAPS
import com.doublebasshero.engine.Exercise.FLOOR_HEEL_DOWN
import com.doublebasshero.engine.Exercise.FLOOR_HEEL_UP
import com.doublebasshero.engine.Exercise.FLOOR_HIP
import com.doublebasshero.engine.Exercise.PEDAL_NORMAL
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoPedalsTest {
    private fun plan(level: Int, ratings: Map<Exercise, List<FormRating>> = emptyMap()) = Levels.byNumber(level).let {
        SessionBuilder.build(it, DayType.ENDURANCE, DayType.ENDURANCE, it.minBpm, emptyMap(), ratings, prioritizeWeak = true, deload = false, noPedals = true)
    }

    private fun sets(p: SessionPlan) = p.exercises.associate { it.exercise to it.sets }

    @Test fun `floor exercises take Pedal Normal's sets`() {
        assertEquals(mapOf(FLOOR_HIP to 4, FLOOR_HEEL_UP to 4, FLOOR_HEEL_DOWN to 4), sets(plan(4)))
        assertEquals(mapOf(FLOOR_HEEL_DOWN to 4, FLOOR_ELEVATED_TAPS to 5), sets(plan(6)))
        assertEquals(mapOf(FLOOR_HEEL_DOWN to 6, FLOOR_ELEVATED_TAPS to 6), sets(plan(7)))
        assertEquals(mapOf(FLOOR_HEEL_DOWN to 4, FLOOR_ELEVATED_TAPS to 4), sets(plan(8)))
        assertEquals(mapOf(FLOOR_HEEL_DOWN to 5, FLOOR_ELEVATED_TAPS to 5), sets(plan(10)))
        assertTrue(plan(4).noPedals)
    }

    @Test fun `session keeps the same length`() {
        for (n in listOf(1, 4, 6, 8, 10)) {
            val level = Levels.byNumber(n)
            val normal = SessionBuilder.build(level, DayType.ENDURANCE, DayType.ENDURANCE, level.minBpm, emptyMap(), emptyMap(), false, false)
            assertEquals(normal.exercises.sumOf { it.sets }, plan(n).exercises.sumOf { it.sets }, "level $n")
        }
    }

    @Test fun `start BPMs exist for floor exercises outside the level`() {
        val p = plan(8)
        assertEquals(StartBpms.default(Levels.byNumber(8), 180), p.exercises.first().startBpm)
    }

    @Test fun `weakness balancing works on the no-pedals plan`() {
        val r = mapOf(FLOOR_HEEL_DOWN to List(8) { FormRating.BROKEN }, FLOOR_ELEVATED_TAPS to List(8) { FormRating.CLEAN })
        assertEquals(mapOf(FLOOR_HEEL_DOWN to 7, FLOOR_ELEVATED_TAPS to 1), sets(plan(8, r)), "the stronger one keeps at least 1 set")
    }

    private val l4 = Levels.byNumber(4)
    private val mon = LocalDate.of(2026, 10, 19)

    private fun session(day: Int, type: DayType, top: Int, exercises: List<Exercise>) = SessionResult(
        mon.plusDays(day.toLong()), type, 4, false, false,
        exercises.map { ExerciseResult(it, listOf(SetResult(top - 2, FormRating.CLEAN, 2), SetResult(top, FormRating.CLEAN))) },
    )

    @Test fun `floor-only sessions don't undo a converted Speed day`() {
        val pedals = session(0, DayType.SPEED, 119, l4.exercises)
        val holiday = session(7, DayType.SPEED, 119, NoPedals.exercises(l4))
        assertTrue(CapRules.converts(l4, DayType.SPEED, listOf(pedals)))
        assertTrue(CapRules.converts(l4, DayType.SPEED, listOf(pedals, holiday)), "Pedal Normal keeps its pre-holiday result")
        assertEquals(119, CapRules.latestTop(l4, DayType.SPEED, PEDAL_NORMAL, listOf(pedals, holiday)))
    }

    @Test fun `review freezes Pedal Normal and progresses floor exercises`() {
        val l6 = Levels.byNumber(6)
        val week = listOf(SessionResult(mon, DayType.ENDURANCE, 6, false, false, NoPedals.exercises(l6).map {
            ExerciseResult(it, listOf(SetResult(140, FormRating.CLEAN, 2), SetResult(142, FormRating.CLEAN)))
        }))
        val recs = WeeklyReview.recommend(l6, 145, emptyMap(), week).filter { it.dayType == DayType.ENDURANCE }.associateBy { it.exercise }
        assertEquals(0, recs.getValue(PEDAL_NORMAL).delta)
        assertEquals(ReviewReason.NoSession, recs.getValue(PEDAL_NORMAL).reason)
        assertEquals(2, recs.getValue(FLOOR_HEEL_DOWN).delta, "Heel-Down isn't a level-6 exercise but still progresses")
        assertEquals(2, recs.getValue(FLOOR_ELEVATED_TAPS).delta)
    }

    @Test fun `return date switches the mode off and the benchmark waits for pedals`() {
        val today = LocalDate.of(2026, 10, 21)
        val profile = Profile(
            trueBpm = 112, restDays = setOf(3, 6), programStart = LocalDate.of(2026, 10, 5), cycleStart = LocalDate.of(2026, 10, 12),
            benchmarkUnlocked = true, noPedals = true, noPedalsUntil = LocalDate.of(2026, 10, 26),
        )
        val noMakeup = WeekOverrides(today.weekStart(), makeupAccepted = false)
        val cal = TrainingCalendar(today, profile, emptyList(), noMakeup)
        assertTrue(cal.day(today).noPedals)
        assertFalse(cal.day(LocalDate.of(2026, 10, 26)).noPedals, "back with pedals on the return date")
        val plan = assertNotNull(cal.buildSession(today, emptyMap()))
        assertTrue(plan.exercises.none { it.exercise == PEDAL_NORMAL })
        assertEquals(LocalDate.of(2026, 10, 26), cal.benchmarkDay, "first fresh pedal day: Monday after the Sunday rest")
        val forever = TrainingCalendar(today, profile.copy(noPedalsUntil = null), emptyList(), noMakeup)
        assertNull(forever.benchmarkDay)
    }
}
