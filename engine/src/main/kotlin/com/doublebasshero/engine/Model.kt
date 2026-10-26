package com.doublebasshero.engine

import kotlinx.serialization.Serializable

/** What a calendar day is for. BENCHMARK is a Speed/Endurance slot used for the level test. */
@Serializable
enum class DayType(val letter: Char, val title: String) {
    SPEED('S', "Speed"),
    HYBRID('H', "Hybrid"),
    ENDURANCE('E', "Endurance"),
    REST('R', "Rest"),
    BENCHMARK('B', "Benchmark");

    val isTraining: Boolean get() = this != REST

    companion object {
        /** Day types with their own start BPMs and weekly progression. */
        val PROGRESSING = listOf(SPEED, HYBRID, ENDURANCE)
    }
}

@Serializable
enum class Exercise(val title: String, val description: String) {
    FLOOR_HIP("Floor Hip", "Trains the upper legs"),
    FLOOR_HEEL_UP("Floor Heel-Up", "Calf · the slow rotation"),
    FLOOR_HEEL_DOWN("Floor Heel-Down", "Calf and shin · the fast rotation, feet planted"),
    FLOOR_ELEVATED_TAPS("Floor Elevated Taps", "Elevated ankle rotation, off the floor"),
    PEDAL_ELEVATED_TAPS("Pedal Elevated Taps", "Elevated ankle rotation, on the pedals"),
    PEDAL_NORMAL("Pedal Normal", "Full technique in normal playing position"),
}

enum class Quest(val number: Int, val title: String) {
    PERFECT_FORM(1, "Perfect Form"),
    FLOOR_TRIO(2, "The Floor Trio"),
    ANKLE_PATH(3, "4-Stage Ankle Path"),
    ENDGAME(4, "The Endgame"),
}

data class Level(
    val number: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val quest: Quest,
    val exercises: List<Exercise>,
    val setsPerExercise: Int,
    val setDurationSec: Int,
) {
    /** No set ever goes above the cap. */
    val cap: Int get() = maxBpm

    /** Passing 4:00 of non-stop 16ths at this BPM moves to the next level. */
    val benchmarkBpm: Int get() = maxBpm + 1

    val isTop: Boolean get() = number == Levels.all.last().number
}

object Levels {
    private val floorTrio = listOf(Exercise.FLOOR_HIP, Exercise.FLOOR_HEEL_UP, Exercise.FLOOR_HEEL_DOWN, Exercise.PEDAL_NORMAL)
    private val ankle = listOf(Exercise.FLOOR_ELEVATED_TAPS, Exercise.PEDAL_ELEVATED_TAPS, Exercise.PEDAL_NORMAL)
    private val pedal = listOf(Exercise.PEDAL_NORMAL)

    val all: List<Level> = listOf(
        Level(1, 60, 69, Quest.PERFECT_FORM, floorTrio, 3, 120),
        Level(2, 70, 79, Quest.PERFECT_FORM, floorTrio, 3, 120),
        Level(3, 80, 99, Quest.PERFECT_FORM, floorTrio, 3, 120),
        Level(4, 100, 119, Quest.FLOOR_TRIO, floorTrio, 3, 120),
        Level(5, 120, 139, Quest.FLOOR_TRIO, floorTrio, 3, 120),
        Level(6, 140, 159, Quest.ANKLE_PATH, ankle, 3, 180),
        Level(7, 160, 179, Quest.ANKLE_PATH, ankle, 4, 180),
        Level(8, 180, 199, Quest.ENDGAME, pedal, 8, 180),
        Level(9, 200, 209, Quest.ENDGAME, pedal, 10, 180),
        Level(10, 210, 220, Quest.ENDGAME, pedal, 10, 240),
    )

    fun forTrueBpm(bpm: Int): Level =
        all.firstOrNull { bpm in it.minBpm..it.maxBpm } ?: if (bpm < all.first().minBpm) all.first() else all.last()

    fun byNumber(number: Int): Level = all.first { it.number == number }
}

/** For periods without pedals (e.g. holidays): floor exercises only. */
object NoPedals {
    fun exercises(level: Level): List<Exercise> =
        if (level.number <= 5) listOf(Exercise.FLOOR_HIP, Exercise.FLOOR_HEEL_UP, Exercise.FLOOR_HEEL_DOWN)
        else listOf(Exercise.FLOOR_HEEL_DOWN, Exercise.FLOOR_ELEVATED_TAPS)

    /**
     * Pedal Normal's sets are spread over the floor exercises, so the session keeps
     * its length. Extra sets go to the more advanced (later) exercises.
     */
    fun baseSets(level: Level): Map<Exercise, Int> {
        val ex = exercises(level)
        val total = level.exercises.size * level.setsPerExercise
        val extra = total % ex.size
        return ex.withIndex().associate { (i, e) -> e to total / ex.size + if (i >= ex.size - extra) 1 else 0 }
    }
}

/** What one bar of the metronome pattern asks the player to do. */
enum class BarKind { SIXTEENTHS, EIGHTHS, REST }

object Patterns {
    fun forDay(type: DayType): List<BarKind> = when (type) {
        DayType.SPEED -> listOf(BarKind.SIXTEENTHS, BarKind.REST, BarKind.REST, BarKind.REST)
        DayType.HYBRID -> listOf(BarKind.EIGHTHS, BarKind.SIXTEENTHS)
        DayType.ENDURANCE, DayType.BENCHMARK -> listOf(BarKind.SIXTEENTHS)
        DayType.REST -> emptyList()
    }
}

@Serializable
enum class FormRating(val score: Int, val title: String) {
    CLEAN(2, "Clean"),
    SHAKY(1, "Shaky"),
    BROKEN(0, "Broken"),
}

