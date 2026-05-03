import { defineStore } from 'pinia'
import { get, set } from 'idb-keyval'
import type { SaveSlot } from '@/shared/types/save'

export const useSaveStore = defineStore('save', {
  state: () => ({
    slots: [null, null, null] as (SaveSlot | null)[],
    activeSlotId: null as 1 | 2 | 3 | null,
  }),
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
      if (slot) await set(`slot:${id}`, slot)
    }
  }
})
