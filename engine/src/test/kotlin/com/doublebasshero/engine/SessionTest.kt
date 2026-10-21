package com.doublebasshero.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTest {
    private val l4 = Levels.byNumber(4)

    private fun plan(type: DayType = DayType.ENDURANCE, deload: Boolean = false, ratings: Map<Exercise, List<FormRating>> = emptyMap()) =
        SessionBuilder.build(l4, type, type, 112, emptyMap(), ratings, prioritizeWeak = true, deload = deload)

    @Test fun `levels map True BPM to level`() {
        assertEquals(1, Levels.forTrueBpm(60).number)
        assertEquals(4, Levels.forTrueBpm(119).number)
        assertEquals(5, Levels.forTrueBpm(120).number)
        assertEquals(10, Levels.forTrueBpm(230).number)
        assertEquals(1, Levels.forTrueBpm(40).number)
        assertEquals(120, l4.benchmarkBpm)
    }

    @Test fun `reference session durations match the course`() {
        val expected = mapOf(1 to 2250, 6 to 2220, 7 to 2820, 8 to 1720, 9 to 2120, 10 to 2720)
        for ((n, sec) in expected) {
            val level = Levels.byNumber(n)
            val p = SessionBuilder.build(level, DayType.ENDURANCE, DayType.ENDURANCE, level.minBpm, emptyMap(), emptyMap(), false, false)
            assertEquals(sec, SessionBuilder.estimatedDurationSec(p), "level $n")
        }
    }

    @Test fun `default start BPM ends the ramp on the True BPM`() {
        val p = plan()
        assertTrue(p.exercises.all { it.startBpm == 108 })
        assertEquals(112, SessionBuilder.projectedTop(p, p.exercises.first()))
        assertEquals(Exercise.PEDAL_NORMAL, p.exercises.last().exercise)
    }

    @Test fun `deload halves sets and has no ramp choice`() {
        val p = plan(deload = true)
        assertTrue(p.exercises.all { it.sets == 2 })
        val rests = SessionRunner.steps(p).filterIsInstance<Step.Rest>()
        assertTrue(rests.all { it.nextSetOf == null })
    }

    @Test fun `speed days rest long between sets`() {
        val rests = SessionRunner.steps(plan(DayType.SPEED)).filterIsInstance<Step.Rest>()
        assertTrue(rests.none { it.kind == RestKind.SHORT })
        assertEquals(RestKind.FINAL, rests.last().kind)
    }

    @Test fun `weakness balancing doubles the weakest exercise`() {
        val r = mapOf(
            Exercise.FLOOR_HIP to List(8) { FormRating.CLEAN },
            Exercise.FLOOR_HEEL_UP to List(8) { if (it < 6) FormRating.CLEAN else FormRating.SHAKY },
            Exercise.FLOOR_HEEL_DOWN to List(8) { if (it < 2) FormRating.CLEAN else FormRating.BROKEN },
            Exercise.PEDAL_NORMAL to List(8) { FormRating.SHAKY },
        )
        val p = plan(ratings = r)
        val sets = p.exercises.associate { it.exercise to it.sets }
        assertEquals(6, sets[Exercise.FLOOR_HEEL_DOWN])
        assertEquals(3, sets[Exercise.PEDAL_NORMAL])
        assertEquals(12, sets.values.sum())
        assertTrue(p.exercises.first { it.exercise == Exercise.FLOOR_HEEL_DOWN }.weak)
    }

    @Test fun `no balancing without enough data`() {
        val p = plan(ratings = mapOf(Exercise.FLOOR_HIP to List(2) { FormRating.BROKEN }))
        assertTrue(p.exercises.all { it.sets == 3 && !it.weak })
    }

    @Test fun `runner logs sets, ratings and the ramp`() {
        var s = SessionRunner.start(plan())
        repeat(4) { assertIs<Step.Warmup>(s.step); s = SessionRunner.advance(s) }
        assertEquals(108, SessionRunner.bpm(s))
        s = SessionRunner.advance(s) // set 1 done
        s = SessionRunner.rate(s, FormRating.CLEAN)
        assertEquals(110, SessionRunner.nextBpm(s))
        s = SessionRunner.chooseNext(s, 4)
        s = SessionRunner.advance(s) // rest done
        assertEquals(112, SessionRunner.bpm(s))
        s = SessionRunner.advance(s) // set 2 done
        s = SessionRunner.rate(s, FormRating.SHAKY)
        assertEquals(112, SessionRunner.nextBpm(s), "shaky preselects +0")
        s = SessionRunner.advance(s)
        val sets = s.sets.getValue(Exercise.FLOOR_HIP)
        assertEquals(listOf(108, 112), sets.map { it.bpm })
        assertEquals(listOf(4, 0), sets.map { it.rampDelta })
        assertEquals(listOf(FormRating.CLEAN, FormRating.SHAKY), sets.map { it.rating })
    }

    @Test fun `ramp never goes above the cap`() {
        val p = SessionBuilder.build(l4, DayType.ENDURANCE, DayType.ENDURANCE, 119, mapOf(StartKey(Exercise.FLOOR_HIP, DayType.ENDURANCE) to 118), emptyMap(), false, false)
        var s = SessionRunner.start(p)
        repeat(5) { s = SessionRunner.advance(s) }
        s = SessionRunner.chooseNext(s, 4)
        assertEquals(119, SessionRunner.nextBpm(s))
    }

    @Test fun `snapshot survives serialization and resumes with a re-warm-up`() {
        var s = SessionRunner.start(plan())
        repeat(6) { s = SessionRunner.advance(s) }
        val json = kotlinx.serialization.json.Json.encodeToString(RunnerState.serializer(), s)
        val back = kotlinx.serialization.json.Json.decodeFromString(RunnerState.serializer(), json)
        assertEquals(s, back)
        val resumed = SessionRunner.resume(back, rewarm = true)
        assertIs<Step.Warmup>(resumed.step)
        assertEquals(s.steps.size + 4, resumed.steps.size)
        assertEquals(1, resumed.setsDone)
    }

    @Test fun `resume rules for the warm-up and for rests`() {
        var s = SessionRunner.start(plan())
        s = SessionRunner.advance(s) // into Sticking 2
        val restarted = SessionRunner.resume(s, rewarm = true)
        assertEquals(0, restarted.index, "an interrupted warm-up starts over")
        assertEquals(s.steps.size, restarted.steps.size, "no second warm-up")
        repeat(4) { s = SessionRunner.advance(s) } // set 1 done, now resting
        assertIs<Step.Rest>(s.step)
        val r = SessionRunner.resume(s, rewarm = true)
        assertIs<Step.Rest>(r.step, "the rest comes first")
        assertIs<Step.Warmup>(r.steps[r.index + 1], "then the re-warm-up, before set 2")
    }

    @Test fun `benchmark session is warm-up plus 4 minutes`() {
        val p = SessionBuilder.benchmark(l4, 120, 112, placement = false)
        val steps = SessionRunner.steps(p)
        assertEquals(5, steps.size)
        assertEquals(Step.BenchmarkTest(120), steps.last())
        assertEquals(1, SessionRunner.steps(SessionBuilder.benchmark(l4, 112, 112, placement = true)).size)
    }

    @Test fun `warm-up grids are in 8ths`() {
        assertTrue(Warmup.exercises.all { ex -> ex.bars.all { it.length == 8 } })
        assertEquals(listOf("RRR-RRR-", "RRR-RRR-", "RRRRR---", "RRRRR---", "RRR-RRRR", "--RRRRR-"), Warmup.exercises[2].bars)
    }

    @Test fun `rating average thresholds`() {
        assertEquals(FormRating.CLEAN, Ratings.average(listOf(FormRating.CLEAN, FormRating.CLEAN, FormRating.SHAKY)))
        assertEquals(FormRating.SHAKY, Ratings.average(listOf(FormRating.CLEAN, FormRating.BROKEN)))
        assertEquals(FormRating.BROKEN, Ratings.average(listOf(FormRating.SHAKY, FormRating.BROKEN, FormRating.BROKEN)))
        assertNull(Ratings.average(emptyList()))
    }
}
