package com.premkumar.jiwtracker.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.premkumar.jiwtracker.wearprotocol.SessionSnapshot
import com.premkumar.jiwtracker.wearprotocol.SnapshotCodec
import com.premkumar.jiwtracker.wearprotocol.WatchPhase
import com.premkumar.jiwtracker.wearprotocol.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Publishes the live session to the paired watch.
 *
 * Deliberately NOT a one-message-per-second stream. A snapshot goes out only when something
 * changes the schedule the watch is counting down against — phase, cycle, pause/resume, stop —
 * plus a slow heartbeat so steps and calories stay roughly current. The watch projects
 * everything in between locally, so a Bluetooth drop leaves the countdown correct and the
 * radio stays idle for most of the walk.
 */
object WatchBridge {

    private const val TAG = "WatchBridge"

    /** How often to refresh steps / calories when nothing else has changed. */
    private const val METRICS_HEARTBEAT_SECONDS = 15

    private var scope: CoroutineScope? = null
    private var sequence = 0L
    private var lastPublished: SessionSnapshot? = null
    private var lastHeartbeatElapsed = Int.MIN_VALUE

    fun start(context: Context) {
        if (scope != null) return
        val appContext = context.applicationContext
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope

        newScope.launch {
            WalkingForegroundService.currentState.collect { state ->
                val snapshot = toSnapshot(state)
                if (shouldPublish(snapshot)) {
                    publish(appContext, snapshot)
                }
            }
        }
    }

    /**
     * Publishes a final "no session" snapshot so the watch stops showing a stale countdown,
     * then tears down. Fire-and-forget on a detached job — the caller is usually mid-teardown.
     */
    fun stop(context: Context) {
        val appContext = context.applicationContext
        val current = scope
        scope = null
        lastPublished = null
        lastHeartbeatElapsed = Int.MIN_VALUE

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            publish(appContext, SessionSnapshot.IDLE)
        }
        current?.coroutineContext?.get(Job)?.cancel()
    }

    private fun toSnapshot(state: WalkingForegroundService.ServiceState): SessionSnapshot {
        if (state !is WalkingForegroundService.ServiceState.Active) {
            return SessionSnapshot.IDLE
        }

        val phase = when (state.currentPhase) {
            WalkingForegroundService.Phase.PREPARE -> WatchPhase.PREPARE
            WalkingForegroundService.Phase.SLOW -> WatchPhase.SLOW
            WalkingForegroundService.Phase.FAST -> WatchPhase.FAST
        }

        return SessionSnapshot(
            active = true,
            running = state.isRunning,
            phase = phase,
            secondsRemainingInPhase = state.timeLeftInPhaseSeconds,
            phaseTotalSeconds = state.phaseDurationTotalSeconds,
            cycle = state.currentCycle,
            totalCycles = state.totalCycles,
            slowSeconds = WalkingForegroundService.slowDurationMinutes * 60,
            fastSeconds = WalkingForegroundService.fastDurationMinutes * 60,
            elapsedTotalSeconds = state.elapsedTotalSeconds,
            steps = state.steps,
            calories = state.calories,
            sequence = 0L // assigned at publish time
        )
    }

    /**
     * True when this snapshot changes something the watch cannot work out for itself.
     * Everything else is left to the watch's local projection.
     */
    private fun shouldPublish(snapshot: SessionSnapshot): Boolean {
        val previous = lastPublished ?: return true

        val scheduleChanged = snapshot.active != previous.active ||
            snapshot.running != previous.running ||
            snapshot.phase != previous.phase ||
            snapshot.cycle != previous.cycle ||
            snapshot.slowSeconds != previous.slowSeconds ||
            snapshot.fastSeconds != previous.fastSeconds ||
            snapshot.totalCycles != previous.totalCycles

        if (scheduleChanged) return true

        // A skip shortens the phase without changing which phase we're in, so the remaining
        // time jumps backwards. Catch that too, or the watch keeps the old countdown.
        if (snapshot.secondsRemainingInPhase > previous.secondsRemainingInPhase) return true

        val sinceHeartbeat = snapshot.elapsedTotalSeconds - lastHeartbeatElapsed
        return sinceHeartbeat >= METRICS_HEARTBEAT_SECONDS
    }

    private suspend fun publish(context: Context, snapshot: SessionSnapshot) {
        val stamped = snapshot.copy(sequence = ++sequence)
        try {
            val request = PutDataMapRequest.create(WearProtocol.PATH_SESSION).apply {
                dataMap.putAll(SnapshotCodec.toDataMap(stamped))
            }.asPutDataRequest()
                // Without setUrgent the system is free to batch this for a long time, which
                // would make phase cues arrive late. Every snapshot we send is time-critical.
                .setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            lastPublished = stamped
            lastHeartbeatElapsed = stamped.elapsedTotalSeconds
        } catch (e: Throwable) {
            // No paired watch, no Play services, or Bluetooth down. The watch recovers from
            // the next snapshot, so this is not worth surfacing to the user.
            Log.d(TAG, "Could not publish session snapshot to watch: ${e.message}")
        }
    }
}
