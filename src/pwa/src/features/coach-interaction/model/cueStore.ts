import { defineStore } from 'pinia'
import { API_BASE } from '@/shared/config/api'

export interface Cue {
  id: string
  text: string
  emotion: string
  timestamp: number
}

export const useCueStore = defineStore('cue', {
  state: () => ({
    activeCue: null as Cue | null,
    queue: [] as Cue[],
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
      this._es = new EventSource(`${API_BASE}/cues/stream?session_id=${sid}`)
      
      this._es.onmessage = (e) => {
        const cue = JSON.parse(e.data) as Cue
        this.queue.push(cue)
        this._retryCount = 0 // reset on successful message
      }
      
      this._es.onerror = () => {
        this.activeCue = null
        this._es?.close()
        this._es = null
        
        if (this._retryCount < this._maxRetries) {
          this._retryCount++
          console.warn(`[Cues] Reconnecting (${this._retryCount}/${this._maxRetries})…`)
          this._retryTimeout = window.setTimeout(() => this._connect(sid), this._retryMs)
        } else {
          console.error(`[Cues] Gave up after ${this._maxRetries} retries`)
        }
      }
    },
    
    close() {
      if (this._retryTimeout) clearTimeout(this._retryTimeout)
      this._retryTimeout = null
      this._es?.close()
      this._es = null
      this._retryCount = 0
    }
  }
})
