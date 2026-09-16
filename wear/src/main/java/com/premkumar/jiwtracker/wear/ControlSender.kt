package com.premkumar.jiwtracker.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.premkumar.jiwtracker.wearprotocol.ControlCommand
import com.premkumar.jiwtracker.wearprotocol.WearProtocol
import kotlinx.coroutines.tasks.await

/**
 * Sends pause / skip / stop to the phone.
 *
 * MessageClient rather than DataClient on purpose: these are fire-and-forget commands where
 * low latency matters and a delivery that arrives minutes late would be actively wrong.
 */
object ControlSender {

    private const val TAG = "ControlSender"

    suspend fun send(context: Context, command: ControlCommand): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.d(TAG, "No connected phone to send $command to")
                return false
            }
            val payload = command.name.toByteArray(Charsets.UTF_8)
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, WearProtocol.PATH_CONTROL, payload)
                    .await()
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to send $command to phone: ${e.message}")
            false
        }
    }
}
