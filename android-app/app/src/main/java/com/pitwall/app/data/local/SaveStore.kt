package com.pitwall.app.data.local

import android.content.Context
import com.pitwall.app.data.remote.SessionSummaryDto
import com.pitwall.app.di.SessionHolder
import com.pitwall.app.entities.save.SaveSettingsSlot
import com.pitwall.app.entities.save.SaveSlot
import com.pitwall.app.entities.save.SessionSummarySlot
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Three-slot persistence aligned with the PWA IndexedDB save slots ([useSaveStore]).
 */
object SaveStore {

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private const val PREFS = "pitwall_save"

    private val slots: Array<SaveSlot?> = arrayOfNulls(3)

    var activeSlotId: Int? = null
        private set

    /** Bumped on any slot/settings change so [PitwallTheme] recomposes (night mode, motion, etc.). */
    private val _uiRevision = MutableStateFlow(0L)
    val uiRevision: StateFlow<Long> = _uiRevision.asStateFlow()

    private fun bumpUi() {
        _uiRevision.value = _uiRevision.value + 1L
    }

    fun hydrate(context: Context) {
        val p = prefs(context)
        activeSlotId = p.getString(KEY_ACTIVE, null)?.toIntOrNull()?.takeIf { it in 1..3 }
        for (i in 1..3) {
            val raw = p.getString(slotKey(i), null) ?: continue
            runCatching { slots[i - 1] = json.decodeFromString<SaveSlot>(raw) }
        }
        syncSessionDriver()
        bumpUi()
    }

    fun activeSlot(): SaveSlot? = activeSlotId?.let { slots.getOrNull(it - 1) }

    fun slot(slotId: Int): SaveSlot? = slots.getOrNull(slotId - 1)

    fun setActiveSlot(context: Context, slotId: Int?) {
        activeSlotId = slotId?.takeIf { it in 1..3 }
        prefs(context).edit().apply {
            if (activeSlotId == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, activeSlotId.toString())
            apply()
        }
        syncSessionDriver()
        bumpUi()
    }

    fun upsertSlot(context: Context, slot: SaveSlot) {
        require(slot.id in 1..3)
        slots[slot.id - 1] = slot
        prefs(context).edit().putString(slotKey(slot.id), json.encodeToString(slot)).apply()
        syncSessionDriver()
        bumpUi()
    }

    fun touchSlot(context: Context, slotId: Int) {
        val cur = slots.getOrNull(slotId - 1) ?: return
        upsertSlot(context, cur.copy(lastPlayedAt = Instant.now().toString()))
    }

    fun deleteSlot(context: Context, slotId: Int) {
        require(slotId in 1..3)
        slots[slotId - 1] = null
        prefs(context).edit().remove(slotKey(slotId)).apply()
        if (activeSlotId == slotId) {
            activeSlotId = null
            prefs(context).edit().remove(KEY_ACTIVE).apply()
        }
        syncSessionDriver()
        bumpUi()
    }

    fun updateSettings(context: Context, transform: (SaveSettingsSlot) -> SaveSettingsSlot) {
        val id = activeSlotId ?: return
        val cur = slots.getOrNull(id - 1) ?: return
        upsertSlot(
            context,
            cur.copy(
                settings = transform(cur.settings),
                lastPlayedAt = Instant.now().toString(),
            ),
        )
    }

    fun updatePreferredCoach(context: Context, coachId: String) {
        val id = activeSlotId ?: return
        val cur = slots.getOrNull(id - 1) ?: return
        upsertSlot(context, cur.copy(preferredCoach = coachId, lastPlayedAt = Instant.now().toString()))
    }

    /**
     * Union Flask `GET /sessions` into the active save slot’s [SaveSlot.sessions] list (PWA parity).
     * Existing rows are updated by [SessionSummarySlot.sessionId]; newer bridge sessions are appended.
     */
    fun mergeSessionsFromBridge(context: Context, fromBridge: List<SessionSummaryDto>) {
        if (fromBridge.isEmpty()) return
        val id = activeSlotId ?: return
        val cur = slots.getOrNull(id - 1) ?: return
        val coachId = cur.preferredCoach
        val mergedById = cur.sessions.associateBy { it.sessionId }.toMutableMap()
        for (s in fromBridge) {
            val trackKey =
                s.track.trim().lowercase().replace(Regex("\\s+"), "_").ifEmpty { "unknown" }
            mergedById[s.sessionId] =
                SessionSummarySlot(
                    sessionId = s.sessionId,
                    startedAt = s.startedAt.orEmpty(),
                    trackId = trackKey,
                    bestLapS = s.bestLapS,
                    lapCount = s.lapCount,
                    coachId = coachId,
                )
        }
        val merged =
            mergedById.values
                .sortedWith(compareByDescending<SessionSummarySlot> { it.startedAt }.thenByDescending { it.sessionId })
                .take(200)
        upsertSlot(
            context,
            cur.copy(sessions = merged, lastPlayedAt = Instant.now().toString()),
        )
    }

    fun persistActiveDriverName(context: Context, driverName: String) {
        val id = activeSlotId ?: return
        val cur = slots.getOrNull(id - 1) ?: return
        val trimmed = driverName.trim().ifEmpty { "DRIVER" }
        SessionHolder.activeDriver = trimmed
        upsertSlot(context, cur.copy(driverName = trimmed, lastPlayedAt = Instant.now().toString()))
    }

    fun newSlotFromOnboarding(
        context: Context,
        slotId: Int,
        driverName: String,
        skillLevel: String,
    ) {
        val now = Instant.now().toString()
        val slot =
            SaveSlot(
                id = slotId,
                createdAt = now,
                lastPlayedAt = now,
                driverName = driverName.trim().ifEmpty { "DRIVER" },
                skillLevel = skillLevel,
            )
        upsertSlot(context, slot)
        setActiveSlot(context, slotId)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun slotKey(id: Int) = "slot_$id"

    private const val KEY_ACTIVE = "active_slot_id"

    private fun syncSessionDriver() {
        val name = activeSlot()?.driverName?.trim()?.takeIf { it.isNotEmpty() }
        if (name != null) {
            SessionHolder.activeDriver = name
        }
    }
}
