package com.premkumar.jiwtracker.wearprotocol

/**
 * Wire contract between the phone app and the watch companion display.
 *
 * Design note — why a snapshot and not a stream:
 * the phone does NOT push one message per second. It publishes a [SessionSnapshot] only when
 * something changes the schedule (phase change, pause, skip, stop) plus a slow heartbeat for
 * metrics. The watch projects the snapshot forward locally with [IntervalSchedule.project],
 * so the countdown stays correct even while Bluetooth is disconnected, and the radio stays
 * idle for almost the whole walk.
 */
object WearProtocol {

    /** Bumped only on an incompatible change. The receiver ignores snapshots it can't read. */
    const val PROTOCOL_VERSION = 1

    /** DataClient item holding the current [SessionSnapshot]. Persists across reconnects. */
    const val PATH_SESSION = "/jiw/session"

    /** MessageClient path for watch -> phone control commands. Fire-and-forget. */
    const val PATH_CONTROL = "/jiw/control"

    const val KEY_PROTOCOL_VERSION = "protocol_version"
    const val KEY_ACTIVE = "active"
    const val KEY_RUNNING = "running"
    const val KEY_PHASE = "phase"
    const val KEY_SECONDS_REMAINING = "seconds_remaining"
    const val KEY_PHASE_TOTAL = "phase_total"
    const val KEY_CYCLE = "cycle"
    const val KEY_TOTAL_CYCLES = "total_cycles"
    const val KEY_SLOW_SECONDS = "slow_seconds"
    const val KEY_FAST_SECONDS = "fast_seconds"
    const val KEY_ELAPSED_TOTAL = "elapsed_total"
    const val KEY_STEPS = "steps"
    const val KEY_CALORIES = "calories"
    const val KEY_SEQUENCE = "sequence"
}

/**
 * Phase as the watch understands it. Mirrors `WalkingForegroundService.Phase`, plus an explicit
 * [FINISHED] terminal that the phone's enum expresses by ending the session instead.
 */
enum class WatchPhase {
    PREPARE,
    SLOW,
    FAST,
    FINISHED;

    companion object {
        fun fromName(name: String?): WatchPhase =
            entries.firstOrNull { it.name == name } ?: PREPARE
    }
}

/** Commands the watch can send to an already-running phone session. */
enum class ControlCommand {
    PAUSE,
    SKIP,
    STOP;

    companion object {
        fun fromName(name: String?): ControlCommand? =
            entries.firstOrNull { it.name == name }
    }
}

/**
 * A complete description of the session at one instant, and enough of the plan for the watch
 * to continue the schedule on its own.
 *
 * [sequence] is monotonic per session so a late-delivered stale item can be discarded.
 */
data class SessionSnapshot(
    val active: Boolean,
    val running: Boolean,
    val phase: WatchPhase,
    val secondsRemainingInPhase: Int,
    val phaseTotalSeconds: Int,
    val cycle: Int,
    val totalCycles: Int,
    val slowSeconds: Int,
    val fastSeconds: Int,
    val elapsedTotalSeconds: Int,
    val steps: Int,
    val calories: Double,
    val sequence: Long
) {
    companion object {
        /** The "nothing is running" snapshot the watch shows on its waiting screen. */
        val IDLE = SessionSnapshot(
            active = false,
            running = false,
            phase = WatchPhase.PREPARE,
            secondsRemainingInPhase = 0,
            phaseTotalSeconds = 0,
            cycle = 0,
            totalCycles = 0,
            slowSeconds = 0,
            fastSeconds = 0,
            elapsedTotalSeconds = 0,
            steps = 0,
            calories = 0.0,
            sequence = 0L
        )
    }
}
