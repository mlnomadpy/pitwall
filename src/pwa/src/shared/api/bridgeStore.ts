import { defineStore } from 'pinia'

export interface HealthResponse {
  status: string
  version: string
  can_bridge: boolean
}

export const useBridgeStore = defineStore('bridge', {
  state: () => ({
    health: null as HealthResponse | null,
    healthFetchedAt: 0,
    healthError: null as string | null,
    consecutiveFailures: 0,
    isPolling: false,
    mockOffline: false // for dev testing
  }),
  actions: {
    async pollHealth() {
      if (this.mockOffline) {
        this.healthError = 'MOCK_OFFLINE'
        this.consecutiveFailures++
        return
      }
      
      try {
        const r = await fetch('http://127.0.0.1:8765/health')
        if (!r.ok) throw new Error(`HTTP ${r.status}`)
        this.health = await r.json()
        this.healthFetchedAt = Date.now()
        this.healthError = null
        this.consecutiveFailures = 0
      } catch (e) {
        this.healthError = String(e)
        this.consecutiveFailures++
      }
    },
    
    startPolling() {
      if (this.isPolling) return
      this.isPolling = true
      
      // Initial poll
      this.pollHealth()
      
      // Set interval
      setInterval(() => {
        this.pollHealth()
      }, 5000)
    },
    
    toggleMockOffline() {
      this.mockOffline = !this.mockOffline
      if (this.mockOffline) {
        // Immediately bump failures to test banner appearance faster
        this.consecutiveFailures = 3
        this.healthError = 'MOCK_OFFLINE'
      } else {
        // Immediately recover
        this.consecutiveFailures = 0
        this.healthError = null
      }
    }
  }
})
