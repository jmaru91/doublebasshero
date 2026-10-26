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
        // The course's totals include a 2:20 warm-up; ours is measured in whole passes instead.
        val expected = mapOf(1 to 2250, 6 to 2220, 7 to 2820, 8 to 1720, 9 to 2120, 10 to 2720)
        for ((n, sec) in expected) {
            val level = Levels.byNumber(n)
            val p = SessionBuilder.build(level, DayType.ENDURANCE, DayType.ENDURANCE, level.minBpm, emptyMap(), emptyMap(), false, false)
            val noWarmup = SessionRunner.steps(p).filter { it !is Step.Warmup }.sumOf { SessionRunner.hintSeconds(p, it) }
            assertEquals(sec - 140, noWarmup, "level $n")
        }
    }

    @Test fun `Solo Leg keeps the PDF's hands, stretched to 8ths`() {
        val solo = Warmup.exercises(0)[2]
        assertEquals(List(6) { "x-x-x-x-" }, solo.hats, "hi-hat on every beat")
        assertEquals(List(6) { "----s---" }, solo.snare, "snare on beat 3 of each bar (beats 2 and 4 as written)")
        assertEquals(null, Warmup.exercises(1)[0].hats, "stickings are feet only, as in the PDF")
    }

    @Test fun `Solo Leg 1 and 2 alternate, 16 bars right and 24 left`() {
        val one = Warmup.exercises(1)
        val two = Warmup.exercises(2)
        assertEquals(listOf("RRR-RRR-", "RRR-RRR-", "RRRRR---", "RRRRR---"), one[2].bars)
        assertEquals(listOf("RRR-RRRR", "-RR-RRR-"), two[2].bars)
        assertEquals("Solo Leg 2 · left", two[3].title)
        assertEquals(listOf(4, 6), listOf(one[2].passes(), one[3].passes()), "Solo Leg 1: 16 bars right, 24 left")
        assertEquals(listOf(8, 12), listOf(two[2].passes(), two[3].passes()), "Solo Leg 2 is half as long, so twice the passes")
        assertEquals(13, two[3].passes(Foot.LEFT), "the weaker foot still gets an extra pass")
        assertEquals(listOf(1, 2, 1, 2), (0..3).map { Warmup.soloLegFor(it) })
    }

    @Test fun `warm-up plays whole passes with a tempo ramp`() {
        assertEquals(listOf(4, 2), Warmup.exercises(1).take(2).map { it.passes() }, "stickings: at least 8 bars, at least 2 passes")
        val bpms = (0..3).map { Warmup.bpm(117, it) }
        assertEquals(listOf(76, 87, 99, 99), bpms, "quarter clicks: stickings 65% → 75%, both Solo Leg feet at 85%")
        assertEquals(40, Warmup.bpm(60, 0), "never below 40")
        assertEquals(16 * 4 * 60_000L / 99, Warmup.millis(Warmup.exercises(1)[2], 99), "Solo Leg 1 right: 4 passes × 4 bars × 4 beats (8ths, two per click)")
        val p = plan()
        assertEquals(1, p.soloLeg)
        assertEquals((0..3).map { Warmup.bpm(112, it) }, (0..3).map { SessionRunner.bpm(SessionRunner.start(p), Step.Warmup(it)) })
        val total = (0..3).sumOf { SessionRunner.hintSeconds(p, Step.Warmup(it)) }
        assertTrue(total in 100..240, "about two to four minutes, was $total s")
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
        assertEquals(110, SessionRunner.nextBpm(s), "an unrated set counts as clean")
        s = SessionRunner.rate(s, FormRating.CLEAN)
        assertEquals(110, SessionRunner.nextBpm(s))
        s = SessionRunner.advance(s) // rest done
        assertEquals(110, SessionRunner.bpm(s))
        s = SessionRunner.advance(s) // set 2 done
        s = SessionRunner.rate(s, FormRating.SHAKY)
        assertEquals(110, SessionRunner.nextBpm(s), "shaky holds the BPM")
        s = SessionRunner.advance(s)
        val sets = s.sets.getValue(Exercise.FLOOR_HIP)
        assertEquals(listOf(108, 110), sets.map { it.bpm })
        assertEquals(listOf(2, 0), sets.map { it.rampDelta })
        assertEquals(listOf(FormRating.CLEAN, FormRating.SHAKY), sets.map { it.rating })
    }

    @Test fun `two clean sets in a row earn +4`() {
        var s = SessionRunner.start(plan(DayType.HYBRID))
        repeat(5) { s = SessionRunner.advance(s) } // warm-up, set 1
        s = SessionRunner.rate(s, FormRating.CLEAN)
        assertEquals(110, SessionRunner.nextBpm(s), "first clean set: +2")
        repeat(2) { s = SessionRunner.advance(s) } // set 2
        s = SessionRunner.rate(s, FormRating.CLEAN)
        assertEquals(114, SessionRunner.nextBpm(s), "second clean in a row: +4")
        s = SessionRunner.rate(s, FormRating.SHAKY)
        assertEquals(110, SessionRunner.nextBpm(s), "changing the rating changes the step")
    }

    @Test fun `ramp never goes above the cap`() {
        val p = SessionBuilder.build(l4, DayType.ENDURANCE, DayType.ENDURANCE, 119, mapOf(StartKey(Exercise.FLOOR_HIP, DayType.ENDURANCE) to 118), emptyMap(), false, false)
        var s = SessionRunner.start(p)
        repeat(5) { s = SessionRunner.advance(s) }
        s = SessionRunner.rate(s, FormRating.CLEAN)
        assertEquals(119, SessionRunner.nextBpm(s), "118 + 2 is capped at 119")
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
        assertTrue((0..2).all { v -> Warmup.exercises(v).all { ex -> ex.bars.all { it.length == 8 } } })
        assertEquals(listOf("RRR-RRR-", "RRR-RRR-", "RRRRR---", "RRRRR---", "RRR-RRRR", "-RR-RRR-"), Warmup.exercises(0)[2].bars)
    }

    @Test fun `rating average thresholds`() {
        assertEquals(FormRating.CLEAN, Ratings.average(listOf(FormRating.CLEAN, FormRating.CLEAN, FormRating.SHAKY)))
        assertEquals(FormRating.SHAKY, Ratings.average(listOf(FormRating.CLEAN, FormRating.BROKEN)))
        assertEquals(FormRating.BROKEN, Ratings.average(listOf(FormRating.SHAKY, FormRating.BROKEN, FormRating.BROKEN)))
        assertNull(Ratings.average(emptyList()))
    }
}
