package com.doublebasshero.engine

import java.time.LocalDate
import java.time.LocalTime

enum class NotificationType {
    TRAINING_REMINDER,
    MISSED_SESSION,
    WEEK_REVIEW,
    BENCHMARK_UNLOCKED,
    BENCHMARK_DAY,
    CYCLE,
    REST_DAY,
}

data class NotificationPrefs(
    /** Reminder time per weekday, 0 = Monday. */
    val reminderTimes: List<LocalTime> = List(7) { LocalTime.of(18, 0) },
    val missedTime: LocalTime = LocalTime.of(21, 0),
    val enabled: Set<NotificationType> = NotificationType.entries.toSet() - NotificationType.REST_DAY,
    val restCue: Boolean = true,
    val voicePrompts: Boolean = true,
)

/** What the planner needs to know about one day. */
data class NotificationDay(
    val date: LocalDate,
    val type: DayType,
    val estimatedMinutes: Int,
    val pedalStartBpm: Int?,
    val sessionDone: Boolean,
    val weekReviewPending: Boolean,
    val reviewChanges: Int,
    val benchmarkJustUnlockedFor: LocalDate?,
    val deloadStarts: Boolean,
    val cycleStarts: Boolean,
)

enum class NotificationAction { START, NOT_TODAY, OPEN_REVIEW, OPEN }

data class PlannedNotification(
    val date: LocalDate,
    val time: LocalTime,
    val types: Set<NotificationType>,
    val title: String,
    val text: String,
    val actions: List<NotificationAction>,
)

object NotificationPlanner {
    /**
     * At most one planning notification per day at the reminder time (several
     * events are merged into it), plus the evening missed-session check.
     */
    fun plan(day: NotificationDay, prefs: NotificationPrefs): List<PlannedNotification> {
        val out = mutableListOf<PlannedNotification>()
        val on = prefs.enabled
        val time = prefs.reminderTimes[day.date.dayIndex()]
        val titles = mutableListOf<String>()
        val texts = mutableListOf<String>()
        val types = mutableSetOf<NotificationType>()
        val actions = mutableListOf<NotificationAction>()

        if (day.weekReviewPending && NotificationType.WEEK_REVIEW in on) {
            types += NotificationType.WEEK_REVIEW
            titles += "Week review ready"
            texts += "${day.reviewChanges} start BPM${if (day.reviewChanges == 1) "" else "s"} to change."
        }
        if (day.benchmarkJustUnlockedFor != null && NotificationType.BENCHMARK_UNLOCKED in on) {
            types += NotificationType.BENCHMARK_UNLOCKED
            texts += "Benchmark unlocked: ${day.benchmarkJustUnlockedFor.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}."
        }
        if (day.deloadStarts && NotificationType.CYCLE in on) {
            types += NotificationType.CYCLE
            texts += "Deload week: half the sets, no ramp."
        }
        if (day.cycleStarts && NotificationType.CYCLE in on) {
            types += NotificationType.CYCLE
            texts += "New cycle: week 1 of 3."
        }
        when {
            day.sessionDone -> Unit
            day.type == DayType.BENCHMARK && NotificationType.BENCHMARK_DAY in on -> {
                types += NotificationType.BENCHMARK_DAY
                titles += "Benchmark today"
                texts += "Warm up and stay fresh."
                actions += NotificationAction.START
            }
            day.type.isTraining && NotificationType.TRAINING_REMINDER in on -> {
                types += NotificationType.TRAINING_REMINDER
                titles += "${day.type.title} day · ~${day.estimatedMinutes} min"
                day.pedalStartBpm?.let { texts += "Pedal Normal starts at $it." }
                actions += listOf(NotificationAction.START, NotificationAction.NOT_TODAY)
            }
            day.type == DayType.REST && NotificationType.REST_DAY in on -> {
                types += NotificationType.REST_DAY
                titles += "Rest day"
                texts += "No leg work today."
            }
        }
        if (types.isNotEmpty()) {
            if (NotificationType.WEEK_REVIEW in types) actions.add(0, NotificationAction.OPEN_REVIEW)
            out += PlannedNotification(
                date = day.date,
                time = time,
                types = types,
                title = titles.joinToString(" · ").ifEmpty { "Double Bass Hero" },
                text = texts.joinToString(" "),
                actions = actions.distinct().take(3),
            )
        }
        if (day.type.isTraining && !day.sessionDone && NotificationType.MISSED_SESSION in on) {
            out += PlannedNotification(
                date = day.date,
                time = prefs.missedTime,
                types = setOf(NotificationType.MISSED_SESSION),
                title = "No session yet today",
                text = "There's still time. It only counts as rest after midnight.",
                actions = listOf(NotificationAction.START, NotificationAction.NOT_TODAY),
            )
        }
        return out
    }
}
