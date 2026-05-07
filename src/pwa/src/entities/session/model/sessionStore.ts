import { defineStore } from 'pinia'

export const useSessionStore = defineStore('session', {
  state: () => ({
    activeSessionId: null as string | null,
    lapCount: 0,
    goalsHit: 0,
    goalsTotal: 0,
  }),
  actions: {
    startSession(sid: string) {
      this.activeSessionId = sid
      this.lapCount = 0
      this.goalsHit = 0
      this.goalsTotal = 0
    },
    endSession() {
      this.activeSessionId = null
    }
  }
})
