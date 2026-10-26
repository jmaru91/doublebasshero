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

/**
 * A warm-up exercise drawn as a step grid: one string per bar, one character per
 * 8th note. 'R' / 'L' = foot, '-' = rest.
 */
data class WarmupExercise(val title: String, val subtitle: String, val bars: List<String>)

object Warmup {
    /** Solo Leg 1 (two bars) and Solo Leg 2 (one bar) as written, in 16ths. */
    private val soloWritten = listOf("xxx-xxx-xxx-xxx-", "xxxxx---xxxxx---", "xxx-xxxx--xxxxx-")

    /** The warm-up plays everything in 8ths: each written bar of 16ths becomes two bars. */
    private val solo8 = soloWritten.flatMap { listOf(it.substring(0, 8), it.substring(8)) }

    val exercises: List<WarmupExercise> = listOf(
        WarmupExercise("Sticking 1", "8th notes · alternate, then four on one foot", listOf("RLRLRRRR", "LRLRLLLL")),
        WarmupExercise(
            "Sticking 2", "8th notes · alternate, then twelve on one foot",
            listOf("RLRLRRRR", "RRRRRRRR", "LRLRLLLL", "LLLLLLLL"),
        ),
        WarmupExercise("Solo leg · right", "8th notes · Solo Leg 1 + 2 · right foot only", solo8.map { it.replace('x', 'R') }),
        WarmupExercise("Solo leg · left", "8th notes · Solo Leg 1 + 2 · left foot only", solo8.map { it.replace('x', 'L') }),
    )

    /** Whole warm-up length; derived from the reference session durations. */
    const val TOTAL_SEC = 140

    val secondsPerExercise: Int get() = TOTAL_SEC / exercises.size

    /** Warm-up tempo: a relaxed 70% of the True BPM. */
    fun bpm(trueBpm: Int): Int = (trueBpm * 0.7).toInt().coerceAtLeast(40)
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
