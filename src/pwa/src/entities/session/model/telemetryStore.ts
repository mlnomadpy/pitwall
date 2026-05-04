import { defineStore } from 'pinia'
import { API_BASE } from '@/shared/config/api'

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
  }),
  actions: {
    open(sid: string) {
      this.close()
      this._connect(sid)
    },
    
    _connect(sid: string) {
      this._es = new EventSource(`${API_BASE}/telemetry/stream?session_id=${sid}`)
      
      this._es.addEventListener('telemetry', (e) => {
        try {
          this.frame = JSON.parse(e.data) as TelemetryFrame
          this._retryCount = 0 // reset on successful message
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
      this._retryTimeout = null
      this._es?.close()
      this._es = null
      this.frame = null
      this._retryCount = 0
    }
  }
})
