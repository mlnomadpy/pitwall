package com.pitwall.app.bridge

import com.pitwall.app.data.remote.SessionSummaryDto
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory sessions for the Kotlin bridge (replaces Python+DuckDB until native persistence lands). */
class SessionRepository {
    private val sessions = ConcurrentHashMap<String, SessionSummaryDto>()

    fun all(): List<SessionSummaryDto> = sessions.values.toList()

    operator fun get(id: String): SessionSummaryDto? = sessions[id]

    fun upsert(summary: SessionSummaryDto) {
        sessions[summary.sessionId] = summary
    }

    fun remove(id: String) {
        sessions.remove(id)
    }

    companion object {
        fun newSessionId(): String = "pitwall-android-${UUID.randomUUID().toString().take(8)}"
    }
}
