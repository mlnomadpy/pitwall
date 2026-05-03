import { defineStore } from 'pinia'

export const useDialogueStore = defineStore('dialogue', {
  state: () => ({
    queue: [] as string[],
    currentText: '',
    isTyping: false
  }),
  actions: {
    enqueue(text: string) {
      this.queue.push(text)
    },
    // Teletype progression logic
    next() {
      if (this.queue.length > 0) {
        this.currentText = this.queue.shift()!
        this.isTyping = true
      } else {
        this.currentText = ''
        this.isTyping = false
      }
    }
  }
})
