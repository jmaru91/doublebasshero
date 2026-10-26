package com.doublebasshero.data

import com.doublebasshero.engine.CapRules
import com.doublebasshero.engine.Cycle
import com.doublebasshero.engine.DayType
import com.doublebasshero.engine.Exercise
import com.doublebasshero.engine.ExerciseResult
import com.doublebasshero.engine.Levels
import com.doublebasshero.engine.NotificationDay
import com.doublebasshero.engine.NotificationPrefs
import com.doublebasshero.engine.NotificationType
import com.doublebasshero.engine.Profile
import com.doublebasshero.engine.Recommendation
import com.doublebasshero.engine.RunnerState
import com.doublebasshero.engine.SavedSession
import com.doublebasshero.engine.SessionBuilder
import com.doublebasshero.engine.SessionResult
import com.doublebasshero.engine.SetResult
import com.doublebasshero.engine.StartKey
import com.doublebasshero.engine.TrainingCalendar
import com.doublebasshero.engine.WeekOverrides
import com.doublebasshero.engine.WeeklyReview
import com.doublebasshero.engine.dayIndex
import com.doublebasshero.engine.weekStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private val setsSerializer = MapSerializer(Exercise.serializer(), ListSerializer(SetResult.serializer()))

fun encodeSets(sets: Map<Exercise, List<SetResult>>): String = json.encodeToString(setsSerializer, sets)
fun decodeSets(s: String): Map<Exercise, List<SetResult>> = runCatching { json.decodeFromString(setsSerializer, s) }.getOrDefault(emptyMap())
fun encodeRunner(r: RunnerState): String = json.encodeToString(RunnerState.serializer(), r)
fun decodeRunner(s: String): RunnerState? = runCatching { json.decodeFromString(RunnerState.serializer(), s) }.getOrNull()

fun LocalDate.epoch(): Long = toEpochDay()
fun day(epoch: Long): LocalDate = LocalDate.ofEpochDay(epoch)

private fun csvDays(s: String): Set<LocalDate> = s.split(',').filter { it.isNotBlank() }.map { day(it.toLong()) }.toSet()
private fun Set<LocalDate>.csv(): String = joinToString(",") { it.epoch().toString() }

fun ProfileEntity.toProfile() = Profile(
    trueBpm = trueBpm,
    restDays = setOf(restDay1, restDay2),
    programStart = day(programStart),
    cycleStart = day(cycleStart),
    benchmarkUnlocked = benchmarkUnlocked,
    prioritizeWeak = prioritizeWeak,
    noPedals = noPedals,
    noPedalsUntil = noPedalsUntil.takeIf { it >= 0 }?.let { day(it) },
    weakFoot = com.doublebasshero.engine.Foot.entries.firstOrNull { it.name == weakFoot },
)

fun ProfileEntity.notificationPrefs() = NotificationPrefs(
    reminderTimes = reminderMinutes.split(',').map { LocalTime.of(it.trim().toInt() / 60, it.trim().toInt() % 60) },
    missedTime = LocalTime.of(missedMinutes / 60, missedMinutes % 60),
    enabled = NotificationType.entries.toSet() - disabledNotifications.split(',').mapNotNull { n -> NotificationType.entries.firstOrNull { it.name == n } }.toSet(),
    restCue = restCue,
    voicePrompts = voicePrompts,
)

fun SessionEntity.toResult() = SessionResult(
    date = day(date),
    dayType = DayType.valueOf(dayType),
    level = level,
    deload = deload,
    partial = status == SessionStatus.PARTIAL,
    exercises = decodeSets(setsJson).map { (ex, sets) -> ExerciseResult(ex, sets) },
)

fun SessionEntity.toSaved() = SavedSession(DayType.valueOf(slot), toResult())

