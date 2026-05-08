import { defineStore } from 'pinia'
import { bridge } from '@/shared/api/bridge'

// ── Types ────────────────────────────────────────────────────────────────────

export interface Quest {
  id: string
  title: string
  desc: string
  progress: number
  target: number
  xp: number
  completed: boolean
}

export interface Insight {
  id: string
  rank: number
  title: string
  detail: string
  corners: string[]
  metric_label: string
  metric_value: string
  effort: number
  est_gain_s: number
  evidence_bursts: number
}

interface InsightsResponse {
  insights: Insight[]
  session_bursts: number
  generated_at: string
}

// ── Store ────────────────────────────────────────────────────────────────────

export const useQuestStore = defineStore('quest', {
  state: () => ({
    daily: [] as Quest[],
    weekly: [] as Quest[],
    insights: [] as Insight[],
    isLoading: false,
    error: null as string | null,
  }),

  actions: {
    /**
     * Fetch quests: tries backend /insights first,
     * maps insights to Quest-shaped goals. Falls back to static quests
     * if the bridge is unavailable.
     */
    async fetchQuests() {
      this.isLoading = true
      this.error = null

      try {
        // Try to get real coaching insights from the backend
        const res = await bridge.get<InsightsResponse>('/insights')
        
        // Map insights to quest-shaped daily goals
        this.daily = res.insights.map((insight, i) => ({
          id: insight.id,
          title: insight.title.toUpperCase(),
          desc: insight.detail,
          progress: 0,
          target: 1,
          xp: Math.round(insight.est_gain_s * 100),
          completed: false,
        }))

        this.insights = res.insights

        // If no insights returned, provide sensible defaults
        if (this.daily.length === 0) {
          this._loadFallbackQuests()
        }
      } catch (e: any) {
        // Bridge offline — use fallback quests
        console.warn('[questStore] Bridge unavailable, using fallback quests:', e.message)
        this._loadFallbackQuests()
      } finally {
        this.isLoading = false
      }
    },

    /** Static fallback quests when backend is unavailable. */
    _loadFallbackQuests() {
      this.daily = [
        { id: 'd1', title: 'CLEAN SECTOR', desc: 'Complete Sector 1 at Sonoma with 0 off-tracks.', progress: 0, target: 1, xp: 50, completed: false },
        { id: 'd2', title: 'CONSISTENCY', desc: 'String 3 consecutive laps within 0.5s of each other.', progress: 0, target: 3, xp: 100, completed: false },
        { id: 'd3', title: 'LATE BRAKER', desc: 'Brake at the 50m board into Turn 11.', progress: 0, target: 1, xp: 75, completed: false },
      ]
      this.weekly = [
        { id: 'w1', title: 'TRACK DAY', desc: 'Complete 50 total laps at any track.', progress: 0, target: 50, xp: 500, completed: false },
        { id: 'w2', title: 'COACH AFFINITY', desc: 'Reach Level 3 trust with any Coach.', progress: 0, target: 3, xp: 400, completed: false },
        { id: 'w3', title: 'PODIUM', desc: 'Place Top 3 on the Global Leaderboard.', progress: 0, target: 1, xp: 1000, completed: false },
      ]
    },
  }
})
