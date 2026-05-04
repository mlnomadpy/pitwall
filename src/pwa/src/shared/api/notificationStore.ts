import { defineStore } from 'pinia'

export type NotificationKind = 'debrief-ready' | 'medal-earned' | 'level-up' | 'affinity-tier' | 'track-unlock' | 'hardware-warning' | 'evolution-ready' | 'session-saved'

export interface AppNotification {
  id: string
  kind: NotificationKind
  title: string
  subText: string
  timestamp: string
  isRead: boolean
  route?: string
}

export const useNotificationsStore = defineStore('notifications', {
  state: () => ({
    items: [] as AppNotification[]
  }),
  getters: {
    unreadCount: (state) => state.items.filter(i => !i.isRead).length
  },
  actions: {
    markRead(id: string) {
      const item = this.items.find(i => i.id === id)
      if (item) item.isRead = true
    },
    markAllRead() {
      this.items.forEach(i => i.isRead = true)
    },
    add(notification: Omit<AppNotification, 'id' | 'isRead'>) {
      this.items.unshift({
        ...notification,
        id: Math.random().toString(36).substr(2, 9),
        isRead: false
      })
    }
  }
})
