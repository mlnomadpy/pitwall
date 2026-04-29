package com.pitwall.app.arbiter

import android.util.Log
import com.pitwall.app.data.CoachingMessage
import com.pitwall.app.data.TelemetryFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs

private const val TAG = "MessageArbiter"

/**
 * Message Arbiter — coordinates all coaching output from both paths.
 *
 * Rules (from coaching-engine.md):
 *   P3 Safety     → deliver immediately, interrupt everything
 *   P2 Technique  → hold until |gLat| < 0.3G (on straight), max wait 5s
 *   P1 Strategy   → queue behind P2
 *   Conflict      → same corner from both paths: higher priority wins; tie → hot path wins
 *   Cooldown      → 3s minimum between deliveries
 *   Stale expiry  → drop messages > 5s in queue
 */
class MessageArbiter {

    private val queue = mutableListOf<CoachingMessage>()
    private var lastDeliveredAt = 0L
    private var lastDeliveredCorner: String? = null

    private val _delivered = MutableSharedFlow<CoachingMessage>(extraBufferCapacity = 16)
    val delivered: SharedFlow<CoachingMessage> = _delivered.asSharedFlow()

    /**
     * Submit a message from either hot path or warm path.
     * Thread-safe — call from any coroutine.
     */
    @Synchronized
    fun submit(message: CoachingMessage) {
        // Conflict de-dupe: if we already have a message for the same corner,
        // keep the higher-priority one; tie goes to hot path (fresher data).
        if (message.targetCorner != null) {
            val existing = queue.indexOfFirst { it.targetCorner == message.targetCorner }
            if (existing >= 0) {
                val incumbent = queue[existing]
                val keepNew = message.priority > incumbent.priority ||
                        (message.priority == incumbent.priority &&
                                message.source == CoachingMessage.Source.HOT_PATH)
                if (keepNew) {
                    queue[existing] = message
                    Log.d(TAG, "Replaced ${incumbent.source} P${incumbent.priority} with ${message.source} P${message.priority} for ${message.targetCorner}")
                }
                return
            }
        }
        queue += message
        queue.sortByDescending { it.priority }  // highest priority first
    }

    /**
     * Evaluate the queue against the current telemetry frame.
     * Called every frame (10Hz) by PitwallService.
     * Returns a message to deliver, or null.
     */
    @Synchronized
    fun evaluate(frame: TelemetryFrame): CoachingMessage? {
        // Prune stale messages
        queue.removeAll { it.isStale }

        if (queue.isEmpty()) return null

        val now = System.currentTimeMillis()
        val onStraight = abs(frame.gLat.value) < 0.3f

        val candidate = queue.firstOrNull { msg ->
            when (msg.priority) {
                3 -> true                        // P3: always deliver
                2 -> onStraight                  // P2: only on straight
                1 -> onStraight && queue.none { it.priority >= 2 }  // P1: when no P2 pending
                else -> false
            }
        } ?: return null

        // Cooldown check (P3 bypasses cooldown)
        if (candidate.priority < 3 && now - lastDeliveredAt < CoachingMessage.COOLDOWN_MS) {
            return null
        }

        queue.remove(candidate)
        lastDeliveredAt = now
        lastDeliveredCorner = candidate.targetCorner

        Log.i(TAG, "Delivering P${candidate.priority} [${candidate.source}]: \"${candidate.text}\"")
        _delivered.tryEmit(candidate)
        return candidate
    }

    @Synchronized
    fun reset() {
        queue.clear()
        lastDeliveredAt = 0L
        lastDeliveredCorner = null
    }
}
