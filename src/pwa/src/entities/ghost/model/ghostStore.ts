import { defineStore } from 'pinia'

export interface GhostData {
  id: string
  name: string
  car: string
  time: string
  delta: string
  status: 'active' | 'inactive' | 'locked'
}

export const useGhostStore = defineStore('ghost', {
  state: () => ({
    ghosts: [] as GhostData[],
    isLoading: false,
    error: null as Error | null
  }),
  actions: {
    async fetchGhosts() {
      this.isLoading = true
      this.error = null
      
      await new Promise(resolve => setTimeout(resolve, 800))
      
      try {
        this.ghosts = [
          { id: 'g1', name: 'YOUR BEST', car: 'GT3_911', time: '1:36.450', delta: '-', status: 'active' },
          { id: 'g2', name: 'TRD (WR)', car: 'GT3_911', time: '1:34.210', delta: '-2.240', status: 'active' },
          { id: 'g3', name: 'AI_PRO_1', car: 'AMG_GT3', time: '1:35.500', delta: '-0.950', status: 'inactive' },
          { id: 'g4', name: 'FRIEND_01', car: 'M4_GT3', time: '1:37.000', delta: '+0.550', status: 'locked' },
        ]
      } catch (e: any) {
        this.error = e
      } finally {
        this.isLoading = false
      }
    }
  }
})
