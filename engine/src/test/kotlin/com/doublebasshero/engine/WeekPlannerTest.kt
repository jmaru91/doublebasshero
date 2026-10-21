package com.doublebasshero.engine

import com.doublebasshero.engine.DayType.ENDURANCE
import com.doublebasshero.engine.DayType.HYBRID
import com.doublebasshero.engine.DayType.REST
import com.doublebasshero.engine.DayType.SPEED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekPlannerTest {
    private fun letters(days: List<DayType>) = days.map { it.letter }.joinToString("")
    private fun letters(plan: WeekPlan) = plan.days.map { it.type.letter }.joinToString("")

    @Test fun `rest Thu and Sun gives the ideal week`() =
        assertEquals("SHEREER", letters(WeekPlanner.recommend(setOf(3, 6))))

    @Test fun `rest Sat and Sun gives the simple week`() =
        assertEquals("SHEEERR", letters(WeekPlanner.recommend(setOf(5, 6))))

    @Test fun `rest Mon and Fri starts Speed on Tuesday`() =
        assertEquals("RSHERE" + "E", letters(WeekPlanner.recommend(setOf(0, 4))))

    @Test fun `every recommended week has 1 S, 1 H, 3 E`() {
        for (a in 0..6) for (b in 0..6) if (a < b) {
            val w = WeekPlanner.recommend(setOf(a, b))
            assertEquals(1, w.count { it == SPEED })
            assertEquals(1, w.count { it == HYBRID })
            assertEquals(3, w.count { it == ENDURANCE })
            val s = w.indexOf(SPEED)
            assertEquals(REST, w[(s + 6) % 7], "Speed after a rest day for rest $a,$b")
        }
    }

    private val ideal = WeekPlanner.recommend(setOf(3, 6))

    @Test fun `nothing changed keeps the plan`() {
        val p = WeekPlanner.replan(ideal, todayIndex = 2, done = mapOf(0 to SPEED, 1 to HYBRID))
        assertEquals("SHEREER", letters(p))
        assertFalse(p.makeupPending)
        assertEquals(5, p.sessionCount)
    }

    @Test fun `skipping today suggests Thursday as make-up`() {
        val p = WeekPlanner.replan(ideal, todayIndex = 2, done = mapOf(0 to SPEED, 1 to HYBRID), skipped = setOf(2))
        assertEquals(listOf(3), p.makeupCandidates)
        assertTrue(p.makeupPending)
        assertEquals("SHREEER", letters(p))
        assertEquals(DayState.MAKEUP, p.days[3].state)
    }

    @Test fun `declining the make-up drops one Endurance`() {
        val p = WeekPlanner.replan(ideal, 2, mapOf(0 to SPEED, 1 to HYBRID), skipped = setOf(2), makeupAccepted = false)
        assertEquals("SHRREER", letters(p))
        assertEquals(4, p.sessionCount)
    }

    @Test fun `missing Monday moves Speed to Tuesday and uses Thursday`() {
        val p = WeekPlanner.replan(ideal, todayIndex = 1, done = emptyMap(), makeupAccepted = true)
        assertEquals("RSHEEER", letters(p))
        assertEquals(DayState.SKIPPED, p.days[0].state)
    }

    @Test fun `busy week drops Endurance, then Speed, then Hybrid`() {
        fun weekWith(unavailable: Set<Int>) =
            letters(WeekPlanner.replan(ideal, 0, emptyMap(), unavailable = unavailable, makeupAccepted = false).days.map { it.type }.filter { it.isTraining })
        assertEquals("SHEE", weekWith(setOf(4)))
        assertEquals("SHE", weekWith(setOf(4, 5)))
        assertEquals("HE", weekWith(setOf(0, 4, 5)))
        assertEquals("E", weekWith(setOf(0, 1, 4, 5)))
    }

    @Test fun `days before program start are plain rest`() {
        val p = WeekPlanner.replan(ideal, todayIndex = 3, done = emptyMap(), makeupAccepted = true, firstDayIndex = 3)
        assertTrue(p.days.take(3).all { it.state == DayState.REST })
        assertEquals("RRRSHEE", letters(p))
    }
}
