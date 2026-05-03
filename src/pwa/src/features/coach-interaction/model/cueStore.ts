import { defineStore } from 'pinia'

export interface Cue {
  id: string
  text: string
  emotion: string
  timestamp: number
}

export const useCueStore = defineStore('cue', {
  state: () => ({
    es: null as EventSource | null,
    activeCue: null as Cue | null,
    queue: [] as Cue[],
  }),
  actions: {
    open(sid: string) {
      this.es?.close()
      this.es = new EventSource(`http://127.0.0.1:8765/cues/stream?session_id=${sid}`)
      
      this.es.onmessage = (e) => {
        const cue = JSON.parse(e.data) as Cue
        this.queue.push(cue)
      }
      
      this.es.onerror = () => {
        this.activeCue = null
      }
    },
    close() {
      this.es?.close()
      this.es = null
    }
  }
})
