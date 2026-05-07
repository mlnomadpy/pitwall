import { defineStore } from 'pinia'

export interface LeaderboardEntry {
  rank: number
  initials: string
  car: string
  track: string
  time: string
}

export const useLeaderboardStore = defineStore('leaderboard', {
  state: () => ({
    entries: [] as LeaderboardEntry[],
    isLoading: false,
    error: null as Error | null
  }),
  actions: {
    async fetchLeaderboard() {
      this.isLoading = true
      this.error = null
      
      // Simulate network delay
      await new Promise(resolve => setTimeout(resolve, 800))
      
      try {
        // Mock data
        this.entries = [
          { rank: 1, initials: 'TRD', car: 'GT3_911', track: 'SONOMA', time: '1:34.210' },
          { rank: 2, initials: 'BTY', car: 'M4_GT3', track: 'SONOMA', time: '1:35.050' },
          { rank: 3, initials: 'DRL', car: 'AMG_GT3', track: 'SONOMA', time: '1:35.800' },
          { rank: 4, initials: 'YOU', car: 'GT3_911', track: 'SONOMA', time: '1:36.450' },
          { rank: 5, initials: 'CLM', car: '720S_GT3', track: 'SONOMA', time: '1:37.110' },
          { rank: 6, initials: 'BDY', car: 'M4_GT3', track: 'SONOMA', time: '1:38.000' },
          { rank: 7, initials: 'AI1', car: 'GT3_911', track: 'SONOMA', time: '1:38.500' },
          { rank: 8, initials: 'AI2', car: 'AMG_GT3', track: 'SONOMA', time: '1:39.100' },
        ]
      } catch (e: any) {
        this.error = e
      } finally {
        this.isLoading = false
      }
    }
  }
})
