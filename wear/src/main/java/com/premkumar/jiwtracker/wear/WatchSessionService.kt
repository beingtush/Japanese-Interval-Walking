package com.premkumar.jiwtracker.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.premkumar.jiwtracker.wear.presentation.MainActivity
import com.premkumar.jiwtracker.wearprotocol.SessionSnapshot
import com.premkumar.jiwtracker.wearprotocol.WatchPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the session alive on the wrist while the screen is off: fires the phase-change haptics
 * and publishes the Ongoing Activity so the running session is one tap away from the watch face.
 *
 * Power shape, deliberately: this does NOT poll once a second. It sleeps until the next phase
 * boundary — up to three minutes on a standard plan — wakes to buzz, then sleeps again. The
 * countdown shown in the notification is rendered by the system's own chronometer, so keeping
 * it live costs no wakeups at all.
 */
class WatchSessionService : android.app.Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastPhase: WatchPhase? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(SessionRepository.projectNow())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (loop == null) {
            loop = scope.launch { run() }
        }
        return START_STICKY
    }

    private suspend fun run() {
        while (scope.isActive) {
            val projected = SessionRepository.projectNow()

            if (!projected.active || projected.phase == WatchPhase.FINISHED) {
                if (lastPhase != null && lastPhase != WatchPhase.FINISHED) {
                    vibrate(PATTERN_FINISHED)
                }
                notify(buildNotification(projected))
                stopSelf()
                return
            }

            val phase = projected.phase
            if (lastPhase != null && phase != lastPhase) {
                vibrate(patternFor(phase))
            }
            lastPhase = phase

            notify(buildNotification(projected))

            // Sleep straight through to the next boundary. The cap is a cheap re-sync in case
            // the device slept through part of the delay despite the wake lock.
            val untilBoundaryMs = projected.secondsRemainingInPhase.coerceAtLeast(0) * 1000L
            val paused = !projected.running
            val sleepMs = when {
                paused -> RESYNC_CAP_MS
                untilBoundaryMs <= 0L -> MIN_SLEEP_MS
                else -> untilBoundaryMs.coerceIn(MIN_SLEEP_MS, RESYNC_CAP_MS)
            }
            delay(sleepMs)
        }
    }

    private fun patternFor(phase: WatchPhase): LongArray = when (phase) {
        // Speeding up is the cue you must not miss: two hard buzzes.
        WatchPhase.FAST -> PATTERN_SPEED_UP
        // Slowing down is permissive: one soft, longer buzz.
        WatchPhase.SLOW -> PATTERN_SLOW_DOWN
        else -> PATTERN_SLOW_DOWN
    }

    private fun vibrate(pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Throwable) {
            // A watch without a vibrator, or a transient service failure. The visual cue stands.
        }
    }

    private fun buildNotification(snapshot: SessionSnapshot): Notification {
        val touchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val phaseLabel = when (snapshot.phase) {
            WatchPhase.PREPARE -> getString(R.string.phase_prepare)
            WatchPhase.SLOW -> getString(R.string.phase_slow)
            WatchPhase.FAST -> getString(R.string.phase_fast)
            WatchPhase.FINISHED -> getString(R.string.phase_finished)
        }
        val title = if (!snapshot.running && snapshot.active) {
            "${getString(R.string.paused)} · $phaseLabel"
        } else {
            phaseLabel
        }
        val subtitle = if (snapshot.totalCycles > 0) {
            "${snapshot.cycle}/${snapshot.totalCycles}"
        } else {
            ""
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_session)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(touchIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Let the system render the countdown. This is what keeps a live timer on the watch
        // face without this service waking up to redraw it.
        if (snapshot.active && snapshot.running && snapshot.phase != WatchPhase.FINISHED) {
            builder
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + snapshot.secondsRemainingInPhase * 1000L)
        } else {
            builder.setUsesChronometer(false).setShowWhen(false)
        }

        val ongoing = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_session)
            .setTouchIntent(touchIntent)
            .setStatus(
                Status.Builder()
                    .addTemplate(if (subtitle.isEmpty()) "#phase#" else "#phase# #cycle#")
                    .addPart("phase", Status.TextPart(phaseLabel))
                    .addPart("cycle", Status.TextPart(subtitle))
                    .build()
            )
            .build()
        ongoing.apply(applicationContext)

        return builder.build()
    }

    private fun notify(notification: Notification) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        } catch (e: Throwable) {
            // Notifications permission revoked mid-session. Nothing actionable here.
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.session_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        try {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "JIWWear::SessionWakelock"
            ).apply {
                setReferenceCounted(false)
                // Bounded so a session that somehow never ends cannot drain the watch.
                acquire(MAX_SESSION_MS)
            }
        } catch (e: Throwable) {
            wakeLock = null
        }
    }

    override fun onDestroy() {
        loop?.cancel()
        loop = null
        scope.coroutineContext[Job]?.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Throwable) {
            // Already released.
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "jiw_wear_session"
        const val NOTIFICATION_ID = 4137

        const val MIN_SLEEP_MS = 1_000L
        /** Never sleep longer than this without re-deriving state. */
        const val RESYNC_CAP_MS = 60_000L
        /** Four hours. Far beyond any plausible session. */
        const val MAX_SESSION_MS = 4L * 60L * 60L * 1000L

        val PATTERN_SPEED_UP = longArrayOf(0, 260, 140, 260)
        val PATTERN_SLOW_DOWN = longArrayOf(0, 420)
        val PATTERN_FINISHED = longArrayOf(0, 200, 120, 200, 120, 520)
    }
}