/** Everything the screens need, recomputed whenever the database changes. */
data class AppData(
    val today: LocalDate,
    val profile: ProfileEntity,
    val calendar: TrainingCalendar,
    val startBpms: Map<StartKey, Int>,
    val sessions: List<SessionEntity>,
    val inProgress: SessionEntity?,
    val override: WeekOverrideEntity?,
    val levelEvents: List<LevelEventEntity>,
) {
    val level get() = calendar.level

    /** Recommendations for the ended week waiting for review, or null. */
    val review: Pair<LocalDate, List<Recommendation>>? by lazy {
        if (profile.pendingReviewWeek < 0) return@lazy null
        val week = day(profile.pendingReviewWeek)
        week to WeeklyReview.recommend(level, profile.trueBpm, startBpms, calendar.sessionsIn(week))
    }

    fun sessionOn(date: LocalDate): SessionEntity? = sessions.lastOrNull { it.date == date.epoch() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class Repository(private val dao: TrainingDao) {
    private val todayFlow = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = todayFlow

    /** Call on resume and around midnight. */
    fun refreshToday() {
        todayFlow.value = LocalDate.now()
    }

    val data: Flow<AppData?> = todayFlow.flatMapLatest { today ->
        combine(
            dao.profile(),
            dao.sessions(),
            dao.startBpms(),
            dao.override(today.weekStart().epoch()),
            combine(dao.inProgress(), dao.levelEvents()) { a, b -> a to b },
        ) { profile, sessions, starts, override, (inProgress, events) ->
            if (profile == null || !profile.onboarded) return@combine null
            buildData(today, profile, sessions, starts, override, inProgress, events)
        }
    }

    val profileFlow: Flow<ProfileEntity?> = dao.profile()

    private fun buildData(
        today: LocalDate,
        profile: ProfileEntity,
        sessions: List<SessionEntity>,
        starts: List<StartBpmEntity>,
        override: WeekOverrideEntity?,
        inProgress: SessionEntity?,
        events: List<LevelEventEntity>,
    ): AppData {
        val ov = override?.let {
            WeekOverrides(
                weekStart = day(it.weekStart),
                skipped = csvDays(it.skipped),
                unavailable = csvDays(it.unavailable),
                makeupAccepted = when (it.makeup) { 1 -> true; 0 -> false; else -> null },
            )
        }
        val calendar = TrainingCalendar(today, profile.toProfile(), sessions.map { it.toSaved() }, ov)
        val startMap = starts.associate { StartKey(Exercise.valueOf(it.exercise), DayType.valueOf(it.dayType)) to it.bpm }
        return AppData(today, profile, calendar, startMap, sessions, inProgress, override, events)
    }

    suspend fun snapshot(): AppData? {
        val profile = dao.profileNow() ?: return null
        if (!profile.onboarded) return null
        val today = LocalDate.now()
        return buildData(
            today, profile, dao.sessionsNow(), dao.startBpmsNow(),
            dao.overrideNow(today.weekStart().epoch()), dao.inProgressNow(), emptyList(),
        )
    }

    suspend fun profileNow(): ProfileEntity = dao.profileNow() ?: ProfileEntity()

    suspend fun updateProfile(f: (ProfileEntity) -> ProfileEntity) = dao.saveProfile(f(profileNow()))

    // ---------- onboarding, retest, level up ----------

    suspend fun completeOnboarding(trueBpm: Int, restDays: Set<Int>, reminderMinutes: List<Int>) {
        val today = LocalDate.now()
        val (a, b) = restDays.sorted()
        dao.clearStartBpms()
        updateProfile {
            it.copy(
                onboarded = true, trueBpm = trueBpm, restDay1 = a, restDay2 = b,
                programStart = today.epoch(), cycleStart = today.weekStart().epoch(),
                lastProcessedWeek = today.weekStart().minusWeeks(1).epoch(), pendingReviewWeek = -1,
                benchmarkUnlocked = false, reminderMinutes = reminderMinutes.joinToString(","),
            )
        }
    }

    /** New placement: level, start BPMs and cycle reset; history kept. */
    suspend fun retest(trueBpm: Int) {
        val today = LocalDate.now()
        dao.clearStartBpms()
        updateProfile {
            it.copy(
                trueBpm = trueBpm, cycleStart = today.weekStart().epoch(), benchmarkUnlocked = false,
                pendingReviewWeek = -1, lastProcessedWeek = today.weekStart().minusWeeks(1).epoch(),
            )
        }
    }

    suspend fun benchmarkResult(date: LocalDate, passed: Boolean, bpm: Int, slot: DayType, startedAt: Long) {
        val p = profileNow()
        val before = Levels.forTrueBpm(p.trueBpm)
        dao.saveSession(
            SessionEntity(
                date = date.epoch(), slot = slot.name, dayType = DayType.BENCHMARK.name, level = before.number,
                deload = false, status = SessionStatus.COMPLETE, startedAt = startedAt, updatedAt = System.currentTimeMillis(),
                benchmarkBpm = bpm, benchmarkPassed = passed,
            ),
        )
        if (passed) {
            val after = Levels.forTrueBpm(bpm)
            dao.addLevelEvent(LevelEventEntity(date = date.epoch(), fromLevel = before.number, toLevel = after.number, trueBpm = bpm))
            dao.clearStartBpms()
            dao.saveProfile(p.copy(trueBpm = bpm, benchmarkUnlocked = false))
        } else {
            dao.saveProfile(p.copy(benchmarkUnlocked = false))
        }
    }

    // ---------- sessions ----------

    suspend fun saveInProgress(entity: SessionEntity): Long = dao.saveSession(entity)

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun inProgressNow(): SessionEntity? = dao.inProgressNow()

    suspend fun setNotice(text: String) = updateProfile { it.copy(notice = text) }

    // ---------- this week ----------

    private suspend fun editOverride(today: LocalDate, f: (WeekOverrideEntity) -> WeekOverrideEntity) {
        val ws = today.weekStart().epoch()
        dao.saveOverride(f(dao.overrideNow(ws) ?: WeekOverrideEntity(ws)))
    }

    suspend fun skipDay(date: LocalDate) = editOverride(date) {
        it.copy(skipped = (csvDays(it.skipped) + date).csv(), makeup = -1)
    }

    /** Undo a skip tapped by mistake; any make-up suggestion is re-planned. */
    suspend fun unskipDay(date: LocalDate) = editOverride(date) {
        it.copy(skipped = (csvDays(it.skipped) - date).csv(), makeup = -1)
    }

    suspend fun toggleUnavailable(date: LocalDate) = editOverride(date) {
        val set = csvDays(it.unavailable)
        it.copy(unavailable = (if (date in set) set - date else set + date).csv(), makeup = -1)
    }

    suspend fun setMakeup(today: LocalDate, accepted: Boolean) = editOverride(today) { it.copy(makeup = if (accepted) 1 else 0) }

    suspend fun resetWeek(today: LocalDate) = dao.saveOverride(WeekOverrideEntity(today.weekStart().epoch()))

    // ---------- week review and rollover ----------

    /**
     * Processes every week that ended since the last call: restarts the cycle
     * after a short hard week, queues the review of the latest real hard week,
     * and unlocks the benchmark when that week earned it.
     */
    suspend fun rollover(today: LocalDate = LocalDate.now()) {
        var p = dao.profileNow() ?: return
        if (!p.onboarded) return
        if (p.noPedals && p.noPedalsUntil >= 0 && today.epoch() >= p.noPedalsUntil) {
            // Back with pedals: the mode switched itself off.
            p = p.copy(noPedals = false, noPedalsUntil = -1)
            dao.saveProfile(p)
        }
        val current = today.weekStart()
        var week = day(p.lastProcessedWeek).plusWeeks(1)
        if (week >= current) return
        val sessions = dao.sessionsNow().map { it.toResult() }
        var cycleStart = day(p.cycleStart)
        var pending = p.pendingReviewWeek
        var unlocked = p.benchmarkUnlocked
        val level = Levels.forTrueBpm(p.trueBpm)
        while (week < current) {
            val inWeek = sessions.filter { it.date >= week && it.date < week.plusWeeks(1) && it.date >= day(p.programStart) }
            val count = inWeek.count { it.dayType.isTraining }
            pending = if (Cycle.reviewApplies(cycleStart, week, count)) week.epoch() else -1
            if (!Cycle.isDeload(cycleStart, week) && CapRules.benchmarkUnlocked(level, inWeek)) unlocked = true
            cycleStart = Cycle.rollover(cycleStart, week, count)
            week = week.plusWeeks(1)
        }
        dao.saveProfile(
            p.copy(
                cycleStart = cycleStart.epoch(), pendingReviewWeek = pending, benchmarkUnlocked = unlocked,
                lastProcessedWeek = current.minusWeeks(1).epoch(),
            ),
        )
    }

    suspend fun acceptReview(chosen: Map<StartKey, Int>) {
        val data = snapshot() ?: return
        val (_, recs) = data.review ?: return
        val applied = WeeklyReview.apply(data.startBpms, recs, data.level.cap, chosen)
        dao.saveStartBpms(applied.map { (k, v) -> StartBpmEntity(k.exercise.name, k.dayType.name, v) })
        updateProfile { it.copy(pendingReviewWeek = -1) }
    }

    // ---------- notifications ----------

    fun notificationDay(data: AppData, date: LocalDate): NotificationDay {
        val info = data.calendar.day(date)
        val plan = data.calendar.buildSession(date, data.startBpms)
        val isFirstDay = date.dayIndex() == 0
        val deloadStarts = isFirstDay && info.deload
        val cycleStarts = isFirstDay && info.cycleWeek == 1 && date > data.calendar.profile.programStart
        return NotificationDay(
            date = date,
            type = if (info.inProgram) info.type else DayType.REST,
            estimatedMinutes = plan?.let { (SessionBuilder.estimatedDurationSec(it) + 30) / 60 } ?: 0,
            pedalStartBpm = plan?.exercises?.firstOrNull { it.exercise == Exercise.PEDAL_NORMAL }?.startBpm,
            noPedals = info.noPedals,
            sessionDone = info.session != null || data.sessionOn(date) != null,
            weekReviewPending = date == data.today && data.review != null,
            reviewChanges = data.review?.second?.count { it.delta != 0 } ?: 0,
            benchmarkJustUnlockedFor = if (date == data.today && data.review != null) data.calendar.benchmarkDay else null,
            deloadStarts = deloadStarts,
            cycleStarts = cycleStarts,
        )
    }
}
