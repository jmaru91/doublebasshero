package com.doublebasshero.engine

import com.doublebasshero.engine.FormRating.BROKEN
import com.doublebasshero.engine.FormRating.CLEAN
import com.doublebasshero.engine.FormRating.SHAKY
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProgressionTest {
    private val l4 = Levels.byNumber(4)
    private val mon = LocalDate.of(2026, 10, 12)

    private fun session(
        day: Int,
        type: DayType,
        level: Level = l4,
        sets: (Exercise) -> List<SetResult>,
    ) = SessionResult(mon.plusDays(day.toLong()), type, level.number, false, false, level.exercises.map { ExerciseResult(it, sets(it)) })

    private fun clean(vararg bpm: Int, delta: Int = 2) = bpm.mapIndexed { i, b -> SetResult(b, CLEAN, if (i < bpm.size - 1) delta else null) }

    @Test fun `weekly review rule`() {
        val week = listOf(
            session(0, DayType.SPEED) { clean(108, 110, 112) },
            session(1, DayType.HYBRID) { ex ->
                if (ex == Exercise.FLOOR_HIP) listOf(SetResult(108, BROKEN, 0), SetResult(108, BROKEN, 0), SetResult(108, SHAKY))
                else clean(108, 110, 112)
            },
            session(2, DayType.ENDURANCE) { clean(108, 112, 116, delta = 4) },
            session(4, DayType.ENDURANCE) { ex -> if (ex == Exercise.FLOOR_HEEL_UP) listOf(SetResult(108, CLEAN, 0), SetResult(108, CLEAN, 2), SetResult(110, CLEAN)) else clean(108, 112, 116, delta = 4) },
            session(5, DayType.ENDURANCE) { ex -> if (ex == Exercise.PEDAL_NORMAL) listOf(SetResult(108, CLEAN, 2), SetResult(110, SHAKY, 0), SetResult(110, SHAKY)) else clean(108, 110, 112) },
        )
        val recs = WeeklyReview.recommend(l4, 112, emptyMap(), week).associateBy { it.exercise to it.dayType }
        assertEquals(2, recs.getValue(Exercise.FLOOR_HIP to DayType.SPEED).delta)
        assertEquals(-2, recs.getValue(Exercise.FLOOR_HIP to DayType.HYBRID).delta)
        assertIs<ReviewReason.Broken>(recs.getValue(Exercise.FLOOR_HIP to DayType.HYBRID).reason)
        assertEquals(0, recs.getValue(Exercise.FLOOR_HEEL_UP to DayType.ENDURANCE).delta, "+0 choice holds back")
        assertEquals(0, recs.getValue(Exercise.PEDAL_NORMAL to DayType.ENDURANCE).delta, "shaky holds back")
        assertEquals(4, recs.getValue(Exercise.FLOOR_HIP to DayType.ENDURANCE).delta, "+4 in 2 of 3 sessions")
    }

    @Test fun `no session keeps the BPM and applying respects the cap`() {
        val recs = WeeklyReview.recommend(l4, 118, mapOf(StartKey(Exercise.FLOOR_HIP, DayType.SPEED) to 118), listOf(session(0, DayType.SPEED) { clean(118, 119, 119) }))
        assertEquals(ReviewReason.NoSession, recs.first { it.dayType == DayType.HYBRID }.reason)
        val applied = WeeklyReview.apply(emptyMap(), recs, l4.cap)
        assertEquals(119, applied[StartKey(Exercise.FLOOR_HIP, DayType.SPEED)])
    }

    @Test fun `speed converts only when every exercise hit the cap`() {
        val almost = session(0, DayType.SPEED) { ex -> if (ex == Exercise.FLOOR_HEEL_DOWN) clean(113, 115, 117) else clean(115, 117, 119) }
        assertFalse(CapRules.converts(l4, DayType.SPEED, listOf(almost)))
        val all = session(7, DayType.SPEED) { clean(115, 117, 119) }
        assertTrue(CapRules.converts(l4, DayType.SPEED, listOf(almost, all)))
        assertFalse(CapRules.converts(l4, DayType.ENDURANCE, listOf(all)))
        assertFalse(CapRules.converts(Levels.byNumber(5), DayType.SPEED, listOf(all)), "a new level starts over")
    }

    @Test fun `benchmark unlocks only with every exercise clean at the cap on Endurance`() {
        val good = listOf(session(2, DayType.ENDURANCE) { clean(115, 117, 119) }, session(4, DayType.ENDURANCE) { clean(113, 115, 117) })
        assertTrue(CapRules.benchmarkUnlocked(l4, good))
        val shaky = good + session(5, DayType.ENDURANCE) { ex -> if (ex == Exercise.PEDAL_NORMAL) listOf(SetResult(115, SHAKY), SetResult(117, SHAKY)) else clean(115, 117, 119) }
        assertFalse(CapRules.benchmarkUnlocked(l4, shaky))
        assertFalse(CapRules.benchmarkUnlocked(l4, listOf(session(2, DayType.SPEED) { clean(115, 117, 119) })))
        assertFalse(CapRules.benchmarkUnlocked(Levels.byNumber(10), good))
    }

    @Test fun `cycle with short-week restart`() {
        val start = LocalDate.of(2026, 10, 5)
        assertEquals(1, Cycle.weekIndex(start, start))
        assertEquals(3, Cycle.weekIndex(start, LocalDate.of(2026, 10, 21)))
        assertTrue(Cycle.isDeload(start, LocalDate.of(2026, 10, 28)))
        assertEquals(1, Cycle.weekIndex(start, LocalDate.of(2026, 11, 2)))
        assertEquals(LocalDate.of(2026, 10, 19), Cycle.rollover(start, LocalDate.of(2026, 10, 12), sessionsDone = 2))
        assertEquals(start, Cycle.rollover(start, LocalDate.of(2026, 10, 12), sessionsDone = 3))
        assertEquals(start, Cycle.rollover(start, LocalDate.of(2026, 10, 26), sessionsDone = 0), "deload weeks don't restart")
        assertFalse(Cycle.reviewApplies(start, LocalDate.of(2026, 10, 26), 5))
        assertFalse(Cycle.reviewApplies(start, LocalDate.of(2026, 10, 12), 2))
        assertTrue(Cycle.reviewApplies(start, LocalDate.of(2026, 10, 12), 3))
    }

    @Test fun `resume policy`() {
        val p = SessionBuilder.build(l4, DayType.ENDURANCE, DayType.ENDURANCE, 112, emptyMap(), emptyMap(), false, false)
        val t = LocalDateTime.of(2026, 10, 21, 18, 0)
        assertEquals(ResumeAction.RESUME, ResumePolicy.decide(p, t, t.plusMinutes(4)))
        assertEquals(ResumeAction.REWARM_THEN_RESUME, ResumePolicy.decide(p, t, t.plusMinutes(40)))
        assertEquals(ResumeAction.SAVE_PARTIAL, ResumePolicy.decide(p, t, t.plusHours(7)))
        assertEquals(ResumeAction.REWARM_THEN_RESUME, ResumePolicy.decide(p.copy(dayType = DayType.SPEED), t, t.plusMinutes(1)))
        assertEquals(ResumeAction.DROP_BENCHMARK, ResumePolicy.decide(SessionBuilder.benchmark(l4, 120, 112, false), t, t.plusMinutes(1)))
    }

    @Test fun `retest cannot skip the benchmark`() {
        assertTrue(Retest.accepts(119, l4, null))
        assertTrue(Retest.accepts(90, l4, null))
        assertFalse(Retest.accepts(125, l4, null))
        assertTrue(Retest.accepts(125, l4, 125))
    }

    @Test fun `battery warning`() {
        assertTrue(BatteryRule.warn(15, charging = false))
        assertFalse(BatteryRule.warn(15, charging = true))
        assertFalse(BatteryRule.warn(40, charging = false))
    }
}
