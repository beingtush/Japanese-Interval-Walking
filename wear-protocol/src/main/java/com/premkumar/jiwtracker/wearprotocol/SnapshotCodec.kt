package com.premkumar.jiwtracker.wearprotocol

import com.google.android.gms.wearable.DataMap

/**
 * DataMap <-> [SessionSnapshot]. Kept in the shared module so the phone and the watch can never
 * disagree about the wire format.
 */
object SnapshotCodec {

    fun toDataMap(snapshot: SessionSnapshot): DataMap = DataMap().apply {
        putInt(WearProtocol.KEY_PROTOCOL_VERSION, WearProtocol.PROTOCOL_VERSION)
        putBoolean(WearProtocol.KEY_ACTIVE, snapshot.active)
        putBoolean(WearProtocol.KEY_RUNNING, snapshot.running)
        putString(WearProtocol.KEY_PHASE, snapshot.phase.name)
        putInt(WearProtocol.KEY_SECONDS_REMAINING, snapshot.secondsRemainingInPhase)
        putInt(WearProtocol.KEY_PHASE_TOTAL, snapshot.phaseTotalSeconds)
        putInt(WearProtocol.KEY_CYCLE, snapshot.cycle)
        putInt(WearProtocol.KEY_TOTAL_CYCLES, snapshot.totalCycles)
        putInt(WearProtocol.KEY_SLOW_SECONDS, snapshot.slowSeconds)
        putInt(WearProtocol.KEY_FAST_SECONDS, snapshot.fastSeconds)
        putInt(WearProtocol.KEY_ELAPSED_TOTAL, snapshot.elapsedTotalSeconds)
        putInt(WearProtocol.KEY_STEPS, snapshot.steps)
        putDouble(WearProtocol.KEY_CALORIES, snapshot.calories)
        putLong(WearProtocol.KEY_SEQUENCE, snapshot.sequence)
    }

    /** Returns null for a payload written by an incompatible protocol version. */
    fun fromDataMap(map: DataMap): SessionSnapshot? {
        val version = map.getInt(WearProtocol.KEY_PROTOCOL_VERSION, -1)
        if (version != WearProtocol.PROTOCOL_VERSION) return null

        return SessionSnapshot(
            active = map.getBoolean(WearProtocol.KEY_ACTIVE, false),
            running = map.getBoolean(WearProtocol.KEY_RUNNING, false),
            phase = WatchPhase.fromName(map.getString(WearProtocol.KEY_PHASE)),
            secondsRemainingInPhase = map.getInt(WearProtocol.KEY_SECONDS_REMAINING, 0),
            phaseTotalSeconds = map.getInt(WearProtocol.KEY_PHASE_TOTAL, 0),
            cycle = map.getInt(WearProtocol.KEY_CYCLE, 0),
            totalCycles = map.getInt(WearProtocol.KEY_TOTAL_CYCLES, 0),
            slowSeconds = map.getInt(WearProtocol.KEY_SLOW_SECONDS, 0),
            fastSeconds = map.getInt(WearProtocol.KEY_FAST_SECONDS, 0),
            elapsedTotalSeconds = map.getInt(WearProtocol.KEY_ELAPSED_TOTAL, 0),
            steps = map.getInt(WearProtocol.KEY_STEPS, 0),
            calories = map.getDouble(WearProtocol.KEY_CALORIES, 0.0),
            sequence = map.getLong(WearProtocol.KEY_SEQUENCE, 0L)
        )
    }
}
