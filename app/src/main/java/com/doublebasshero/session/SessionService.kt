package com.doublebasshero.session

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.doublebasshero.R
import com.doublebasshero.app
import com.doublebasshero.engine.FormRating
import com.doublebasshero.engine.RestKind
import com.doublebasshero.engine.SessionRunner
import com.doublebasshero.engine.Step
import com.doublebasshero.engine.Warmup
import com.doublebasshero.notify.Channels
import com.doublebasshero.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Keeps a session alive with the screen off: foreground service with the session
 * notification (lock-screen buttons), a wake lock, and a media session for
 * headphone / Bluetooth buttons (one press = pause or Ready, two = skip).
 */
class SessionService : LifecycleService() {
    private val session get() = app.controller
    private lateinit var media: MediaSessionCompat
    private var wakeLock: PowerManager.WakeLock? = null
    private var pendingPress: kotlinx.coroutines.Job? = null
    private var lastShown = ""

    override fun onCreate() {
        super.onCreate()
        Channels.ensure(this)
        startInForeground(build())
        com.doublebasshero.notify.Reminders.cancelPosted(this)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dbh:session").apply { acquire(3 * 60 * 60 * 1000L) }
        media = MediaSessionCompat(this, "dbh").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(intent: Intent): Boolean {
                    val ev = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
                    if (ev.action != KeyEvent.ACTION_DOWN) return true
                    when (ev.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> session.advance()
                        KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> headsetPress()
                        else -> return false
                    }
                    return true
                }
            })
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                    .build(),
            )
            isActive = true
        }
        lifecycleScope.launch {
            session.state.collect { s ->
                if (s == null || s.outcome != null) {
                    ServiceCompat.stopForeground(this@SessionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val key = describe()
                    if (key != lastShown) {
                        lastShown = key
                        runCatching { NotificationManagerCompat.from(this@SessionService).notify(NOTIFICATION_ID, build()) }
                    }
                }
            }
        }
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    /** One press = pause/resume or Ready; two presses within 400 ms = skip. */
    private fun headsetPress() {
        val pending = pendingPress
        if (pending != null && pending.isActive) {
            pending.cancel()
            pendingPress = null
            session.advance()
        } else {
            pendingPress = lifecycleScope.launch {
                kotlinx.coroutines.delay(400)
                session.primary()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE -> session.togglePause()
            ACTION_SKIP, ACTION_READY -> session.advance()
            ACTION_CLEAN -> session.rate(FormRating.CLEAN)
            ACTION_SHAKY -> session.rate(FormRating.SHAKY)
            ACTION_BROKEN -> session.rate(FormRating.BROKEN)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        media.isActive = false
        media.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun action(name: String) = PendingIntent.getService(
        this, name.hashCode(), Intent(this, SessionService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Notification text that changes at most once a second (avoids notification spam). */
    private fun describe(): String {
        val s = session.state.value ?: return ""
        val sec = s.elapsedMs / 1000
        return "${s.runner.index}|${s.paused}|${s.countingIn}|$sec|${SessionRunner.pendingRating(s.runner)}|${SessionRunner.nextBpm(s.runner)}|${s.restCued}"
    }

    private fun build(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, Channels.SESSION)
            .setSmallIcon(R.drawable.ic_stat_dbh)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setGroup("session")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        val s = session.state.value ?: return b.setContentTitle("Double Bass Hero").build()
        val r = s.runner
        when (val step = s.step) {
            is Step.Rest -> {
                val rated = SessionRunner.pendingRating(r)
                val restName = when (step.kind) {
                    RestKind.SHORT -> "Short rest"
                    RestKind.LONG, RestKind.SWITCH_EXERCISE -> "Long rest"
                    RestKind.FINAL -> "Done!"
                }
                val resting = "resting ${s.elapsedMs / 60000}:${"%02d".format((s.elapsedMs / 1000) % 60)}" + if (s.restCued) " · rest is up" else ""
                if (rated == null) {
                    val set = r.sets[step.ratedExercise]?.lastOrNull()
                    b.setContentTitle("$restName · rate your set")
                    b.setContentText("${step.ratedExercise.title} · ${set?.bpm ?: ""} BPM · $resting")
                    b.addAction(0, "Clean", action(ACTION_CLEAN))
                    b.addAction(0, "Shaky", action(ACTION_SHAKY))
                    b.addAction(0, "Broken", action(ACTION_BROKEN))
                } else if (step.kind == RestKind.FINAL) {
                    b.setContentTitle("Session done")
                    b.setContentText("Last set rated ${rated.title.lowercase()}. Open the app for your summary.")
                    b.addAction(0, "Finish", action(ACTION_READY))
                } else if (step.nextSetOf != null) {
                    b.setContentTitle("Next set: ${SessionRunner.nextBpm(r)} BPM")
                    b.setContentText("$restName · $resting")
                    b.addAction(0, "Ready", action(ACTION_READY))
                } else {
                    b.setContentTitle("$restName · next: ${nextTitle(r)}")
                    b.setContentText(resting)
                    b.addAction(0, "Ready", action(ACTION_READY))
                }
            }
            null -> b.setContentTitle("Double Bass Hero")
            else -> {
                val title = when (step) {
                    is Step.Warmup -> "Warm-up · ${Warmup.exercises[step.index].title}"
                    is Step.Work -> "${step.exercise.title} · Set ${step.set}/${step.of}"
                    is Step.BenchmarkTest -> "Benchmark · 4:00"
                    else -> ""
                }
                val left = s.stepSeconds - s.elapsedMs / 1000
                b.setContentTitle("$title · ${SessionRunner.bpm(r)} BPM")
                b.setContentText(
                    when {
                        s.paused -> "Paused"
                        s.countingIn -> "Count-in…"
                        else -> "${left / 60}:${"%02d".format(left % 60)} left"
                    },
                )
                b.setProgress(s.stepSeconds, (s.elapsedMs / 1000).toInt(), false)
                b.addAction(0, if (s.paused) "Resume" else "Pause", action(ACTION_PAUSE))
                b.addAction(0, "Skip", action(ACTION_SKIP))
            }
        }
        return b.build()
    }

    private fun nextTitle(r: com.doublebasshero.engine.RunnerState): String =
        r.steps.drop(r.index + 1).firstOrNull { it !is Step.Rest }?.let {
            when (it) {
                is Step.Work -> it.exercise.title
                is Step.Warmup -> Warmup.exercises[it.index].title
                is Step.BenchmarkTest -> "Benchmark"
                else -> ""
            }
        } ?: "summary"

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_PAUSE = "dbh.PAUSE"
        const val ACTION_SKIP = "dbh.SKIP"
        const val ACTION_READY = "dbh.READY"
        const val ACTION_CLEAN = "dbh.CLEAN"
        const val ACTION_SHAKY = "dbh.SHAKY"
        const val ACTION_BROKEN = "dbh.BROKEN"
    }
}
