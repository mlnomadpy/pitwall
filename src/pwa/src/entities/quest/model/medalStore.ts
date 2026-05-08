import { defineStore } from 'pinia'
import { bridge } from '@/shared/api/bridge'

export interface Medal {
  id: string
  tier: string
  name: string
  desc: string
  unlocked: boolean
}

/**
 * MedalStore — shared medal data for QuestLog and TrainerCard.
 * 
 * Attempts to fetch real medal data from GET /session/:sid/scorecard.
 * Falls back to progression-based seed data when backend is offline.
 */
export const useMedalStore = defineStore('medal', {
  state: () => ({
    medals: [] as Medal[],
    isLoading: false,
    error: null as string | null,
  }),

  getters: {
    unlockedCount: (state) => state.medals.filter(m => m.unlocked).length,
    totalCount: (state) => state.medals.length,
    byTier: (state) => (tier: string) => 
      tier === 'ALL' ? state.medals : state.medals.filter(m => m.tier === tier),
  },

  actions: {
    async fetchMedals() {
      if (this.medals.length > 0) return // already loaded
      
      this.isLoading = true
      this.error = null

      try {
        // Try to get session list and derive medals from completed sessions
        const res = await bridge.get<{ sessions: any[]; count: number }>('/sessions?limit=50')
        const sessionCount = res.count || 0
        
        // Generate medals based on real session progress
        this.medals = this._generateProgressMedals(sessionCount)
      } catch {
        // Fallback: generate with 0 sessions (all locked)
        this.medals = this._generateProgressMedals(0)
      } finally {
        this.isLoading = false
      }
    },

    /**
     * Generate medals based on real session count.
     * Tiers unlock progressively as the driver completes more sessions.
     */
    _generateProgressMedals(sessionCount: number): Medal[] {
      const tiers = [
        { name: 'BRONZE', count: 5, threshold: 1 },
        { name: 'SILVER', count: 15, threshold: 5 },
        { name: 'GOLD', count: 15, threshold: 15 },
        { name: 'PLATINUM', count: 4, threshold: 30 },
        { name: 'RAINBOW', count: 1, threshold: 50 },
      ]

      const medalNames: Record<string, string[]> = {
        BRONZE: ['First Lap', 'Ignition', 'Rolling Start', 'Clean Lap', 'On Track'],
        SILVER: ['Brake Master', 'Trail Blazer', 'Apex Hunter', 'Line Finder', 'Speed Demon',
                 'Consistency', 'Smooth Operator', 'Corner Cutter', 'G-Force', 'Throttle Control',
                 'Sector King', 'Fast Learner', 'Night Owl', 'Dawn Patrol', 'Track Rat'],
        GOLD: ['Perfect Sector', 'Zero Coast', 'Maximum Attack', 'Silk Smooth', 'Maestro',
               'Corner Mastery', 'Brake Point Pro', 'Exit Speed', 'Trail Brake', 'Vision',
               'Zen Mode', 'Flow State', 'Limit Finder', 'Grip Monster', 'Pedal Poet'],
        PLATINUM: ['Sonoma Master', 'Coach Favorite', 'Legend', 'Ultimate'],
        RAINBOW: ['Transcendence'],
      }

      const medals: Medal[] = []
      let idx = 0
      for (const tier of tiers) {
        const names = medalNames[tier.name] || []
        for (let i = 0; i < tier.count; i++) {
          medals.push({
            id: `medal_${idx}`,
            tier: tier.name,
            name: names[i] || `${tier.name} ${i + 1}`,
            desc: `Earned by completing ${tier.threshold} sessions at this level.`,
            unlocked: sessionCount >= tier.threshold && i < Math.ceil(sessionCount / tier.threshold),
          })
          idx++
        }
      }
      return medals
    },
  }
})
