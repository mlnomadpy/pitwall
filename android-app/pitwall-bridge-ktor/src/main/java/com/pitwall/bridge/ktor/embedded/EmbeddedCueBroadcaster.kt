package com.pitwall.bridge.ktor.embedded

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Fan-out SSE cues per session_id (mirrors Flask cue_bus.publish).
 */
class EmbeddedCueBroadcaster {

    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<LinkedBlockingQueue<String>>>()

    fun subscribe(sessionId: String): LinkedBlockingQueue<String> {
        val q = LinkedBlockingQueue<String>(256)
        subscribers.compute(sessionId) { _, list ->
            val l = list ?: CopyOnWriteArrayList()
            l.add(q)
            l
        }
        return q
    }

    fun unsubscribe(sessionId: String, q: LinkedBlockingQueue<String>) {
        subscribers[sessionId]?.remove(q)
    }

    fun publish(sessionId: String, jsonPayload: String) {
        subscribers[sessionId]?.forEach { it.offer(jsonPayload) }
    }

    companion object {
        fun heartbeatComment(): String = ": ping\n\n"

        fun formatEvent(dataJson: String): String = "data: $dataJson\n\n"

        fun drainWithTimeout(q: LinkedBlockingQueue<String>, timeoutMs: Long): String? =
            q.poll(timeoutMs, TimeUnit.MILLISECONDS)
    }
}
