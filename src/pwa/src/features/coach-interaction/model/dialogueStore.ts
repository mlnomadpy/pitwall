import { defineStore } from 'pinia'

export interface DialogueMessage {
  id: string
  text: string
  speaker: 'coach' | 'driver' | 'system'
  emotion?: 'neutral' | 'encouraging' | 'focused' | 'concerned' | 'excited'
  speed?: 'slow' | 'normal' | 'fast'
}

export const useDialogueStore = defineStore('dialogue', {
  state: () => ({
    queue: [] as DialogueMessage[],
    activeMessage: null as DialogueMessage | null,
    history: [] as DialogueMessage[],
    isTyping: false,
    typewriterSpeed: 30, // ms per char
  }),

  actions: {
    push(msg: Omit<DialogueMessage, 'id'>) {
      const message = { ...msg, id: Math.random().toString(36).substring(7) }
      this.queue.push(message)
      if (!this.activeMessage) {
        this.next()
      }
    },

    next() {
      if (this.activeMessage) {
        this.history.push(this.activeMessage)
        if (this.history.length > 50) this.history.shift()
      }

      if (this.queue.length > 0) {
        this.activeMessage = this.queue.shift()!
        this.isTyping = true
      } else {
        this.activeMessage = null
        this.isTyping = false
      }
    },

    finishTyping() {
      this.isTyping = false
    },

    clear() {
      this.queue = []
      this.activeMessage = null
      this.isTyping = false
    }
  }
})
