package com.pitwall.bridge.ktor.embedded

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Live CAN / USB snapshot for `GET /signals/registry?include_can_state=true`,
 * shaped like Flask [`can_state_snapshot()`](../../../../../src/pitwall/features/bp_diagnostics.py).
 */
object EmbeddedNativeCanState {

    private val framesTotal = AtomicLong(0)
    private val framesUnknown = AtomicLong(0)
    private val unknownIdCounts = ConcurrentHashMap<Long, AtomicLong>()
    private val windowTimestampsMs = ArrayDeque<Long>(600)

    private val usbLoaded = AtomicBoolean(false)
    private var channelLabel: String? = null
    private val lastFrameWallMs = AtomicLong(0L)

    private const val FPS_WINDOW_MS = 5000L

    fun setUsbSerialOpen(opened: Boolean, channel: String?) {
        usbLoaded.set(opened)
        channelLabel = channel
        if (!opened) {
            lastFrameWallMs.set(0L)
        }
    }

    /** Every successfully parsed slCAN frame (before DBC decode). Matches Flask frame counters. */
    fun recordFrameReceived() {
        framesTotal.incrementAndGet()
        val now = System.currentTimeMillis()
        lastFrameWallMs.set(now)
        synchronized(windowTimestampsMs) {
            windowTimestampsMs.addLast(now)
            trimWindowLocked(now)
        }
    }

    fun recordUnknownId(arbitrationId: Long) {
        framesUnknown.incrementAndGet()
        unknownIdCounts.computeIfAbsent(arbitrationId) { AtomicLong(0) }.incrementAndGet()
    }

    fun resetCounters() {
        framesTotal.set(0)
        framesUnknown.set(0)
        unknownIdCounts.clear()
        synchronized(windowTimestampsMs) { windowTimestampsMs.clear() }
        lastFrameWallMs.set(0L)
    }

    private fun trimWindowLocked(nowMs: Long) {
        while (windowTimestampsMs.isNotEmpty() && nowMs - windowTimestampsMs.first() > FPS_WINDOW_MS) {
            windowTimestampsMs.removeFirst()
        }
    }

    /**
     * @param androidContext When non-null (e.g. [StandaloneBridgeRuntime.context]), fills **`usb_devices`**
     * from [UsbManager.getDeviceList] — optional parity with Flask [`_detect_usb_can_devices`](../../../../../src/pitwall/features/bp_diagnostics.py).
     */
    fun snapshotJson(activeSessionId: String?, androidContext: Context? = null): JsonObject {
        val now = System.currentTimeMillis()
        val framesWindow: Int
        synchronized(windowTimestampsMs) {
            trimWindowLocked(now)
            framesWindow = windowTimestampsMs.size
        }
        val fps = if (framesWindow > 0) framesWindow / 5.0 else 0.0
        val lastMs = lastFrameWallMs.get()
        val lastAge =
            if (lastMs > 0L) {
                (now - lastMs) / 1000.0
            } else {
                null
            }
        val connected =
            usbLoaded.get() &&
                lastAge != null &&
                lastAge <= 5.0

        val unknownTop =
            unknownIdCounts.entries
                .sortedByDescending { it.value.get() }
                .take(10)
                .map { (id, cnt) ->
                    buildJsonObject {
                        put("can_id", JsonPrimitive("0x${id.toString(16)}"))
                        put("count", JsonPrimitive(cnt.get()))
                    }
                }

        val usbDevices: JsonArray = buildUsbDevicesJson(androidContext)

        return buildJsonObject {
            put("loaded", JsonPrimitive(usbLoaded.get()))
            put("connected", JsonPrimitive(connected))
            put("interface", JsonPrimitive("android-usb-slcan"))
            put("channel", JsonPrimitive(channelLabel ?: ""))
            put("bitrate", JsonPrimitive(500_000))
            put("session_id", JsonPrimitive(activeSessionId ?: ""))
            put("frames_total", JsonPrimitive(framesTotal.get()))
            put("frames_unknown", JsonPrimitive(framesUnknown.get()))
            put("frames_per_second", JsonPrimitive(kotlin.math.round(fps * 10) / 10.0))
            put(
                "last_frame_age_s",
                if (lastAge != null) JsonPrimitive(kotlin.math.round(lastAge * 100) / 100.0) else JsonNull,
            )
            put("unknown_ids", JsonArray(unknownTop))
            put("usb_devices", usbDevices)
        }
    }

    private fun buildUsbDevicesJson(androidContext: Context?): JsonArray {
        if (androidContext == null) return buildJsonArray { }
        val mgr = androidContext.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return buildJsonArray { }
        val devices = mgr.deviceList.values
        if (devices.isEmpty()) return buildJsonArray { }
        return buildJsonArray {
            for (d in devices) {
                add(usbDeviceToJson(d))
            }
        }
    }

    /** Flask-shaped entries for Pit Stall (`device`, `vid`, `pid`, …). */
    private fun usbDeviceToJson(d: UsbDevice): JsonObject {
        val vid = d.vendorId
        val pid = d.productId
        val desc =
            listOfNotNull(d.productName?.takeIf { it.isNotBlank() }, d.manufacturerName?.takeIf { it.isNotBlank() })
                .joinToString(" ")
                .trim()
        return buildJsonObject {
            put("device", JsonPrimitive(d.deviceName))
            put("vid", JsonPrimitive(hexVidPid(vid)))
            put("pid", JsonPrimitive(hexVidPid(pid)))
            put("description", JsonPrimitive(desc))
            put("manufacturer", JsonPrimitive(d.manufacturerName ?: ""))
            put("model", JsonPrimitive(d.productName ?: "USB device"))
            put("kind", JsonPrimitive("usb"))
            put("is_known", JsonPrimitive(false))
        }
    }

    private fun hexVidPid(id: Int): String {
        val u = id and 0xFFFF
        return "0x${u.toString(16).padStart(4, '0')}"
    }
}
