import { defineStore } from 'pinia'

export interface Quest {
  id: string
  title: string
  desc: string
  progress: number
  target: number
  xp: number
  completed: boolean
}

export const useQuestStore = defineStore('quest', {
  state: () => ({
    daily: [] as Quest[],
    weekly: [] as Quest[],
    isLoading: false,
    error: null as Error | null
  }),
  actions: {
    async fetchQuests() {
      this.isLoading = true
      this.error = null
      
      // Simulate network delay
      await new Promise(resolve => setTimeout(resolve, 800))
      
      try {
        this.daily = [
          { id: 'd1', title: 'CLEAN SECTOR', desc: 'Complete Sector 1 at Sonoma with 0 off-tracks.', progress: 1, target: 1, xp: 50, completed: true },
          { id: 'd2', title: 'CONSISTENCY', desc: 'String 3 consecutive laps within 0.5s of each other.', progress: 1, target: 3, xp: 100, completed: false },
          { id: 'd3', title: 'LATE BRAKER', desc: 'Brake at the 50m board into Turn 11.', progress: 0, target: 1, xp: 75, completed: false },
        ]
        
        this.weekly = [
          { id: 'w1', title: 'TRACK DAY', desc: 'Complete 50 total laps at any track.', progress: 32, target: 50, xp: 500, completed: false },
          { id: 'w2', title: 'COACH AFFINITY', desc: 'Reach Level 3 trust with any Coach.', progress: 2, target: 3, xp: 400, completed: false },
          { id: 'w3', title: 'PODIUM', desc: 'Place Top 3 on the Global Leaderboard.', progress: 0, target: 1, xp: 1000, completed: false },
        ]
      } catch (e: any) {
        this.error = e
      } finally {
        this.isLoading = false
      }
    }
  }
})
