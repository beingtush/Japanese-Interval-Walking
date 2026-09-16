package com.premkumar.jiwtracker.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.premkumar.jiwtracker.wear.R
import com.premkumar.jiwtracker.wearprotocol.ControlCommand
import com.premkumar.jiwtracker.wearprotocol.IntervalSchedule
import com.premkumar.jiwtracker.wearprotocol.SessionSnapshot
import com.premkumar.jiwtracker.wearprotocol.WatchPhase
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * The one screen that matters.
 *
 * Reading order is deliberate and matches how you actually glance at a wrist mid-stride:
 * the phase colour registers first, then the countdown, then the cycle. Metrics sit last and
 * smallest — they are nice to know, they are not why you looked.
 */
@Composable
fun SessionScreen(
    snapshot: SessionSnapshot,
    connected: Boolean,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onControl: (ControlCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val phaseColor = if (!snapshot.running) Palette.Paused else Palette.forPhase(snapshot.phase)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggleControls
            ),
        contentAlignment = Alignment.Center
    ) {
        PhaseArc(
            progress = IntervalSchedule.phaseProgress(snapshot),
            color = phaseColor
        )

        if (controlsVisible) {
            Controls(
                isRunning = snapshot.running,
                onControl = onControl
            )
        } else {
            SessionReadout(
                snapshot = snapshot,
                phaseColor = phaseColor,
                connected = connected
            )
        }
    }
}

/** Progress around the bezel. Hand-drawn rather than a component so it cannot drift visually. */
@Composable
private fun PhaseArc(progress: Float, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
        val stroke = 8.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = Palette.Track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        if (progress > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun SessionReadout(
    snapshot: SessionSnapshot,
    phaseColor: Color,
    connected: Boolean
) {
    val phaseLabel = when {
        !snapshot.running -> stringResource(R.string.paused)
        snapshot.phase == WatchPhase.PREPARE -> stringResource(R.string.phase_prepare)
        snapshot.phase == WatchPhase.SLOW -> stringResource(R.string.phase_slow)
        snapshot.phase == WatchPhase.FAST -> stringResource(R.string.phase_fast)
        else -> stringResource(R.string.phase_finished)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp)
    ) {
        Text(
            text = phaseLabel,
            color = phaseColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (snapshot.phase != WatchPhase.FINISHED) {
            Text(
                text = IntervalSchedule.formatTime(snapshot.secondsRemainingInPhase),
                color = Palette.OnDark,
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${snapshot.cycle} / ${snapshot.totalCycles}",
                color = Palette.Dim,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${snapshot.steps} steps · ${snapshot.calories.toInt()} kcal",
                color = Palette.Dim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Text(
                text = IntervalSchedule.formatTime(snapshot.elapsedTotalSeconds),
                color = Palette.OnDark,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${snapshot.steps} steps · ${snapshot.calories.toInt()} kcal",
                color = Palette.Dim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        // Only ever shown as a small hint. The countdown itself stays correct while
        // disconnected, so a lost phone is not an error state — only the metrics go stale.
        if (!connected) {
            Text(
                text = stringResource(R.string.not_connected),
                color = Palette.Dim,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun Controls(
    isRunning: Boolean,
    onControl: (ControlCommand) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
    ) {
        ControlButton(
            label = if (isRunning) "❚❚" else "▶",
            tint = Palette.OnDark,
            contentDescription = if (isRunning) R.string.action_pause else R.string.action_resume,
            onClick = { onControl(ControlCommand.PAUSE) }
        )
        ControlButton(
            label = "▶▶",
            tint = Palette.OnDark,
            contentDescription = R.string.action_skip,
            onClick = { onControl(ControlCommand.SKIP) }
        )
        ControlButton(
            label = "■",
            tint = Palette.Stop,
            contentDescription = R.string.action_stop,
            onClick = { onControl(ControlCommand.STOP) }
        )
    }
}

/**
 * Hand-rolled rather than a Wear component so the touch target stays a guaranteed 52dp —
 * these get pressed with cold or sweaty fingers, mid-walk.
 */
@Composable
private fun ControlButton(
    label: String,
    tint: Color,
    contentDescription: Int,
    onClick: () -> Unit
) {
    // The glyphs are decorative; the string resource is what TalkBack announces.
    val description = stringResource(contentDescription)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Palette.Track)
            .semantics {
                role = Role.Button
                this.contentDescription = description
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WaitingScreen(connected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Palette.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 26.dp)
        ) {
            Text(
                text = stringResource(R.string.no_session_title),
                color = Palette.OnDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    if (connected) R.string.no_session_body else R.string.not_connected
                ),
                color = Palette.Dim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
