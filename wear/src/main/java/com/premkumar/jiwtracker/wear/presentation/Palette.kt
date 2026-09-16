package com.premkumar.jiwtracker.wear.presentation

import androidx.compose.ui.graphics.Color
import com.premkumar.jiwtracker.wearprotocol.WatchPhase

/**
 * Colours are chosen for a glance in daylight, not for subtlety: the phase must be readable
 * before the numbers are. Deliberately explicit values rather than theme tokens, so the two
 * phases can never end up looking similar after a palette change.
 */
object Palette {
    val Background = Color(0xFF000000)
    val Dim = Color(0xFF9E9E9E)
    val OnDark = Color(0xFFFFFFFF)

    val Slow = Color(0xFF4FC3F7)
    val Fast = Color(0xFFFFB300)
    val Prepare = Color(0xFFB0BEC5)
    val Finished = Color(0xFF66BB6A)
    val Paused = Color(0xFFBDBDBD)

    val Track = Color(0xFF2A2A2A)
    val Stop = Color(0xFFEF5350)

    fun forPhase(phase: WatchPhase): Color = when (phase) {
        WatchPhase.PREPARE -> Prepare
        WatchPhase.SLOW -> Slow
        WatchPhase.FAST -> Fast
        WatchPhase.FINISHED -> Finished
    }
}
