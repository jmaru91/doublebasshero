package com.doublebasshero.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.doublebasshero.R
import com.doublebasshero.app
import com.doublebasshero.data.notificationPrefs
import com.doublebasshero.engine.NotificationAction
import com.doublebasshero.engine.NotificationPlanner
import com.doublebasshero.engine.NotificationType
import com.doublebasshero.engine.PlannedNotification
import com.doublebasshero.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Channels {
    const val SESSION = "session_live"

    /** One channel per notification type, so each can also be managed in system settings. */
    fun forType(t: NotificationType) = "n_" + t.name.lowercase()

    private fun titleOf(t: NotificationType) = when (t) {
        NotificationType.TRAINING_REMINDER -> "Training reminder"
        NotificationType.MISSED_SESSION -> "Missed session"
        NotificationType.WEEK_REVIEW -> "Week review"
        NotificationType.BENCHMARK_UNLOCKED -> "Benchmark unlocked"
        NotificationType.BENCHMARK_DAY -> "Benchmark day"
        NotificationType.CYCLE -> "Deload / new cycle"
        NotificationType.REST_DAY -> "Rest day"
    }

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(SESSION, "Session in progress", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Lock-screen controls while you train"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        NotificationType.entries.forEach {
            nm.createNotificationChannel(NotificationChannel(forType(it), titleOf(it), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}

/**
 * Schedules the next planned notification with an inexact alarm (reminders don't
 * need to-the-second precision), and posts it when it fires.
 */
object Reminders {
    private const val ALARM_REQUEST = 100
    const val ACTION_FIRE = "dbh.reminder.FIRE"
    const val ACTION_NOT_TODAY = "dbh.reminder.NOT_TODAY"
    const val ACTION_START = "dbh.reminder.START"
    const val ACTION_REVIEW = "dbh.reminder.REVIEW"
    private const val NOTIFICATION_ID = 2
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Recomputes and schedules the next notification after now. */
    fun reschedule(context: Context) = scope.launch { scheduleNext(context) }

    private suspend fun scheduleNext(context: Context) {
        val repo = context.app.repository
        val data = repo.snapshot() ?: return
        val prefs = data.profile.notificationPrefs()
        val now = LocalDateTime.now()
        val next = (0L..7L).asSequence()
            .flatMap { d -> NotificationPlanner.plan(repo.notificationDay(data, data.today.plusDays(d)), prefs).asSequence() }
            .map { LocalDateTime.of(it.date, it.time) }
            .filter { it.isAfter(now) }
            .minOrNull() ?: return
        val am = context.getSystemService(AlarmManager::class.java)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60 * 1000L, firePending(context))
    }

    private fun firePending(context: Context) = PendingIntent.getBroadcast(
        context, ALARM_REQUEST, Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Posts whatever is due now (within the last 30 minutes), then schedules the next one. */
    fun fire(context: Context) = scope.launch {
        val repo = context.app.repository
        repo.rollover()
        val data = repo.snapshot()
        if (data != null) {
            val prefs = data.profile.notificationPrefs()
            val now = LocalDateTime.now()
            val due = NotificationPlanner.plan(repo.notificationDay(data, data.today), prefs)
                .filter { val t = LocalDateTime.of(it.date, it.time); !t.isAfter(now.plusMinutes(1)) && t.isAfter(now.minusMinutes(30)) }
                .lastOrNull()
            if (due != null) post(context, due)
        }
        scheduleNext(context)
    }

    private fun post(context: Context, n: PlannedNotification) {
        Channels.ensure(context)
        val type = n.types.firstOrNull { it == NotificationType.MISSED_SESSION } ?: n.types.first()
        val b = NotificationCompat.Builder(context, Channels.forType(type))
            .setSmallIcon(R.drawable.ic_stat_dbh)
            .setContentTitle(n.title)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setAutoCancel(true)
            .setGroup("reminders")
            .setContentIntent(activity(context, null))
        n.actions.forEach { a ->
            when (a) {
                NotificationAction.START -> b.addAction(0, "Start", activity(context, ACTION_START))
                NotificationAction.OPEN_REVIEW -> b.addAction(0, "Open review", activity(context, ACTION_REVIEW))
                NotificationAction.NOT_TODAY -> b.addAction(
                    0, "Not today",
                    PendingIntent.getBroadcast(
                        context, 3, Intent(context, ReminderReceiver::class.java).setAction(ACTION_NOT_TODAY),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                NotificationAction.OPEN -> Unit
            }
        }
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, b.build()) }
            .onFailure { android.util.Log.w("dbh", "Could not post reminder", it) }
    }

    private fun activity(context: Context, action: String?) = PendingIntent.getActivity(
        context, (action ?: "open").hashCode(),
        Intent(context, MainActivity::class.java).setAction(action ?: Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Debug builds only: posts the next planned notification right away, to check how it looks. */
    fun postNextNow(context: Context) = scope.launch {
        val repo = context.app.repository
        val data = repo.snapshot() ?: return@launch
        val prefs = data.profile.notificationPrefs()
        val now = LocalDateTime.now()
        (0L..7L).asSequence()
            .flatMap { d -> NotificationPlanner.plan(repo.notificationDay(data, data.today.plusDays(d)), prefs).asSequence() }
            .firstOrNull { LocalDateTime.of(it.date, it.time).isAfter(now) }
            ?.let { post(context, it) }
    }

    fun cancelPosted(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    fun notToday(context: Context) = scope.launch {
        context.app.repository.skipDay(LocalDate.now())
        cancelPosted(context)
        scheduleNext(context)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val job = when (intent.action) {
            Reminders.ACTION_NOT_TODAY -> Reminders.notToday(context)
            "dbh.reminder.TEST" -> if (com.doublebasshero.BuildConfig.DEBUG) Reminders.postNextNow(context) else Reminders.fire(context)
            else -> Reminders.fire(context)
        }
        job.invokeOnCompletion { pending.finish() }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Reminders.reschedule(context).invokeOnCompletion { pending.finish() }
    }
}
