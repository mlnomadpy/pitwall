import { defineStore } from 'pinia'
import type { CoachId } from '@/shared/types/save'

export const useCoachStore = defineStore('coach', {
  state: () => ({
    activeCoach: 'trod' as CoachId,
    concepts: [] as string[]
  }),
  actions: {
    setCoach(coachId: CoachId) {
      this.activeCoach = coachId
    }
  }
})
