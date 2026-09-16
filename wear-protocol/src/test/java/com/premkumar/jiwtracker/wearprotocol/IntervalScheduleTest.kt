package com.premkumar.jiwtracker.wearprotocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalScheduleTest {

    /** A standard 3-minute / 3-minute, 5-cycle session, one second into the 5s PREPARE. */
    private fun standard(
        phase: WatchPhase = WatchPhase.PREPARE,
        secondsRemaining: Int = 5,
        phaseTotal: Int = 5,
        cycle: Int = 1,
        running: Boolean = true,
        elapsed: Int = 0
    ) = SessionSnapshot(
        active = true,
        running = running,
        phase = phase,
        secondsRemainingInPhase = secondsRemaining,
        phaseTotalSeconds = phaseTotal,
        cycle = cycle,
        totalCycles = 5,
        slowSeconds = 180,
        fastSeconds = 180,
        elapsedTotalSeconds = elapsed,
        steps = 0,
        calories = 0.0,
        sequence = 1L
    )

    @Test
    fun `within a phase it just counts down`() {
        val result = IntervalSchedule.project(standard(WatchPhase.SLOW, 180, 180), 30)
        assertEquals(WatchPhase.SLOW, result.phase)
        assertEquals(150, result.secondsRemainingInPhase)
        assertEquals(1, result.cycle)
    }

    @Test
    fun `prepare rolls into slow without advancing total elapsed`() {
        // 5s of PREPARE then 10s of SLOW. Only the SLOW seconds count toward elapsed,
        // matching WalkingForegroundService, which returns early from PREPARE ticks.
        val result = IntervalSchedule.project(standard(), 15)
        assertEquals(WatchPhase.SLOW, result.phase)
        assertEquals(170, result.secondsRemainingInPhase)
        assertEquals(180, result.phaseTotalSeconds)
        assertEquals(10, result.elapsedTotalSeconds)
        assertEquals(1, result.cycle)
    }

    @Test
    fun `slow rolls into fast in the same cycle`() {
        val result = IntervalSchedule.project(standard(WatchPhase.SLOW, 180, 180), 180)
        assertEquals(WatchPhase.FAST, result.phase)
        assertEquals(180, result.secondsRemainingInPhase)
        assertEquals(1, result.cycle)
    }

    @Test
    fun `fast rolls into the next cycle's slow`() {
        val result = IntervalSchedule.project(standard(WatchPhase.FAST, 180, 180), 180)
        assertEquals(WatchPhase.SLOW, result.phase)
        assertEquals(2, result.cycle)
    }

    @Test
    fun `a boundary lands exactly on the transition`() {
        // Consuming precisely the remaining seconds must cross the boundary, matching the
        // phone's `if (nextTimeLeft <= 0) transition` on the same tick.
        val result = IntervalSchedule.project(standard(WatchPhase.SLOW, 1, 180), 1)
        assertEquals(WatchPhase.FAST, result.phase)
    }

    @Test
    fun `projecting across the whole session finishes it`() {
        // 5s prepare + 5 cycles x (180 slow + 180 fast) = 1805s.
        val result = IntervalSchedule.project(standard(), 1805)
        assertEquals(WatchPhase.FINISHED, result.phase)
        assertEquals(0, result.secondsRemainingInPhase)
        assertEquals(1800, result.elapsedTotalSeconds)
    }

    @Test
    fun `overshooting the end stays finished rather than wrapping`() {
        val result = IntervalSchedule.project(standard(), 99_999)
        assertEquals(WatchPhase.FINISHED, result.phase)
    }

    @Test
    fun `a paused session does not advance`() {
        val paused = standard(WatchPhase.SLOW, 120, 180, running = false)
        val result = IntervalSchedule.project(paused, 600)
        assertEquals(120, result.secondsRemainingInPhase)
        assertEquals(WatchPhase.SLOW, result.phase)
    }

    @Test
    fun `an inactive session does not advance`() {
        val result = IntervalSchedule.project(SessionSnapshot.IDLE, 600)
        assertEquals(SessionSnapshot.IDLE, result)
    }

    @Test
    fun `a malformed zero-length plan terminates instead of spinning`() {
        val broken = standard(WatchPhase.SLOW, 10, 10).copy(slowSeconds = 0, fastSeconds = 0)
        val result = IntervalSchedule.project(broken, 600)
        assertEquals(WatchPhase.FINISHED, result.phase)
    }

    @Test
    fun `second-by-second projection matches one big jump`() {
        // Guards the loop against off-by-one drift: 900 individual 1s steps must land on the
        // same state as a single 900s projection.
        var stepwise = standard()
        repeat(900) { stepwise = IntervalSchedule.project(stepwise, 1) }
        val oneShot = IntervalSchedule.project(standard(), 900)

        assertEquals(oneShot.phase, stepwise.phase)
        assertEquals(oneShot.cycle, stepwise.cycle)
        assertEquals(oneShot.secondsRemainingInPhase, stepwise.secondsRemainingInPhase)
        assertEquals(oneShot.elapsedTotalSeconds, stepwise.elapsedTotalSeconds)
    }

    @Test
    fun `phase progress runs from zero to one`() {
        assertEquals(0f, IntervalSchedule.phaseProgress(standard(WatchPhase.SLOW, 180, 180)), 0.001f)
        assertEquals(0.5f, IntervalSchedule.phaseProgress(standard(WatchPhase.SLOW, 90, 180)), 0.001f)
        assertEquals(1f, IntervalSchedule.phaseProgress(standard(WatchPhase.SLOW, 0, 180)), 0.001f)
        assertTrue(IntervalSchedule.phaseProgress(SessionSnapshot.IDLE) == 0f)
    }

    @Test
    fun `time formatting matches the phone`() {
        assertEquals("3:00", IntervalSchedule.formatTime(180))
        assertEquals("0:05", IntervalSchedule.formatTime(5))
        assertEquals("0:00", IntervalSchedule.formatTime(-4))
        assertEquals("12:34", IntervalSchedule.formatTime(754))
    }
}
