import { defineStore } from 'pinia'
import { bridge } from '@/shared/api/bridge'
import { useSessionStore } from '@/entities/session/model/sessionStore'

// ── Types matching GET /session/:sid/lap_time_table response ─────────────────

export interface SectorTime {
  name: string
  time_s: number
  is_best: boolean
}

export interface LapTime {
  lap_number: number
  lap_time_s: number
  delta_to_best_s: number
  is_best: boolean
  sectors: SectorTime[]
}

interface LapTimeTableResponse {
  session_id: string
  lap_count: number
  best_lap_s: number
  best_lap_number: number
  laps: LapTime[]
}

// ── Store ────────────────────────────────────────────────────────────────────

export const useLapTimeStore = defineStore('lapTime', {
  state: () => ({
    laps: [] as LapTime[],
    bestLapS: null as number | null,
    bestLapNumber: null as number | null,
    isLoading: false,
    error: null as string | null,
  }),

  getters: {
    /** Best lap formatted as M:SS.mmm */
    bestFormatted: (state) => {
      if (!state.bestLapS) return '--:--.---'
      const mins = Math.floor(state.bestLapS / 60)
      const secs = (state.bestLapS % 60).toFixed(3)
      return `${mins}:${secs.padStart(6, '0')}`
    },
    /** Laps sorted fastest-first */
    sortedByTime: (state) => [...state.laps].sort((a, b) => a.lap_time_s - b.lap_time_s),
    /** Lap count */
    count: (state) => state.laps.length,
  },

  actions: {
    /**
     * Fetch lap times from the backend.
     * Uses the active session from sessionStore if no sid provided.
     */
    async fetchLapTimes(sid?: string) {
      const sessionStore = useSessionStore()
      const sessionId = sid ?? sessionStore.activeSessionId
      if (!sessionId) {
        this.error = 'No active session'
        return
      }

      this.isLoading = true
      this.error = null

      try {
        const res = await bridge.get<LapTimeTableResponse>(`/session/${sessionId}/lap_time_table`)
        this.laps = res.laps
        this.bestLapS = res.best_lap_s
        this.bestLapNumber = res.best_lap_number
      } catch (e: any) {
        this.error = e.message ?? String(e)
        console.warn('[lapTimeStore] fetchLapTimes failed:', e)
        // Keep existing data on error (graceful degradation)
      } finally {
        this.isLoading = false
      }
    },

    /** Clear lap data (e.g., on session change). */
    clear() {
      this.laps = []
      this.bestLapS = null
      this.bestLapNumber = null
      this.error = null
    },
  }
})
