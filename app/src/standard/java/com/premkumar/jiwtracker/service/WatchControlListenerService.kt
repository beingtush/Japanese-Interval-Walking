package com.premkumar.jiwtracker.service

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.premkumar.jiwtracker.wearprotocol.ControlCommand
import com.premkumar.jiwtracker.wearprotocol.WearProtocol

/**
 * Receives pause / skip / stop from the watch and forwards them to the running session.
 *
 * Note the guard in [onMessageReceived]: commands are only forwarded while a session is
 * already active. Apps targeting Android 12+ cannot start a foreground service from the
 * background, so a watch message can never be allowed to *start* a session — only to control
 * one the user already started on the phone.
 */
class WatchControlListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearProtocol.PATH_CONTROL) return

        val command = ControlCommand.fromName(String(messageEvent.data, Charsets.UTF_8))
        if (command == null) {
            Log.w(TAG, "Ignoring unrecognised control command from watch")
            return
        }

        val isSessionRunning =
            WalkingForegroundService.currentState.value is WalkingForegroundService.ServiceState.Active
        if (!isSessionRunning) {
            Log.d(TAG, "Ignoring $command from watch: no active session to control")
            return
        }

        val action = when (command) {
            ControlCommand.PAUSE -> WalkingForegroundService.ACTION_PAUSE
            ControlCommand.SKIP -> WalkingForegroundService.ACTION_SKIP
            ControlCommand.STOP -> WalkingForegroundService.ACTION_STOP
        }

        try {
            startService(
                Intent(this, WalkingForegroundService::class.java).setAction(action)
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to forward $command to the walking service", e)
        }
    }

    private companion object {
        const val TAG = "WatchControlListener"
    }
}
