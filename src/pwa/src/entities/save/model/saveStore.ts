import { defineStore } from 'pinia'
import { get, set } from 'idb-keyval'
import type { SaveSlot } from '@/shared/types/save'

export const useSaveStore = defineStore('save', {
  state: () => ({
    slots: [null, null, null] as (SaveSlot | null)[],
    activeSlotId: null as 1 | 2 | 3 | null,
  }),
  getters: {
    activeSlot: (state) => state.activeSlotId ? state.slots[state.activeSlotId - 1] : null
  },
  actions: {
    async hydrate() {
      this.slots = await Promise.all([1, 2, 3].map(
        async (id) => (await get<SaveSlot>(`slot:${id}`)) ?? null
      ))
    },
    async save() {
      const id = this.activeSlotId
      if (id === null) return
      const slot = this.slots[id - 1]
      if (!slot) return
      // Strip Pinia reactive proxy — IDB's structured clone can't handle Proxy objects
      const plain = JSON.parse(JSON.stringify(slot)) as SaveSlot
      await set(`slot:${id}`, plain)
    }
  }
})

