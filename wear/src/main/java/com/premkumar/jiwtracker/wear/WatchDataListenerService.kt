package com.premkumar.jiwtracker.wear

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.premkumar.jiwtracker.wearprotocol.SnapshotCodec
import com.premkumar.jiwtracker.wearprotocol.WearProtocol

/** Receives session snapshots pushed by the phone and hands them to [SessionRepository]. */
class WatchDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != WearProtocol.PATH_SESSION) continue

            val snapshot = SnapshotCodec.fromDataMap(
                DataMapItem.fromDataItem(event.dataItem).dataMap
            )
            if (snapshot == null) {
                Log.w(TAG, "Dropping session item written by an incompatible protocol version")
                continue
            }

            SessionRepository.update(snapshot)

            // Keep the foreground service in step with the session's liveness so phase haptics
            // fire with the screen off, and so it stops as soon as the session ends.
            if (snapshot.active) {
                tryStartSessionService(this)
            } else {
                stopService(Intent(this, WatchSessionService::class.java))
            }
        }
    }

    private fun tryStartSessionService(context: Context) {
        // Apps targeting Android 12+ cannot start a foreground service from the background, and
        // a Data Layer callback IS the background. When the user has the watch app open the
        // activity starts it instead; this attempt just covers the cases where it is allowed.
        try {
            val intent = Intent(context, WatchSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Could not start session service from background: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "WatchDataListener"
    }
}
