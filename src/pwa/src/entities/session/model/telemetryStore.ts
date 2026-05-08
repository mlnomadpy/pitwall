import { defineStore } from 'pinia'
import { API_BASE } from '@/shared/config/api'
import { useBridgeStore } from '@/shared/api/bridgeStore'


export interface TelemetryFrame {
  timestamp: number
  distance: number
  speed: number
  g_lat: number
  g_long: number
  combo_g: number
  brake_pressure: number
  throttle: number
  steering: number
  rpm: number
  lat: number
  lon: number
}

export const useTelemetryStore = defineStore('telemetry', {
  state: () => ({
    frame: null as TelemetryFrame | null,
    _es: null as EventSource | null,
    _retryCount: 0,
    _retryTimeout: null as number | null,
    _maxRetries: 10,
    _retryMs: 3000,
    _simInterval: null as number | null,
  }),
  actions: {
    open(sid: string) {
      this.close()
      const bridge = useBridgeStore()
      
      // If bridge is online and reporting an active session, always prefer the real stream
      const sessionToConnect = sid === 'SIM' ? (bridge.health?.active_session_id || 'SIM') : sid
      
      // Only run local fake simulation if we are explicitly in SIM mode AND bridge is unreachable
      if (sid === 'SIM' && !bridge.health) {
        this.startSimulation()
      } else {
        this._connect(sessionToConnect)
      }
    },

    
    startSimulation() {
      this.close()
      let time = 0
      this._simInterval = window.setInterval(() => {
        time += 0.1
        this.frame = {
          timestamp: Date.now(),
          distance: (time * 50) % 4000,
          speed: 120 + Math.sin(time) * 80,
          g_lat: Math.cos(time) * 1.5,
          g_long: Math.sin(time) * 1.0,
          combo_g: Math.sqrt(Math.pow(Math.cos(time)*1.5, 2) + Math.pow(Math.sin(time)*1.0, 2)),
          brake_pressure: Math.sin(time) < 0 ? Math.abs(Math.sin(time)) * 100 : 0,
          throttle: Math.sin(time) > 0 ? Math.abs(Math.sin(time)) * 100 : 0,
          steering: Math.cos(time) * 90,
          rpm: 5000 + Math.abs(Math.sin(time * 2)) * 3000,
          lat: 38.1611,
          lon: -122.4546
        }
      }, 100)
    },
    
    _connect(sid: string) {
      this._es = new EventSource(`${API_BASE}/telemetry/stream?session_id=${sid}`)
      
      this._es.addEventListener('telemetry', (e) => {
        try {
          const data = JSON.parse(e.data)
          if (data && data.timestamp) {
            this.frame = data as TelemetryFrame
            this._retryCount = 0 // reset only on valid payload
          }
        } catch (err) {
          console.error('Failed to parse telemetry', err)
        }
      })
      
      this._es.onerror = () => {
        this.frame = null
        this._es?.close()
        this._es = null
        
        if (this._retryCount < this._maxRetries) {
          this._retryCount++
          console.warn(`[Telemetry] Reconnecting (${this._retryCount}/${this._maxRetries})…`)
          this._retryTimeout = window.setTimeout(() => this._connect(sid), this._retryMs)
        } else {
          console.error(`[Telemetry] Gave up after ${this._maxRetries} retries`)
        }
      }
    },
    
    close() {
      if (this._retryTimeout) clearTimeout(this._retryTimeout)
      if (this._simInterval) clearInterval(this._simInterval)
      this._retryTimeout = null
      this._simInterval = null
      this._es?.close()
      this._es = null
      this.frame = null
      this._retryCount = 0
    }
  }
})