@Serializable
enum class Foot(val title: String) { RIGHT("Right"), LEFT("Left") }

/**
 * A warm-up exercise: one string per bar, one character per 8th note.
 * Feet: 'R' / 'L' = foot, '-' = rest. Hands (as written in the PDF, null when the
 * exercise is feet only): [hats] 'x' = hi-hat, [snare] 's' = snare.
 * [foot] is set for the one-foot exercises. [basePasses] = whole passes of the pattern.
 */
data class WarmupExercise(
    val title: String,
    val subtitle: String,
    val bars: List<String>,
    val foot: Foot? = null,
    val hats: List<String>? = null,
    val snare: List<String>? = null,
    val basePasses: Int = Warmup.defaultPasses(bars.size),
) {
    /** The weaker foot's Solo Leg gets one extra pass. */
    fun passes(weakFoot: Foot? = null): Int = basePasses + if (weakFoot != null && foot == weakFoot) 1 else 0
}

object Warmup {
    /** Solo Leg 1 (two bars) and Solo Leg 2 (one bar) as written, in 16ths. */
    private val solo1Written = listOf("xxx-xxx-xxx-xxx-", "xxxxx---xxxxx---")
    private val solo2Written = listOf("xxx-xxxx-xx-xxx-")

    /** Hands in the PDF's Solo Leg: hi-hat in 8ths, snare on beats 2 and 4 (16th grid). */
    private const val SOLO_HATS = "x-x-x-x-x-x-x-x-"
    private const val SOLO_SNARE = "----s-------s---"

    /** Each exercise plays whole passes of its pattern, at least this many bars. */
    const val MIN_BARS = 8

    /**
     * Solo Leg alternates between days. The one of the day plays 16 bars on the right
     * foot and a bit more, 24 bars, on the left: Solo Leg 1 (4 bars) 4 / 6 passes,
     * Solo Leg 2 (2 bars) 8 / 12 passes.
     */
    fun soloBars(foot: Foot): Int = if (foot == Foot.LEFT) 24 else 16

    /** At least 2 passes and at least [MIN_BARS] bars, never cut mid-pattern. */
    fun defaultPasses(bars: Int): Int = maxOf(2, (MIN_BARS + bars - 1) / bars)

    /** The warm-up plays everything in 8ths: each written bar of 16ths becomes two bars. */
    private fun to8ths(written: List<String>) = written.flatMap { listOf(it.substring(0, 8), it.substring(8)) }

    private val stickings = listOf(
        WarmupExercise("Sticking 1", "8th notes · alternate, then four on one foot", listOf("RLRLRRRR", "LRLRLLLL")),
        WarmupExercise(
            "Sticking 2", "8th notes · alternate, then twelve on one foot",
            listOf("RLRLRRRR", "RRRRRRRR", "LRLRLLLL", "LLLLLLLL"),
        ),
    )

    /**
     * The warm-up of a day. [soloLeg] = 1 or 2: that Solo Leg only, with double the
     * passes (they alternate between training days); 0 = both, as in sessions saved
     * by older versions.
     */
    fun exercises(soloLeg: Int = 0): List<WarmupExercise> {
        val written = when (soloLeg) { 1 -> solo1Written; 2 -> solo2Written; else -> solo1Written + solo2Written }
        val bars = to8ths(written)
        val hats = to8ths(List(written.size) { SOLO_HATS })
        val snare = to8ths(List(written.size) { SOLO_SNARE })
        val name = if (soloLeg in 1..2) "Solo Leg $soloLeg" else "Solo Leg 1 + 2"
        fun solo(foot: Foot) = WarmupExercise(
            "$name · ${foot.title.lowercase()}", "8th notes · ${foot.title.lowercase()} foot only",
            bars.map { it.replace('x', if (foot == Foot.RIGHT) 'R' else 'L') }, foot, hats, snare,
            if (soloLeg in 1..2) soloBars(foot) / bars.size else defaultPasses(bars.size),
        )
        return stickings + solo(Foot.RIGHT) + solo(Foot.LEFT)
    }

    /** Solo Leg 1 and 2 alternate: [sessionsBefore] = training sessions already done. */
    fun soloLegFor(sessionsBefore: Int): Int = sessionsBefore % 2 + 1

    /**
     * Warm-up metronome tempo (quarter-note clicks; the notes are 8ths, two per
     * click), as a share of the True BPM: the stickings ramp up (65% → 75%), both
     * Solo Leg feet play at the same 85%.
     */
    private val TEMPO = listOf(0.65, 0.75, 0.85, 0.85)

    const val MIN_BPM = 40

    fun bpm(trueBpm: Int, index: Int): Int = (trueBpm * TEMPO[index]).toInt().coerceAtLeast(MIN_BPM)

    /** Exact length of an exercise in ms (count-in excluded): passes × bars × 4 beats. */
    fun millis(ex: WarmupExercise, bpm: Int, weakFoot: Foot? = null): Long =
        ex.passes(weakFoot) * ex.bars.size * 4 * 60_000L / bpm
}

/** Rest hints. They are suggestions: nothing starts on its own. */
object Rests {
    const val SHORT_SEC = 20
    const val LONG_SEC = 170
}

/** The benchmark: 4:00 of non-stop 16ths with clean form. */
object Benchmark {
    const val DURATION_SEC = 240
}

/** BPM steps between sets of the same exercise, set by the ratings. */
object Ramp {
    /** After a Clean (or unrated) set. */
    const val DEFAULT_STEP = 2

    /** After two Clean sets in a row: strong enough to push. */
    const val STRONG_STEP = 4
}
