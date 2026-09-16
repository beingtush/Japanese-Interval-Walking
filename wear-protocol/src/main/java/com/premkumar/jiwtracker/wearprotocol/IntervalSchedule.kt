package com.premkumar.jiwtracker.wearprotocol

/**
 * Pure projection of a [SessionSnapshot] forward in time. No Android, no clocks, no state —
 * the caller supplies how many seconds have passed since the snapshot was taken.
 *
 * This is what lets the watch keep an accurate countdown with the radio idle, and what makes
 * a Bluetooth drop mid-walk harmless.
 *
 * The transition rules deliberately mirror `WalkingForegroundService.tickOneSecond`:
 *  - PREPARE ends into SLOW, and the cycle counter stays where it is.
 *  - SLOW ends into FAST within the same cycle.
 *  - FAST ends into the next cycle's SLOW, or finishes the session past the last cycle.
 *  - Total elapsed does NOT advance during PREPARE. The phone returns early from its tick
 *    before incrementing elapsed, so counting it here would drift the two displays apart.
 */
object IntervalSchedule {

    /** Defensive bound. A well-formed snapshot terminates long before this. */
    private const val MAX_TRANSITIONS = 10_000

    fun project(snapshot: SessionSnapshot, secondsElapsed: Long): SessionSnapshot {
        if (!snapshot.active) return snapshot
        if (snapshot.phase == WatchPhase.FINISHED) return snapshot
        // A paused session is frozen: the phone is not advancing it either.
        if (!snapshot.running) return snapshot
        if (secondsElapsed <= 0L) return snapshot

        var toConsume = secondsElapsed
        var phase = snapshot.phase
        var left = snapshot.secondsRemainingInPhase.toLong()
        var phaseTotal = snapshot.phaseTotalSeconds
        var cycle = snapshot.cycle
        var elapsedTotal = snapshot.elapsedTotalSeconds.toLong()
        var transitions = 0

        while (toConsume > 0L) {
            if (toConsume < left) {
                if (phase != WatchPhase.PREPARE) elapsedTotal += toConsume
                left -= toConsume
                toConsume = 0L
                break
            }

            // The rest of this phase elapses, then we cross a boundary.
            if (phase != WatchPhase.PREPARE) elapsedTotal += left
            toConsume -= left

            when (phase) {
                WatchPhase.PREPARE -> {
                    phase = WatchPhase.SLOW
                    left = snapshot.slowSeconds.toLong()
                    phaseTotal = snapshot.slowSeconds
                }

                WatchPhase.SLOW -> {
                    phase = WatchPhase.FAST
                    left = snapshot.fastSeconds.toLong()
                    phaseTotal = snapshot.fastSeconds
                }

                WatchPhase.FAST -> {
                    cycle += 1
                    if (cycle > snapshot.totalCycles) {
                        return snapshot.copy(
                            phase = WatchPhase.FINISHED,
                            secondsRemainingInPhase = 0,
                            phaseTotalSeconds = 0,
                            cycle = snapshot.totalCycles,
                            elapsedTotalSeconds = elapsedTotal.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                    }
                    phase = WatchPhase.SLOW
                    left = snapshot.slowSeconds.toLong()
                    phaseTotal = snapshot.slowSeconds
                }

                WatchPhase.FINISHED -> break
            }

            // A zero- or negative-length phase would spin forever. Treat a malformed plan as
            // finished rather than hanging the UI thread.
            if (left <= 0L) {
                return snapshot.copy(
                    phase = WatchPhase.FINISHED,
                    secondsRemainingInPhase = 0,
                    phaseTotalSeconds = 0,
                    elapsedTotalSeconds = elapsedTotal.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                )
            }

            if (++transitions >= MAX_TRANSITIONS) break
        }

        return snapshot.copy(
            phase = phase,
            secondsRemainingInPhase = left.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            phaseTotalSeconds = phaseTotal,
            cycle = cycle,
            elapsedTotalSeconds = elapsedTotal.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
    }

    /** Fraction of the current phase already completed, in 0f..1f. Used for the progress arc. */
    fun phaseProgress(snapshot: SessionSnapshot): Float {
        if (snapshot.phaseTotalSeconds <= 0) return 0f
        val done = snapshot.phaseTotalSeconds - snapshot.secondsRemainingInPhase
        return (done.toFloat() / snapshot.phaseTotalSeconds.toFloat()).coerceIn(0f, 1f)
    }

    /** `m:ss`, matching the phone's own timer formatting. */
    fun formatTime(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
    }
}
