package com.pitwall.bridge.ktor.embedded

import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the active [StandaloneBridgeRuntime] so native ingest (USB CAN / slcan) can append
 * telemetry without a second DuckDB connection. Set from [com.pitwall.bridge.ktor.PitwallEmbeddedBridge].
 */
object EmbeddedBridgeRegistry {

    private val runtimeRef = AtomicReference<StandaloneBridgeRuntime?>(null)

    internal fun attach(runtime: StandaloneBridgeRuntime) {
        runtimeRef.set(runtime)
    }

    internal fun detach() {
        runtimeRef.set(null)
    }

    fun isAttached(): Boolean = runtimeRef.get() != null

    /**
     * Append decoded telemetry rows to the active session (or create `usb-can-*` if none).
     * Returns rows appended, or 0 if bridge not running or empty input.
     */
    suspend fun appendTelemetryFrames(rows: List<EmbeddedDuckDb.FrameRow>): Int {
        if (rows.isEmpty()) return 0
        val rt = runtimeRef.get() ?: return 0
        val sid =
            rt.activeSessionId.get() ?: run {
                val newId = newSessionId("usb-can")
                rt.activeSessionId.set(newId)
                rt.sessions.ensureSession(
                    newId,
                    driver = null,
                    driverLevel = "intermediate",
                    track = "sonoma",
                    car = "usb-slcan",
                    note = "native USB-CAN ingest",
                )
                newId
            }
        return rt.duck.withConnection {
            rt.duck.appendFrames(this, sid, rows)
        }
    }

    /**
     * USB-CAN path: optional wide row (10 Hz throttled) + ADR-015 [`telemetry_signals`](../../../../../../src/pitwall/db.py) rows.
     */
    suspend fun appendUsbCanIngest(
        wideRow: EmbeddedDuckDb.FrameRow?,
        tallSamples: List<Triple<String, Double, Double>>,
    ): Pair<Int, Int> {
        val rt = runtimeRef.get() ?: return 0 to 0
        if (wideRow == null && tallSamples.isEmpty()) return 0 to 0
        val sid =
            rt.activeSessionId.get() ?: run {
                val newId = newSessionId("usb-can")
                rt.activeSessionId.set(newId)
                rt.sessions.ensureSession(
                    newId,
                    driver = null,
                    driverLevel = "intermediate",
                    track = "sonoma",
                    car = "usb-slcan",
                    note = "native USB-CAN ingest",
                )
                newId
            }
        return rt.duck.withConnection {
            var w = 0
            var t = 0
            if (wideRow != null) {
                w = rt.duck.appendFrames(this, sid, listOf(wideRow))
            }
            if (tallSamples.isNotEmpty()) {
                t = rt.duck.appendTelemetrySignals(this, sid, tallSamples)
            }
            w to t
        }
    }
}
