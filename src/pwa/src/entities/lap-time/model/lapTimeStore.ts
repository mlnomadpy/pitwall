import { defineStore } from 'pinia'

export interface LapTime {
  lap: number
  time: string
  delta: string
  valid: boolean
  sectors: string[]
  best?: boolean
  outlier?: boolean
}

export const useLapTimeStore = defineStore('lapTime', {
  state: () => ({
    laps: [] as LapTime[],
    isLoading: false,
    error: null as Error | null
  }),
  actions: {
    async fetchLapTimes() {
      this.isLoading = true
      this.error = null
      
      await new Promise(resolve => setTimeout(resolve, 800))
      
      try {
        this.laps = [
          { lap: 5, time: '1:36.450', delta: '-0.120', valid: true, sectors: ['31.2', '35.1', '30.150'] },
          { lap: 4, time: '1:36.570', delta: '+0.400', valid: true, sectors: ['31.4', '35.0', '30.170'] },
          { lap: 3, time: '1:36.170', delta: '-0.800', valid: false, sectors: ['31.1', '34.8', '--'], outlier: true }, 
          { lap: 2, time: '1:36.970', delta: '-2.100', valid: true, sectors: ['31.5', '35.2', '30.270'] },
          { lap: 1, time: '1:39.070', delta: 'OUT', valid: true, sectors: ['--', '36.1', '31.400'], best: true },
        ]
      } catch (e: any) {
        this.error = e
      } finally {
        this.isLoading = false
      }
    }
  }
})
