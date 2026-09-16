package com.premkumar.jiwtracker.wear.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.MaterialTheme
import com.premkumar.jiwtracker.wear.SessionRepository
import com.premkumar.jiwtracker.wear.WatchSessionService
import com.premkumar.jiwtracker.wear.ControlSender
import com.premkumar.jiwtracker.wearprotocol.ControlCommand
import com.premkumar.jiwtracker.wearprotocol.SessionSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tap-to-wake / raise-to-wake by design: this activity deliberately does NOT hold the screen
 * on and does NOT implement ambient mode. The display sleeps normally, and because everything
 * shown is projected from the anchor at render time, whatever you come back to is correct the
 * instant it draws — there is no catching up and no stale frame.
 */
class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationsIfNeeded()

        setContent {
            MaterialTheme {
                WatchApp(
                    onControl = { command ->
                        lifecycleScope.launch { ControlSender.send(applicationContext, command) }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up a session that was already running before this app was opened, then hand off
        // to the foreground service. Starting it from here is the legal path: the activity is
        // visible, so the Android 12+ background-start restriction does not apply.
        lifecycleScope.launch {
            SessionRepository.primeFrom(applicationContext)
            if (SessionRepository.projectNow().active) {
                startSessionService()
            }
        }
    }

    private fun startSessionService() {
        try {
            val intent = Intent(this, WatchSessionService::class.java)
            startForegroundService(intent)
        } catch (e: Throwable) {
            // Nothing the user can act on; the in-app display still works while it is open.
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun WatchApp(onControl: (ControlCommand) -> Unit) {
    val anchored by SessionRepository.anchor.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(false) }

    // Recomputes once a second, but only while this composable is actually on screen. With the
    // display asleep Compose is not drawing, so this costs nothing in the pocket.
    val snapshot: SessionSnapshot by produceState(
        initialValue = SessionRepository.project(anchored, SystemClock.elapsedRealtime()),
        anchored
    ) {
        while (true) {
            value = SessionRepository.project(anchored, SystemClock.elapsedRealtime())
            delay(1_000L)
        }
    }

    // The controls overlay hides itself so a glance never lands on buttons instead of the timer.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(6_000L)
            controlsVisible = false
        }
    }

    if (snapshot.active) {
        SessionScreen(
            snapshot = snapshot,
            connected = anchored.everReceived,
            controlsVisible = controlsVisible,
            onToggleControls = { controlsVisible = !controlsVisible },
            onControl = { command ->
                controlsVisible = false
                onControl(command)
            }
        )
    } else {
        WaitingScreen(connected = anchored.everReceived)
    }
}
