package com.doublebasshero.engine

/** Days of a week are indexed 0 = Monday … 6 = Sunday. */
object WeekPlanner {
    /** Every week: 1 Speed, 1 Hybrid, 3 Endurance, in this order. */
    val SEQUENCE = listOf(DayType.SPEED, DayType.HYBRID, DayType.ENDURANCE, DayType.ENDURANCE, DayType.ENDURANCE)

    /**
     * When a week can't hold everything: extra Endurance days go first, then Speed,
     * then Hybrid; one Endurance day is kept to the end.
     * 5 → S H E E E, 4 → S H E E, 3 → S H E, 2 → H E, 1 → E.
     */
    private fun dropOne(needed: MutableList<DayType>) {
        val drop = when {
            needed.count { it == DayType.ENDURANCE } > 1 -> DayType.ENDURANCE
            DayType.SPEED in needed -> DayType.SPEED
            DayType.HYBRID in needed -> DayType.HYBRID
            else -> DayType.ENDURANCE
        }
        needed.removeAt(needed.lastIndexOf(drop))
    }

    /**
     * Recommends the default week from the user's two usual days off:
     * Speed right after a rest day, then Speed → Hybrid → first Endurance on
     * consecutive days, the remaining Endurance days on the other training days.
     */
    fun recommend(restDays: Set<Int>): List<DayType> {
        require(restDays.size == 2 && restDays.all { it in 0..6 }) { "Exactly two rest days in 0..6" }
        val training = (0..6).filter { it !in restDays }
        var best: List<Int>? = null
        var bestOk = false
        for (r in restDays.sorted()) {
            val start = (r + 1) % 7
            if (start in restDays) continue
            val order = (0..6).map { (start + it) % 7 }.filter { it in training }
            val ok = (order[1] - order[0] + 7) % 7 == 1 && (order[2] - order[1] + 7) % 7 == 1
            if (best == null || (ok && !bestOk)) {
                best = order
                bestOk = ok
            }
        }
        val week = MutableList(7) { DayType.REST }
        (best ?: training).forEachIndexed { i, d -> week[d] = SEQUENCE[i] }
        return week
    }

    /**
     * Re-plans the current week around what already happened.
     *
     * - Days before [todayIndex] without a session were missed: they count as rest.
     * - [skipped] and [unavailable] days count as rest too.
     * - If a usual day off could replace a lost session (keeping at least 2 rest
     *   days), it is offered as a make-up day. [makeupAccepted] null = still to be
     *   decided (shown tentatively), true = included, false = declined.
     * - If the week still can't hold every session, sessions are dropped by
     *   priority (see [dropOne]).
     *
     * [done] maps a day to the week slot its session used (the planned type
     * before any Speed/Hybrid → Endurance conversion). Days before
     * [firstDayIndex] are before the program started: plain rest.
     */
    fun replan(
        base: List<DayType>,
        todayIndex: Int,
        done: Map<Int, DayType>,
        skipped: Set<Int> = emptySet(),
        unavailable: Set<Int> = emptySet(),
        makeupAccepted: Boolean? = null,
        firstDayIndex: Int = 0,
    ): WeekPlan {
        require(base.size == 7)
        val days = arrayOfNulls<PlannedDay>(7)
        val doneSlots = mutableListOf<DayType>()
        var restCount = 0
        for (i in 0..6) {
            days[i] = when {
                i in done -> PlannedDay(done.getValue(i), DayState.DONE).also { doneSlots += done.getValue(i) }
                i < firstDayIndex -> PlannedDay(DayType.REST, DayState.REST).also { restCount++ }
                i in skipped -> PlannedDay(DayType.REST, DayState.SKIPPED).also { restCount++ }
                i < todayIndex && base[i] == DayType.REST -> PlannedDay(DayType.REST, DayState.REST).also { restCount++ }
                i < todayIndex -> PlannedDay(DayType.REST, DayState.SKIPPED).also { restCount++ }
                i in unavailable -> PlannedDay(DayType.REST, DayState.UNAVAILABLE).also { restCount++ }
                else -> null
            }
        }
        val needed = SEQUENCE.toMutableList()
        for (slot in doneSlots) {
            val j = needed.indexOf(if (slot == DayType.BENCHMARK) DayType.SPEED else slot)
            if (j >= 0) needed.removeAt(j) else needed.lastIndexOf(DayType.ENDURANCE).takeIf { it >= 0 }?.let { needed.removeAt(it) }
        }
        val free = (0..6).filter { days[it] == null }
        val trainingFree = free.filter { base[it] != DayType.REST }
        val offFree = free.filter { base[it] == DayType.REST }
        val extraAllowed = maxOf(0, restCount + offFree.size - 2)
        val short = maxOf(0, needed.size - trainingFree.size)
        val candidates = offFree.take(minOf(extraAllowed, short))
        val makeup = if (makeupAccepted == false) emptyList() else candidates
        val slots = (trainingFree + makeup).sorted()
        while (needed.size > slots.size) dropOne(needed)
        slots.forEachIndexed { j, d ->
            days[d] = PlannedDay(needed[j], if (d in makeup) DayState.MAKEUP else DayState.PLANNED)
        }
        for (i in free) if (days[i] == null) days[i] = PlannedDay(DayType.REST, DayState.REST)
        val list = days.map { it!! }
        return WeekPlan(
            days = list,
            makeupCandidates = candidates,
            makeupPending = candidates.isNotEmpty() && makeupAccepted == null,
            sessionCount = list.count { it.type.isTraining },
        )
    }
}

enum class DayState { DONE, SKIPPED, UNAVAILABLE, REST, PLANNED, MAKEUP }

data class PlannedDay(val type: DayType, val state: DayState)

data class WeekPlan(
    val days: List<PlannedDay>,
    /** Usual days off that could make up for lost sessions. */
    val makeupCandidates: List<Int>,
    /** The make-up suggestion still needs an answer. */
    val makeupPending: Boolean,
    /** Sessions done + still planned. Fewer than 3 = the week becomes a deload. */
    val sessionCount: Int,
)
