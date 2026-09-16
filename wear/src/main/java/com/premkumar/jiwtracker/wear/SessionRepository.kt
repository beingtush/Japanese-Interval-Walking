package com.premkumar.jiwtracker.wear

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.premkumar.jiwtracker.wearprotocol.IntervalSchedule
import com.premkumar.jiwtracker.wearprotocol.SessionSnapshot
import com.premkumar.jiwtracker.wearprotocol.SnapshotCodec
import com.premkumar.jiwtracker.wearprotocol.WearProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Single source of truth on the watch: the most recent snapshot from the phone, plus the local
 * clock reading at the moment it arrived.
 *
 * Everything displayed is derived by projecting that anchor forward. Nothing is stored, and
 * the watch never computes health data of its own — the phone remains the only thing counting.
 */
object SessionRepository {

    private const val TAG = "SessionRepository"

    data class Anchored(
        val snapshot: SessionSnapshot,
        /**
         * From [SystemClock.elapsedRealtime], NOT wall clock. The phone's and watch's wall
         * clocks can disagree by seconds, which would show a visibly wrong countdown.
         * Anchoring on local monotonic time at receipt sidesteps that entirely; the cost is
         * ignoring transmission latency, which is sub-second over Bluetooth and is re-corrected
         * by the next snapshot.
         */
        val receivedAtRealtime: Long,
        /** False until the first snapshot of this process arrives. */
        val everReceived: Boolean
    )

    private val _anchor = MutableStateFlow(
        Anchored(SessionSnapshot.IDLE, SystemClock.elapsedRealtime(), everReceived = false)
    )
    val anchor: StateFlow<Anchored> = _anchor.asStateFlow()

    /**
     * Accepts every snapshot that arrives. The phone publishes a single DataClient item, so the
     * system already delivers latest-write-wins; adding a sequence guard here would only risk
     * rejecting valid state after a phone restart resets the counter.
     */
    fun update(snapshot: SessionSnapshot) {
        _anchor.value = Anchored(snapshot, SystemClock.elapsedRealtime(), everReceived = true)
    }

    /** The session as it stands right now, projected forward from the anchor. */
    fun projectNow(): SessionSnapshot = project(_anchor.value, SystemClock.elapsedRealtime())

    fun project(anchored: Anchored, nowRealtime: Long): SessionSnapshot {
        val secondsSinceAnchor = (nowRealtime - anchored.receivedAtRealtime) / 1000L
        return IntervalSchedule.project(anchored.snapshot, secondsSinceAnchor)
    }

    /**
     * Reads the DataItem that already exists when the watch app opens.
     *
     * Without this, opening the app mid-session shows nothing until the phone's next push —
     * up to a heartbeat interval of staring at a blank watch, which is exactly the problem
     * this app exists to remove.
     */
    suspend fun primeFrom(context: Context) {
        try {
            val buffer = Wearable.getDataClient(context)
                .getDataItems(Uri.parse("wear://*${WearProtocol.PATH_SESSION}"))
                .await()
            try {
                val latest = buffer
                    .mapNotNull { item -> SnapshotCodec.fromDataMap(DataMapItem.fromDataItem(item).dataMap) }
                    .maxByOrNull { it.sequence }
                if (latest != null) update(latest)
            } finally {
                buffer.release()
            }
        } catch (e: Throwable) {
            Log.d(TAG, "No existing session item to prime from: ${e.message}")
        }
    }
}
